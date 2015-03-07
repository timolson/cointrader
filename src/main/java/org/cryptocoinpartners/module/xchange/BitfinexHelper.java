package org.cryptocoinpartners.module.xchange;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;

@SuppressWarnings("UnusedDeclaration")
public class BitfinexHelper extends XchangeDataHelperBase {
    /** Send the lastTradeTime in millis as the first parameter to getTrades() */
    @Override
    public Object[] getTradesParameters(CurrencyPair pair, final long lastTradeTime, long lastTradeId) {
        return new Object[] { Long.valueOf(lastTradeTime) };

    }

    @Override
    public void handleTrades(Trades xchangeTrades) {
    }
}
