package com.stockalert.market;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;

/**
 * Latest price per symbol in Redis. The update runs as a Lua script so that checking the
 * timestamp and writing the price are atomic, and a redelivered old tick cannot overwrite
 * a newer price. Keys include the feed source so that simulated prices, which are stamped
 * with the current time, can never hide or pass for real ones after switching feeds.
 */
@Component
public class PriceStore {

	private static final RedisScript<Long> UPDATE_IF_NEWER = new DefaultRedisScript<>("""
			local ts = redis.call('HGET', KEYS[1], 'ts')
			if ts and tonumber(ts) > tonumber(ARGV[2]) then return 0 end
			redis.call('HSET', KEYS[1], 'paise', ARGV[1], 'ts', ARGV[2])
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;

	private final String source;

	public PriceStore(StringRedisTemplate redis, AppProperties properties) {
		this.redis = redis;
		this.source = properties.feed().source();
	}

	public boolean updateIfNewer(PriceTick tick) {
		Long updated = this.redis.execute(UPDATE_IF_NEWER, List.of(key(tick.symbol())), String.valueOf(tick.paise()),
				String.valueOf(tick.timestamp().toEpochMilli()));
		return Long.valueOf(1).equals(updated);
	}

	public Optional<PriceTick> latest(String symbol) {
		Map<Object, Object> entry = this.redis.opsForHash().entries(key(symbol));
		if (entry.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new PriceTick(symbol, Long.parseLong((String) entry.get("paise")),
				Instant.ofEpochMilli(Long.parseLong((String) entry.get("ts")))));
	}

	private String key(String symbol) {
		return "price:" + this.source + ":" + symbol;
	}

}
