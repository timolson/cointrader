package com.cryptocoinpartners.schema;

import javax.persistence.*;
import java.math.BigDecimal;


/**
 * A Fund has many Positions.  A Position represents an amount of some Security.
 *
 * @author Tim Olson
 */
@Entity
public class Position extends EntityBase {

    public Position(Fund fund, Market market, Fungible fungible, BigDecimal amount) {
        this.fund = fund;
        this.market = market;
        this.amount = amount;
        this.fungible = fungible;
    }


    @ManyToOne(optional = false) public Fund getFund() { return fund; }
    @ManyToOne(optional = false) public Market getMarket() { return market; }
    @Basic(optional = false) public BigDecimal getAmount() { return amount; }
    @OneToOne(optional = false) public Fungible getFungible() { return fungible; }


    // JPA
    protected Position() { }
    protected void setFund(Fund fund) { this.fund = fund; }
    protected void setMarket(Market market) { this.market = market; }
    protected void setAmount(BigDecimal amount) { this.amount = amount; }
    protected void setFungible(Fungible fungible) { this.fungible = fungible; }


    private Fund fund;
    private BigDecimal amount;
    private Fungible fungible;
    private Market market;
}
