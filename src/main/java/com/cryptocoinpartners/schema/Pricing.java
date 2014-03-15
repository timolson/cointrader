package com.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class Pricing extends MarketData {

    public Pricing(Instant time, Security security, BigDecimal price, BigDecimal size) {
        super(time, security);
        this.price = price;
        this.size = size;
    }


    @Column(precision = 30, scale = 15)
    public BigDecimal getPrice() { return price; }

    @Column(precision = 30, scale = 15)
    public BigDecimal getSize() { return size; }


    // JPA
    protected Pricing() { super(); }
    protected void setPrice(BigDecimal price) { this.price = price; }
    protected void setSize(BigDecimal size) { this.size = size; }


    private BigDecimal price;
    private BigDecimal size;
}
