package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.math.BigDecimal;


/**
 * A Tick is a point-in-time snapshot of a MarketListing's last price, volume and spread
 *
 * @author Tim Olson
 */
@Entity
public class Tick extends Event implements Spread {


    public Instant getStartInstant() { return startInstant; }


    @Transient
    public Instant getEndInstant() { return getTime(); }


    @ManyToOne
    public MarketListing getMarketListing() { return marketListing; }


    /**
     @return null if no Trade prior to getStartInstant() could be found.
     */
    @Column(precision = 30, scale = 15)
    public @Nullable BigDecimal getLastPrice() { return lastPrice; }


    /**
     @return how much volume was transacted during the window at any price
     */
    @Column(precision = 30, scale = 15)
    public BigDecimal getAmount() { return amount; }


    /**
     @return null if no book was found prior to the window
     */
    @ManyToOne
    public @Nullable Bid getBestBid() { return bestBid; }


    /**
     @return null if no book was found prior to the window
     */
    @ManyToOne
    public @Nullable Ask getBestAsk() { return bestAsk; }


    public Tick( MarketListing marketListing, Instant startInstant, Instant endInstant,
                 BigDecimal lastPrice, BigDecimal amount, Bid bestBid, Ask bestAsk )
    {
        super(endInstant);
        this.marketListing = marketListing;
        this.startInstant = startInstant;
        this.lastPrice = lastPrice;
        this.amount = amount;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
    }


    public String toString()
    {
        return String.format("Tick{%s last:%g@@g bid:%s ask:%s}",
                             getLastPrice(),getAmount(),getBestBid(),getBestAsk());
    }


    // JPA
    protected Tick() {}
    protected void setStartInstant( Instant startInstant ) { this.startInstant = startInstant; }
    protected void setLastPrice( BigDecimal lastPrice ) { this.lastPrice = lastPrice; }
    protected void setAmount( BigDecimal amount ) { this.amount = amount; }
    protected void setBestBid( Bid bestBid ) { this.bestBid = bestBid; }
    protected void setBestAsk( Ask bestAsk ) { this.bestAsk = bestAsk; }
    protected void setMarketListing( MarketListing marketListing ) { this.marketListing = marketListing; }


    private MarketListing marketListing;
    private Instant startInstant;
    private BigDecimal lastPrice;
    private BigDecimal amount;
    private Bid bestBid;
    private Ask bestAsk;
}
