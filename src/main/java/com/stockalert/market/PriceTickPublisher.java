package com.stockalert.market;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;
import com.stockalert.config.KafkaConfig;

import tools.jackson.databind.json.JsonMapper;

/**
 * Entry point for market data. Every price feed publishes through this class, so everything
 * downstream stays the same whichever feed is in use.
 */
@Component
public class PriceTickPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final JsonMapper jsonMapper;

	private final String source;

	public PriceTickPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
			AppProperties properties) {
		this.kafkaTemplate = kafkaTemplate;
		this.jsonMapper = jsonMapper;
		this.source = properties.feed().source();
	}

	public void publish(PriceTick tick) {
		// Keying by symbol keeps each stock's ticks in order on one partition.
		this.kafkaTemplate.send(KafkaConfig.PRICE_TICKS, tick.symbol(),
				this.jsonMapper.writeValueAsString(tick.withSource(this.source)));
	}

}
