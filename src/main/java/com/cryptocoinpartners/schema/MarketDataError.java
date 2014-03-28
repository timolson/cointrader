package com.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;


/**
 * This event is posted when there are any problems retreiving market data
 *
 * @author Tim Olson
 */
@MappedSuperclass
public class MarketDataError extends Event {

    public MarketDataError(Listing listing) {
        this(listing,null);
    }


    public MarketDataError(Listing listing, @Nullable Exception exception) {
        this.exception = exception;
        this.listing = listing;
    }


    @Nullable
    public Exception getException() {
        return exception;
    }


    @ManyToOne
    public Listing getListing() {
        return listing;
    }


    protected void setException(@Nullable Exception exception) {
        this.exception = exception;
    }


    protected void setListing(Listing listing) {
        this.listing = listing;
    }


    private Exception exception;
    private Listing listing;
}
