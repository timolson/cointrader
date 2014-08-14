package org.cryptocoinpartners.util;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Fill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class FeesUtil {

	public static Amount getExchangeFees(Fill fill) {
		double rate = fill.getMarket().getExchange().getFeeRate();
		FeeMethod method = fill.getMarket().getExchange().getFeeMethod();
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

	private static Amount calculatePercentagePerUnit(Amount price, Amount ammount, double rate) {
		Amount notional = (price.times(ammount, Remainder.ROUND_EVEN)).times(rate, Remainder.ROUND_EVEN);
		return notional.toBasis(Double.valueOf("0.01"), Remainder.ROUND_EVEN);
	}

	private static Amount calculatePerUnit(Amount ammount, double rate) {

		Amount notional = ammount.times(rate, Remainder.ROUND_EVEN);
		return notional.toBasis(Double.valueOf("0.01"), Remainder.ROUND_EVEN);
	}

	public static Logger log = LoggerFactory.getLogger(FeesUtil.class);

}
