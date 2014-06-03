package org.cryptocoinpartners.util;

import org.cryptocoinpartners.schema.MarketListing;

import java.util.Collection;


public class MarketDataUtil
{
    public Collection<MarketListing> allMarketListings() {
        return MarketListing.findAll();
    }


}
