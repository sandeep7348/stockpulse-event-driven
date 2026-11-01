package com.stockalert.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockalert.config.AppProperties;

class MarketHoursTests {

	// 2 Nov 2026 is a Monday.
	private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

	private final MarketHours marketHours = new MarketHours(new AppProperties(null, null, null, null,
			new AppProperties.Market(List.of(LocalDate.of(2026, 11, 3))), List.of()));

	@Test
	void openFromNineFifteenInclusiveToThreeThirtyExclusive() {
		assertThat(this.marketHours.isOpen(ist(MONDAY, 9, 14))).isFalse();
		assertThat(this.marketHours.isOpen(ist(MONDAY, 9, 15))).isTrue();
		assertThat(this.marketHours.isOpen(ist(MONDAY, 15, 29))).isTrue();
		assertThat(this.marketHours.isOpen(ist(MONDAY, 15, 30))).isFalse();
	}

	@Test
	void closedOnWeekends() {
		assertThat(this.marketHours.isOpen(ist(LocalDate.of(2026, 10, 31), 11, 0))).isFalse();
		assertThat(this.marketHours.isOpen(ist(LocalDate.of(2026, 11, 1), 11, 0))).isFalse();
	}

	@Test
	void closedOnConfiguredHolidays() {
		assertThat(this.marketHours.isOpen(ist(LocalDate.of(2026, 11, 3), 11, 0))).isFalse();
	}

	@Test
	void usesIndianTimeRegardlessOfSystemZone() {
		// 04:00 UTC is 09:30 IST.
		assertThat(this.marketHours.isOpen(Instant.parse("2026-11-02T04:00:00Z"))).isTrue();
	}

	private static Instant ist(LocalDate date, int hour, int minute) {
		return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(MarketHours.IST).toInstant();
	}

}
