package com.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class MarketData extends RemoteEvent {


    public MarketData(Instant time, @Nullable String remoteKey, Listing listing) {
        super(time,remoteKey);
        this.listing = listing;
    }


    public @ManyToOne
    Listing getListing() { return listing; }


    // JPA
    protected MarketData() {
        super();
    }

    protected void setListing(Listing listing) { this.listing = listing; }


    private Listing listing;
}
