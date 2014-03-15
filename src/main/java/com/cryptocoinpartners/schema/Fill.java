package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.ManyToOne;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
public class Fill extends Pricing {


    public Fill(Order order, Instant time, Security security, BigDecimal price, BigDecimal amount ) {
        super(time, security, price, amount);
        this.order = order;
    }


    public @ManyToOne Order getOrder() { return order; }


    protected void setOrder(Order order) { this.order = order; }


    private Order order;
}
