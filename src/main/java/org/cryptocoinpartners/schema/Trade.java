package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Visitor;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Trade represents a single known transaction of a Market
 *
 * @author Tim Olson
 */
@Entity
@Cacheable(false)
@Table(indexes = { @Index(columnList = "time"), @Index(columnList = "timeReceived"), @Index(columnList = "market"), @Index(columnList = "market,time"),
        @Index(columnList = "market,remoteKey") })
public class Trade extends PriceData {
    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    private static final String SEPARATOR = ",";

    public static Trade fromDoubles(Market market, Instant time, @Nullable String remoteKey, double price, double volume) {
        long priceCount = Math.round(price / market.getPriceBasis());
        long volumeCount = Math.round(volume / market.getVolumeBasis());
        return new Trade(market, time, remoteKey, priceCount, volumeCount);
    }

    public static Trade fromDoubles(Market market, Instant time, Instant timeRecieved, @Nullable String remoteKey, double price, double volume) {
        long priceCount = Math.round(price / market.getPriceBasis());
        long volumeCount = Math.round(volume / market.getVolumeBasis());
        return new Trade(market, time, timeRecieved, remoteKey, priceCount, volumeCount);
    }

    /**
     * @param market what Market was traded
     * @param time when the trade originally occured
     * @param remoteKey the unique key assigned by the market data provider to this trade.  helps prevent duplication of market data
     * @param priceCount the trade price as a count of "pips," where the size of the pip is the market's priceBasis()
     * @param volumeCount the trade price as a count of "pips," where the size of the pip is the market's volumeBasis()
     */
    public Trade(Market market, Instant time, @Nullable String remoteKey, long priceCount, long volumeCount) {
        super(time, remoteKey, market, priceCount, volumeCount);
    }

    public Trade(Market market, Instant time, Instant timeRecieved, @Nullable String remoteKey, long priceCount, long volumeCount) {
        super(time, timeRecieved, remoteKey, market, priceCount, volumeCount);
    }

    public Trade(Market market, Instant time, @Nullable String remoteKey, BigDecimal price, BigDecimal volume) {
        super(time, remoteKey, market, price, volume);
    }

    public static void find(Interval timeInterval, Visitor<Trade> visitor) {
        PersistUtil.queryEach(Trade.class, visitor, "select t from Trade t where time > ?1 and time < ?2", timeInterval.getStartMillis(),
                timeInterval.getEndMillis());
    }

    public static void forAll(Visitor<Trade> visitor) {
        PersistUtil.queryEach(Trade.class, visitor, "select t from Trade t");
    }

    @Override
    public String toString() {

        return "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "Market=" + getMarket() + SEPARATOR + "Price=" + getPriceAsDouble()
                + SEPARATOR + "Volume=" + getVolumeAsDouble();
    }

    public Trade() {
    } // JPA only

}
