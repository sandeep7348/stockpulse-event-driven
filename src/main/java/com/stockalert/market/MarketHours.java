package com.stockalert.market;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;

/**
 * NSE normal trading session: Monday to Friday, 09:15 to 15:30 IST, excluding configured
 * holidays. Special sessions such as Muhurat trading are not modelled.
 */
@Component
public class MarketHours {

	public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private static final LocalTime OPEN = LocalTime.of(9, 15);

	private static final LocalTime CLOSE = LocalTime.of(15, 30);

	private final Set<LocalDate> holidays;

	public MarketHours(AppProperties properties) {
		this.holidays = Set.copyOf(properties.market().holidays());
	}

	public boolean isOpen(Instant instant) {
		ZonedDateTime now = instant.atZone(IST);
		DayOfWeek day = now.getDayOfWeek();
		if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || this.holidays.contains(now.toLocalDate())) {
			return false;
		}
		LocalTime time = now.toLocalTime();
		return !time.isBefore(OPEN) && time.isBefore(CLOSE);
	}

}
