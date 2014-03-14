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
public class Pricing extends Event {

    public Pricing(Security security, Instant time, BigDecimal price, BigDecimal size) {
        super(time);
        this.security = security;
        this.price = price;
        this.size = size;
    }


    public @ManyToOne Security getSecurity() { return security; }

    @Column(precision = 30, scale = 15)
    public BigDecimal getPrice() { return price; }

    @Column(precision = 30, scale = 15)
    public BigDecimal getSize() { return size; }


    // JPA
    protected Pricing() { }
    protected void setSecurity(Security security) { this.security = security; }
    protected void setPrice(BigDecimal price) { this.price = price; }
    protected void setSize(BigDecimal size) { this.size = size; }


    private Security security;
    private BigDecimal price;
    private BigDecimal size;
}
