package com.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class Pricing extends MarketData {

    public Pricing(Instant time, Security security, BigDecimal price, BigDecimal amount) {
        super(time, security);
        this.price = price;
        this.amount = amount;
    }


    @Column(precision = 30, scale = 15)
    public BigDecimal getPrice() { return price; }

    @Column(precision = 30, scale = 15)
    public BigDecimal getAmount() { return amount; }


    // JPA
    protected Pricing() { super(); }
    protected void setPrice(BigDecimal price) { this.price = price; }
    protected void setAmount(BigDecimal amount) { this.amount = amount; }


    private BigDecimal price;
    private BigDecimal amount;
}
