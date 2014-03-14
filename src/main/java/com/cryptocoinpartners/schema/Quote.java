package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.MappedSuperclass;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class Quote extends Pricing {

    public Quote(Side side, Security security, Instant time, BigDecimal price, BigDecimal size) {
        super(security, time, price, size);
        this.side = side;
    }


    public Side getSide() {
        return side;
    }


    // JPA
    protected Quote() { }
    protected void setSide(Side side) { this.side = side; }


    private Side side;
}
