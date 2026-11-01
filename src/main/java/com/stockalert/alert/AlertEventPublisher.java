package com.stockalert.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stockalert.config.KafkaConfig;

import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes triggered alerts and records the broker's acknowledgement on the alert. An
 * unacknowledged alert is retried by {@link UnpublishedAlertSweeper}.
 */
@Component
public class AlertEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final MongoTemplate mongo;

	private final JsonMapper jsonMapper;

	public AlertEventPublisher(KafkaTemplate<String, String> kafkaTemplate, MongoTemplate mongo,
			JsonMapper jsonMapper) {
		this.kafkaTemplate = kafkaTemplate;
		this.mongo = mongo;
		this.jsonMapper = jsonMapper;
	}

	public void publish(Alert alert) {
		String payload = this.jsonMapper.writeValueAsString(AlertTriggeredEvent.from(alert));
		this.kafkaTemplate.send(KafkaConfig.ALERT_TRIGGERED, alert.getUserId(), payload)
			.whenComplete((result, ex) -> {
				if (ex != null) {
					log.warn("Publishing alert {} failed; the sweeper will retry", alert.getId(), ex);
					return;
				}
				this.mongo.updateFirst(Query.query(Criteria.where("_id").is(alert.getId())),
						Update.update("eventPublished", true), Alert.class);
			});
	}

}
