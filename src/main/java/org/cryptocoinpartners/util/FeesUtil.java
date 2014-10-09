package org.cryptocoinpartners.util;

import java.math.BigDecimal;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class FeesUtil {

	public static Amount getExchangeFees(Fill fill) {
		double rate = fill.getMarket().getExchange().getFeeRate();
		FeeMethod method = fill.getMarket().getExchange().getFeeMethod();
		double basis = fill.getMarket().getPriceBasis();
		Amount price = fill.getPrice();
		Amount ammount = fill.getVolume();
		switch (method) {
			case PercentagePerUnit:
				return calculatePercentagePerUnit(price, ammount, rate, fill.getMarket());
			case PerUnit:
				return calculatePerUnit(ammount, rate, fill.getMarket());
			default:
				log.error("No exchange fee method calcation for : " + method);
				return null;
		}

	}

	public static Amount getExchangeFees(Order order) {
		if (order.getMarket() != null) {

			double rate = order.getMarket().getExchange().getFeeRate();
			FeeMethod method = order.getMarket().getExchange().getFeeMethod();
			double basis = order.getMarket().getPriceBasis();
			Amount price = order.getLimitPrice();
			Amount ammount = order.getVolume().abs();
			switch (method) {
				case PercentagePerUnit:
					return calculatePercentagePerUnit(price, ammount, rate, order.getMarket());
				case PerUnit:
					return calculatePerUnit(ammount, rate, order.getMarket());
				default:
					log.error("No exchange fee method calcation for : " + method);
					return null;
			}
		}
		return null;

	}

	public static Amount getExchangeFees(Amount price, Amount ammount, Market market) {
		double rate = market.getExchange().getFeeRate();
		FeeMethod method = market.getExchange().getFeeMethod();
		switch (method) {
			case PercentagePerUnit:
				return calculatePercentagePerUnit(price, ammount, rate, market);
			case PerUnit:
				return calculatePerUnit(ammount, rate, market);
			default:
				log.error("No exchange fee method calcation for : " + method);
				return null;
		}

	}

	private static Amount calculatePercentagePerUnit(Amount price, Amount amount, double rate, Market market) {
		Amount notional = (price.times(amount, Remainder.ROUND_EVEN)).times(rate, Remainder.ROUND_EVEN).abs();
		BigDecimal fees = notional.asBigDecimal().setScale(price.getScale(), BigDecimal.ROUND_UP);
		BigDecimal precision = BigDecimal.valueOf(market.getPriceBasis());
		fees = fees.divide(precision, BigDecimal.ROUND_UP);
		long feeCount = fees.longValue();
		DiscreteAmount newAmount = new DiscreteAmount(feeCount, market.getPriceBasis());
		return newAmount.negate();

	}

	private static Amount calculatePerUnit(Amount amount, double rate, Market market) {

		Amount notional = (amount.times(rate, Remainder.ROUND_EVEN)).abs();
		BigDecimal fees = notional.asBigDecimal().setScale(amount.getScale(), BigDecimal.ROUND_UP);
		BigDecimal precision = BigDecimal.valueOf(market.getPriceBasis());
		fees = fees.divide(precision, BigDecimal.ROUND_UP);
		long feeCount = fees.longValue();
		DiscreteAmount newAmount = new DiscreteAmount(feeCount, market.getPriceBasis());
		return newAmount.negate();

	}

	public static Logger log = LoggerFactory.getLogger(FeesUtil.class);

}
