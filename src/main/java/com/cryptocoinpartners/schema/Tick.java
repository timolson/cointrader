package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import java.math.BigDecimal;


/**
 * A Tick is a point-in-time snapshot of a Security
 *
 * @author Tim Olson
 */
public class Tick extends Pricing {
    public Tick(Security security, Instant time, BigDecimal price, BigDecimal size) {
        super(security, time, price, size);
    }
}
