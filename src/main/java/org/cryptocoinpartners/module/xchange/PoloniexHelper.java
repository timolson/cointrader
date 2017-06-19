package org.cryptocoinpartners.module.xchange;

import java.util.Date;

import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.IOrderFlags;
import org.knowm.xchange.poloniex.dto.trade.PoloniexOrderFlags;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;

@SuppressWarnings("UnusedDeclaration")
public class PoloniexHelper extends XchangeHelperBase {
    /** Send the lastTradeTime in millis as the first parameter to getTrades() 
     * @return */
    @Override
    public TradeHistoryParams getTradeHistoryParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId) {

        TradeHistoryParamsAll all = new TradeHistoryParamsAll();
        all.setCurrencyPair(pair);

        all.setStartTime(new Date(lastTradeTime));
        return all;

    }

    @Override
    public IOrderFlags[] getOrderFlags(SpecificOrder specificOrder) {
        if (specificOrder.getPositionEffect() == PositionEffect.OPEN || specificOrder.getPositionEffect() == PositionEffect.CLOSE) {

            IOrderFlags[] flags = new IOrderFlags[1];
            flags[0] = PoloniexOrderFlags.MARGIN;

            return flags;
        }
        return new IOrderFlags[0];

    }
}
