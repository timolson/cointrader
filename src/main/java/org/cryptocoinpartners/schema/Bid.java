package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;


/**
 * @author Tim Olson
 */
@Entity
public class Bid extends Quote {
    public Bid(MarketListing marketListing, Instant time, Instant timeReceived, long priceCount, long volumeCount) {
        super(Side.BUY, marketListing, time, null, priceCount, volumeCount);
        setTimeReceived(timeReceived);
    }


    // JPA
    protected Bid() {}
}
