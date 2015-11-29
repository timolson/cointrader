package org.cryptocoinpartners.util;

import java.util.Collection;

import org.cryptocoinpartners.schema.Market;

import com.google.inject.Inject;

public class MarketDataUtil

{
    @Inject
    Market market;

    public Collection<Market> allMarkets() {
        return market.findAll();
    }

}
