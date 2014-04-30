package com.cryptocoinpartners.schema;


import com.cryptocoinpartners.util.PersistUtil;
import com.cryptocoinpartners.util.Visitor;
import org.joda.time.Instant;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.List;


/**
 * Trade represents a single known transaction of a MarketListing
 *
 * @author Tim Olson
 */
@Entity
@Table(indexes = {@Index(columnList = "time"),@Index(columnList = "timeReceived"),@Index(columnList = "marketListing_id,remoteKey")})
public class Trade extends Pricing {

    public Trade(MarketListing marketListing, Instant time, @Nullable String remoteKey, BigDecimal price, BigDecimal amount) {
        super(time, remoteKey, marketListing, price, amount);
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
