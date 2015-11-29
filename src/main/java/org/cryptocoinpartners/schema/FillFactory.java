package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface FillFactory {
    Fill create(SpecificOrder order, @Assisted("fillTime") Instant time, @Assisted("fillTimeReceived") Instant timeReceived, Market market,
            @Assisted("fillPriceCount") long priceCount, @Assisted("fillVolumeCount") long volumeCount, String remoteKey);

    // Fill create(SpecificOrder order, Instant time, Instant timeReceived, Market market, long priceCount, long volumeCount, Amount commission, String remoteKey);

}
