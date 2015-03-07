package org.cryptocoinpartners.module.xchange;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Trades;

public class XchangeDataHelperBase implements XchangeData.Helper {
    @Override
    public Object[] getTradesParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId) {
        return new Object[0];
    }

    @Override
    public void handleTrades(Trades xchangeTrades) {
    }

    @Override
    public Object[] getOrderBookParameters(CurrencyPair pair) {
        return new Object[0];
    }

    @Override
    public void handleOrderBook(OrderBook orderBook) {
    }
}
