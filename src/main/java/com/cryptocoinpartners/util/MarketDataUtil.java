package com.cryptocoinpartners.util;

import com.cryptocoinpartners.schema.MarketListing;
import com.cryptocoinpartners.schema.Trade;

import java.util.Collection;
import java.util.List;


public class MarketDataUtil
{
    public Collection<MarketListing> allMarketListings() {
        return MarketListing.findAll();
    }


}
