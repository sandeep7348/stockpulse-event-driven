package com.stockalert.market.score;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Reported figures by Yahoo's type name, each list oldest first. */
record Fundamentals(Map<String, List<Reported>> series) {

	static final String PE = "trailingPeRatio";

	static final String PB = "trailingPbRatio";

	static final String REVENUE = "annualTotalRevenue";

	static final String NET_INCOME = "annualNetIncome";

	static final String EQUITY = "annualStockholdersEquity";

	static final String DEBT = "annualTotalDebt";

	static final String OPERATING_CASH_FLOW = "annualOperatingCashFlow";

	static final List<String> ALL_TYPES = List.of(PE, PB, REVENUE, NET_INCOME, EQUITY, DEBT, OPERATING_CASH_FLOW);

	List<Reported> of(String type) {
		return this.series.getOrDefault(type, List.of());
	}

	Double latest(String type) {
		List<Reported> values = of(type);
		return values.isEmpty() ? null : values.get(values.size() - 1).value();
	}

	record Reported(LocalDate date, double value) {
	}

}
