package com.stockalert.market.history;

import java.time.Duration;

import org.springframework.http.HttpStatus;

import com.stockalert.common.ApiException;

/**
 * Each range fetches more history than it shows, so a 50-period moving average has values
 * from the first visible point. Exactly one of {@code sessions} and {@code days} limits
 * what is shown.
 */
public enum ChartRange {

	D1("1D", "5d", "5m", 1, 0, Duration.ofMinutes(5)),

	W1("1W", "1mo", "30m", 5, 0, Duration.ofMinutes(5)),

	M1("1M", "6mo", "1d", 0, 30, Duration.ofHours(1)),

	M6("6M", "1y", "1d", 0, 183, Duration.ofHours(1)),

	Y1("1Y", "2y", "1d", 0, 365, Duration.ofHours(1)),

	Y5("5Y", "10y", "1wk", 0, 5 * 365, Duration.ofHours(6));

	private final String label;

	private final String fetchRange;

	private final String interval;

	private final int sessions;

	private final int days;

	private final Duration freshFor;

	ChartRange(String label, String fetchRange, String interval, int sessions, int days, Duration freshFor) {
		this.label = label;
		this.fetchRange = fetchRange;
		this.interval = interval;
		this.sessions = sessions;
		this.days = days;
		this.freshFor = freshFor;
	}

	public static ChartRange parse(String label) {
		for (ChartRange range : values()) {
			if (range.label.equalsIgnoreCase(label)) {
				return range;
			}
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown chart range: " + label);
	}

	public String label() {
		return this.label;
	}

	public String fetchRange() {
		return this.fetchRange;
	}

	public String interval() {
		return this.interval;
	}

	public int sessions() {
		return this.sessions;
	}

	public int days() {
		return this.days;
	}

	public Duration freshFor() {
		return this.freshFor;
	}

}
