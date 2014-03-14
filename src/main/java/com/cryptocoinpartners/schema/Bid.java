package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
public class Bid extends Quote {
    public Bid(Security security, Instant time, BigDecimal price, BigDecimal size) {
        super(Side.BUY, security, time, price, size);
    }
}
