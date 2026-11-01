package com.stockalert.market.history;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.stockalert.common.ApiException;
import com.stockalert.market.yahoo.YahooPriceFeed;

import tools.jackson.databind.json.JsonMapper;

/**
 * History changes slowly and many viewers can ask for the same chart, so results are cached
 * in Redis. Copies are kept well past their freshness so that a chart still works, marked
 * as stale, when Yahoo is unavailable.
 */
@Service
public class HistoryService {

	private static final Duration KEEP_FOR = Duration.ofDays(7);

	private final StringRedisTemplate redis;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	private final ObjectProvider<YahooHistoryClient> client;

	private final ObjectProvider<YahooPriceFeed> feed;

	public HistoryService(StringRedisTemplate redis, JsonMapper jsonMapper, Clock clock,
			ObjectProvider<YahooHistoryClient> client, ObjectProvider<YahooPriceFeed> feed) {
		this.redis = redis;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.client = client;
		this.feed = feed;
	}

	public PriceHistory get(String symbol, ChartRange range) {
		YahooHistoryClient yahoo = this.client.getIfAvailable();
		YahooPriceFeed requests = this.feed.getIfAvailable();
		if (yahoo == null || requests == null) {
			throw new ApiException(HttpStatus.CONFLICT, "Price history needs the Yahoo Finance feed");
		}
		PriceHistory cached = read(symbol, range);
		Instant now = this.clock.instant();
		if (cached != null && now.isBefore(cached.fetchedAt().plus(range.freshFor()))) {
			return cached;
		}
		if (!requests.tryAcquireRequest()) {
			return staleOrUnavailable(cached);
		}
		try {
			Optional<PriceHistory> fetched = yahoo.fetch(symbol, range);
			if (fetched.isEmpty()) {
				if (cached != null) {
					return cached.asStale();
				}
				throw new ApiException(HttpStatus.NOT_FOUND, "No price history is available for " + symbol);
			}
			write(fetched.get());
			return fetched.get();
		}
		catch (IOException ex) {
			requests.reportUnavailable(ex);
			return staleOrUnavailable(cached);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return staleOrUnavailable(cached);
		}
	}

	private PriceHistory staleOrUnavailable(PriceHistory cached) {
		if (cached != null) {
			return cached.asStale();
		}
		throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
				"Price history isn't available right now. Please try again shortly.");
	}

	private PriceHistory read(String symbol, ChartRange range) {
		String json = this.redis.opsForValue().get(key(symbol, range.label()));
		return (json != null) ? this.jsonMapper.readValue(json, PriceHistory.class) : null;
	}

	private void write(PriceHistory history) {
		this.redis.opsForValue()
			.set(key(history.symbol(), history.range()), this.jsonMapper.writeValueAsString(history), KEEP_FOR);
	}

	private static String key(String symbol, String range) {
		return "history:" + symbol + ":" + range;
	}

}
