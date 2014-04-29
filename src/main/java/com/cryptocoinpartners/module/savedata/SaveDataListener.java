package com.cryptocoinpartners.module.savedata;

import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.module.When;
import com.cryptocoinpartners.schema.MarketData;
import com.cryptocoinpartners.schema.Trade;
import com.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
public class SaveDataListener extends ModuleListenerBase {

    @When("select * from MarketData")
    public void handleMarketData( MarketData m ) {
        if( m instanceof Trade ) {
            Trade trade = (Trade) m;
            final Trade duplicate = PersistUtil.queryZeroOne(Trade.class,
                                                      "select t from Trade t where marketListing=?1 and remoteKey=?2",
                                                      trade.getMarketListing(), trade.getRemoteKey());
            if( duplicate == null )
                PersistUtil.insert(m);
            else
                log.warn("dropped duplicate Trade "+trade);
        }
        else
            // if not a Trade, persist unconditionally
            PersistUtil.insert(m);
    }

}
