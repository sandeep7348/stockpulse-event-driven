package com.stockalert.market.upstox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.protobuf.InvalidProtocolBufferException;
import com.stockalert.market.PriceTick;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.Feed;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.FeedResponse;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.LTPC;

/**
 * Converts Upstox protobuf messages to price ticks. Messages with no usable prices, such
 * as the market status message sent first on each connection, produce no ticks.
 */
final class UpstoxMessageDecoder {

	private final Map<String, String> symbolsByKey;

	UpstoxMessageDecoder(Map<String, String> symbolsByKey) {
		this.symbolsByKey = Map.copyOf(symbolsByKey);
	}

	List<PriceTick> decode(byte[] message) throws InvalidProtocolBufferException {
		FeedResponse response = FeedResponse.parseFrom(message);
		List<PriceTick> ticks = new ArrayList<>();
		for (Map.Entry<String, Feed> entry : response.getFeedsMap().entrySet()) {
			String symbol = this.symbolsByKey.get(entry.getKey());
			Feed feed = entry.getValue();
			if (symbol == null || !feed.hasLtpc() || feed.getLtpc().getLtp() <= 0) {
				continue;
			}
			LTPC ltpc = feed.getLtpc();
			long millis = (ltpc.getLtt() > 0) ? ltpc.getLtt() : response.getCurrentTs();
			ticks.add(new PriceTick(symbol, Math.round(ltpc.getLtp() * 100), Instant.ofEpochMilli(millis)));
		}
		return ticks;
	}

}
