package com.stockalert.notification;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
public class Notification {

	@Id
	private String id;

	// The unique index is what makes delivery idempotent: one notification per alert.
	@Indexed(unique = true)
	private String alertId;

	private String userId;

	private String symbol;

	private Long triggeredPaise;

	private String message;

	private Instant createdAt;

	public Notification(String alertId, String userId, String symbol, Long triggeredPaise, String message,
			Instant createdAt) {
		this.alertId = alertId;
		this.userId = userId;
		this.symbol = symbol;
		this.triggeredPaise = triggeredPaise;
		this.message = message;
		this.createdAt = createdAt;
	}

	public String getId() {
		return this.id;
	}

	public String getAlertId() {
		return this.alertId;
	}

	public String getUserId() {
		return this.userId;
	}

	public String getSymbol() {
		return this.symbol;
	}

	public Long getTriggeredPaise() {
		return this.triggeredPaise;
	}

	public String getMessage() {
		return this.message;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
