package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;


/**
 * @author Tim Olson
 */
@Entity
public class Ask extends Quote {
    public Ask( Market market, Instant time, Instant timeReceived, long priceCount, long volumeCount) {
        super(Side.SELL, market, time, null, priceCount, volumeCount);
        setTimeReceived(timeReceived);
    }


    // JPA
    protected Ask() { }
}
