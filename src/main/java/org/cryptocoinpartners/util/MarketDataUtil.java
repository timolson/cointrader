package org.cryptocoinpartners.util;

import org.cryptocoinpartners.schema.Market;

import java.util.Collection;


public class MarketDataUtil
{
    public Collection<Market> allMarkets() {
        return Market.findAll();
    }


}
