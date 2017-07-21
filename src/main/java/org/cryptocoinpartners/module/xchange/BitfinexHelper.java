package org.cryptocoinpartners.module.xchange;

import java.util.Date;

import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.knowm.xchange.bitfinex.v1.dto.trade.BitfinexOrderFlags;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;

@SuppressWarnings("UnusedDeclaration")
public class BitfinexHelper extends XchangeHelperBase {
  /** Send the lastTradeTime in millis as the first parameter to getTrades() */
  @Override
  public Object[] getTradesParameters(CurrencyPair pair, final long lastTradeTime, long lastTradeId) {
    return new Object[]{Long.valueOf(lastTradeTime)};

  }

  @Override
  public Object[] getOrderBookParameters(CurrencyPair pair) {
    return new Object[]{Integer.valueOf(50), Integer.valueOf(50)};

  }

  @Override
  public TradeHistoryParams getTradeHistoryParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId) {

    TradeHistoryParamsAll all = new TradeHistoryParamsAll();
    all.setCurrencyPair(pair);

    all.setStartTime(new Date(lastTradeTime));
    return all;

  }

  @Override
  public org.knowm.xchange.dto.Order adjustOrder(SpecificOrder specificOrder, org.knowm.xchange.dto.Order xchangeOrder) {
    //Set Margin Flags
    if (specificOrder.getPositionEffect() == PositionEffect.OPEN || specificOrder.getPositionEffect() == PositionEffect.CLOSE) {
      xchangeOrder.addOrderFlag(BitfinexOrderFlags.MARGIN);
    }

    //we need to adjust any exit's shorts to be inculsive of fees

    //we need to set the order as a market closing order if there is no quanity on the exchange this is to auto settle the unknown loans.

    return xchangeOrder;

  }

  @Override
  public void handleTrades(Trades xchangeTrades) {
  }
}
