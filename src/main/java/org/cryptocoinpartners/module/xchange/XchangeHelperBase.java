package org.cryptocoinpartners.module.xchange;

import org.cryptocoinpartners.schema.SpecificOrder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.IOrderFlags;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;

public class XchangeHelperBase implements XchangeData.Helper {
    @Override
    public Object[] getTradesParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId) {
        return new Object[0];
    }

    @Override
    public TradeHistoryParams getTradeHistoryParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId) {
        return new TradeHistoryParamsAll();
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

    @Override
    public IOrderFlags[] getOrderFlags(SpecificOrder specificOrder) {
        return new IOrderFlags[0];
    }
}
