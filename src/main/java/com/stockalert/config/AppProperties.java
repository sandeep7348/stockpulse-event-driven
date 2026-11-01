package com.stockalert.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Feed feed, Upstox upstox, Yahoo yahoo, Market market,
		List<Instrument> instruments) {

	public record Jwt(String secret, Duration ttl) {
	}

	public record Feed(String source, Duration interval, boolean respectMarketHours) {
	}

	public record Upstox(String baseUrl, String accessToken) {
	}

	public record Yahoo(String baseUrl, Duration pollInterval, String userAgent, int maxPerRound,
			int onDemandPerMinute) {
	}

	public record Market(List<LocalDate> holidays) {
	}

	public record Instrument(String symbol, String name, String isin, BigDecimal basePrice) {

		public String upstoxKey() {
			return "NSE_EQ|" + this.isin;
		}

	}

}
