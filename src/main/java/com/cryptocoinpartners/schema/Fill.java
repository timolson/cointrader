package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
// todo Fill is not a pricing (which is market data)
@Entity
public class Fill extends Pricing {


    public Fill(Order order, Instant time, Listing listing, BigDecimal price, BigDecimal amount ) {
        super(time, null, listing, price, amount);
        this.order = order;
    }


    public @ManyToOne Order getOrder() { return order; }


    // JPA
    protected Fill() {}
    protected void setOrder(Order order) { this.order = order; }


    private Order order;
}
