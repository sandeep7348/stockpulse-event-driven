package com.stockalert.market.upstox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.stockalert.config.AppProperties;
import com.stockalert.config.AppProperties.Instrument;
import com.stockalert.market.MarketHours;
import com.stockalert.market.PriceTick;
import com.stockalert.market.PriceTickPublisher;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streams live prices from the Upstox Market Data Feed V3 into {@link PriceTickPublisher}.
 * Each connection needs a new single-use URL from the authorize endpoint, so the URL is
 * never logged and every reconnect starts by authorizing again.
 */
@Component
@ConditionalOnProperty(name = "app.feed.source", havingValue = "upstox")
public class UpstoxPriceFeed implements HealthIndicator {

	private static final Logger log = LoggerFactory.getLogger(UpstoxPriceFeed.class);

	private static final String AUTHORIZE_PATH = "/v3/feed/market-data-feed/authorize";

	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

	private static final Duration INITIAL_RECONNECT_DELAY = Duration.ofSeconds(1);

	private static final Duration MAX_RECONNECT_DELAY = Duration.ofSeconds(60);

	private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(60);

	enum State {

		DISCONNECTED, CONNECTED, TOKEN_REJECTED

	}

	private final PriceTickPublisher publisher;

	private final MarketHours marketHours;

	private final Clock clock;

	private final JsonMapper jsonMapper;

	private final String baseUrl;

	private final String accessToken;

	private final List<String> instrumentKeys;

	private final UpstoxMessageDecoder decoder;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor((task) -> {
		Thread thread = new Thread(task, "upstox-feed");
		thread.setDaemon(true);
		return thread;
	});

	private volatile State state = State.DISCONNECTED;

	private volatile boolean stopped;

	private volatile int failedAttempts;

	private volatile Instant lastActivity;

	private volatile FeedListener currentListener;

	public UpstoxPriceFeed(PriceTickPublisher publisher, MarketHours marketHours, Clock clock, JsonMapper jsonMapper,
			AppProperties properties) {
		if (!StringUtils.hasText(properties.upstox().accessToken())) {
			throw new IllegalStateException("FEED_SOURCE is upstox but UPSTOX_ACCESS_TOKEN is not set");
		}
		this.publisher = publisher;
		this.marketHours = marketHours;
		this.clock = clock;
		this.jsonMapper = jsonMapper;
		this.baseUrl = properties.upstox().baseUrl();
		this.accessToken = properties.upstox().accessToken();
		this.instrumentKeys = properties.instruments().stream().map(Instrument::upstoxKey).toList();
		this.decoder = new UpstoxMessageDecoder(properties.instruments()
			.stream()
			.collect(Collectors.toMap(Instrument::upstoxKey, Instrument::symbol)));
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		this.executor.execute(this::connect);
	}

	@PreDestroy
	public void stop() {
		this.stopped = true;
		this.executor.shutdownNow();
		FeedListener listener = this.currentListener;
		if (listener != null) {
			listener.abort();
		}
	}

	/**
	 * A connection can die without either side noticing. Ticks should arrive constantly
	 * during market hours, so silence then means the connection needs replacing.
	 */
	@Scheduled(fixedDelayString = "PT15S")
	public void reconnectIfIdle() {
		Instant now = this.clock.instant();
		FeedListener listener = this.currentListener;
		if (this.state != State.CONNECTED || listener == null || !this.marketHours.isOpen(now)
				|| Duration.between(this.lastActivity, now).compareTo(IDLE_TIMEOUT) < 0) {
			return;
		}
		log.warn("No data from Upstox for {} during market hours; reconnecting", IDLE_TIMEOUT);
		listener.abort();
		listener.connectionLost();
	}

	@Override
	public Health health() {
		Health.Builder builder = (this.state == State.CONNECTED) ? Health.up() : Health.down();
		return builder.withDetail("state", this.state).build();
	}

	State getState() {
		return this.state;
	}

