package org.cryptocoinpartners.module.xchangedata;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Trades;


public class XchangeDataHelperBase implements XchangeData.Helper
{
    public Object[] getTradesParameters( CurrencyPair pair, long lastTradeTime, long lastTradeId ) { return new Object[0]; }
    public void handleTrades( Trades tradeSpec ) { }
    public Object[] getOrderBookParameters( CurrencyPair pair ) { return new Object[0]; }
    public void handleOrderBook( OrderBook orderBook ) { }
}
