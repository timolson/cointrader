package org.cryptocoinpartners.module.xchange;

import java.util.Date;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;

@SuppressWarnings("UnusedDeclaration")
public class BinanceHelper extends XchangeHelperBase {
	/** Send the lastTradeTime in millis as the first parameter to getTrades() */
	@Override
	public Object[] getTradesParameters(CurrencyPair pair, final long lastTradeTime, long lastTradeId) {
		return new Object[] { Long.valueOf(lastTradeId) };

	}

	@Override
	public Object[] getOrderBookParameters(CurrencyPair pair) {
		return new Object[] { Integer.valueOf(20) };

	}

	@Override
	public TradeHistoryParams getTradeHistoryParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId) {

		TradeHistoryParamsAll all = new TradeHistoryParamsAll();
		all.setCurrencyPair(pair);

		all.setStartTime(new Date(lastTradeTime));
		return all;

	}

}
