package com.stockalert.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Finds the alerts a price has crossed without scanning every alert. Each symbol and
 * direction has a Redis sorted set of alert ids scored by target price, so a tick costs
 * O(log n + matches). MongoDB stays the source of truth: this index can be rebuilt from it.
 */
@Component
public class AlertIndex {

	private final StringRedisTemplate redis;

	public AlertIndex(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void add(Alert alert) {
		this.redis.opsForZSet()
			.add(key(alert.getSymbol(), alert.getDirection()), alert.getId(), alert.getTargetPaise());
	}

	public void remove(String symbol, Direction direction, String alertId) {
		this.redis.opsForZSet().remove(key(symbol, direction), alertId);
	}

	public List<Match> findCrossed(String symbol, long pricePaise) {
		List<Match> matches = new ArrayList<>();
		collect(matches, Direction.ABOVE, this.redis.opsForZSet()
			.rangeByScore(key(symbol, Direction.ABOVE), Double.NEGATIVE_INFINITY, pricePaise));
		collect(matches, Direction.BELOW, this.redis.opsForZSet()
			.rangeByScore(key(symbol, Direction.BELOW), pricePaise, Double.POSITIVE_INFINITY));
		return matches;
	}

	private static void collect(List<Match> matches, Direction direction, Set<String> alertIds) {
		if (alertIds != null) {
			alertIds.forEach((alertId) -> matches.add(new Match(alertId, direction)));
		}
	}

	private static String key(String symbol, Direction direction) {
		return "alerts:" + symbol + ":" + direction;
	}

	public record Match(String alertId, Direction direction) {
	}

}
