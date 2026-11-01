package com.stockalert.market.score;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.stockalert.market.score.Fundamentals.Reported;
import com.stockalert.market.score.Scorecard.Band;
import com.stockalert.market.score.Scorecard.Category;
import com.stockalert.market.score.Scorecard.Confidence;
import com.stockalert.market.score.Scorecard.Parameter;

/**
 * Scores each parameter from 0 to 100 by straight-line interpolation between a worst and a
 * best value. Each category is the average of its available parameters, and the total is the
 * average of the categories, so a category with missing data does not count as a low score.
 * The thresholds are general rules of thumb, not comparisons with a stock's own sector.
 */
final class ScoreCalculator {

	private static final int STRONG_FROM = 70;

	private static final int MIXED_FROM = 40;

	private static final double MIN_COVERAGE_FOR_SCORE = 0.5;

	private static final double HIGH_CONFIDENCE_COVERAGE = 0.85;

	private static final double MEDIUM_CONFIDENCE_COVERAGE = 0.6;

	private static final int FRESH_FINANCIALS_MONTHS = 18;

	private static final int OLD_FINANCIALS_MONTHS = 30;

	private static final int TRADING_DAYS_PER_YEAR = 252;

	private static final int LONG_AVERAGE_DAYS = 200;

	private static final int SHORT_AVERAGE_DAYS = 50;

	private ScoreCalculator() {
	}

	static Scorecard calculate(String symbol, Fundamentals fundamentals, double[] dailyCloses, boolean financialCompany,
			LocalDate today, Instant fetchedAt) {
		List<Category> categories = new ArrayList<>();
		categories.add(category("Profitability", returnOnEquity(fundamentals), netMargin(fundamentals)));
		categories.add(category("Growth", revenueGrowth(fundamentals), profitGrowth(fundamentals)));
		// A lender's deposits count as debt and its cash flow reflects lending, so these would mislead.
		if (!financialCompany) {
			categories.add(category("Financial health", debtToEquity(fundamentals), cashConversion(fundamentals)));
		}
		categories.add(category("Valuation", priceToEarnings(fundamentals), priceToBook(fundamentals)));
		categories.add(category("Price trend", versusLongAverage(dailyCloses), shortVersusLongAverage(dailyCloses),
				oneYearReturn(dailyCloses)));
		categories.add(category("Risk", volatility(dailyCloses), maxDrawdown(dailyCloses)));

		int total = categories.stream().mapToInt((category) -> category.parameters().size()).sum();
		int available = (int) categories.stream()
			.flatMap((category) -> category.parameters().stream())
			.filter((parameter) -> parameter.score() != null)
			.count();
		double coverage = (double) available / total;
		LocalDate financialsAsOf = latestFinancialsDate(fundamentals);
		Integer score = (coverage >= MIN_COVERAGE_FOR_SCORE) ? overall(categories) : null;
		return new Scorecard(symbol, score, band(score), confidence(coverage, financialsAsOf, today), available, total,
				financialsAsOf, financialCompany, categories, fetchedAt, false);
	}

	private static Category category(String name, Parameter... parameters) {
		List<Parameter> list = List.of(parameters);
		double average = list.stream()
			.map(Parameter::score)
			.filter(Objects::nonNull)
			.mapToInt(Integer::intValue)
			.average()
			.orElse(Double.NaN);
		return new Category(name, Double.isNaN(average) ? null : (int) Math.round(average), list);
	}

	private static Integer overall(List<Category> categories) {
		double average = categories.stream()
			.map(Category::score)
			.filter(Objects::nonNull)
			.mapToInt(Integer::intValue)
			.average()
			.orElse(Double.NaN);
		return Double.isNaN(average) ? null : (int) Math.round(average);
	}

	private static Band band(Integer score) {
		if (score == null) {
			return Band.INSUFFICIENT_DATA;
		}
		return (score >= STRONG_FROM) ? Band.STRONG : (score >= MIXED_FROM) ? Band.MIXED : Band.WEAK;
	}

	private static Confidence confidence(double coverage, LocalDate financialsAsOf, LocalDate today) {
		long age = (financialsAsOf != null) ? ChronoUnit.MONTHS.between(financialsAsOf, today) : Long.MAX_VALUE;
		if (coverage < MEDIUM_CONFIDENCE_COVERAGE || age > OLD_FINANCIALS_MONTHS) {
			return Confidence.LOW;
		}
		return (coverage >= HIGH_CONFIDENCE_COVERAGE && age <= FRESH_FINANCIALS_MONTHS) ? Confidence.HIGH
				: Confidence.MEDIUM;
	}

