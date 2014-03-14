package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
public class Ask extends Quote {
    public Ask( Security security, Instant time, BigDecimal price, BigDecimal size) {
        super(Side.SELL, security, time, price, size);
    }
}
