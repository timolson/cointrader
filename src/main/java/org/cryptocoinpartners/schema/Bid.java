package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;


/**
 * @author Tim Olson
 */
@Entity
public class Bid extends Quote {
    public Bid(Market market, Instant time, Instant timeReceived, long priceCount, long volumeCount) {
        super(Side.BUY, market, time, null, priceCount, volumeCount);
        setTimeReceived(timeReceived);
    }


    // JPA
    protected Bid() {}
}
