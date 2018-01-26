package org.cryptocoinpartners.module.xchange;

import java.util.Date;

import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.util.XchangeUtil;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;

@SuppressWarnings("UnusedDeclaration")
public class BinanceHelper extends XchangeHelperBase {
	/** Send the lastTradeTime in millis as the first parameter to getTrades() */
	@Override
	public Object[] getTradesParameters(Listing listing, final long lastTradeTime, long lastTradeId) {
		return new Object[] { Long.valueOf(lastTradeId) };

	}

	@Override
	public Object[] getOrderBookParameters(Listing listing) {
		return new Object[] { Integer.valueOf(20) };

	}

	@Override
	public TradeHistoryParams getTradeHistoryParameters(Listing listing, long lastTradeTime, long lastTradeId) {

		TradeHistoryParamsAll all = new TradeHistoryParamsAll();
		all.setCurrencyPair(XchangeUtil.getCurrencyPairForListing(listing));

		all.setStartTime(new Date(lastTradeTime));
		return all;

	}

}
