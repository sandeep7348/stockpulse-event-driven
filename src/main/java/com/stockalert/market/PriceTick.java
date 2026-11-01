package com.stockalert.market;

import java.time.Instant;

/**
 * @param source the feed that produced the tick, set when it is published
 */
public record PriceTick(String symbol, long paise, Instant timestamp, String source) {

	public PriceTick(String symbol, long paise, Instant timestamp) {
		this(symbol, paise, timestamp, null);
	}

	PriceTick withSource(String newSource) {
		return new PriceTick(this.symbol, this.paise, this.timestamp, newSource);
	}

}
