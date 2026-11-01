package com.stockalert.market.score;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.stockalert.common.ApiException;
import com.stockalert.market.InstrumentCatalog;
import com.stockalert.market.MarketHours;
import com.stockalert.market.history.ChartRange;
import com.stockalert.market.history.HistoryService;
import com.stockalert.market.history.PriceHistory;
import com.stockalert.market.yahoo.YahooPriceFeed;

import tools.jackson.databind.json.JsonMapper;

/**
 * Builds and caches scorecards. Reported results change a few times a year, so scorecards stay
 * fresh for hours and are kept for days, in case Yahoo is unavailable later.
 */
@Service
public class ScorecardService {

	private static final Duration FRESH_FOR = Duration.ofHours(6);

	private static final Duration KEEP_FOR = Duration.ofDays(7);

	/** Yahoo's endpoint has no sector, so lenders and insurers are recognised by name. */
	private static final Pattern FINANCIAL_NAME = Pattern
		.compile("BANK|FINANC|FINSERV|\\bFIN\\b|INSURANCE|ASSURANCE|CAPITAL|CREDIT|LENDING|HOUSING");

	private final StringRedisTemplate redis;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	private final InstrumentCatalog catalog;

	private final HistoryService historyService;

	private final ObjectProvider<YahooFundamentalsClient> client;

	private final ObjectProvider<YahooPriceFeed> feed;

	public ScorecardService(StringRedisTemplate redis, JsonMapper jsonMapper, Clock clock, InstrumentCatalog catalog,
			HistoryService historyService, ObjectProvider<YahooFundamentalsClient> client,
			ObjectProvider<YahooPriceFeed> feed) {
		this.redis = redis;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.catalog = catalog;
		this.historyService = historyService;
		this.client = client;
		this.feed = feed;
	}

	public Scorecard get(String symbol) {
		YahooFundamentalsClient yahoo = this.client.getIfAvailable();
		YahooPriceFeed requests = this.feed.getIfAvailable();
		if (yahoo == null || requests == null) {
			throw new ApiException(HttpStatus.CONFLICT, "The stock score needs the Yahoo Finance feed");
		}
		Scorecard cached = read(symbol);
		Instant now = this.clock.instant();
		if (cached != null && now.isBefore(cached.fetchedAt().plus(FRESH_FOR))) {
			return cached;
		}
		if (!requests.tryAcquireRequest()) {
			return staleOrUnavailable(cached);
		}
		try {
			Fundamentals fundamentals = yahoo.fetch(symbol);
			Scorecard scorecard = ScoreCalculator.calculate(symbol, fundamentals, dailyCloses(symbol),
					isFinancialCompany(symbol), now.atZone(MarketHours.IST).toLocalDate(), now);
			write(scorecard);
			return scorecard;
		}
		catch (IOException ex) {
			requests.reportUnavailable(ex);
			return staleOrUnavailable(cached);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return staleOrUnavailable(cached);
		}
	}

	static boolean looksFinancial(String symbol, String name) {
		return FINANCIAL_NAME.matcher((symbol + " " + name).toUpperCase(Locale.ROOT)).find();
	}

	private boolean isFinancialCompany(String symbol) {
		return looksFinancial(symbol, this.catalog.nameOf(symbol));
	}

	/** Without prices the score can still cover the company's results, with lower confidence. */
	private double[] dailyCloses(String symbol) {
		try {
			List<PriceHistory.Point> points = this.historyService.get(symbol, ChartRange.Y1).points();
			return points.stream().mapToDouble(PriceHistory.Point::close).toArray();
		}
		catch (ApiException ex) {
			return new double[0];
		}
	}

	private Scorecard staleOrUnavailable(Scorecard cached) {
		if (cached != null) {
			return cached.asStale();
		}
		throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
				"The stock score isn't available right now. Please try again shortly.");
	}

	private Scorecard read(String symbol) {
		String json = this.redis.opsForValue().get(key(symbol));
		return (json != null) ? this.jsonMapper.readValue(json, Scorecard.class) : null;
	}

	private void write(Scorecard scorecard) {
		this.redis.opsForValue().set(key(scorecard.symbol()), this.jsonMapper.writeValueAsString(scorecard), KEEP_FOR);
	}

	private static String key(String symbol) {
		return "scorecard:" + symbol;
	}

}
