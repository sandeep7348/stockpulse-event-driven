package com.stockalert.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Restores the Redis index from MongoDB on startup, covering a Redis data loss or a crash
 * between saving an alert and indexing it. Adding an existing member is harmless.
 */
@Component
public class AlertIndexRebuilder {

	private static final Logger log = LoggerFactory.getLogger(AlertIndexRebuilder.class);

	private final AlertRepository alerts;

	private final AlertIndex index;

	public AlertIndexRebuilder(AlertRepository alerts, AlertIndex index) {
		this.alerts = alerts;
		this.index = index;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void rebuild() {
		var active = this.alerts.findByStatus(AlertStatus.ACTIVE);
		active.forEach(this.index::add);
		log.info("Indexed {} active alerts", active.size());
	}

}
