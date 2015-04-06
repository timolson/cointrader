package org.cryptocoinpartners.module.xchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.module.BaseOrderService;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.util.XchangeUtil;
import org.slf4j.Logger;

import com.google.common.collect.HashBiMap;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;
import com.xeiam.xchange.service.polling.trade.PollingTradeService;

/**
 * This module routes SpecificOrders through Xchange
 *
 * @author Tim Olson
 */
@Singleton
public class XchangeOrderService extends BaseOrderService {

    @Override
    protected void handleSpecificOrder(SpecificOrder specificOrder) {
        Exchange exchange = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange());
        PollingTradeService tradeService = exchange.getPollingTradeService();
        if (specificOrder.getLimitPrice() != null && specificOrder.getStopPrice() != null)
            reject(specificOrder, "Stop-limit orders are not supported");
        Order.OrderType orderType = specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
        BigDecimal tradeableVolume = specificOrder.getVolume().abs().asBigDecimal();
        CurrencyPair currencyPair = XchangeUtil.getCurrencyPairForListing(specificOrder.getMarket().getListing());
        String id = specificOrder.getId().toString();
        Date timestamp = specificOrder.getTime().toDate();
        if (specificOrder.getLimitPrice() != null) {
            LimitOrder limitOrder = new LimitOrder(orderType, tradeableVolume, currencyPair, "", null, specificOrder.getLimitPrice().asBigDecimal());
            // todo put on a queue
            try {
                specificOrder.setRemoteKey(tradeService.placeLimitOrder(limitOrder));
                updateOrderState(specificOrder, OrderState.PLACED, false);
            } catch (IOException e) {
                e.printStackTrace();
                // todo retry until expiration or reject as invalid
            }
        } else {
            MarketOrder marketOrder = new MarketOrder(orderType, tradeableVolume, currencyPair, id, timestamp);
            // todo put on a queue
            try {
                specificOrder.setRemoteKey(tradeService.placeMarketOrder(marketOrder));
                updateOrderState(specificOrder, OrderState.PLACED, false);
            } catch (IOException e) {
                // todo retry until expiration or reject as invalid
                log.warn("Could not place this order: " + specificOrder, e);
            } catch (NotYetImplementedForExchangeException e) {
                log.warn("XChange adapter " + exchange + " does not support this order: " + specificOrder, e);
                reject(specificOrder, "XChange adapter " + exchange + " does not support this order");
            }
        }

    }

    @Inject
    Logger log;
    @Inject
    private Portfolio portfolio;

    @Override
    public Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio) {
        com.xeiam.xchange.Exchange exchange = XchangeUtil.getExchangeForMarket(market.getExchange());
        PollingTradeService tradeService = exchange.getPollingTradeService();
        Collection<SpecificOrder> pendingOrders = new ArrayList<>();
        SpecificOrder specificOrder;
        try {
            OpenOrders openOrders = tradeService.getOpenOrders();
            for (LimitOrder xchangeOrder : openOrders.getOpenOrders()) {
                for (org.cryptocoinpartners.schema.Order cointraderOrder : orderStateMap.keySet()) {
                    if (cointraderOrder instanceof SpecificOrder) {
                        specificOrder = (SpecificOrder) cointraderOrder;
                        if (xchangeOrder.getId().equals(specificOrder.getRemoteKey()) && specificOrder.getMarket().equals(market)) {
                            specificOrder.update(xchangeOrder);
                            updateOrderState(specificOrder, OrderState.PLACED, false);
                            pendingOrders.add(specificOrder);
                            break;
                        } else {
                            specificOrder = new SpecificOrder(xchangeOrder, exchange, portfolio);
                            updateOrderState(specificOrder, OrderState.PLACED, false);
                            pendingOrders.add(specificOrder);
                            break;

                        }
                    }
                }
                specificOrder = new SpecificOrder(xchangeOrder, exchange, portfolio);
                updateOrderState(specificOrder, OrderState.PLACED, false);
                pendingOrders.add(specificOrder);

                log.debug("completed itteration of orders");
            }

        } catch (IOException e) {
            e.printStackTrace();

        }
        return pendingOrders;

    }

    @Override
    public List<SpecificOrder> getPendingOrders() {
        return new ArrayList<>();

    }

    @Override
    public void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleCancelSpecificOrder(SpecificOrder specificOrder) {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market) {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market) {
        // TODO Auto-generated method stub

    }

    @Override
    public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio) {
        return new ArrayList<>();

    }

    protected static final Collection<SpecificOrder> pendingOrders = new ConcurrentLinkedQueue<SpecificOrder>();
    protected static final HashBiMap<SpecificOrder, com.xeiam.xchange.dto.Order> externalOrderMap = HashBiMap.create();

    @Override
    public void init() {
        // TODO Auto-generated method stub

    }

}
