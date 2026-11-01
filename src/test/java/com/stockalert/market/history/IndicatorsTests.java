package com.stockalert.market.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class IndicatorsTests {

	@Test
	void smaIsTheMeanOfTheLastCloses() {
		assertThat(Indicators.sma(new double[] { 1, 2, 3, 4, 5 }, 3)).containsExactly(null, null, 2.0, 3.0, 4.0);
	}

	@Test
	void smaIsEmptyWithTooLittleData() {
		assertThat(Indicators.sma(new double[] { 1, 2 }, 3)).containsOnlyNulls();
	}

	@Test
	void rsiIsAtItsLimitsWhenPricesOnlyMoveOneWay() {
		double[] rising = { 1, 2, 3, 4, 5, 6 };
		double[] falling = { 6, 5, 4, 3, 2, 1 };

		assertThat(Indicators.rsi(rising, 3)).containsExactly(null, null, null, 100.0, 100.0, 100.0);
		assertThat(Indicators.rsi(falling, 3)).containsExactly(null, null, null, 0.0, 0.0, 0.0);
	}

	@Test
	void rsiIsNeutralWhenPricesDoNotMove() {
		double[] flat = new double[20];
		Arrays.fill(flat, 100);

		assertThat(Indicators.rsi(flat, 14)[19]).isEqualTo(50.0);
	}

	@Test
	void rsiIsEmptyWithTooLittleData() {
		assertThat(Indicators.rsi(new double[] { 1, 2, 3 }, 14)).containsOnlyNulls();
	}

	@Test
	void rsiUsesWilderSmoothing() {
		// Hand-worked, period 2. Changes: +2, -1, then +3.
		// First averages: gain 1, loss 0.5, so RSI = 100 - 100 / (1 + 2) = 66.67.
		// Smoothed: gain (1 + 3) / 2 = 2, loss (0.5 + 0) / 2 = 0.25, so RSI = 100 - 100 / 9 = 88.89.
		Double[] rsi = Indicators.rsi(new double[] { 10, 12, 11, 14 }, 2);

		assertThat(rsi[1]).isNull();
		assertThat(rsi[2]).isCloseTo(66.67, within(0.01));
		assertThat(rsi[3]).isCloseTo(88.89, within(0.01));
	}

}
