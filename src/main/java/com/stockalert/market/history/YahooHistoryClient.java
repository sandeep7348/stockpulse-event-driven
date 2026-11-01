package com.stockalert.market.history;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;
import com.stockalert.market.MarketHours;
import com.stockalert.market.history.PriceHistory.Point;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "app.feed.source", havingValue = "yahoo")
public class YahooHistoryClient {

	private static final String SOURCE = "yahoo";

	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

	private static final int SHORT_AVERAGE = 20;

	private static final int LONG_AVERAGE = 50;

	private static final int RSI_PERIOD = 14;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	private final String baseUrl;

	private final String userAgent;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

	public YahooHistoryClient(JsonMapper jsonMapper, Clock clock, AppProperties properties) {
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.baseUrl = properties.yahoo().baseUrl();
		this.userAgent = properties.yahoo().userAgent();
	}

	/**
	 * @return the history, or empty if Yahoo has no data for this stock
	 * @throws IOException if Yahoo is unavailable or rate limiting
	 */
	public Optional<PriceHistory> fetch(String symbol, ChartRange range) throws IOException, InterruptedException {
		String ticker = URLEncoder.encode(symbol + ".NS", StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest
			.newBuilder(URI.create(this.baseUrl + "/v8/finance/chart/" + ticker + "?range=" + range.fetchRange()
					+ "&interval=" + range.interval()))
			.timeout(HTTP_TIMEOUT)
			.header("User-Agent", this.userAgent)
			.header("Accept", "application/json")
			.GET()
			.build();
		HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			return Optional.empty();
		}
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		return parse(symbol, range, response.body());
	}

	private Optional<PriceHistory> parse(String symbol, ChartRange range, String body) {
		JsonNode result = this.jsonMapper.readTree(body).path("chart").path("result").path(0);
		JsonNode times = result.path("timestamp");
		JsonNode quote = result.path("indicators").path("quote").path(0);
		List<Candle> candles = new ArrayList<>();
		for (int i = 0; i < times.size(); i++) {
			// Yahoo leaves gaps as nulls, such as the interval still in progress.
			JsonNode close = quote.path("close").path(i);
			if (close.isNumber()) {
				double closeValue = close.asDouble();
				candles.add(new Candle(Instant.ofEpochSecond(times.path(i).asLong()),
						quote.path("open").path(i).asDouble(closeValue), quote.path("high").path(i).asDouble(closeValue),
						quote.path("low").path(i).asDouble(closeValue), closeValue,
						quote.path("volume").path(i).asLong(0)));
			}
		}
		if (candles.isEmpty()) {
			return Optional.empty();
		}
		JsonNode meta = result.path("meta");
		return Optional.of(new PriceHistory(symbol, range.label(), range.interval(),
				meta.path("currency").asString("INR"), SOURCE, positiveOrNull(meta.path("fiftyTwoWeekHigh")),
				positiveOrNull(meta.path("fiftyTwoWeekLow")), this.clock.instant(), false, toPoints(candles, range)));
	}

	/** Indicators use all the fetched history; only the requested range is returned. */
	private List<Point> toPoints(List<Candle> candles, ChartRange range) {
		double[] closes = candles.stream().mapToDouble(Candle::close).toArray();
		Double[] ma20 = Indicators.sma(closes, SHORT_AVERAGE);
		Double[] ma50 = Indicators.sma(closes, LONG_AVERAGE);
		Double[] rsi = Indicators.rsi(closes, RSI_PERIOD);
		List<Point> points = new ArrayList<>();
		for (int i = firstVisible(candles, range); i < candles.size(); i++) {
			Candle candle = candles.get(i);
			points.add(new Point(candle.time(), round(candle.open()), round(candle.high()), round(candle.low()),
					round(candle.close()), candle.volume(), round(ma20[i]), round(ma50[i]), round(rsi[i])));
		}
		return points;
	}

	private int firstVisible(List<Candle> candles, ChartRange range) {
		if (range.days() > 0) {
			Instant cutoff = candles.get(candles.size() - 1).time().minus(Duration.ofDays(range.days()));
			for (int i = 0; i < candles.size(); i++) {
				if (!candles.get(i).time().isBefore(cutoff)) {
					return i;
				}
			}
			return 0;
		}
		int sessionsSeen = 0;
		LocalDate session = null;
		for (int i = candles.size() - 1; i >= 0; i--) {
			LocalDate date = candles.get(i).time().atZone(MarketHours.IST).toLocalDate();
			if (!date.equals(session)) {
				session = date;
				sessionsSeen++;
				if (sessionsSeen > range.sessions()) {
					return i + 1;
				}
			}
		}
		return 0;
	}

	private static Double positiveOrNull(JsonNode value) {
		return (value.isNumber() && value.asDouble() > 0) ? round(value.asDouble()) : null;
	}

	private static double round(double value) {
		return Math.round(value * 100) / 100.0;
	}

	private static Double round(Double value) {
		return (value != null) ? Double.valueOf(round(value.doubleValue())) : null;
	}

	private record Candle(Instant time, double open, double high, double low, double close, long volume) {
	}

}
