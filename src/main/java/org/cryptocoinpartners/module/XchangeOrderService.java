package org.cryptocoinpartners.module;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.cryptocoinpartners.schema.OrderState;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.util.XchangeUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;


/**
 * This module routes SpecificOrders through Xchange
 *
 * @author Tim Olson
 */
public class XchangeOrderService extends BaseOrderService {


    @Inject
    public XchangeOrderService(Context context) {
        super(context);
    }


    protected void handleSpecificOrder(SpecificOrder specificOrder) {
        Exchange exchange = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange());
        PollingTradeService tradeService = exchange.getPollingTradeService();
        if( specificOrder.getLimitPriceCount() != 0 && specificOrder.getStopPriceCount() != 0 )
            reject(specificOrder,"Stop-limit orders are not supported");
        Order.OrderType orderType = specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
        BigDecimal tradeableVolume = specificOrder.getVolume().asBigDecimal();
        CurrencyPair currencyPair = XchangeUtil.getCurrencyPairForListing(specificOrder.getMarket().getListing());
        String id = specificOrder.getId().toString();
        Date timestamp = specificOrder.getTime().toDate();
        if( specificOrder.getLimitPriceCount() != 0 ) {
            LimitOrder limitOrder = new LimitOrder(orderType, tradeableVolume, currencyPair, id, timestamp,
                                                   specificOrder.getLimitPrice().asBigDecimal() );
            // todo put on a queue
            try {
                tradeService.placeLimitOrder(limitOrder);
                updateOrderState(specificOrder, OrderState.PLACED);
            }
            catch( IOException e ) {
                e.printStackTrace();
                // todo retry until expiration
            }
        }
        else {
            MarketOrder marketOrder = new MarketOrder(orderType,tradeableVolume,currencyPair,id,timestamp);
            // todo put on a queue
            try {
                tradeService.placeMarketOrder(marketOrder);
                updateOrderState(specificOrder, OrderState.PLACED);
            }
            catch( IOException e ) {
                e.printStackTrace();
            }
        }

    }


}
