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


    public Instant getEndInstant() { return endInstant; }


    /**
     @return null if no Trade prior to getStartInstant() could be found.
     */
    @ManyToOne
    public @Nullable Trade getLastTrade() { return lastTrade; }


    /**
     @return how much volume was transacted during the window at any price
     */
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


    // JPA
    protected Tick() {}
    protected void setStartInstant( Instant startInstant ) { this.startInstant = startInstant; }
    protected void setEndInstant( Instant endInstant ) { this.endInstant = endInstant; }
    protected void setLastTrade( Trade lastTrade ) { this.lastTrade = lastTrade; }
    protected void setAmount( BigDecimal amount ) { this.amount = amount; }
    protected void setBestBid( Bid bestBid ) { this.bestBid = bestBid; }
    protected void setBestAsk( Ask bestAsk ) { this.bestAsk = bestAsk; }


    private Instant startInstant;
    private Instant endInstant;
    private Trade lastTrade;
    private BigDecimal amount;
    private Bid bestBid;
    private Ask bestAsk;
}
