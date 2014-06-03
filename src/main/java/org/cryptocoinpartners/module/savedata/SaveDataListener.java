package org.cryptocoinpartners.module.savedata;

import org.cryptocoinpartners.module.ModuleListenerBase;
import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.MarketData;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.util.PersistUtil;


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
