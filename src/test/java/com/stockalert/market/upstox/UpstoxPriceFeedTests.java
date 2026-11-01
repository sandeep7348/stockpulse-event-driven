package com.stockalert.market.upstox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.stockalert.config.AppProperties;
import com.stockalert.market.MarketHours;
import com.stockalert.market.PriceTick;
import com.stockalert.market.PriceTickPublisher;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class UpstoxPriceFeedTests {

	private static final String RELIANCE_KEY = "NSE_EQ|INE002A01018";

	private static final String TOKEN = "test-token";

	// Monday 2 Nov 2026, 11:00 IST: inside market hours.
	private static final Instant MARKET_OPEN = Instant.parse("2026-11-02T05:30:00Z");

	// Monday 2 Nov 2026, 20:00 IST: outside market hours.
	private static final Instant MARKET_CLOSED = Instant.parse("2026-11-02T14:30:00Z");

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final JsonMapper json = JsonMapper.builder().build();

	private final List<PriceTick> published = new CopyOnWriteArrayList<>();

	private final MutableClock clock = new MutableClock(MARKET_OPEN);

	private FakeUpstoxServer server;

	private UpstoxPriceFeed feed;

	@BeforeEach
	void startServer() throws Exception {
		this.server = new FakeUpstoxServer();
		this.feed = newFeed(TOKEN);
	}

	@AfterEach
	void stopServer() throws Exception {
		this.feed.stop();
		this.server.close();
	}

	@Test
	void authorizesThenSubscribesWithABinaryJsonMessage() throws Exception {
		connect();

		await().atMost(TIMEOUT).until(() -> !this.server.binaryFrames.isEmpty());
		assertThat(this.server.authorizationHeaders).containsExactly("Bearer " + TOKEN);
		assertThat(this.server.textFrames).isEmpty();
		JsonNode subscribe = this.json.readTree(this.server.binaryFrames.get(0));
		assertThat(subscribe.path("method").asString()).isEqualTo("sub");
		assertThat(subscribe.path("guid").asString()).isNotBlank();
		assertThat(subscribe.path("data").path("mode").asString()).isEqualTo("ltpc");
		assertThat(subscribe.path("data").path("instrumentKeys").get(0).asString()).isEqualTo(RELIANCE_KEY);
		assertThat(this.feed.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void publishesPricesFromCompleteAndFragmentedMessages() throws Exception {
		connect();

		this.server.send(UpstoxMessageDecoderTests.message(RELIANCE_KEY, 1400.50, 1000L));
		this.server.sendFragmented(UpstoxMessageDecoderTests.message(RELIANCE_KEY, 1400.60, 2000L));

		await().atMost(TIMEOUT).until(() -> this.published.size() == 2);
		assertThat(this.published).containsExactly(new PriceTick("RELIANCE", 140050, Instant.ofEpochMilli(1000L)),
				new PriceTick("RELIANCE", 140060, Instant.ofEpochMilli(2000L)));
	}

	@Test
	void undecodableMessageDoesNotBreakTheConnection() throws Exception {
		connect();

		this.server.send(new byte[] { (byte) 0xFF, (byte) 0xFF, 0x01 });
		this.server.send(UpstoxMessageDecoderTests.message(RELIANCE_KEY, 1400.50, 1000L));

		await().atMost(TIMEOUT).until(() -> this.published.size() == 1);
		assertThat(this.server.requestedCodes).hasSize(1);
	}

	@Test
	void reconnectsAfterADropWithANewSingleUseUrlAndResubscribes() throws Exception {
		connect();

		this.server.dropConnection();

		await().atMost(TIMEOUT).until(() -> this.server.binaryFrames.size() == 2);
		assertThat(this.server.requestedCodes).containsExactly("1", "2");
		this.server.send(UpstoxMessageDecoderTests.message(RELIANCE_KEY, 1400.50, 1000L));
		await().atMost(TIMEOUT).until(() -> this.published.size() == 1);
	}

	@Test
	void retriesWhenUpstoxIsTemporarilyUnavailable() {
		this.server.respondToNextAuthorizeWith(500);

		connect();

		assertThat(this.server.authorizationHeaders).hasSize(2);
	}

	@Test
	void stopsRetryingWhenTheTokenIsRejected() throws Exception {
		this.server.respondToNextAuthorizeWith(401);

		this.feed.start();

		await().atMost(TIMEOUT).until(() -> this.feed.getState() == UpstoxPriceFeed.State.TOKEN_REJECTED);
		TimeUnit.MILLISECONDS.sleep(2500);
		assertThat(this.server.authorizationHeaders).hasSize(1);
		assertThat(this.feed.health().getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	void reconnectsWhenSilentDuringMarketHours() throws Exception {
		connect();

		this.clock.instant = MARKET_OPEN.plusSeconds(61);
		this.feed.reconnectIfIdle();

		await().atMost(TIMEOUT).until(() -> this.server.binaryFrames.size() == 2);
		assertThat(this.server.requestedCodes).hasSize(2);
	}

	@Test
	void staysConnectedWhenRecentlyActiveDuringMarketHours() throws Exception {
		connect();

		this.clock.instant = MARKET_OPEN.plusSeconds(59);
		this.feed.reconnectIfIdle();
		TimeUnit.MILLISECONDS.sleep(1500);

		assertThat(this.server.requestedCodes).hasSize(1);
	}

	@Test
	void silenceOutsideMarketHoursIsNormal() throws Exception {
		this.clock.instant = MARKET_CLOSED;
		connect();

		this.clock.instant = MARKET_CLOSED.plusSeconds(3600);
		this.feed.reconnectIfIdle();
		TimeUnit.MILLISECONDS.sleep(1500);

		assertThat(this.server.requestedCodes).hasSize(1);
		assertThat(this.feed.getState()).isEqualTo(UpstoxPriceFeed.State.CONNECTED);
	}

	@Test
	void refusesToStartWithoutAToken() {
		assertThatIllegalStateException().isThrownBy(() -> newFeed(" "))
			.withMessageContaining("UPSTOX_ACCESS_TOKEN");
	}

	private void connect() {
		this.feed.start();
		await().atMost(TIMEOUT).until(() -> this.feed.getState() == UpstoxPriceFeed.State.CONNECTED
				&& !this.server.binaryFrames.isEmpty());
	}

	private UpstoxPriceFeed newFeed(String token) {
		AppProperties properties = new AppProperties(null, new AppProperties.Feed("upstox", null, false),
				new AppProperties.Upstox(this.server.baseUrl(), token), null, new AppProperties.Market(List.of()),
				List.of(new AppProperties.Instrument("RELIANCE", "Reliance Industries", "INE002A01018",
						new BigDecimal("1400.00"))));
		PriceTickPublisher publisher = new PriceTickPublisher(null, null, properties) {
			@Override
			public void publish(PriceTick tick) {
				UpstoxPriceFeedTests.this.published.add(tick);
			}
		};
		return new UpstoxPriceFeed(publisher, new MarketHours(properties), this.clock, this.json, properties);
	}

	private static final class MutableClock extends Clock {

		private volatile Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public Instant instant() {
			return this.instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
