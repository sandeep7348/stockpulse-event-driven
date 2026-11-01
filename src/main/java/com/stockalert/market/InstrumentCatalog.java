package com.stockalert.market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.stockalert.common.ApiException;
import com.stockalert.config.AppProperties;
import com.stockalert.config.AppProperties.Instrument;

/**
 * The configured stocks form the default watchlist. With the Yahoo feed, which can price any
 * NSE stock on request, every stock in the loaded catalog can also be searched and alerted on.
 * The other feeds only produce prices for the configured stocks.
 */
@Component
public class InstrumentCatalog {

	private final List<Instrument> defaults;

	private final MongoTemplate mongo;

	private final boolean fullCatalog;

	public InstrumentCatalog(AppProperties properties, MongoTemplate mongo) {
		this.defaults = List.copyOf(properties.instruments());
		this.mongo = mongo;
		this.fullCatalog = "yahoo".equals(properties.feed().source());
	}

	public List<Instrument> all() {
		return this.defaults;
	}

	public boolean hasFullCatalog() {
		return this.fullCatalog;
	}

	public String requireSymbol(String symbol) {
		String normalized = symbol.trim().toUpperCase(Locale.ROOT);
		boolean isDefault = this.defaults.stream().anyMatch((item) -> item.symbol().equals(normalized));
		if (isDefault || (this.fullCatalog && this.mongo.findById(normalized, CatalogInstrument.class) != null)) {
			return normalized;
		}
		throw new ApiException(HttpStatus.NOT_FOUND, "Unknown NSE symbol: " + symbol);
	}

	/** The company name, or an empty string if the stock is not in the catalog. */
	public String nameOf(String symbol) {
		for (Instrument item : this.defaults) {
			if (item.symbol().equals(symbol)) {
				return item.name();
			}
		}
		CatalogInstrument found = this.fullCatalog ? this.mongo.findById(symbol, CatalogInstrument.class) : null;
		return (found != null) ? found.getName() : "";
	}

	/** Symbols that start with the term rank first, then matches on the company name or ISIN. */
	public List<Instrument> search(String term, int limit) {
		String trimmed = term.trim();
		if (trimmed.isEmpty()) {
			return List.of();
		}
		Map<String, Instrument> results = new LinkedHashMap<>();
		if (this.fullCatalog) {
			Pattern prefix = Pattern.compile("^" + Pattern.quote(trimmed), Pattern.CASE_INSENSITIVE);
			Pattern contains = Pattern.compile(Pattern.quote(trimmed), Pattern.CASE_INSENSITIVE);
			addMatches(results, Criteria.where("_id").regex(prefix), limit);
			addMatches(results, new Criteria().orOperator(Criteria.where("name").regex(contains),
					Criteria.where("isin").regex(prefix)), limit);
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		this.defaults.stream()
			.filter((item) -> item.symbol().toLowerCase(Locale.ROOT).contains(lower)
					|| item.name().toLowerCase(Locale.ROOT).contains(lower)
					|| item.isin().toLowerCase(Locale.ROOT).contains(lower))
			.forEach((item) -> results.putIfAbsent(item.symbol(), item));
		return results.values().stream().limit(limit).toList();
	}

	private void addMatches(Map<String, Instrument> results, Criteria criteria, int limit) {
		Query query = Query.query(criteria).with(Sort.by("_id")).limit(limit);
		for (CatalogInstrument item : this.mongo.find(query, CatalogInstrument.class)) {
			results.putIfAbsent(item.getSymbol(),
					new Instrument(item.getSymbol(), item.getName(), item.getIsin(), null));
		}
	}

}
