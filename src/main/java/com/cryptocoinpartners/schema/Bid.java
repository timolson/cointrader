package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
public class Bid extends Quote {
    public Bid(MarketListing marketListing, Instant time, Instant timeReceived, BigDecimal price, BigDecimal amount) {
        super(Side.BUY, marketListing, time, null, price, amount);
        setTimeReceived(timeReceived);
    }
}
