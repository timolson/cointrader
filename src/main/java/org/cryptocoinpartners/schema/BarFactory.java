package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;

import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface BarFactory {

    Bar create(long timestamp, @Assisted("barOpen") Double open, @Assisted("barClose") Double close, @Assisted("barHigh") Double high,
            @Assisted("barLow") Double low, Market market);

    Bar create(@Assisted Bar bar);

    Bar create(@Assisted("barTime") Instant time, @Assisted("barRecievedTime") Instant recievedTime, @Nullable String remoteKey,
            @Assisted("barOpen") Double open, @Assisted("barClose") Double close, @Assisted("barHigh") Double high, @Assisted("barLow") Double low,
            Market market);
}
