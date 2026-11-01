package com.stockalert.market;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockalert.config.AppProperties;
import com.stockalert.config.AppProperties.Instrument;

/**
 * Generates a random walk for each instrument. These are not real market prices.
 */
@Component
@ConditionalOnProperty(name = "app.feed.source", havingValue = "simulated")
public class SimulatedPriceFeed {

	private static final double MAX_MOVE = 0.002;

	private final PriceTickPublisher publisher;

	private final MarketHours marketHours;

	private final Clock clock;

	private final boolean respectMarketHours;

	private final Map<String, Long> prices = new LinkedHashMap<>();

	public SimulatedPriceFeed(PriceTickPublisher publisher, MarketHours marketHours, Clock clock,
			AppProperties properties) {
		this.publisher = publisher;
		this.marketHours = marketHours;
		this.clock = clock;
		this.respectMarketHours = properties.feed().respectMarketHours();
		for (Instrument instrument : properties.instruments()) {
			this.prices.put(instrument.symbol(), Paise.fromRupees(instrument.basePrice()));
		}
	}

	@Scheduled(fixedDelayString = "${app.feed.interval}")
	void tick() {
		Instant now = this.clock.instant();
		if (this.respectMarketHours && !this.marketHours.isOpen(now)) {
			return;
		}
		this.prices.replaceAll((symbol, paise) -> nextPrice(paise));
		this.prices.forEach((symbol, paise) -> this.publisher.publish(new PriceTick(symbol, paise, now)));
	}

	static long nextPrice(long paise) {
		double move = ThreadLocalRandom.current().nextDouble(-MAX_MOVE, MAX_MOVE);
		return Math.max(Paise.TICK, Paise.roundToTick(Math.round(paise * (1 + move))));
	}

}
