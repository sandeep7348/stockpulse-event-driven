package com.stockalert.market;

import java.util.Collection;

/** The stocks that have active alerts, which price feeds must keep polling. */
@FunctionalInterface
public interface AlertedSymbols {

	Collection<String> symbols();

}
