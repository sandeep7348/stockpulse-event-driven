package com.stockalert.market;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockalert.common.ApiException;
import com.stockalert.config.AppProperties;
import com.stockalert.config.AppProperties.Instrument;
import com.stockalert.market.history.ChartRange;
import com.stockalert.market.history.HistoryService;
import com.stockalert.market.history.PriceHistory;
import com.stockalert.market.score.Scorecard;
import com.stockalert.market.score.ScorecardService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/market")
@SecurityRequirement(name = "bearerAuth")
public class MarketController {

	private final InstrumentCatalog catalog;

	private final PriceService priceService;

	private final HistoryService historyService;

	private final ScorecardService scorecardService;

	private final MarketHours marketHours;

	private final Clock clock;

	private final String source;

	public MarketController(InstrumentCatalog catalog, PriceService priceService, HistoryService historyService,
			ScorecardService scorecardService, MarketHours marketHours, Clock clock, AppProperties properties) {
		this.catalog = catalog;
		this.priceService = priceService;
		this.historyService = historyService;
		this.scorecardService = scorecardService;
		this.marketHours = marketHours;
		this.clock = clock;
		this.source = properties.feed().source();
	}

	@GetMapping("/status")
	public MarketStatus status() {
		return new MarketStatus("NSE", this.marketHours.isOpen(this.clock.instant()), MarketHours.IST.getId(),
				this.source);
	}

	@GetMapping("/instruments")
	public List<InstrumentResponse> instruments() {
		return this.catalog.all().stream().map(InstrumentResponse::from).toList();
	}

	@GetMapping("/instruments/search")
	public List<InstrumentResponse> search(@RequestParam @Size(max = 40) String q,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
		return this.catalog.search(q, limit).stream().map(InstrumentResponse::from).toList();
	}

	@GetMapping("/prices/{symbol}")
	public PriceResponse price(@PathVariable String symbol) {
		String known = this.catalog.requireSymbol(symbol);
		PriceTick tick = this.priceService.latest(known)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No price received yet for " + known));
		return new PriceResponse(known, "INR", Paise.toRupees(tick.paise()), tick.timestamp(), this.source);
	}

	@GetMapping("/history/{symbol}")
	public PriceHistory history(@PathVariable String symbol, @RequestParam(defaultValue = "1M") String range) {
		return this.historyService.get(this.catalog.requireSymbol(symbol), ChartRange.parse(range));
	}

	@GetMapping("/scorecard/{symbol}")
	public Scorecard scorecard(@PathVariable String symbol) {
		return this.scorecardService.get(this.catalog.requireSymbol(symbol));
	}

	public record MarketStatus(String exchange, boolean open, String timeZone, String priceSource) {
	}

	public record InstrumentResponse(String symbol, String name, String isin, String exchange) {

		static InstrumentResponse from(Instrument instrument) {
			return new InstrumentResponse(instrument.symbol(), instrument.name(), instrument.isin(), "NSE");
		}

	}

	public record PriceResponse(String symbol, String currency, BigDecimal price, Instant timestamp, String source) {
	}

}
