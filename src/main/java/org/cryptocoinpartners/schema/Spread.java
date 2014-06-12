package org.cryptocoinpartners.schema;

import org.joda.time.Instant;


public interface Spread
{
    public Instant getTime();
    public Market getMarket();
    public Offer getBestBid();
    public Offer getBestAsk();
}
