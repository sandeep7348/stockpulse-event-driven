package com.stockalert.notification;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stockalert.alert.AlertTriggeredEvent;
import com.stockalert.config.KafkaConfig;
import com.stockalert.market.Paise;

import tools.jackson.databind.json.JsonMapper;

@Component
public class NotificationConsumer {

	private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

	private final NotificationRepository notifications;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public NotificationConsumer(NotificationRepository notifications, JsonMapper jsonMapper, Clock clock) {
		this.notifications = notifications;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@KafkaListener(topics = KafkaConfig.ALERT_TRIGGERED, groupId = "notification-service")
	public void onAlertTriggered(String payload) {
		AlertTriggeredEvent event = this.jsonMapper.readValue(payload, AlertTriggeredEvent.class);
		String message = "%s crossed %s Rs %s: now Rs %s".formatted(event.symbol(), event.direction(),
				Paise.toRupees(event.targetPaise()), Paise.toRupees(event.triggeredPaise()));
		try {
			this.notifications.insert(new Notification(event.alertId(), event.userId(), event.symbol(),
					event.triggeredPaise(), message, this.clock.instant()));
			log.info("Notified user {}: {}", event.userId(), message);
		}
		catch (DuplicateKeyException ex) {
			log.debug("Ignoring duplicate event for alert {}", event.alertId());
		}
	}

}
