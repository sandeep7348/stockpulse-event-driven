package com.stockalert.alert;

import java.time.Clock;
import java.time.Duration;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Transactional-outbox recovery: the alert document is the record of what still has to be
 * sent. Republishing can produce duplicates, which the notification consumer ignores.
 */
@Component
public class UnpublishedAlertSweeper {

	private static final Duration GRACE_PERIOD = Duration.ofSeconds(30);

	private static final int BATCH_SIZE = 100;

	private final MongoTemplate mongo;

	private final AlertEventPublisher publisher;

	private final Clock clock;

	public UnpublishedAlertSweeper(MongoTemplate mongo, AlertEventPublisher publisher, Clock clock) {
		this.mongo = mongo;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "PT10S")
	public void sweep() {
		Query query = Query
			.query(Criteria.where("status")
				.is(AlertStatus.TRIGGERED)
				.and("eventPublished")
				.is(false)
				.and("triggeredAt")
				.lt(this.clock.instant().minus(GRACE_PERIOD)))
			.limit(BATCH_SIZE);
		this.mongo.find(query, Alert.class).forEach(this.publisher::publish);
	}

}
