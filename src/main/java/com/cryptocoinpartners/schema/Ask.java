package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
public class Ask extends Quote {
    public Ask( Listing listing, Instant time, Instant timeReceived, BigDecimal price, BigDecimal amount) {
        super(Side.SELL, listing, time, null, price, amount);
        setTimeReceived(timeReceived);
    }
}
