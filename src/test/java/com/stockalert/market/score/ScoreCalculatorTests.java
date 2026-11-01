package com.stockalert.market.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockalert.market.score.Fundamentals.Reported;
import com.stockalert.market.score.Scorecard.Band;
import com.stockalert.market.score.Scorecard.Category;
import com.stockalert.market.score.Scorecard.Confidence;
import com.stockalert.market.score.Scorecard.Parameter;

class ScoreCalculatorTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

	private static final LocalDate LAST_YEAR_END = LocalDate.of(2026, 3, 31);

	private static final Instant NOW = Instant.parse("2026-11-01T06:00:00Z");

	@Test
	void linearScoresInEitherDirectionAndClamps() {
		assertThat(ScoreCalculator.linear(7.5, 0, 15)).isEqualTo(50);
		assertThat(ScoreCalculator.linear(-5, 0, 15)).isZero();
		assertThat(ScoreCalculator.linear(99, 0, 15)).isEqualTo(100);
		// Lower is better for measures like debt.
		assertThat(ScoreCalculator.linear(0.5, 2, 0.5)).isEqualTo(100);
		assertThat(ScoreCalculator.linear(2, 2, 0.5)).isZero();
		assertThat(ScoreCalculator.linear(1.25, 2, 0.5)).isEqualTo(50);
	}

	@Test
	void yearlyGrowthIsCompound() {
		List<Reported> doubledOverTwoYears = List.of(new Reported(LocalDate.of(2024, 3, 31), 100),
				new Reported(LocalDate.of(2026, 3, 31), 200));

		assertThat(ScoreCalculator.yearlyGrowth(doubledOverTwoYears)).isCloseTo(41.4, within(0.1));
	}

	@Test
	void growthFromALossIsNotMeaningful() {
		List<Reported> fromLoss = List.of(new Reported(LocalDate.of(2024, 3, 31), -100),
				new Reported(LocalDate.of(2026, 3, 31), 200));

		assertThat(ScoreCalculator.yearlyGrowth(fromLoss)).isNull();
		assertThat(ScoreCalculator.yearlyGrowth(fromLoss.subList(0, 1))).isNull();
	}

	@Test
	void drawdownIsTheLargestFallFromAPeak() {
		assertThat(ScoreCalculator.drawdown(new double[] { 100, 120, 90, 110, 60, 130 })).isCloseTo(50, within(0.01));
		assertThat(ScoreCalculator.drawdown(new double[] { 1, 2, 3 })).isZero();
	}

	@Test
	void volatilityIsZeroForSteadyGrowthAndAnnualised() {
		double[] steady = new double[100];
		steady[0] = 100;
		for (int i = 1; i < steady.length; i++) {
			steady[i] = steady[i - 1] * 1.01;
		}
		assertThat(ScoreCalculator.annualVolatility(steady)).isCloseTo(0, within(1e-6));

		// Alternating +x, -x log returns have a daily deviation of about x, scaled by the root of 252.
		double[] swinging = new double[101];
		for (int i = 0; i < swinging.length; i++) {
			swinging[i] = (i % 2 == 0) ? 100 : 100 * Math.exp(0.01);
		}
		assertThat(ScoreCalculator.annualVolatility(swinging)).isCloseTo(15.87, within(0.2));
	}

	@Test
	void parametersAreCalculatedFromTheReportedFigures() {
		Scorecard card = calculate(healthy(), rising(), false);

		assertThat(parameter(card, "Return on equity").value()).isEqualTo("20.0%");
		assertThat(parameter(card, "Return on equity").score()).isEqualTo(100);
		assertThat(parameter(card, "Net profit margin").value()).isEqualTo("10.0%");
		assertThat(parameter(card, "Net profit margin").score()).isEqualTo(67);
		assertThat(parameter(card, "Debt to equity").value()).isEqualTo("0.50");
		assertThat(parameter(card, "P/E ratio").value()).isEqualTo("15.00");
		assertThat(card.financialsAsOf()).isEqualTo(LAST_YEAR_END);
	}

	@Test
	void aStrongCompleteProfileScoresHighWithHighConfidence() {
		Scorecard card = calculate(healthy(), rising(), false);

		assertThat(card.band()).isEqualTo(Band.STRONG);
		assertThat(card.confidence()).isEqualTo(Confidence.HIGH);
		assertThat(card.parametersAvailable()).isEqualTo(card.parametersTotal());
	}

	@Test
	void aLossMakingFallingCompanyScoresLow() {
		Map<String, List<Reported>> series = new HashMap<>();
		series.put(Fundamentals.REVENUE, List.of(new Reported(LocalDate.of(2024, 3, 31), 1000), yearEnd(500)));
		series.put(Fundamentals.NET_INCOME, List.of(new Reported(LocalDate.of(2024, 3, 31), 100), yearEnd(-200)));
		series.put(Fundamentals.EQUITY, List.of(yearEnd(100)));
		series.put(Fundamentals.DEBT, List.of(yearEnd(500)));
		series.put(Fundamentals.OPERATING_CASH_FLOW, List.of(yearEnd(-50)));
		double[] falling = new double[250];
		for (int i = 0; i < falling.length; i++) {
			falling[i] = 500 - i * 1.5;
		}

		Scorecard card = calculate(new Fundamentals(series), falling, false);

		assertThat(card.band()).isEqualTo(Band.WEAK);
		assertThat(parameter(card, "Profit growth").value()).isEqualTo("Loss-making");
		assertThat(parameter(card, "P/E ratio").value()).isEqualTo("Loss-making");
		assertThat(parameter(card, "P/E ratio").score()).isZero();
	}

	@Test
	void lendersSkipDebtAndCashFlowParametersRatherThanScoringThem() {
		Scorecard card = calculate(healthy(), rising(), true);

		assertThat(card.financialCompany()).isTrue();
		assertThat(card.categories()).extracting(Category::name).doesNotContain("Financial health");
		assertThat(card.parametersTotal()).isEqualTo(calculate(healthy(), rising(), false).parametersTotal() - 2);
	}

	@Test
	void missingCategoriesDoNotCountAsLowScores() {
		Scorecard withoutPrices = calculate(healthy(), new double[0], false);

		assertThat(withoutPrices.score()).isNotNull();
		assertThat(withoutPrices.band()).isEqualTo(Band.STRONG);
		assertThat(category(withoutPrices, "Price trend").score()).isNull();
		assertThat(withoutPrices.confidence()).isEqualTo(Confidence.MEDIUM);
	}

	@Test
	void tooLittleDataGivesNoScoreAtAll() {
		Scorecard card = calculate(new Fundamentals(Map.of()), new double[0], false);

		assertThat(card.score()).isNull();
		assertThat(card.band()).isEqualTo(Band.INSUFFICIENT_DATA);
		assertThat(card.confidence()).isEqualTo(Confidence.LOW);
		assertThat(card.parametersAvailable()).isZero();
	}

	@Test
	void oldFinancialsLowerConfidence() {
		Map<String, List<Reported>> series = new HashMap<>(healthy().series());
		series.put(Fundamentals.REVENUE, List.of(new Reported(LocalDate.of(2022, 3, 31), 800),
				new Reported(LocalDate.of(2024, 3, 31), 1000)));
		series.put(Fundamentals.NET_INCOME, List.of(new Reported(LocalDate.of(2022, 3, 31), 60),
				new Reported(LocalDate.of(2024, 3, 31), 100)));
		series.put(Fundamentals.EQUITY, List.of(new Reported(LocalDate.of(2024, 3, 31), 500)));

		// Figures from March 2024 are 31 months old on this date.
		assertThat(calculate(new Fundamentals(series), rising(), false).confidence()).isEqualTo(Confidence.LOW);
	}

	@Test
	void financialCompaniesAreRecognisedByName() {
		assertThat(ScorecardService.looksFinancial("HDFCBANK", "HDFC Bank")).isTrue();
		assertThat(ScorecardService.looksFinancial("TATACAP", "TATA CAPITAL LIMITED")).isTrue();
		assertThat(ScorecardService.looksFinancial("M&MFIN", "M&M FIN. SERVICES LTD")).isTrue();
		assertThat(ScorecardService.looksFinancial("BAJFINANCE", "BAJAJ FINANCE LIMITED")).isTrue();
		assertThat(ScorecardService.looksFinancial("HDFCLIFE", "HDFC LIFE INSURANCE CO LTD")).isTrue();
		assertThat(ScorecardService.looksFinancial("RELIANCE", "Reliance Industries")).isFalse();
		assertThat(ScorecardService.looksFinancial("TCS", "Tata Consultancy Services")).isFalse();
	}

	private static Scorecard calculate(Fundamentals fundamentals, double[] closes, boolean financial) {
		return ScoreCalculator.calculate("TEST", fundamentals, closes, financial, TODAY, NOW);
	}

	private static Fundamentals healthy() {
		Map<String, List<Reported>> series = new HashMap<>();
		series.put(Fundamentals.REVENUE, List.of(new Reported(LocalDate.of(2024, 3, 31), 700), yearEnd(1000)));
		series.put(Fundamentals.NET_INCOME, List.of(new Reported(LocalDate.of(2024, 3, 31), 70), yearEnd(100)));
		series.put(Fundamentals.EQUITY, List.of(yearEnd(500)));
		series.put(Fundamentals.DEBT, List.of(yearEnd(250)));
		series.put(Fundamentals.OPERATING_CASH_FLOW, List.of(yearEnd(120)));
		series.put(Fundamentals.PE, List.of(new Reported(TODAY, 15)));
		series.put(Fundamentals.PB, List.of(new Reported(TODAY, 1.5)));
		return new Fundamentals(series);
	}

	private static Reported yearEnd(double value) {
		return new Reported(LAST_YEAR_END, value);
	}

	private static double[] rising() {
		double[] closes = new double[250];
		Arrays.setAll(closes, (i) -> 100 * Math.pow(1.002, i));
		return closes;
	}

	private static Category category(Scorecard card, String name) {
		return card.categories().stream().filter((item) -> item.name().equals(name)).findFirst().orElseThrow();
	}

	private static Parameter parameter(Scorecard card, String name) {
		return card.categories()
			.stream()
			.flatMap((item) -> item.parameters().stream())
			.filter((item) -> item.name().equals(name))
			.findFirst()
			.orElseThrow();
	}

}
