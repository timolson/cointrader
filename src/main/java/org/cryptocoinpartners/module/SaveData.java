package org.cryptocoinpartners.module;

import org.cryptocoinpartners.schema.MarketData;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.util.PersistUtil;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * @author Tim Olson
 */
@Singleton
public class SaveData {

    @When("select * from MarketData")
    public void handleMarketData( MarketData m ) {
        if( m instanceof Trade ) {
            Trade trade = (Trade) m;
            final Trade duplicate = PersistUtil.queryZeroOne(Trade.class,
                                                      "select t from Trade t where market=?1 and remoteKey=?2",
                                                      trade.getMarket(), trade.getRemoteKey());
            if( duplicate == null )
                PersistUtil.insert(trade);
            else
                log.warn("dropped duplicate Trade "+trade);
        }
        else {
            // if not a Trade, persist unconditionally
            try {
                PersistUtil.insert(m);
            }
            catch( Throwable e ) {
                throw new Error("Could not insert "+m,e);
            }
        }
    }


    @Inject
    private Logger log;
}
