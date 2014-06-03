package org.cryptocoinpartners.schema;


import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Visitor;
import org.joda.time.Instant;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.math.BigDecimal;


/**
 * Trade represents a single known transaction of a MarketListing
 *
 * @author Tim Olson
 */
@Entity
@Table(indexes = {@Index(columnList = "time"),@Index(columnList = "timeReceived"),@Index(columnList = "marketListing_id,remoteKey")})
public class Trade extends PriceData {

    public static Trade fromDoubles( MarketListing marketListing, Instant time, @Nullable String remoteKey,
                                     double price, double volume) {
        long priceCount = Math.round(price/marketListing.getPriceBasis());
        long volumeCount = Math.round(volume/marketListing.getVolumeBasis());
        return new Trade(marketListing,time,remoteKey,priceCount,volumeCount);
    }


    /**
     * @param marketListing what MarketListing was traded
     * @param time when the trade originally occured
     * @param remoteKey the unique key assigned by the market data provider to this trade.  helps prevent duplication of market data
     * @param priceCount the trade price as a count of "pips," where the size of the pip is the marketListing's priceBasis()
     * @param volumeCount the trade price as a count of "pips," where the size of the pip is the marketListing's volumeBasis()
     */
    public Trade( MarketListing marketListing, Instant time, @Nullable String remoteKey,
                  long priceCount, long volumeCount) {
        super(time, remoteKey, marketListing, priceCount, volumeCount);
    }


    public Trade( MarketListing marketListing, Instant time, @Nullable String remoteKey,
                  BigDecimal price, BigDecimal volume ) {
        super(time, remoteKey, marketListing, price, volume);
    }


    public static void find(Interval timeInterval,Visitor<Trade> visitor) {
        PersistUtil.queryEach(Trade.class,visitor,"select t from Trade t where time > ?1 and time < ?2",
                              timeInterval.getStartMillis(), timeInterval.getEndMillis());
    }


    public static void forAll(Visitor<Trade> visitor) {
        PersistUtil.queryEach(Trade.class,visitor,"select t from Trade t");
    }


    protected Trade() {}  // JPA only
}
