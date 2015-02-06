package org.cryptocoinpartners.module.xchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.module.BaseOrderService;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.util.XchangeUtil;
import org.slf4j.Logger;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;

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
			LimitOrder limitOrder = new LimitOrder(orderType, tradeableVolume, currencyPair, id, timestamp, specificOrder.getLimitPrice().asBigDecimal());
			// todo put on a queue
			try {
				tradeService.placeLimitOrder(limitOrder);
				updateOrderState(specificOrder, OrderState.PLACED);
			} catch (IOException e) {
				e.printStackTrace();
				// todo retry until expiration or reject as invalid
			}
		} else {
			MarketOrder marketOrder = new MarketOrder(orderType, tradeableVolume, currencyPair, id, timestamp);
			// todo put on a queue
			try {
				tradeService.placeMarketOrder(marketOrder);
				updateOrderState(specificOrder, OrderState.PLACED);
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

	@Override
	public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio) {
		return null;
		// TODO Auto-generated method stub

	}

	@Override
	public void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleCancelSpecificOrder(SpecificOrder specificOrder) {
		// TODO Auto-generated method stub

	}

}
