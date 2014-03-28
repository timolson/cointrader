package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
public class Bid extends Quote {
    public Bid(Listing listing, Instant time, BigDecimal price, BigDecimal amount) {
        super(Side.BUY, listing, time, null, price, amount);
    }
}
