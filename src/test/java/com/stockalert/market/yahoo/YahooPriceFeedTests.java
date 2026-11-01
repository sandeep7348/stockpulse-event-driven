package com.stockalert.market.yahoo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.stockalert.config.AppProperties;
import com.stockalert.market.MarketHours;
import com.stockalert.market.PriceTick;
import com.stockalert.market.PriceTickPublisher;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

class YahooPriceFeedTests {

	private static final String USER_AGENT = "test-agent";

	// Monday 2 Nov 2026, 11:00 IST: inside market hours.
	private static final Instant MARKET_OPEN = Instant.parse("2026-11-02T05:30:00Z");

	// Monday 2 Nov 2026, 20:00 IST: outside market hours.
	private static final Instant MARKET_CLOSED = Instant.parse("2026-11-02T14:30:00Z");

	private static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

	private static final int MAX_PER_ROUND = 3;

	private static final int ON_DEMAND_PER_MINUTE = 2;

	private final List<PriceTick> published = new CopyOnWriteArrayList<>();

	private final List<String> requests = new CopyOnWriteArrayList<>();

	private final List<String> userAgents = new CopyOnWriteArrayList<>();

	private final Map<String, Response> responses = new ConcurrentHashMap<>();

	private final List<String> alerted = new CopyOnWriteArrayList<>();

	private final MutableClock clock = new MutableClock(MARKET_OPEN);

	private HttpServer server;

	private YahooPriceFeed feed;

