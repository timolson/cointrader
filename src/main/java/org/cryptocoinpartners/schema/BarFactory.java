package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;

import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface BarFactory {

    Bar create(long timestamp, @Assisted("barInterval") Double interval, @Assisted("barOpen") Double open, @Assisted("barClose") Double close,
            @Assisted("barHigh") Double high, @Assisted("barLow") Double low, @Assisted("barVolume") Double volume, Tradeable market);

    Bar create(@Assisted Bar bar);

    Bar create(@Assisted("barTime") Instant time, @Assisted("barRecievedTime") Instant recievedTime, @Nullable String remoteKey,
            @Assisted("barInterval") Double interval, @Assisted("barOpen") Double open, @Assisted("barClose") Double close, @Assisted("barHigh") Double high,
            @Assisted("barLow") Double low, @Assisted("barVolume") Double volume, Tradeable market);
}
