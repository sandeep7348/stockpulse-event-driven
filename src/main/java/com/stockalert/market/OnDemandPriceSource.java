package com.stockalert.market;

import java.util.Optional;

/** A feed that can fetch one stock's price when asked, rather than only on its own schedule. */
public interface OnDemandPriceSource {

	/**
	 * @return a freshly fetched price, or empty if the stock was fetched recently, the feed
	 * is over its request budget or backing off, or no price is available
	 */
	Optional<PriceTick> fetchIfDue(String symbol);

}
