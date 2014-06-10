package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.math.BigDecimal;


/**
 * A GeneralOrder only specifies a Listing but not a Market.  The GeneralOrder must be processed and broken down into
 * a series of SpecificOrders before it can be placed on Markets.  GeneralOrders express their amounts and prices using
 * BigDecimal, since the trading basis at each Market may be different, thus a DiscreteAmount cannot be used.
 *
 * @author Tim Olson
 */
@Entity
public class GeneralOrder extends Order {

    public GeneralOrder(Listing listing, BigDecimal amount)
    {
        this.listing = listing;
        this.amount = amount;
    }


    public GeneralOrder(Listing listing, double amount)
    {
        this.listing = listing;
        this.amount = BigDecimal.valueOf(amount);
    }


    @ManyToOne(optional = false)
    public Listing getListing() { return listing; }


    @Column(precision = 65, scale = 30)
    @Basic(optional = false)
    public BigDecimal getAmount() { return amount; }


    @Column(precision = 65, scale = 30)
    @Nullable
    public BigDecimal getLimitPrice() { return limitPrice; }


    @Column(precision = 65, scale = 30)
    @Nullable
    public BigDecimal getStopPrice() { return stopPrice; }


    @Transient
    public boolean isBid() { return amount.compareTo(BigDecimal.ZERO) > 0; }


    protected GeneralOrder() { }
    protected void setAmount(BigDecimal amount) { this.amount = amount; }
    protected void setLimitPrice(BigDecimal limit) { this.limitPrice = limit; }
    protected void setStopPrice(BigDecimal stopPrice) { this.stopPrice = stopPrice; }
    protected void setListing(Listing listing) { this.listing = listing; }


    private Listing listing;
    private BigDecimal amount;
    private BigDecimal limitPrice;
    private BigDecimal stopPrice;
}
