package org.cryptocoinpartners.module.xchange;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Trades;

@SuppressWarnings("UnusedDeclaration")
public class BitfinexHelper extends XchangeHelperBase {
    /** Send the lastTradeTime in millis as the first parameter to getTrades() */
    @Override
    public Object[] getTradesParameters(CurrencyPair pair, final long lastTradeTime, long lastTradeId) {
        return new Object[] { Long.valueOf(lastTradeTime) };

    }

    @Override
    public void handleTrades(Trades xchangeTrades) {
    }
}
