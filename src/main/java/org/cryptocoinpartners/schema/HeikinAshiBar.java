package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;

import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

@SuppressWarnings("UnusedDeclaration")
// @Entity
// @Table(indexes = {@Index(columnList = "market,time,interval")})
public class HeikinAshiBar extends Bar {

  @AssistedInject
  public HeikinAshiBar(
      @Assisted long timestamp,
      @Assisted("barInterval") Double interval,
      @Assisted("barOpen") Double open,
      @Assisted("barClose") Double close,
      @Assisted("barHigh") Double high,
      @Assisted("barLow") Double low,
      @Assisted("barVolume") Double volume,
      @Assisted("barBuyVolume") Double buyVolume,
      @Assisted("barSellVolume") Double sellVolume,
      @Assisted Tradeable market) {
    this(
        new Instant(timestamp),
        Instant.now(),
        null,
        interval,
        open,
        close,
        high,
        low,
        volume,
        buyVolume,
        sellVolume,
        market);
  }

  @AssistedInject
  public HeikinAshiBar(@Assisted Bar bar) {
    super(bar);
  }

  // JPA
  protected HeikinAshiBar() {}

  @AssistedInject
  public HeikinAshiBar(
      @Assisted("barTime") Instant time,
      @Assisted("barRecievedTime") Instant recievedTime,
      @Nullable @Assisted String remoteKey,
      @Assisted("barInterval") Double interval,
      @Assisted("barOpen") Double open,
      @Assisted("barClose") Double close,
      @Assisted("barHigh") Double high,
      @Assisted("barLow") Double low,
      @Assisted("barVolume") Double volume,
      @Assisted("barBuyVolume") Double buyVolume,
      @Assisted("barSellVolume") Double sellVolume,
      @Assisted Tradeable market) {

    super(
        time,
        recievedTime,
        remoteKey,
        interval,
        open,
        close,
        high,
        low,
        volume,
        buyVolume,
        sellVolume,
        market);
  }

  @Override
  public String toString() {

    return "HeikinAshiBar("
        + System.identityHashCode(this)
        + ") Start="
        + (getTimestamp() != 0 ? (FORMAT.print(getTimestamp())) : "")
        + SEPARATOR
        + "Market="
        + getMarket()
        + SEPARATOR
        + "Interval="
        + getInterval()
        + SEPARATOR
        + "Open="
        + getOpen() * getMarket().getPriceBasis()
        + SEPARATOR
        + "High="
        + getHigh() * getMarket().getPriceBasis()
        + SEPARATOR
        + "Low="
        + getLow() * getMarket().getPriceBasis()
        + SEPARATOR
        + "Close="
        + getClose() * getMarket().getPriceBasis()
        + SEPARATOR
        + "Volume="
        + getVolume() * getMarket().getVolumeBasis()
        + SEPARATOR
        + "Buy Volume="
        + getBuyVolume() * getMarket().getVolumeBasis()
        + SEPARATOR
        + "Sell Volume="
        + getSellVolume() * getMarket().getVolumeBasis();
  }
}
