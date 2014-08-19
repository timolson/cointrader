package org.cryptocoinpartners.util;

import java.math.BigDecimal;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.SpecificOrder;
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
				return calculatePercentagePerUnit(price, ammount, rate);
			case PerUnit:
				return calculatePerUnit(ammount, rate);
			default:
				log.error("No exchange fee method calcation for : " + method);
				return null;
		}

	}

	public static Amount getExchangeFees(SpecificOrder order) {
		double rate = order.getMarket().getExchange().getFeeRate();
		FeeMethod method = order.getMarket().getExchange().getFeeMethod();
		double basis = order.getMarket().getPriceBasis();
		Amount price = order.getLimitPrice();
		Amount ammount = order.getVolume().abs();
		switch (method) {
			case PercentagePerUnit:
				return calculatePercentagePerUnit(price, ammount, rate);
			case PerUnit:
				return calculatePerUnit(ammount, rate);
			default:
				log.error("No exchange fee method calcation for : " + method);
				return null;
		}

	}

	public static Amount getExchangeFees(Amount price, Amount ammount, Market market) {
		double rate = market.getExchange().getFeeRate();
		FeeMethod method = market.getExchange().getFeeMethod();
		switch (method) {
			case PercentagePerUnit:
				return calculatePercentagePerUnit(price, ammount, rate);
			case PerUnit:
				return calculatePerUnit(ammount, rate);
			default:
				log.error("No exchange fee method calcation for : " + method);
				return null;
		}

	}

	private static Amount calculatePercentagePerUnit(Amount price, Amount amount, double rate) {
		Amount notional = (price.times(amount, Remainder.ROUND_EVEN)).times(rate, Remainder.ROUND_EVEN).abs();
		BigDecimal fees = notional.asBigDecimal().setScale(price.getPrecision(), BigDecimal.ROUND_UP);
		BigDecimal precision = BigDecimal.valueOf(price.getBasis());
		fees = fees.divide(precision, BigDecimal.ROUND_UP);
		long feeCount = fees.longValue();
		DiscreteAmount newAmount = new DiscreteAmount(feeCount, price.getBasis());
		return newAmount.negate();

	}

	private static Amount calculatePerUnit(Amount amount, double rate) {

		Amount notional = (amount.times(rate, Remainder.ROUND_EVEN)).abs();
		BigDecimal fees = notional.asBigDecimal().setScale(amount.getPrecision(), BigDecimal.ROUND_UP);
		BigDecimal precision = BigDecimal.valueOf(amount.getBasis());
		fees = fees.divide(precision, BigDecimal.ROUND_UP);
		long feeCount = fees.longValue();
		DiscreteAmount newAmount = new DiscreteAmount(feeCount, amount.getBasis());
		return newAmount.negate();

	}

	public static Logger log = LoggerFactory.getLogger(FeesUtil.class);

}