	private void connect() {
		if (this.stopped) {
			return;
		}
		try {
			URI feedUri = authorize();
			FeedListener listener = new FeedListener();
			this.httpClient.newWebSocketBuilder()
				.connectTimeout(HTTP_TIMEOUT)
				.buildAsync(feedUri, listener)
				.whenComplete((webSocket, ex) -> {
					if (ex != null) {
						log.warn("Could not open the Upstox feed: {}", ex.getClass().getSimpleName());
						scheduleReconnect();
					}
				});
		}
		catch (TokenRejectedException ex) {
			this.state = State.TOKEN_REJECTED;
			log.error("Upstox rejected the access token. Tokens expire daily at 3:30 AM: generate a new one, "
					+ "update UPSTOX_ACCESS_TOKEN and restart. No live prices until then.");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Could not authorize with Upstox: {}", ex.getMessage());
			scheduleReconnect();
		}
	}

	private URI authorize() throws IOException, InterruptedException, TokenRejectedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(this.baseUrl + AUTHORIZE_PATH))
			.timeout(HTTP_TIMEOUT)
			.header("Authorization", "Bearer " + this.accessToken)
			.header("Accept", "application/json")
			.GET()
			.build();
		HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 401 || response.statusCode() == 403) {
			throw new TokenRejectedException();
		}
		if (response.statusCode() != 200) {
			throw new IOException("authorize returned HTTP " + response.statusCode());
		}
		String uri = this.jsonMapper.readTree(response.body()).path("data").path("authorized_redirect_uri").asString();
		if (!StringUtils.hasText(uri)) {
			throw new IOException("authorize response had no feed URL");
		}
		return URI.create(uri);
	}

	private void scheduleReconnect() {
		if (this.stopped) {
			return;
		}
		long delay = Math.min(INITIAL_RECONNECT_DELAY.toMillis() << Math.min(this.failedAttempts, 16),
				MAX_RECONNECT_DELAY.toMillis());
		this.failedAttempts++;
		log.info("Reconnecting to Upstox in {} ms", delay);
		this.executor.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
	}

	private byte[] subscribeMessage() {
		Map<String, Object> message = Map.of("guid", UUID.randomUUID().toString(), "method", "sub", "data",
				Map.of("mode", "ltpc", "instrumentKeys", this.instrumentKeys));
		return this.jsonMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
	}

	private void handleMessage(byte[] message) {
		try {
			List<PriceTick> ticks = this.decoder.decode(message);
			if (!ticks.isEmpty()) {
				this.failedAttempts = 0;
			}
			ticks.forEach(this.publisher::publish);
		}
		catch (InvalidProtocolBufferException ex) {
			log.warn("Ignoring an Upstox message that could not be decoded");
		}
	}

	private final class FeedListener implements WebSocket.Listener {

		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		private final AtomicBoolean lost = new AtomicBoolean();

		private volatile WebSocket webSocket;

		@Override
		public void onOpen(WebSocket webSocket) {
			this.webSocket = webSocket;
			UpstoxPriceFeed.this.currentListener = this;
			UpstoxPriceFeed.this.lastActivity = UpstoxPriceFeed.this.clock.instant();
			UpstoxPriceFeed.this.state = State.CONNECTED;
			log.info("Connected to Upstox; subscribing to {} instruments", UpstoxPriceFeed.this.instrumentKeys.size());
			// Upstox requires the subscribe request as a binary frame, not text.
			webSocket.sendBinary(ByteBuffer.wrap(subscribeMessage()), true);
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
			UpstoxPriceFeed.this.lastActivity = UpstoxPriceFeed.this.clock.instant();
			byte[] chunk = new byte[data.remaining()];
			data.get(chunk);
			this.buffer.writeBytes(chunk);
			if (last) {
				handleMessage(this.buffer.toByteArray());
				this.buffer.reset();
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
			UpstoxPriceFeed.this.lastActivity = UpstoxPriceFeed.this.clock.instant();
			return WebSocket.Listener.super.onPing(webSocket, message);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			log.warn("Upstox closed the feed with status {}", statusCode);
			connectionLost();
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			log.warn("Upstox feed error: {}", error.getClass().getSimpleName());
			connectionLost();
		}

		void abort() {
			if (this.webSocket != null) {
				this.webSocket.abort();
			}
		}

		// Both close and error callbacks can fire for one connection, but it reconnects once.
		void connectionLost() {
			if (this.lost.compareAndSet(false, true)) {
				UpstoxPriceFeed.this.state = State.DISCONNECTED;
				scheduleReconnect();
			}
		}

	}

	private static final class TokenRejectedException extends Exception {

	}

}
