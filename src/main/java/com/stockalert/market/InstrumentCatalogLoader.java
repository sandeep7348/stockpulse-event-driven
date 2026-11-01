package com.stockalert.market;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the catalog of NSE stocks current from Upstox's public instrument file, which needs
 * no account. The file also lists bonds and other debt, which are left out. If a download
 * fails, the catalog already stored stays in use.
 */
@Component
@ConditionalOnProperty(name = "app.feed.source", havingValue = "yahoo")
public class InstrumentCatalogLoader {

	private static final Logger log = LoggerFactory.getLogger(InstrumentCatalogLoader.class);

	private static final String EQUITY_SEGMENT = "NSE_EQ";

	/** Equity series: main board (EQ, BE, BZ) and SME (SM, ST). */
	private static final Set<String> EQUITY_TYPES = Set.of("EQ", "BE", "BZ", "SM", "ST");

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	private final MongoTemplate mongo;

	private final JsonMapper jsonMapper;

	private final String catalogUrl;

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	public InstrumentCatalogLoader(MongoTemplate mongo, JsonMapper jsonMapper,
			@Value("${app.catalog.url}") String catalogUrl) {
		this.mongo = mongo;
		this.jsonMapper = jsonMapper;
		this.catalogUrl = catalogUrl;
	}

	@Scheduled(fixedDelayString = "PT24H")
	public void load() {
		try {
			store(download());
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Could not refresh the stock catalog; keeping the stored one: {}", ex.toString());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private List<CatalogInstrument> download() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(this.catalogUrl)).timeout(TIMEOUT).GET().build();
		HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body()) {
			if (response.statusCode() != 200) {
				throw new IOException("catalog download returned HTTP " + response.statusCode());
			}
			List<CatalogInstrument> instruments = new ArrayList<>();
			// Streamed one entry at a time, because the file expands to tens of megabytes.
			try (GZIPInputStream json = new GZIPInputStream(body);
					MappingIterator<JsonNode> entries = this.jsonMapper.readerFor(JsonNode.class).readValues(json)) {
				while (entries.hasNext()) {
					JsonNode entry = entries.next();
					if (EQUITY_SEGMENT.equals(entry.path("segment").asString(""))
							&& EQUITY_TYPES.contains(entry.path("instrument_type").asString(""))) {
						instruments.add(new CatalogInstrument(entry.path("trading_symbol").asString(),
								entry.path("name").asString(), entry.path("isin").asString()));
					}
				}
			}
			return instruments;
		}
	}

	private void store(List<CatalogInstrument> instruments) {
		long existing = this.mongo.count(new Query(), CatalogInstrument.class);
		// A damaged file should not wipe out a good catalog.
		if (instruments.size() < existing / 2 || instruments.isEmpty()) {
			log.warn("Ignoring a catalog of {} stocks, against {} stored", instruments.size(), existing);
			return;
		}
		BulkOperations bulk = this.mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, CatalogInstrument.class);
		for (CatalogInstrument item : instruments) {
			bulk.upsert(Query.query(Criteria.where("_id").is(item.getSymbol())),
					new Update().set("name", item.getName()).set("isin", item.getIsin()));
		}
		bulk.execute();
		List<String> symbols = instruments.stream().map(CatalogInstrument::getSymbol).toList();
		this.mongo.remove(Query.query(Criteria.where("_id").nin(symbols)), CatalogInstrument.class);
		log.info("Loaded {} NSE stocks into the catalog", instruments.size());
	}

}
