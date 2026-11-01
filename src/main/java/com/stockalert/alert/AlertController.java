package com.stockalert.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stockalert.market.Paise;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/alerts")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

	private final AlertService alertService;

	public AlertController(AlertService alertService) {
		this.alertService = alertService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AlertResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAlertRequest request) {
		return AlertResponse.from(this.alertService.create(jwt.getSubject(), request.symbol(), request.direction(),
				request.targetPrice()));
	}

	@GetMapping
	public Page<AlertResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) List<AlertStatus> status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		return this.alertService.list(jwt.getSubject(), status, pageable).map(AlertResponse::from);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
		this.alertService.cancel(jwt.getSubject(), id);
	}

	public record CreateAlertRequest(@NotBlank String symbol, @NotNull Direction direction,
			@NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal targetPrice) {
	}

	public record AlertResponse(String id, String symbol, Direction direction, BigDecimal targetPrice,
			AlertStatus status, Instant createdAt, Instant triggeredAt, BigDecimal triggeredPrice) {

		static AlertResponse from(Alert alert) {
			BigDecimal triggeredPrice = (alert.getTriggeredPaise() != null)
					? Paise.toRupees(alert.getTriggeredPaise()) : null;
			return new AlertResponse(alert.getId(), alert.getSymbol(), alert.getDirection(),
					Paise.toRupees(alert.getTargetPaise()), alert.getStatus(), alert.getCreatedAt(),
					alert.getTriggeredAt(), triggeredPrice);
		}

	}

}
