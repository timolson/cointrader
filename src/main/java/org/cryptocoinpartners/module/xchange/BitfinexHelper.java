package org.cryptocoinpartners.module.xchange;

import java.util.ArrayList;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;


@SuppressWarnings("UnusedDeclaration")
public class BitfinexHelper extends XchangeDataHelperBase
{
    /** Send the lastTradeTime in millis as the first parameter to getTrades() */
    public ArrayList<Object> getTradesParameters( CurrencyPair pair, final long lastTradeTime, long lastTradeId )
    {
    	return new ArrayList<Object>() {{ add(lastTradeTime);  }};
    	
    	
        		
    }


    public void handleTrades( Trades xchangeTrades ) { }
}
