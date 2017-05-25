package org.cryptocoinpartners.util;

import java.util.List;

import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Tradeable;

import com.google.inject.Inject;

public class MarketDataUtil

{
    @Inject
    Market market;

    public List<Tradeable> allMarkets() {
        return market.findAll();
    }

}
