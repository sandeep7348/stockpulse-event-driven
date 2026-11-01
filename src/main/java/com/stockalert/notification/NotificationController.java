package com.stockalert.notification;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockalert.market.Paise;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

	private final NotificationRepository notifications;

	public NotificationController(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	public Page<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		return this.notifications.findByUserId(jwt.getSubject(), pageable).map(NotificationResponse::from);
	}

	public record NotificationResponse(String id, String alertId, String symbol, BigDecimal triggeredPrice,
			String message, Instant createdAt) {

		static NotificationResponse from(Notification notification) {
			BigDecimal triggeredPrice = (notification.getTriggeredPaise() != null)
					? Paise.toRupees(notification.getTriggeredPaise()) : null;
			return new NotificationResponse(notification.getId(), notification.getAlertId(), notification.getSymbol(),
					triggeredPrice, notification.getMessage(), notification.getCreatedAt());
		}

	}

}
