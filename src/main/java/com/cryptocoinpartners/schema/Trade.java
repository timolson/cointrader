package com.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import java.math.BigDecimal;


/**
 * Trade represents a single known transaction of a MarketListing
 *
 * @author Tim Olson
 */
@Entity
public class Trade extends Pricing {

    public Trade(MarketListing marketListing, Instant time, @Nullable String remoteKey, BigDecimal price, BigDecimal amount) {
        super(time, remoteKey, marketListing, price, amount);
    }


    protected Trade() {}  // JPA only
}
