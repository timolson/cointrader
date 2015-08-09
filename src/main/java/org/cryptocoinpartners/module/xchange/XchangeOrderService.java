package org.cryptocoinpartners.module.xchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Singleton;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.module.BaseOrderService;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.util.XchangeUtil;

import com.google.common.collect.HashBiMap;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.exceptions.ExchangeException;
import com.xeiam.xchange.exceptions.NotAvailableFromExchangeException;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;
import com.xeiam.xchange.service.polling.trade.PollingTradeService;

/**
 * This module routes SpecificOrders through Xchange
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
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
                specificOrder.persit();
                updateOrderState(specificOrder, OrderState.PLACED, false);
            } catch (IOException e) {
                log.error("Threw a Execption, full stack trace follows:", e);

                e.printStackTrace();
                // todo retry until expiration or reject as invalid
            }
        } else {
            MarketOrder marketOrder = new MarketOrder(orderType, tradeableVolume, currencyPair, id, timestamp);
            // todo put on a queue
            try {
                specificOrder.setRemoteKey(tradeService.placeMarketOrder(marketOrder));
                specificOrder.persit();
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

    @Override
    @Transient
    public Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio) {
        Collection<SpecificOrder> pendingOrders = new ConcurrentLinkedQueue<SpecificOrder>();
        com.xeiam.xchange.Exchange exchange;
        try {
            exchange = XchangeUtil.getExchangeForMarket(market.getExchange());
        } catch (Error err) {
            log.info("market:" + market + " not found");
            return pendingOrders;
        }
        PollingTradeService tradeService = exchange.getPollingTradeService();
        SpecificOrder specificOrder;
        boolean exists = false;
        //TODO: need to check prompts to ensure they have the full OKCOIN_THISWEEK:BTC.USD.THISWEEK not just OKCOIN_THISWEEK:BTC.USD
        try {
            OpenOrders openOrders = tradeService.getOpenOrders();
            for (LimitOrder xchangeOrder : openOrders.getOpenOrders()) {
                for (org.cryptocoinpartners.schema.Order cointraderOrder : orderStateMap.keySet()) {
                    if (cointraderOrder instanceof SpecificOrder) {
                        specificOrder = (SpecificOrder) cointraderOrder;
                        if (xchangeOrder.getId().equals(specificOrder.getRemoteKey()) && specificOrder.getMarket().equals(market)) {
                            specificOrder.update(xchangeOrder);
                            specificOrder.persit();
                            updateOrderState(specificOrder, OrderState.PLACED, false);
                            pendingOrders.add(specificOrder);
                            exists = true;
                            break;
                        }
                    }
                }

                if (!exists) {
                    Date time = (xchangeOrder.getTimestamp() != null) ? xchangeOrder.getTimestamp() : new Date();
                    specificOrder = new SpecificOrder(xchangeOrder, exchange, portfolio, time);
                    specificOrder.persit();
                    updateOrderState(specificOrder, OrderState.PLACED, false);
                    pendingOrders.add(specificOrder);
                }
            }

        } catch (IOException e) {
            log.error("Threw a Execption, full stack trace follows:", e);

            e.printStackTrace();

        }
        return pendingOrders;

    }

    @Override
    public Collection<SpecificOrder> getPendingOrders() {
        return new ConcurrentLinkedQueue<>();

    }

    @Override
    public void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleCancelSpecificOrder(SpecificOrder specificOrder) {
        com.xeiam.xchange.Exchange exchange;
        try {
            exchange = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange());
        } catch (Error err) {
            log.info("market:" + specificOrder.getMarket() + " not found");
            return;
        }
        PollingTradeService tradeService = exchange.getPollingTradeService();

        try {
            tradeService.cancelOrder(specificOrder.getRemoteKey());
        } catch (ExchangeException e) {
            log.error("Unable to cancel order :" + specificOrder);
        } catch (NotAvailableFromExchangeException e) {
            log.error("Unable to cancel order :" + specificOrder);
        } catch (NotYetImplementedForExchangeException e) {
            log.error("Unable to cancel order :" + specificOrder);
        } catch (IOException e) {
            log.error("failed to cancel order " + specificOrder + " with execption:" + e);
            e.printStackTrace();
        }

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
        Collection<SpecificOrder> pendingOrders = new ConcurrentLinkedQueue<SpecificOrder>();

        for (Market market : Market.findAll())
            pendingOrders.addAll(getPendingOrders(market, portfolio));

        return pendingOrders;

    }

    protected static final Collection<SpecificOrder> pendingOrders = new ConcurrentLinkedQueue<SpecificOrder>();
    protected static final HashBiMap<SpecificOrder, com.xeiam.xchange.dto.Order> externalOrderMap = HashBiMap.create();

    @Override
    public void init() {
        // TODO Auto-generated method stub

    }

    @Override
    public Collection<SpecificOrder> getPendingOpenOrders(Market market, Portfolio portfolio) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<SpecificOrder> getPendingOpenOrders(Portfolio portfolio) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<SpecificOrder> getPendingCloseOrders(Portfolio portfolio) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market) {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleCancelAllShortClosingSpecificOrders(Portfolio portfolio, Market market) {
        // TODO Auto-generated method stub

    }

    @Override
    public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio) {
        // TODO Auto-generated method stub
        return null;
    }

}
