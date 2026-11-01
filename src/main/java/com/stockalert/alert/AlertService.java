package com.stockalert.alert;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.stockalert.common.ApiException;
import com.stockalert.market.InstrumentCatalog;
import com.stockalert.market.Paise;
import com.stockalert.market.PriceService;
import com.stockalert.market.PriceTick;

@Service
public class AlertService {

	private static final int MAX_ACTIVE_ALERTS = 100;

	private final AlertRepository alerts;

	private final AlertIndex index;

	private final InstrumentCatalog catalog;

	private final PriceService priceService;

	private final MongoTemplate mongo;

	private final Clock clock;

	public AlertService(AlertRepository alerts, AlertIndex index, InstrumentCatalog catalog, PriceService priceService,
			MongoTemplate mongo, Clock clock) {
		this.alerts = alerts;
		this.index = index;
		this.catalog = catalog;
		this.priceService = priceService;
		this.mongo = mongo;
		this.clock = clock;
	}

	public Alert create(String userId, String symbol, Direction direction, BigDecimal targetPrice) {
		String known = this.catalog.requireSymbol(symbol);
		long target = Paise.fromRupees(targetPrice);
		if (this.alerts.countByUserIdAndStatus(userId, AlertStatus.ACTIVE) >= MAX_ACTIVE_ALERTS) {
			throw new ApiException(HttpStatus.CONFLICT, "Limit of " + MAX_ACTIVE_ALERTS + " active alerts reached");
		}
		PriceTick latest = this.priceService.latest(known).orElse(null);
		// With the full catalog, a stock Yahoo cannot price would leave an alert that never fires.
		if (latest == null && this.catalog.hasFullCatalog()) {
			throw new ApiException(HttpStatus.CONFLICT, "No price is available for " + known + " right now");
		}
		if (latest != null && direction.isCrossed(target, latest.paise())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
					known + " is already " + direction + " this target at Rs " + Paise.toRupees(latest.paise()));
		}
		Alert alert = this.alerts.save(new Alert(userId, known, direction, target, this.clock.instant()));
		this.index.add(alert);
		return alert;
	}

	public Page<Alert> list(String userId, List<AlertStatus> statuses, Pageable pageable) {
		if (statuses == null || statuses.isEmpty()) {
			return this.alerts.findByUserId(userId, pageable);
		}
		return this.alerts.findByUserIdAndStatusIn(userId, statuses, pageable);
	}

	public void cancel(String userId, String alertId) {
		Alert alert = this.alerts.findByIdAndUserId(alertId, userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alert not found"));
		// Conditional update, so a cancel cannot overwrite an alert that has just triggered.
		long modified = this.mongo
			.updateFirst(
					Query.query(Criteria.where("_id").is(alertId).and("status").is(AlertStatus.ACTIVE)),
					Update.update("status", AlertStatus.CANCELLED), Alert.class)
			.getModifiedCount();
		if (modified == 0) {
			throw new ApiException(HttpStatus.CONFLICT, "Only an active alert can be cancelled");
		}
		this.index.remove(alert.getSymbol(), alert.getDirection(), alertId);
	}

}
