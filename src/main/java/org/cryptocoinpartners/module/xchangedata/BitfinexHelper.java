package org.cryptocoinpartners.module.xchangedata;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;


public class BitfinexHelper extends XchangeDataHelperBase
{
    /** Send the lastTradeTime in millis as the first parameter to getTrades() */
    public Object[] getTradesParameters( CurrencyPair pair, long lastTradeTime, long lastTradeId )
    {
        return new Object[] {lastTradeTime};
    }


    public void handleTrades( Trades tradeSpec ) { }
}
