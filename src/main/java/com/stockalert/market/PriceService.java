package com.stockalert.market;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Reads the latest price, first fetching it if the feed supports that and the stock is due.
 * This is how a stock outside the polled set gets a price when someone looks at it.
 */
@Service
public class PriceService {

	private final PriceStore priceStore;

	private final ObjectProvider<OnDemandPriceSource> onDemandSource;

	public PriceService(PriceStore priceStore, ObjectProvider<OnDemandPriceSource> onDemandSource) {
		this.priceStore = priceStore;
		this.onDemandSource = onDemandSource;
	}

	public Optional<PriceTick> latest(String symbol) {
		this.onDemandSource.ifAvailable(
				(source) -> source.fetchIfDue(symbol).ifPresent(this.priceStore::updateIfNewer));
		return this.priceStore.latest(symbol);
	}

}
