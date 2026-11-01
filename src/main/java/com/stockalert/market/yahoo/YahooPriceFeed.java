package com.stockalert.market.yahoo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;
import com.stockalert.config.AppProperties.Instrument;
import com.stockalert.market.AlertedSymbols;
import com.stockalert.market.MarketHours;
import com.stockalert.market.OnDemandPriceSource;
import com.stockalert.market.PriceTick;
import com.stockalert.market.PriceTickPublisher;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Polls Yahoo Finance's chart endpoint for NSE prices. It needs no account or API key, but
 * it is unofficial and may change without notice, and polling can miss moves between
 * requests. Suitable for personal use and demos, not a commercial product.
 * <p>
 * Yahoo is asked one stock at a time, so polling all of NSE would mean thousands of requests
 * a minute. Only stocks in use are polled: those with active alerts, then the default
 * watchlist. Any other stock is fetched when someone looks at it, within a request budget.
 */
@Component
@ConditionalOnProperty(name = "app.feed.source", havingValue = "yahoo")
public class YahooPriceFeed implements HealthIndicator, OnDemandPriceSource {

	private static final Logger log = LoggerFactory.getLogger(YahooPriceFeed.class);

	private static final String NSE_SUFFIX = ".NS";

	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration CLOSED_MARKET_INTERVAL = Duration.ofMinutes(10);

	private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

	private static final Duration BUDGET_WINDOW = Duration.ofMinutes(1);

	private final PriceTickPublisher publisher;

	private final MarketHours marketHours;

	private final Clock clock;

	private final JsonMapper jsonMapper;

	private final AlertedSymbols alertedSymbols;

	private final String baseUrl;

	private final String userAgent;

	private final Duration pollInterval;

	private final int maxPerRound;

	private final int onDemandPerMinute;

	private final List<String> defaultSymbols;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

	private final Map<String, PriceTick> lastPublished = new ConcurrentHashMap<>();

	private final Map<String, Instant> lastFetched = new ConcurrentHashMap<>();

	private volatile Instant retryAfter = Instant.EPOCH;

	private Instant nextClosedMarketPoll = Instant.EPOCH;

	private int roundCursor;

	private volatile int consecutiveFailures;

	private Instant budgetWindowStart = Instant.EPOCH;

	private int budgetUsed;

	public YahooPriceFeed(PriceTickPublisher publisher, MarketHours marketHours, Clock clock, JsonMapper jsonMapper,
			AlertedSymbols alertedSymbols, AppProperties properties) {
		this.publisher = publisher;
		this.marketHours = marketHours;
		this.clock = clock;
		this.jsonMapper = jsonMapper;
		this.alertedSymbols = alertedSymbols;
		this.baseUrl = properties.yahoo().baseUrl();
		this.userAgent = properties.yahoo().userAgent();
		this.pollInterval = properties.yahoo().pollInterval();
		this.maxPerRound = properties.yahoo().maxPerRound();
		this.onDemandPerMinute = properties.yahoo().onDemandPerMinute();
		this.defaultSymbols = properties.instruments().stream().map(Instrument::symbol).toList();
	}

