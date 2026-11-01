package com.stockalert.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class PaiseTests {

	@Test
	void convertsBetweenRupeesAndPaiseExactly() {
		assertThat(Paise.fromRupees(new BigDecimal("1234.55"))).isEqualTo(123455);
		assertThat(Paise.toRupees(123455)).isEqualByComparingTo("1234.55");
	}

	@Test
	void roundsToNseTickSize() {
		assertThat(Paise.roundToTick(123452)).isEqualTo(123450);
		assertThat(Paise.roundToTick(123453)).isEqualTo(123455);
	}

	@RepeatedTest(20)
	void simulatedPricesLandOnTickAndStayPositive() {
		long next = SimulatedPriceFeed.nextPrice(140000);
		assertThat(next % Paise.TICK).isZero();
		assertThat(next).isPositive();
		assertThat(SimulatedPriceFeed.nextPrice(Paise.TICK)).isPositive();
	}

}
