package org.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.*;


/**
 * @author Tim Olson
 */
@Entity
public abstract class MarketData extends RemoteEvent {


    protected MarketData(Instant time, @Nullable String remoteKey, MarketListing marketListing ) {
        super(time,remoteKey);
        this.marketListing = marketListing;
    }


    @ManyToOne(optional = false)
    public MarketListing getMarketListing() { return marketListing; }


    // JPA
    protected MarketData() {
        super();
    }

    protected void setMarketListing(MarketListing marketListing ) { this.marketListing = marketListing; }


    private MarketListing marketListing;
}
