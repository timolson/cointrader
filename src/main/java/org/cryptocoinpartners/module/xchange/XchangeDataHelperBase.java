package org.cryptocoinpartners.module.xchange;

import java.util.ArrayList;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Trades;

public class XchangeDataHelperBase implements XchangeData.Helper {
    @Override
    public ArrayList<Object> getTradesParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId) {
        return new ArrayList<Object>();
    }

    @Override
    public void handleTrades(Trades xchangeTrades) {
    }

    @Override
    public ArrayList<Object> getOrderBookParameters(CurrencyPair pair) {
        return new ArrayList<Object>();
    }

    @Override
    public void handleOrderBook(OrderBook orderBook) {
    }
}
