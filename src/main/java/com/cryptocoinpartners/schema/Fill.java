package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;


/**
 * A Fill represents some completion of an Order.  The volume of the Fill might be less than the requested volume of the
 * Order
 *
 * @author Tim Olson
 */
@Entity
public class Fill extends RemoteEvent {


    public Fill(Order order, Instant time, MarketListing marketListing, long priceCount, long amountCount ) {
        super(time,null);
        this.order = order;
        this.marketListing = marketListing;
        this.priceCount = priceCount;
        this.amountCount = amountCount;
    }


    public @ManyToOne Order getOrder() { return order; }


    // JPA
    protected Fill() {}
    protected void setOrder(Order order) { this.order = order; }
    protected void setMarketListing(MarketListing marketListing) { this.marketListing = marketListing; }
    protected void setPriceCount(long priceCount) { this.priceCount = priceCount; }
    protected void setAmountCount(long amountCount) { this.amountCount = amountCount; }


    private Order order;
    private MarketListing marketListing;
    private long priceCount;
    private long amountCount;
}
