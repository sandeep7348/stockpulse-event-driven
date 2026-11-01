package com.stockalert.alert;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;
import com.stockalert.config.KafkaConfig;
import com.stockalert.market.PriceStore;
import com.stockalert.market.PriceTick;

import tools.jackson.databind.json.JsonMapper;

@Component
public class AlertEvaluator {

	private final AlertIndex index;

	private final PriceStore priceStore;

	private final AlertEventPublisher publisher;

	private final MongoTemplate mongo;

	private final JsonMapper jsonMapper;

	private final String source;

	public AlertEvaluator(AlertIndex index, PriceStore priceStore, AlertEventPublisher publisher, MongoTemplate mongo,
			JsonMapper jsonMapper, AppProperties properties) {
		this.index = index;
		this.priceStore = priceStore;
		this.publisher = publisher;
		this.mongo = mongo;
		this.jsonMapper = jsonMapper;
		this.source = properties.feed().source();
	}

	@KafkaListener(topics = KafkaConfig.PRICE_TICKS, groupId = "alert-evaluator")
	public void onTick(String payload) {
		PriceTick tick = this.jsonMapper.readValue(payload, PriceTick.class);
		// After switching feeds, ticks from the old one can still be queued. A simulated price
		// must never update a real price or fire an alert.
		if (!this.source.equals(tick.source())) {
			return;
		}
		this.priceStore.updateIfNewer(tick);
		// Evaluate even when the tick is older than the stored price. It may be a redelivery
		// after a crash that happened before its alerts were processed, and the price was real.
		for (AlertIndex.Match match : this.index.findCrossed(tick.symbol(), tick.paise())) {
			Alert claimed = claim(match.alertId(), tick);
			if (claimed != null) {
				this.publisher.publish(claimed);
			}
			this.index.remove(tick.symbol(), match.direction(), match.alertId());
		}
	}

	/**
	 * Atomically moves an alert from ACTIVE to TRIGGERED. Only one consumer can win this
	 * update, so an alert fires once even if ticks are redelivered.
	 * @return the updated alert, or null if it was already triggered or cancelled
	 */
	private Alert claim(String alertId, PriceTick tick) {
		Query query = Query.query(Criteria.where("_id").is(alertId).and("status").is(AlertStatus.ACTIVE));
		Update update = new Update().set("status", AlertStatus.TRIGGERED)
			.set("triggeredPaise", tick.paise())
			.set("triggeredAt", tick.timestamp())
			.set("eventPublished", false);
		return this.mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Alert.class);
	}

}
