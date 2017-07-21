package org.cryptocoinpartners.module.xchange;

import java.util.Date;

import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.poloniex.dto.trade.PoloniexOrderFlags;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;

@SuppressWarnings("UnusedDeclaration")
public class PoloniexHelper extends XchangeHelperBase {
  /**
   * Send the lastTradeTime in millis as the first parameter to getTrades()
   * 
   * @return
   */
  @Override
  public Object[] getOrderBookParameters(CurrencyPair pair) {
    return new Object[]{Integer.valueOf(50)};

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
      xchangeOrder.addOrderFlag(PoloniexOrderFlags.MARGIN);
    }
    //we need to adjust any exit's shorts to be inculsive of fees

    //we need to set the order as a market closing order if there is no quanity on the exchange this is to auto settle the unknown loans.

    return xchangeOrder;

  }

  /*
   * @Override public Object[] getTradesParameters(CurrencyPair pair, final long lastTradeTime, long lastTradeId) { return new
   * Object[]{Long.valueOf(lastTradeTime / 1000), new DateTime().getMillis() / 1000}; }
   */
  //    @Override
  //    public IOrderFlags[] getOrderFlags(SpecificOrder specificOrder) {
  //        if (specificOrder.getPositionEffect() == PositionEffect.OPEN || specificOrder.getPositionEffect() == PositionEffect.CLOSE) {
  //
  //            IOrderFlags[] flags = new IOrderFlags[1];
  //            flags[0] = PoloniexOrderFlags.MARGIN;
  //
  //            return flags;
  //        }
  //        return new IOrderFlags[0];
  //
  //    }
}
