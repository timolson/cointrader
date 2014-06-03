package org.cryptocoinpartners.schema;

import org.joda.time.Instant;


public interface Spread
{
    public Instant getTime();
    public MarketListing getMarketListing();
    public Bid getBestBid();
    public Ask getBestAsk();
}
