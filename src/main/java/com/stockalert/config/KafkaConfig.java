package com.stockalert.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConfig {

	public static final String PRICE_TICKS = "price-ticks";

	public static final String ALERT_TRIGGERED = "alert-triggered";

	private static final String DEAD_LETTER_SUFFIX = ".DLT";

	private static final int PARTITIONS = 3;

	@Bean
	NewTopic priceTicksTopic() {
		return topic(PRICE_TICKS);
	}

	@Bean
	NewTopic priceTicksDeadLetterTopic() {
		return topic(PRICE_TICKS + DEAD_LETTER_SUFFIX);
	}

	@Bean
	NewTopic alertTriggeredTopic() {
		return topic(ALERT_TRIGGERED);
	}

	@Bean
	NewTopic alertTriggeredDeadLetterTopic() {
		return topic(ALERT_TRIGGERED + DEAD_LETTER_SUFFIX);
	}

	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, ex) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
		DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
		// A malformed message will never parse, so retrying it only delays the partition.
		handler.addNotRetryableExceptions(JacksonException.class);
		return handler;
	}

	private NewTopic topic(String name) {
		return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
	}

}
