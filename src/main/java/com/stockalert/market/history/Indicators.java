package com.stockalert.market.history;

/**
 * Technical indicators over closing prices. Each result lines up with the input, with null
 * where there is too little earlier data to calculate a value.
 */
final class Indicators {

	private Indicators() {
	}

	/** Simple moving average: the mean of the last {@code period} closes. */
	static Double[] sma(double[] closes, int period) {
		Double[] result = new Double[closes.length];
		double sum = 0;
		for (int i = 0; i < closes.length; i++) {
			sum += closes[i];
			if (i >= period) {
				sum -= closes[i - period];
			}
			if (i >= period - 1) {
				result[i] = sum / period;
			}
		}
		return result;
	}

	/** Relative strength index, using Wilder's smoothing of average gains and losses. */
	static Double[] rsi(double[] closes, int period) {
		Double[] result = new Double[closes.length];
		if (closes.length <= period) {
			return result;
		}
		double avgGain = 0;
		double avgLoss = 0;
		for (int i = 1; i <= period; i++) {
			double change = closes[i] - closes[i - 1];
			avgGain += Math.max(change, 0);
			avgLoss += Math.max(-change, 0);
		}
		avgGain /= period;
		avgLoss /= period;
		result[period] = rsiValue(avgGain, avgLoss);
		for (int i = period + 1; i < closes.length; i++) {
			double change = closes[i] - closes[i - 1];
			avgGain = (avgGain * (period - 1) + Math.max(change, 0)) / period;
			avgLoss = (avgLoss * (period - 1) + Math.max(-change, 0)) / period;
			result[i] = rsiValue(avgGain, avgLoss);
		}
		return result;
	}

	private static double rsiValue(double avgGain, double avgLoss) {
		if (avgLoss == 0) {
			// With no movement at all, neither side has any strength.
			return (avgGain == 0) ? 50 : 100;
		}
		return 100 - 100 / (1 + avgGain / avgLoss);
	}

}
