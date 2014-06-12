package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.*;


/**
 * Offers represent a bid or ask, usually from a Book.  Asks are represented by using a negative volumeCount
 *
 * @author Tim Olson
 */
@Entity
public class Offer extends PriceData {


    /** same as new Offer() */
    public static Offer bid(Market market, Instant time, Instant timeReceived, Long priceCount, Long volumeCount) {
        return new Offer( market, time, timeReceived, priceCount, volumeCount );
    }


    /** same as new Offer() except the volumeCount is negated */
    public static Offer ask(Market market, Instant time, Instant timeReceived, Long priceCount, Long volumeCount) {
        return new Offer( market, time, timeReceived, priceCount, -volumeCount );
    }


    public Offer(Market market, Instant time, Instant timeReceived, Long priceCount, Long volumeCount) {
        super(time, timeReceived, null, market, priceCount, volumeCount);
    }


    public String toString() {
        return "Offer{" +
                       ", market=" + getMarket() +
                       ", priceCount=" + getPriceCount() +
                       ", volumeCount=" + getVolumeCount() +
                       '}';
    }


    @SuppressWarnings("ConstantConditions")
    @Transient
    public Side getSide() { return getVolumeCount() >= 0 ? Side.BUY : Side.SELL; }


    // JPA
    protected Offer() {}

}
