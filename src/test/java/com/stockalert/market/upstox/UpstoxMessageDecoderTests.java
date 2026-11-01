package com.stockalert.market.upstox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.protobuf.InvalidProtocolBufferException;
import com.stockalert.market.PriceTick;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.Feed;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.FeedResponse;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.LTPC;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.MarketInfo;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.MarketStatus;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed.Type;

class UpstoxMessageDecoderTests {

	private static final String RELIANCE_KEY = "NSE_EQ|INE002A01018";

	private final UpstoxMessageDecoder decoder = new UpstoxMessageDecoder(Map.of(RELIANCE_KEY, "RELIANCE"));

	@Test
	void decodesLastTradedPriceAndTime() throws Exception {
		List<PriceTick> ticks = this.decoder.decode(message(RELIANCE_KEY, 1397.75, 1793000000000L));

		assertThat(ticks).containsExactly(new PriceTick("RELIANCE", 139775, Instant.ofEpochMilli(1793000000000L)));
	}

	@Test
	void roundsBinaryFloatingPointToExactPaise() throws Exception {
		// 1234.55 cannot be represented exactly as a double.
		assertThat(this.decoder.decode(message(RELIANCE_KEY, 1234.55, 1L)).get(0).paise()).isEqualTo(123455);
	}

	@Test
	void fallsBackToMessageTimeWhenTradeTimeIsMissing() throws Exception {
		byte[] message = FeedResponse.newBuilder()
			.setCurrentTs(1793000000123L)
			.putFeeds(RELIANCE_KEY, Feed.newBuilder().setLtpc(LTPC.newBuilder().setLtp(100)).build())
			.build()
			.toByteArray();

		assertThat(this.decoder.decode(message).get(0).timestamp()).isEqualTo(Instant.ofEpochMilli(1793000000123L));
	}

	@Test
	void ignoresUnknownInstrumentsAndMissingPrices() throws Exception {
		assertThat(this.decoder.decode(message("NSE_EQ|UNKNOWN", 100, 1L))).isEmpty();
		assertThat(this.decoder.decode(message(RELIANCE_KEY, 0, 1L))).isEmpty();
	}

	@Test
	void marketStatusMessageProducesNoTicks() throws Exception {
		byte[] message = FeedResponse.newBuilder()
			.setType(Type.market_info)
			.setMarketInfo(MarketInfo.newBuilder().putSegmentStatus("NSE_EQ", MarketStatus.NORMAL_OPEN))
			.build()
			.toByteArray();

		assertThat(this.decoder.decode(message)).isEmpty();
	}

	@Test
	void rejectsMalformedMessages() {
		assertThatExceptionOfType(InvalidProtocolBufferException.class)
			.isThrownBy(() -> this.decoder.decode(new byte[] { (byte) 0xFF, (byte) 0xFF, 0x01 }));
	}

	static byte[] message(String key, double ltp, long ltt) {
		return FeedResponse.newBuilder()
			.setType(Type.live_feed)
			.putFeeds(key, Feed.newBuilder().setLtpc(LTPC.newBuilder().setLtp(ltp).setLtt(ltt)).build())
			.build()
			.toByteArray();
	}

}
