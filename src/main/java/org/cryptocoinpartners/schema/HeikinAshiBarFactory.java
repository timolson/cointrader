package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;

import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface HeikinAshiBarFactory {

  HeikinAshiBar create(
      long timestamp,
      @Assisted("barInterval") Double interval,
      @Assisted("barOpen") Double open,
      @Assisted("barClose") Double close,
      @Assisted("barHigh") Double high,
      @Assisted("barLow") Double low,
      @Assisted("barVolume") Double volume,
      @Assisted("barBuyVolume") Double buyVolume,
      @Assisted("barSellVolume") Double sellVolume,
      Tradeable market);

  HeikinAshiBar create(@Assisted Bar bar);

  HeikinAshiBar create(
      @Assisted("barTime") Instant time,
      @Assisted("barRecievedTime") Instant recievedTime,
      @Nullable String remoteKey,
      @Assisted("barInterval") Double interval,
      @Assisted("barOpen") Double open,
      @Assisted("barClose") Double close,
      @Assisted("barHigh") Double high,
      @Assisted("barLow") Double low,
      @Assisted("barVolume") Double volume,
      @Assisted("barBuyVolume") Double buyVolume,
      @Assisted("barSellVolume") Double sellVolume,
      Tradeable market);
}
