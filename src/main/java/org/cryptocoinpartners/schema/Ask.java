package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;


/**
 * @author Tim Olson
 */
@Entity
public class Ask extends Quote {
    public Ask( MarketListing marketListing, Instant time, Instant timeReceived, long priceCount, long volumeCount) {
        super(Side.SELL, marketListing, time, null, priceCount, volumeCount);
        setTimeReceived(timeReceived);
    }


    // JPA
    protected Ask() { }
}
