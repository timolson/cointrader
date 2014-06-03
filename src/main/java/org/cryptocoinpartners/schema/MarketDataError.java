package org.cryptocoinpartners.schema;

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

    public MarketDataError(MarketListing marketListing ) {
        this(marketListing,null);
    }


    public MarketDataError(MarketListing marketListing, @Nullable Exception exception) {
        this.exception = exception;
        this.marketListing = marketListing;
    }


    @Nullable
    public Exception getException() {
        return exception;
    }


    @ManyToOne
    public MarketListing getMarketListing() {
        return marketListing;
    }


    protected MarketDataError() {}
    protected void setException(@Nullable Exception exception) { this.exception = exception; }
    protected void setMarketListing(MarketListing marketListing ) { this.marketListing = marketListing; }


    private Exception exception;
    private MarketListing marketListing;
}
