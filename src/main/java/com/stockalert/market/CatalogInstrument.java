package com.stockalert.market;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** One NSE stock from the full catalog. Stored by symbol so reloading replaces rather than duplicates. */
@Document("instruments")
public class CatalogInstrument {

	@Id
	private String symbol;

	private String name;

	private String isin;

	public CatalogInstrument(String symbol, String name, String isin) {
		this.symbol = symbol;
		this.name = name;
		this.isin = isin;
	}

	public String getSymbol() {
		return this.symbol;
	}

	public String getName() {
		return this.name;
	}

	public String getIsin() {
		return this.isin;
	}

}
