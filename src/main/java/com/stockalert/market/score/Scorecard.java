package com.stockalert.market.score;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A rule-based summary of a stock across several parameters. It describes reported results
 * and past prices against fixed thresholds. It does not predict returns.
 *
 * @param score 0 to 100, or null when too little data is available to give one
 * @param confidence how complete and recent the data is, not how likely the price is to rise
 * @param stale true when the latest fetch failed and this is an older saved copy
 */
public record Scorecard(String symbol, Integer score, Band band, Confidence confidence, int parametersAvailable,
		int parametersTotal, LocalDate financialsAsOf, boolean financialCompany, List<Category> categories,
		Instant fetchedAt, boolean stale) {

	public enum Band {

		STRONG, MIXED, WEAK, INSUFFICIENT_DATA

	}

	public enum Confidence {

		HIGH, MEDIUM, LOW

	}

	public record Category(String name, Integer score, List<Parameter> parameters) {
	}

	/**
	 * @param value the formatted value, or null when the data is not available
	 * @param score 0 to 100, or null when the data is not available
	 * @param rule how the value is scored
	 */
	public record Parameter(String name, String value, Integer score, String rule) {
	}

	Scorecard asStale() {
		return new Scorecard(this.symbol, this.score, this.band, this.confidence, this.parametersAvailable,
				this.parametersTotal, this.financialsAsOf, this.financialCompany, this.categories, this.fetchedAt,
				true);
	}

}
