package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

public interface Spread {
    public Instant getTime();

    public Tradeable getMarket();

    public Offer getBestBid();

    public Offer getBestAsk();

    public Offer getBestBidByVolume(DiscreteAmount volume);

    public Offer getBestAskByVolume(DiscreteAmount volume);
}
