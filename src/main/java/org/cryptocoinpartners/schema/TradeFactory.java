package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.annotation.Nullable;

import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface TradeFactory {

    Trade create(Tradeable market, Instant time, @Nullable String remoteKey, @Assisted("tradePriceCount") long priceCount,
            @Assisted("tradeVolumeCount") long volumeCount);

    Trade create(Tradeable market, @Assisted("tradeTime") Instant time, @Assisted("tradeTimeRecieved") Instant timeRecieved, @Nullable String remoteKey,
            @Assisted("tradePriceCount") long priceCount, @Assisted("tradeVolumeCount") long volumeCount);

    Trade create(Tradeable market, Instant time, @Nullable String remoteKey, @Assisted("tradePrice") BigDecimal price,
            @Assisted("tradeVolume") BigDecimal volume);

    //  Trade fromDoubles(Tradeable market, Instant time, String remoteKey, @Assisted("tradePrice") double price, @Assisted("tradeVolume") double volume);

    //Trade fromDoubles(Tradeable market, @Assisted("tradeTime") Instant time, @Assisted("timeRecieved") Instant timeRecieved, String remoteKey,
    //      @Assisted("tradePrice") double price, @Assisted("tradeVolume") double volume);
}
