package com.stockalert.alert;

import java.time.Instant;

public record AlertTriggeredEvent(String alertId, String userId, String symbol, Direction direction, long targetPaise,
		long triggeredPaise, Instant triggeredAt) {

	static AlertTriggeredEvent from(Alert alert) {
		return new AlertTriggeredEvent(alert.getId(), alert.getUserId(), alert.getSymbol(), alert.getDirection(),
				alert.getTargetPaise(), alert.getTriggeredPaise(), alert.getTriggeredAt());
	}

}
