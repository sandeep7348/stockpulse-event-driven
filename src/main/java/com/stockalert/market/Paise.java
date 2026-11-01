package com.stockalert.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prices are held as whole paise so comparisons are exact, including as Redis sorted set
 * scores, which are doubles.
 */
public final class Paise {

	/** Price step used by the simulator. Real NSE tick sizes vary by stock. */
	public static final long TICK = 5;

	private static final int RUPEE_SCALE = 2;

	private Paise() {
	}

	public static long fromRupees(BigDecimal rupees) {
		return rupees.movePointRight(RUPEE_SCALE).longValueExact();
	}

	public static BigDecimal toRupees(long paise) {
		return BigDecimal.valueOf(paise, RUPEE_SCALE).setScale(RUPEE_SCALE, RoundingMode.UNNECESSARY);
	}

	public static long roundToTick(long paise) {
		return Math.round((double) paise / TICK) * TICK;
	}

}