	private static LocalDate latestFinancialsDate(Fundamentals fundamentals) {
		return List.of(Fundamentals.REVENUE, Fundamentals.NET_INCOME, Fundamentals.EQUITY)
			.stream()
			.map(fundamentals::of)
			.filter((values) -> !values.isEmpty())
			.map((values) -> values.get(values.size() - 1).date())
			.max(LocalDate::compareTo)
			.orElse(null);
	}

	private static Parameter returnOnEquity(Fundamentals data) {
		Double income = data.latest(Fundamentals.NET_INCOME);
		Double equity = data.latest(Fundamentals.EQUITY);
		String rule = "Net profit as a share of shareholders' equity. Full marks at 15% or more, zero at 0% or less.";
		if (income == null || equity == null || equity <= 0) {
			return missing("Return on equity", rule);
		}
		double value = income / equity * 100;
		return scored("Return on equity", percent(value), linear(value, 0, 15), rule);
	}

	private static Parameter netMargin(Fundamentals data) {
		Double income = data.latest(Fundamentals.NET_INCOME);
		Double revenue = data.latest(Fundamentals.REVENUE);
		String rule = "Net profit as a share of revenue. Full marks at 15% or more, zero at 0% or less.";
		if (income == null || revenue == null || revenue <= 0) {
			return missing("Net profit margin", rule);
		}
		double value = income / revenue * 100;
		return scored("Net profit margin", percent(value), linear(value, 0, 15), rule);
	}

	private static Parameter revenueGrowth(Fundamentals data) {
		String rule = "Average yearly revenue growth over the reported years. Full marks at 15% or more, zero at 0% or less.";
		Double growth = yearlyGrowth(data.of(Fundamentals.REVENUE));
		return (growth != null) ? scored("Revenue growth", percent(growth), linear(growth, 0, 15), rule)
				: missing("Revenue growth", rule);
	}

	private static Parameter profitGrowth(Fundamentals data) {
		String rule = "Average yearly net profit growth over the reported years. Full marks at 15% or more, zero at 0% or less or a current loss.";
		List<Reported> income = data.of(Fundamentals.NET_INCOME);
		if (income.size() >= 2 && income.get(income.size() - 1).value() <= 0) {
			return scored("Profit growth", "Loss-making", 0, rule);
		}
		Double growth = yearlyGrowth(income);
		return (growth != null) ? scored("Profit growth", percent(growth), linear(growth, 0, 15), rule)
				: missing("Profit growth", rule);
	}

	/** Compound growth per year. Not meaningful from a loss, so that returns null. */
	static Double yearlyGrowth(List<Reported> values) {
		if (values.size() < 2) {
			return null;
		}
		Reported first = values.get(0);
		Reported last = values.get(values.size() - 1);
		double years = ChronoUnit.DAYS.between(first.date(), last.date()) / 365.25;
		if (first.value() <= 0 || last.value() <= 0 || years <= 0) {
			return null;
		}
		return (Math.pow(last.value() / first.value(), 1 / years) - 1) * 100;
	}

	private static Parameter debtToEquity(Fundamentals data) {
		Double debt = data.latest(Fundamentals.DEBT);
		Double equity = data.latest(Fundamentals.EQUITY);
		String rule = "Total debt divided by shareholders' equity. Full marks at 0.5 or less, zero at 2 or more.";
		if (debt == null || equity == null || equity <= 0) {
			return missing("Debt to equity", rule);
		}
		double value = debt / equity;
		return scored("Debt to equity", number(value), linear(value, 2, 0.5), rule);
	}

	private static Parameter cashConversion(Fundamentals data) {
		Double cash = data.latest(Fundamentals.OPERATING_CASH_FLOW);
		Double income = data.latest(Fundamentals.NET_INCOME);
		String rule = "Operating cash flow divided by net profit, which shows whether profit turns into cash. Full marks at 1 or more, zero at 0 or less.";
		if (cash == null || income == null || income <= 0) {
			return missing("Cash conversion", rule);
		}
		double value = cash / income;
		return scored("Cash conversion", number(value), linear(value, 0, 1), rule);
	}

	private static Parameter priceToEarnings(Fundamentals data) {
		Double value = data.latest(Fundamentals.PE);
		Double income = data.latest(Fundamentals.NET_INCOME);
		String rule = "Price divided by the last year's earnings per share. Full marks at 15 or less, zero at 40 or more or a loss.";
		if (value == null) {
			// Yahoo gives no P/E for a loss-making company.
			return (income != null && income <= 0) ? scored("P/E ratio", "Loss-making", 0, rule)
					: missing("P/E ratio", rule);
		}
		return scored("P/E ratio", number(value), (value <= 0) ? 0 : linear(value, 40, 15), rule);
	}

