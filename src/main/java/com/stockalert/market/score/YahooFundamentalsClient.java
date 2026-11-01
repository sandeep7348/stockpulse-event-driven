package com.stockalert.market.score;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;
import com.stockalert.market.score.Fundamentals.Reported;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads company figures from Yahoo Finance's unofficial fundamentals endpoint, which needs no key. */
@Component
@ConditionalOnProperty(name = "app.feed.source", havingValue = "yahoo")
class YahooFundamentalsClient {

	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

	private static final Duration HISTORY = Duration.ofDays(6 * 365);

	private final JsonMapper jsonMapper;

	private final Clock clock;

	private final String baseUrl;

	private final String userAgent;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

	YahooFundamentalsClient(JsonMapper jsonMapper, Clock clock, AppProperties properties) {
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.baseUrl = properties.yahoo().baseUrl();
		this.userAgent = properties.yahoo().userAgent();
	}

	/**
	 * @return the figures Yahoo has, which may be none
	 * @throws IOException if Yahoo is unavailable or rate limiting
	 */
	Fundamentals fetch(String symbol) throws IOException, InterruptedException {
		String ticker = URLEncoder.encode(symbol + ".NS", StandardCharsets.UTF_8);
		long now = this.clock.instant().getEpochSecond();
		long from = now - HISTORY.toSeconds();
		HttpRequest request = HttpRequest
			.newBuilder(URI.create(this.baseUrl + "/ws/fundamentals-timeseries/v1/finance/timeseries/" + ticker
					+ "?type=" + String.join(",", Fundamentals.ALL_TYPES) + "&period1=" + from + "&period2=" + now))
			.timeout(HTTP_TIMEOUT)
			.header("User-Agent", this.userAgent)
			.header("Accept", "application/json")
			.GET()
			.build();
		HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			return new Fundamentals(Map.of());
		}
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		return parse(response.body());
	}

	private Fundamentals parse(String body) {
		Map<String, List<Reported>> series = new HashMap<>();
		for (JsonNode result : this.jsonMapper.readTree(body).path("timeseries").path("result")) {
			String type = result.path("meta").path("type").path(0).asString("");
			List<Reported> values = new ArrayList<>();
			// Years with no figure come back as null entries.
			for (JsonNode entry : result.path(type)) {
				JsonNode raw = entry.path("reportedValue").path("raw");
				if (raw.isNumber() && entry.hasNonNull("asOfDate")) {
					values.add(new Reported(LocalDate.parse(entry.path("asOfDate").asString()), raw.asDouble()));
				}
			}
			if (!values.isEmpty()) {
				values.sort(Comparator.comparing(Reported::date));
				series.put(type, values);
			}
		}
		return new Fundamentals(series);
	}

}
