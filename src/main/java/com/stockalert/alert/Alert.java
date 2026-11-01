package com.stockalert.alert;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("alerts")
@CompoundIndex(name = "user_status", def = "{'userId': 1, 'status': 1}")
@CompoundIndex(name = "unpublished", def = "{'status': 1, 'eventPublished': 1, 'triggeredAt': 1}")
public class Alert {

	@Id
	private String id;

	private String userId;

	private String symbol;

	private Direction direction;

	private long targetPaise;

	private AlertStatus status;

	private Instant createdAt;

	private Instant triggeredAt;

	private Long triggeredPaise;

	private boolean eventPublished;

	public Alert(String userId, String symbol, Direction direction, long targetPaise, Instant createdAt) {
		this.userId = userId;
		this.symbol = symbol;
		this.direction = direction;
		this.targetPaise = targetPaise;
		this.createdAt = createdAt;
		this.status = AlertStatus.ACTIVE;
	}

	public String getId() {
		return this.id;
	}

	public String getUserId() {
		return this.userId;
	}

	public String getSymbol() {
		return this.symbol;
	}

	public Direction getDirection() {
		return this.direction;
	}

	public long getTargetPaise() {
		return this.targetPaise;
	}

	public AlertStatus getStatus() {
		return this.status;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getTriggeredAt() {
		return this.triggeredAt;
	}

	public Long getTriggeredPaise() {
		return this.triggeredPaise;
	}

	public boolean isEventPublished() {
		return this.eventPublished;
	}

}
