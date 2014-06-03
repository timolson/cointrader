package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;


/**
 * A Tick is a point-in-time snapshot of a MarketListing's last price, volume and spread
 *
 * @author Tim Olson
 */
@Entity
public class Tick extends PriceData implements Spread {


    public Instant getStartInstant() { return startInstant; }


    @Transient
    public Instant getEndInstant() { return getTime(); }


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
                 @Nullable Long lastPriceCount, @Nullable Long volumeCount, Bid bestBid, Ask bestAsk )
    {
        super(endInstant,null,marketListing,lastPriceCount,volumeCount);
        this.startInstant = startInstant;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
    }


    public String toString()
    {
        return String.format("Tick{%s last:%g@%g bid:%s ask:%s}",
                             getMarketListing(), getVolumeAsDouble(), getPriceAsDouble(), getBestBid(), getBestAsk() );
    }


    // JPA
    protected Tick() {}
    protected void setStartInstant( Instant startInstant ) { this.startInstant = startInstant; }
    protected void setBestBid( Bid bestBid ) { this.bestBid = bestBid; }
    protected void setBestAsk( Ask bestAsk ) { this.bestAsk = bestAsk; }


    private Instant startInstant;
    private Bid bestBid;
    private Ask bestAsk;
}