	/**
	 * A rate limit or outage stops the round and backs off, so a struggling service does not
	 * receive more requests.
	 */
	@Scheduled(fixedDelayString = "${app.yahoo.poll-interval}")
	public void poll() {
		Instant now = this.clock.instant();
		if (now.isBefore(this.retryAfter)) {
			return;
		}
		// Prices barely change while closed, but polling occasionally still picks up the close.
		if (!this.marketHours.isOpen(now) && now.isBefore(this.nextClosedMarketPoll)) {
			return;
		}
		try {
			for (String symbol : nextRound()) {
				fetch(symbol, now).ifPresent(this::publishIfNew);
			}
			this.consecutiveFailures = 0;
			this.nextClosedMarketPoll = now.plus(CLOSED_MARKET_INTERVAL);
		}
		catch (IOException ex) {
			recordFailure(now, ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public Optional<PriceTick> fetchIfDue(String symbol) {
		Instant now = this.clock.instant();
		if (now.isBefore(this.retryAfter) || !claimFetch(symbol, now)) {
			return Optional.empty();
		}
		if (!takeFromBudget(now)) {
			// Not fetched after all, so a later request may still try.
			this.lastFetched.remove(symbol);
			return Optional.empty();
		}
		try {
			Optional<PriceTick> tick = fetch(symbol, now);
			tick.ifPresent(this::publishIfNew);
			return tick;
		}
		catch (IOException ex) {
			recordFailure(now, ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		return Optional.empty();
	}

	/** Lets other Yahoo requests share this feed's backoff and on-demand budget. */
	public boolean tryAcquireRequest() {
		Instant now = this.clock.instant();
		return !now.isBefore(this.retryAfter) && takeFromBudget(now);
	}

	public void reportUnavailable(IOException ex) {
		recordFailure(this.clock.instant(), ex);
	}

	@Override
	public Health health() {
		Health.Builder builder = (this.consecutiveFailures == 0) ? Health.up() : Health.down();
		return builder.withDetail("consecutiveFailures", this.consecutiveFailures).build();
	}

	/** Alerted stocks come first. Past the cap, rounds rotate so every stock still gets a turn. */
	private List<String> nextRound() {
		Set<String> inUse = new LinkedHashSet<>(this.alertedSymbols.symbols());
		inUse.addAll(this.defaultSymbols);
		List<String> all = new ArrayList<>(inUse);
		if (all.size() <= this.maxPerRound) {
			return all;
		}
		log.info("{} stocks in use, above the limit of {} per round: each is polled less often", all.size(),
				this.maxPerRound);
		List<String> round = new ArrayList<>(this.maxPerRound);
		for (int i = 0; i < this.maxPerRound; i++) {
			round.add(all.get((this.roundCursor + i) % all.size()));
		}
		this.roundCursor = (this.roundCursor + this.maxPerRound) % all.size();
		return round;
	}

	/** Prevents repeat requests for one stock, including from viewers polling the same page. */
	private boolean claimFetch(String symbol, Instant now) {
		Duration minAge = this.marketHours.isOpen(now) ? this.pollInterval : CLOSED_MARKET_INTERVAL;
		AtomicBoolean claimed = new AtomicBoolean();
		this.lastFetched.compute(symbol, (key, previous) -> {
			if (previous == null || !now.isBefore(previous.plus(minAge))) {
				claimed.set(true);
				return now;
			}
			return previous;
		});
		return claimed.get();
	}

	private synchronized boolean takeFromBudget(Instant now) {
		if (!now.isBefore(this.budgetWindowStart.plus(BUDGET_WINDOW))) {
			this.budgetWindowStart = now;
			this.budgetUsed = 0;
		}
		if (this.budgetUsed >= this.onDemandPerMinute) {
			return false;
		}
		this.budgetUsed++;
		return true;
	}

	private synchronized void recordFailure(Instant now, IOException ex) {
		this.consecutiveFailures++;
		Duration delay = backoff();
		this.retryAfter = now.plus(delay);
		log.warn("Yahoo Finance is unavailable ({}); retrying in {}", ex.getMessage(), delay);
	}

	private Optional<PriceTick> fetch(String symbol, Instant now) throws IOException, InterruptedException {
		this.lastFetched.put(symbol, now);
		String ticker = URLEncoder.encode(symbol + NSE_SUFFIX, StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest
			.newBuilder(URI.create(this.baseUrl + "/v8/finance/chart/" + ticker + "?interval=1d&range=1d"))
			.timeout(HTTP_TIMEOUT)
			.header("User-Agent", this.userAgent)
			.header("Accept", "application/json")
			.GET()
			.build();
		HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			log.warn("Yahoo Finance does not recognise {}", ticker);
			return Optional.empty();
		}
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		return Optional.ofNullable(parse(symbol, response.body()));
	}

	// Identical responses are not new prices, so they do not need to go through Kafka.
	private void publishIfNew(PriceTick tick) {
		if (!tick.equals(this.lastPublished.put(tick.symbol(), tick))) {
			this.publisher.publish(tick);
		}
	}

	private PriceTick parse(String symbol, String body) {
		try {
			JsonNode meta = this.jsonMapper.readTree(body).path("chart").path("result").path(0).path("meta");
			double price = meta.path("regularMarketPrice").asDouble(0);
			long epochSeconds = meta.path("regularMarketTime").asLong(0);
			if (price > 0 && epochSeconds > 0) {
				return new PriceTick(symbol, Math.round(price * 100), Instant.ofEpochSecond(epochSeconds));
			}
		}
		catch (JacksonException ex) {
			log.debug("Unreadable Yahoo Finance response", ex);
		}
		log.warn("Yahoo Finance response for {} had no usable price; its format may have changed", symbol);
		return null;
	}

	private Duration backoff() {
		int doublings = Math.min(this.consecutiveFailures - 1, 10);
		Duration delay = this.pollInterval.multipliedBy(1L << doublings);
		return (delay.compareTo(MAX_BACKOFF) > 0) ? MAX_BACKOFF : delay;
	}

}