	@BeforeEach
	void start() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		this.server.createContext("/v8/finance/chart/", (exchange) -> {
			String path = exchange.getRequestURI().getPath();
			String ticker = path.substring(path.lastIndexOf('/') + 1);
			this.requests.add(ticker);
			this.userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
			Response response = this.responses.getOrDefault(ticker, new Response(404, "{}"));
			byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(response.status(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		this.server.start();
		this.feed = newFeed();
		respond("RELIANCE.NS", 1164.90, 1793354297L);
		respond("TCS.NS", 3500.55, 1793354297L);
	}

	@AfterEach
	void stop() {
		this.server.stop(0);
	}

	@Test
	void publishesExactPaiseAndTradeTimeForEachStock() {
		this.feed.poll();

		assertThat(this.requests).containsExactly("RELIANCE.NS", "TCS.NS");
		assertThat(this.userAgents).containsOnly(USER_AGENT);
		assertThat(this.published).containsExactly(
				new PriceTick("RELIANCE", 116490, Instant.ofEpochSecond(1793354297L)),
				new PriceTick("TCS", 350055, Instant.ofEpochSecond(1793354297L)));
	}

	@Test
	void identicalPricesAreNotPublishedAgain() {
		this.feed.poll();
		this.feed.poll();
		assertThat(this.published).hasSize(2);

		respond("RELIANCE.NS", 1165.00, 1793354327L);
		this.feed.poll();

		assertThat(this.published).hasSize(3);
		assertThat(this.published.get(2).paise()).isEqualTo(116500);
	}

	@Test
	void pollsRarelyWhileTheMarketIsClosed() {
		this.clock.instant = MARKET_CLOSED;
		this.feed.poll();
		assertThat(this.requests).hasSize(2);

		this.clock.instant = MARKET_CLOSED.plus(Duration.ofMinutes(9));
		this.feed.poll();
		assertThat(this.requests).hasSize(2);

		this.clock.instant = MARKET_CLOSED.plus(Duration.ofMinutes(10));
		this.feed.poll();
		assertThat(this.requests).hasSize(4);
	}

	@Test
	void rateLimitStopsTheRoundAndBacksOffExponentially() {
		this.responses.put("RELIANCE.NS", new Response(429, "Too Many Requests"));

		this.feed.poll();
		assertThat(this.requests).containsExactly("RELIANCE.NS");
		assertThat(this.feed.health().getStatus()).isEqualTo(Status.DOWN);

		this.clock.instant = MARKET_OPEN.plus(POLL_INTERVAL).minusSeconds(1);
		this.feed.poll();
		assertThat(this.requests).hasSize(1);

		this.clock.instant = MARKET_OPEN.plus(POLL_INTERVAL);
		this.feed.poll();
		assertThat(this.requests).hasSize(2);

		Instant secondFailure = this.clock.instant;
		this.clock.instant = secondFailure.plus(POLL_INTERVAL);
		this.feed.poll();
		assertThat(this.requests).hasSize(2);

		this.clock.instant = secondFailure.plus(POLL_INTERVAL.multipliedBy(2));
		respond("RELIANCE.NS", 1164.90, 1793354297L);
		this.feed.poll();
		assertThat(this.published).hasSize(2);
		assertThat(this.feed.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void anUnknownStockDoesNotStopTheOthers() {
		this.responses.put("RELIANCE.NS", new Response(404, "{}"));

		this.feed.poll();

		assertThat(this.published).extracting(PriceTick::symbol).containsExactly("TCS");
		assertThat(this.feed.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void unusableResponsesAreSkipped() {
		this.responses.put("RELIANCE.NS", new Response(200, "not json"));
		this.responses.put("TCS.NS", new Response(200, "{\"chart\":{\"result\":null,\"error\":{\"code\":\"x\"}}}"));

		this.feed.poll();

		assertThat(this.published).isEmpty();
	}

	@Test
	void missingOrZeroPricesAreSkipped() {
		respond("RELIANCE.NS", 0, 1793354297L);
		this.responses.put("TCS.NS", new Response(200, "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"INR\"}}]}}"));

		this.feed.poll();

		assertThat(this.published).isEmpty();
	}

	@Test
	void stocksWithAlertsArePolledFirstAndNotTwice() {
		this.alerted.add("WIPRO");
		this.alerted.add("TCS");
		respond("WIPRO.NS", 198.31, 1793354297L);

		this.feed.poll();

		assertThat(this.requests).containsExactly("WIPRO.NS", "TCS.NS", "RELIANCE.NS");
	}

	@Test
	void stocksWithoutAlertsOrDefaultsAreNotPolled() {
		respond("WIPRO.NS", 198.31, 1793354297L);

		this.feed.poll();

		assertThat(this.requests).doesNotContain("WIPRO.NS");
	}

	@Test
	void roundsRotateAboveTheCapSoEveryStockGetsATurn() {
		this.alerted.addAll(List.of("A", "B", "C"));

		this.feed.poll();
		assertThat(this.requests).containsExactly("A.NS", "B.NS", "C.NS");

		this.requests.clear();
		this.feed.poll();
		assertThat(this.requests).containsExactly("RELIANCE.NS", "TCS.NS", "A.NS");
	}

	@Test
	void specialCharactersInSymbolsAreEncoded() {
		respond("M&M.NS", 2840.30, 1793354297L);

		assertThat(this.feed.fetchIfDue("M&M")).isPresent();
		assertThat(this.requests).containsExactly("M&M.NS");
	}

	@Test
	void onDemandFetchesAStockOnceThenWaitsForThePollInterval() {
		respond("WIPRO.NS", 198.31, 1793354297L);

		assertThat(this.feed.fetchIfDue("WIPRO")).isPresent();
		assertThat(this.published).hasSize(1);

		this.clock.instant = MARKET_OPEN.plus(POLL_INTERVAL).minusSeconds(1);
		assertThat(this.feed.fetchIfDue("WIPRO")).isEmpty();
		assertThat(this.requests).hasSize(1);

		this.clock.instant = MARKET_OPEN.plus(POLL_INTERVAL);
		assertThat(this.feed.fetchIfDue("WIPRO")).isPresent();
		assertThat(this.requests).hasSize(2);
	}

	@Test
	void onDemandSkipsStocksThatPollingJustFetched() {
		this.feed.poll();
		this.requests.clear();

		assertThat(this.feed.fetchIfDue("RELIANCE")).isEmpty();
		assertThat(this.requests).isEmpty();
	}

	@Test
	void onDemandStaysWithinItsBudgetPerMinute() {
		respond("A.NS", 10, 1793354297L);
		respond("B.NS", 20, 1793354297L);
		respond("C.NS", 30, 1793354297L);

		assertThat(this.feed.fetchIfDue("A")).isPresent();
		assertThat(this.feed.fetchIfDue("B")).isPresent();
		assertThat(this.feed.fetchIfDue("C")).isEmpty();
		assertThat(this.requests).hasSize(2);

		// A stock refused by the budget can be fetched once the budget renews.
		this.clock.instant = MARKET_OPEN.plus(Duration.ofMinutes(1));
		assertThat(this.feed.fetchIfDue("C")).isPresent();
	}

	@Test
	void onDemandRespectsBackoffAndCountsFailures() {
		this.responses.put("WIPRO.NS", new Response(429, "Too Many Requests"));

		assertThat(this.feed.fetchIfDue("WIPRO")).isEmpty();
		assertThat(this.feed.health().getStatus()).isEqualTo(Status.DOWN);

		respond("TCS.NS", 3500.55, 1793354297L);
		assertThat(this.feed.fetchIfDue("TCS")).isEmpty();
		assertThat(this.requests).containsExactly("WIPRO.NS");
	}

	private void respond(String ticker, double price, long time) {
		this.responses.put(ticker, new Response(200,
				"{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":%s,\"regularMarketTime\":%d}}],\"error\":null}}"
					.formatted(price, time)));
	}

	private YahooPriceFeed newFeed() {
		AppProperties properties = new AppProperties(null, new AppProperties.Feed("yahoo", null, false), null,
				new AppProperties.Yahoo("http://localhost:" + this.server.getAddress().getPort(), POLL_INTERVAL,
						USER_AGENT, MAX_PER_ROUND, ON_DEMAND_PER_MINUTE),
				new AppProperties.Market(List.of()),
				List.of(new AppProperties.Instrument("RELIANCE", "Reliance Industries", "INE002A01018",
						new BigDecimal("1400.00")),
						new AppProperties.Instrument("TCS", "TCS", "INE467B01029", new BigDecimal("3500.00"))));
		PriceTickPublisher publisher = new PriceTickPublisher(null, null, properties) {
			@Override
			public void publish(PriceTick tick) {
				YahooPriceFeedTests.this.published.add(tick);
			}
		};
		return new YahooPriceFeed(publisher, new MarketHours(properties), this.clock, JsonMapper.builder().build(),
				() -> this.alerted, properties);
	}

	private record Response(int status, String body) {
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
