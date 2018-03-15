package org.cryptocoinpartners.module.xchange;

import java.io.IOException;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.util.XchangeUtil;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.okcoin.FuturesContract;
import org.knowm.xchange.okcoin.dto.trade.OkCoinPriceLimit;
import org.knowm.xchange.okcoin.service.OkCoinFuturesTradeService;
import org.knowm.xchange.service.trade.TradeService;

@SuppressWarnings("UnusedDeclaration")
public class OKCoinHelper extends XchangeHelperBase {

	@Override
	public OrderType getOrderType(SpecificOrder specificOrder) {
		if (specificOrder.getPositionEffect() == null || specificOrder.getPositionEffect() == PositionEffect.OPEN)
			return specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
		else if (specificOrder.getPositionEffect() == PositionEffect.CLOSE)
			return specificOrder.isAsk() ? Order.OrderType.EXIT_BID : Order.OrderType.EXIT_ASK;
		return null;
	}

	@Override
	public FuturesContract getContractForListing(Listing listing) {

		return FuturesContract.valueOfIgnoreCase(FuturesContract.class, listing.getPrompt().getSymbol());
	}

	@Override
	public SpecificOrder adjustOrder(SpecificOrder specificOrder) {
		//Get trade services
		TradeService tradeService = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange()).getTradeService();
		OkCoinFuturesTradeService okCoinFuturesTradeService;
		if (tradeService instanceof OkCoinFuturesTradeService) {
			okCoinFuturesTradeService = (OkCoinFuturesTradeService) tradeService;
			try {
				OkCoinPriceLimit priceLimits = okCoinFuturesTradeService.getFuturesPriceLimits(
						XchangeUtil.getCurrencyPairForListing(specificOrder.getMarket().getListing()),
						getContractForListing(specificOrder.getMarket().getListing()));

				//buy order cannot be placed above the max price
				if (specificOrder.isBid() && (specificOrder.getLimitPrice() == null || (specificOrder.getLimitPrice() != null && priceLimits != null
						&& specificOrder.getLimitPrice().asBigDecimal().compareTo(priceLimits.getHigh()) > 0))) {
					DiscreteAmount maxPriceDiscete = new DiscreteAmount(
							DiscreteAmount.roundedCountForBasis(priceLimits.getHigh(), specificOrder.getMarket().getPriceBasis()),
							specificOrder.getMarket().getPriceBasis());
					//willing to buy at a price that is below the curent market price, so we will join the current bid.
					if (specificOrder.getExecutionInstruction() == null
							|| (specificOrder.getExecutionInstruction() != null && specificOrder.getExecutionInstruction().equals(ExecutionInstruction.TAKER)))
						specificOrder.setExecutionInstruction(ExecutionInstruction.MAKER);
					specificOrder.setLimitPriceCount(maxPriceDiscete.getCount());
				} else if (specificOrder.isAsk() && (specificOrder.getLimitPrice() == null
						|| (specificOrder.getLimitPrice() != null && specificOrder.getLimitPrice().asBigDecimal().compareTo(priceLimits.getLow()) < 0))) {
					//willing to sell at a price that is above the curent market price, so we will join the current ask.
					if (specificOrder.getExecutionInstruction() == null
							|| (specificOrder.getExecutionInstruction() != null && specificOrder.getExecutionInstruction().equals(ExecutionInstruction.TAKER)))
						specificOrder.setExecutionInstruction(ExecutionInstruction.MAKER);
					DiscreteAmount minPriceDiscete = new DiscreteAmount(
							DiscreteAmount.roundedCountForBasis(priceLimits.getLow(), specificOrder.getMarket().getPriceBasis()),
							specificOrder.getMarket().getPriceBasis());

					specificOrder.setLimitPriceCount(minPriceDiscete.getCount());
				}
				//sell order cannot be placed below the min price

			} catch (IOException e) {

				// TODO Auto-generated catch block

			}
		}

		return specificOrder;
	}

}
