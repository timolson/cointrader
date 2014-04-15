package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import java.math.BigDecimal;


/**
 * A Tick is a point-in-time snapshot of a MarketListing
 *
 * @author Tim Olson
 */
@Entity
public class Tick extends Pricing {

    public Tick(MarketListing marketListing, String remoteKey, Instant time, BigDecimal price, BigDecimal amount) {
        super(time, remoteKey, marketListing, price, amount);
    }


    // todo


    // JPA
    protected Tick() {}

}
