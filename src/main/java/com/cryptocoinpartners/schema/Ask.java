package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@Entity
public class Ask extends Quote {
    public Ask( MarketListing marketListing, Instant time, Instant timeReceived, BigDecimal price, BigDecimal amount) {
        super(Side.SELL, marketListing, time, null, price, amount);
        setTimeReceived(timeReceived);
    }


    // JPA
    protected Ask() { }
}