	private static Parameter priceToBook(Fundamentals data) {
		Double value = data.latest(Fundamentals.PB);
		String rule = "Price divided by book value per share. Full marks at 1.5 or less, zero at 6 or more.";
		return (value != null && value > 0) ? scored("P/B ratio", number(value), linear(value, 6, 1.5), rule)
				: missing("P/B ratio", rule);
	}

	private static Parameter versusLongAverage(double[] closes) {
		String rule = "Latest price against its 200-day average. Full marks at 10% above, zero at 10% below.";
		if (closes.length < LONG_AVERAGE_DAYS) {
			return missing("Price vs 200-day average", rule);
		}
		double value = (closes[closes.length - 1] / average(closes, LONG_AVERAGE_DAYS) - 1) * 100;
		return scored("Price vs 200-day average", signedPercent(value), linear(value, -10, 10), rule);
	}

	private static Parameter shortVersusLongAverage(double[] closes) {
		String rule = "The 50-day average against the 200-day average. Full marks at 5% above, zero at 5% below.";
		if (closes.length < LONG_AVERAGE_DAYS) {
			return missing("50-day vs 200-day average", rule);
		}
		double value = (average(closes, SHORT_AVERAGE_DAYS) / average(closes, LONG_AVERAGE_DAYS) - 1) * 100;
		return scored("50-day vs 200-day average", signedPercent(value), linear(value, -5, 5), rule);
	}

	private static Parameter oneYearReturn(double[] closes) {
		String rule = "Price change over the last year. Full marks at 30% or more, zero at a fall of 20% or more.";
		if (closes.length < LONG_AVERAGE_DAYS) {
			return missing("1-year return", rule);
		}
		double value = (closes[closes.length - 1] / closes[0] - 1) * 100;
		return scored("1-year return", signedPercent(value), linear(value, -20, 30), rule);
	}

	private static Parameter volatility(double[] closes) {
		String rule = "How much the price swings, as yearly volatility of daily returns. Full marks at 20% or less, zero at 60% or more.";
		if (closes.length < LONG_AVERAGE_DAYS) {
			return missing("Volatility", rule);
		}
		double value = annualVolatility(closes);
		return scored("Volatility", percent(value), linear(value, 60, 20), rule);
	}

	static double annualVolatility(double[] closes) {
		double[] returns = new double[closes.length - 1];
		for (int i = 1; i < closes.length; i++) {
			returns[i - 1] = Math.log(closes[i] / closes[i - 1]);
		}
		double mean = 0;
		for (double value : returns) {
			mean += value;
		}
		mean /= returns.length;
		double variance = 0;
		for (double value : returns) {
			variance += (value - mean) * (value - mean);
		}
		variance /= returns.length - 1;
		return Math.sqrt(variance * TRADING_DAYS_PER_YEAR) * 100;
	}

	private static Parameter maxDrawdown(double[] closes) {
		String rule = "The largest fall from a peak over the last year. Full marks at 15% or less, zero at 60% or more.";
		if (closes.length < LONG_AVERAGE_DAYS) {
			return missing("Largest fall", rule);
		}
		double value = drawdown(closes);
		return scored("Largest fall", percent(value), linear(value, 60, 15), rule);
	}

	static double drawdown(double[] closes) {
		double peak = closes[0];
		double worst = 0;
		for (double close : closes) {
			peak = Math.max(peak, close);
			worst = Math.max(worst, (peak - close) / peak);
		}
		return worst * 100;
	}

	private static double average(double[] closes, int days) {
		double sum = 0;
		for (int i = closes.length - days; i < closes.length; i++) {
			sum += closes[i];
		}
		return sum / days;
	}

	/** 0 at {@code worst} and 100 at {@code best}, in either direction, clamped to that range. */
	static int linear(double value, double worst, double best) {
		double position = (value - worst) / (best - worst);
		return (int) Math.round(Math.max(0, Math.min(1, position)) * 100);
	}

	private static Parameter scored(String name, String value, int score, String rule) {
		return new Parameter(name, value, score, rule);
	}

	private static Parameter missing(String name, String rule) {
		return new Parameter(name, null, null, rule);
	}

	private static String percent(double value) {
		return String.format(Locale.ROOT, "%.1f%%", value);
	}

	private static String signedPercent(double value) {
		return String.format(Locale.ROOT, "%+.1f%%", value);
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

}
