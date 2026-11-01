package com.stockalert.market.history;

import java.time.Instant;
import java.util.List;

/**
 * @param stale true when the latest fetch failed and this is an older saved copy
 */
public record PriceHistory(String symbol, String range, String interval, String currency, String source,
		Double fiftyTwoWeekHigh, Double fiftyTwoWeekLow, Instant fetchedAt, boolean stale, List<Point> points) {

	PriceHistory asStale() {
		return new PriceHistory(this.symbol, this.range, this.interval, this.currency, this.source,
				this.fiftyTwoWeekHigh, this.fiftyTwoWeekLow, this.fetchedAt, true, this.points);
	}

	public record Point(Instant time, double open, double high, double low, double close, long volume, Double ma20,
			Double ma50, Double rsi14) {
	}

}
