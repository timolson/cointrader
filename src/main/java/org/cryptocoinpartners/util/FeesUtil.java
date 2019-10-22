package org.cryptocoinpartners.util;

import javax.inject.Inject;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class FeesUtil {
	@Inject
	protected transient QuoteService quotes;

	public static Amount getCommission(Fill fill) {
		double rate = fill.getMarket().getFeeRate(fill.getOrder().getExecutionInstruction());
		//* fill.getMarket().getContractSize();
		//* fill.getMarket().getMargin();
		FeeMethod method = fill.getMarket().getFeeMethod();
		Amount price = fill.getPrice();
		Amount ammount;
		// if (fill.getMarket().getTradedCurrency(fill.getMarket()) != null)

		ammount = fill.getVolume();
		//   else
		//   ammount = fill.getVolume().times(price, Remainder.ROUND_CEILING);

		Amount commission;
		switch (method) {
			case PercentagePerUnit:
				return calculatePercentagePerUnit(price, ammount, rate, fill.getMarket());
			case PerUnit:
				return calculatePerUnit(ammount, rate, fill.getMarket());
			case PercentagePerUnitOpening:
				commission = (fill.getOrder().getPositionEffect() == (PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, fill.getMarket())
						: DecimalAmount.ZERO;
				return commission;
			case FlatRatePerUnitOpening:
				commission = (fill.getOrder().getPositionEffect() == (PositionEffect.OPEN)) ? calculateFlatRatePerUnit(price, ammount, rate, fill.getMarket())
						: DecimalAmount.ZERO;
				return commission;
			case PerUnitOpening:
				commission = (fill.getOrder().getPositionEffect() == (PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, fill.getMarket())
						: DecimalAmount.ZERO;
				return commission;
			default:
				log.error("No commission fee method calcation for : " + method);
				return DecimalAmount.ZERO;
		}

	}

	public static Amount getMargin(Amount price, Amount ammount, double rate, FeeMethod method, Market market, PositionEffect positionEffect) {
		Amount margin;
		rate = 1 / rate;
		switch (method) {
			case PercentagePerUnit:
				log.debug("FeeUtil:getMargin - Calcuating margin price=" + price + ",ammount=" + ammount + ",rate=" + rate + ",method=" + method
						+ ",positionEffect=" + positionEffect);
				return calculatePercentagePerUnit(price, ammount, rate, market);
			case PerUnit:
				log.debug("FeeUtil:getMargin - Calcuating margin price=" + price + ",ammount=" + ammount + ",rate=" + rate + ",method=" + method
						+ ",positionEffect=" + positionEffect);
				return calculatePerUnit(ammount, rate, market);
			case PercentagePerUnitOpening:
				log.debug("FeeUtil:getMargin - Calcuating margin price=" + price + ",ammount=" + ammount + ",rate=" + rate + ",method=" + method
						+ ",positionEffect=" + positionEffect);
				margin = (positionEffect == (PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, market) : DecimalAmount.ZERO;
				return margin;
			case PerUnitOpening:
				log.debug("FeeUtil:getMargin - Calcuating margin price=" + price + ",ammount=" + ammount + ",rate=" + rate + ",method=" + method
						+ ",positionEffect=" + positionEffect);
				margin = (positionEffect == (PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, market) : DecimalAmount.ZERO;
				return margin;
			default:
				log.debug("FeeUtil:getMargin - Calcuating margin price=" + price + ",ammount=" + ammount + ",rate=" + rate + ",method=" + method
						+ ",positionEffect=" + positionEffect);
				log.error("No margin fee method calcation for : " + method);
				return DecimalAmount.ZERO;
		}

	}

	public static Amount getLiquidationPrice(Position position, Amount balance, Amount currentPrice) {

		double rate = (position.getMarket().getLiquidation() == 0) ? 1 : position.getMarket().getLiquidation();
		Amount ammount = position.getOpenVolume();
		Amount liquidationPrice = DecimalAmount.ZERO;
		if (ammount.isZero())
			return liquidationPrice;
		Market market = position.getMarket();
		Amount price = ammount.isNegative() ? position.getShortAvgPrice() : position.getLongAvgPrice();
		double contractSize = market.getContractSize(market);
		if (contractSize != 1) {
			price = price.invert();

			liquidationPrice = price
					.plus((balance.times(rate, Remainder.ROUND_CEILING)).divide(ammount.times(contractSize, Remainder.ROUND_CEILING), Remainder.ROUND_FLOOR));

			liquidationPrice = liquidationPrice.invert();

		} else {
			liquidationPrice = price.minus((balance.times(rate, Remainder.ROUND_CEILING)).divide(ammount, Remainder.ROUND_FLOOR));

		}

		if (liquidationPrice.compareTo(DecimalAmount.ZERO) <= 0)
			liquidationPrice = DecimalAmount.ZERO;
		return liquidationPrice;

	}

	public static Amount getMargin(Fill fill) {

		double rate = (fill.getMarket().getMargin() == 0) ? 1 : fill.getMarket().getMargin();
		FeeMethod method = (fill.getMarket().getMarginFeeMethod() == null) ? FeeMethod.PercentagePerUnit : fill.getMarket().getMarginFeeMethod();

		Amount price = fill.getPrice();
		Amount ammount;
		// if (fill.getMarket().getTradedCurrency(fill.getMarket()) != null)

		ammount = fill.getVolume();
		// else
		// ammount = fill.getVolume().times(price, Remainder.ROUND_CEILING);

		Market market = fill.getMarket();
		PositionEffect positionEffect = fill.getOrder().getPositionEffect();

		return getMargin(price, ammount, rate, method, market, positionEffect);

	}

	public static Amount getCommission(Order order) {
		if (order.getMarket() != null) {

			double rate = order.getMarket().getFeeRate(order.getExecutionInstruction());
			FeeMethod method = order.getMarket().getFeeMethod();

			Amount price = (order.getLimitPrice() != null) ? order.getLimitPrice() : order.getMarketPrice();
			//Exchanges sometimes calcuated fees based on the price to nearst 1,2 or 3DP.
			double feeBasis = order.getMarket().getExchange().getFeeBasis(order.getMarket());
			price = price.toBasis(feeBasis, Remainder.ROUND_CEILING);
			// Amount ammount;
			//  if (order.getMarket().getTradedCurrency(order.getMarket()) != null)

			Amount ammount = order.getVolume().abs();
			//else
			//  ammount = order.getVolume().abs().times(price, Remainder.ROUND_CEILING);

			Amount commission;
			switch (method) {
				case PercentagePerUnit:
					return calculatePercentagePerUnit(price, ammount, rate, order.getMarket());
				case PerUnit:
					return calculatePerUnit(ammount, rate, order.getMarket());
				case FlatRatePerUnitOpening:
					commission = (order.getPositionEffect() == (PositionEffect.OPEN)) ? calculateFlatRatePerUnit(price, ammount, rate, order.getMarket())
							: DecimalAmount.ZERO;
				case PercentagePerUnitOpening:
					commission = (order.getPositionEffect() == (PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, order.getMarket())
							: DecimalAmount.ZERO;
					return commission;
				case PerUnitOpening:
					commission = (order.getPositionEffect().equals(PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, order.getMarket())
							: DecimalAmount.ZERO;
					return commission;
				default:
					log.error("No commision fee method calcation for : " + method);
					return DecimalAmount.ZERO;
			}
		}
		return DecimalAmount.ZERO;

	}

	public static Amount getMargin(Order order) {
		if (order.getMarket() != null) {

			double rate = (order.getMarket().getMargin() == 0) ? 1 : order.getMarket().getMargin();
			// need a check in here to see if margin fee moethos is null they assume full margin
			//quotes.

			FeeMethod method = (order.getMarket().getMarginFeeMethod() == null) ? FeeMethod.PercentagePerUnit : order.getMarket().getMarginFeeMethod();
			Amount price = (order.getLimitPrice() != null) ? order.getLimitPrice() : order.getMarketPrice();
			Amount ammount;
			//    if (order.getMarket().getTradedCurrency(order.getMarket()) != null)

			ammount = order.getVolume().abs();
			//  else
			//  ammount = order.getVolume().abs().times(price, Remainder.ROUND_CEILING);

			Market market = order.getMarket();
			PositionEffect positionEffect = order.getPositionEffect();
			return getMargin(price, ammount, rate, method, market, positionEffect);

		}
		return DecimalAmount.ZERO;

	}

	public static Amount getMargin(Amount position, Market market, Amount price) {

		double rate = (market.getMargin() == 0) ? 1 : market.getMargin();
		FeeMethod method = (market.getMarginFeeMethod() == null) ? FeeMethod.PercentagePerUnit : market.getMarginFeeMethod();

		PositionEffect positionEffect = PositionEffect.OPEN;
		return getMargin(price, position, rate, method, market, positionEffect);

	}

	public static Amount getMargin(Position position) {
		if (position.isOpen()) {

			double rate = (position.getMarket().getMargin() == 0) ? 1 : position.getMarket().getMargin();
			FeeMethod method = (position.getMarket().getMarginFeeMethod() == null) ? FeeMethod.PercentagePerUnit : position.getMarket().getMarginFeeMethod();

			Amount price = (position.getOpenVolume().isPositive()) ? position.getLongAvgPrice() : position.getShortAvgPrice();
			//    if (position.getMarket().getTradedCurrency(position.getMarket()) != null)

			Amount ammount = position.getOpenVolume();
			//  else
			log.debug("FeeUtil:getMargin - Calcuating margin position=" + position + ",ammount=" + ammount + ",price=" + price);

			//  ammount = (position.isLong()) ? position.getLongVolume().times(position.getLongAvgPrice(), Remainder.ROUND_CEILING) : position
			//    .getShortVolume().times(position.getShortAvgPrice(), Remainder.ROUND_CEILING);
			Market market = position.getMarket();
			PositionEffect positionEffect = PositionEffect.OPEN;
			return getMargin(price, ammount, rate, method, market, positionEffect);

		}
		return DecimalAmount.ZERO;

	}

	public static Amount getCommission(Amount price, Amount ammount, Market market, PositionEffect postionEffect, ExecutionInstruction executionInstruction) {
		double rate = market.getFeeRate(executionInstruction);
		FeeMethod method = market.getFeeMethod();
		Amount commission;
		switch (method) {
			case PercentagePerUnit:
				return calculatePercentagePerUnit(price, ammount, rate, market);
			case PerUnit:
				return calculatePerUnit(ammount, rate, market);
			case PercentagePerUnitOpening:
				commission = (postionEffect == (PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, market) : DecimalAmount.ZERO;
				return commission;
			case PerUnitOpening:
				commission = (postionEffect == (PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, market) : DecimalAmount.ZERO;
				return commission;

			default:
				log.error("No commision fee method calcation for : " + method);
				return DecimalAmount.ZERO;
		}

	}

	protected static Amount getMargin(Amount price, Amount amount, Market market, PositionEffect postionEffect) {
		double rate = market.getMargin();
		FeeMethod method = market.getMarginFeeMethod();

		Amount margin;
		switch (method) {
			case PercentagePerUnit:
				return calculatePercentagePerUnit(price, amount, rate, market);
			case PerUnit:
				return calculatePerUnit(amount, rate, market);
			case PercentagePerUnitOpening:
				margin = (postionEffect == (PositionEffect.OPEN)) ? calculatePercentagePerUnitOpening(price, amount, rate, market) : DecimalAmount.ZERO;
				return margin;
			case PerUnitOpening:
				margin = (postionEffect == (PositionEffect.OPEN)) ? calculatePerUnitOpening(amount, rate, market) : DecimalAmount.ZERO;
				return margin;
			default:
				log.error("No margin fee method calcation for : " + method);
				return DecimalAmount.ZERO;
		}

	}

	private static Amount calculatePercentagePerUnit(Amount price, Amount amount, double rate, Market market) {
		//BigDecimal precision = BigDecimal.valueOf(market.getPriceBasis());
		Amount notional = DecimalAmount.ZERO;
		if (price == null)
			return notional;
		//BTC(base)/USD traded = BTC, ETH(base)/BTC  traded = ETH

		Amount scaledPrice = (market.getContractSize(market) != 1) ? market.getMultiplier(market, price, DecimalAmount.ONE) : price;
		notional = ((scaledPrice.times(amount, Remainder.ROUND_EVEN)).times(rate, Remainder.ROUND_EVEN).abs()).times(market.getContractSize(market),
				Remainder.ROUND_EVEN);
		log.debug("FeesUtil:calculatePercentagePerUnit - Calculated notional=" + notional + ", price=" + price + ", amount=" + amount + ", rate=" + rate
				+ ", market=" + market + ", scaledPrice=" + scaledPrice);

		//     BTC/USD, so seelling BTC and buing $ 450.76 (BTC/USD
		//             buying 1 ETH at 0.02 BTC)
		//     BTC/USD so buying BTC selling $

		Asset tradedCCY = (market.getTradedCurrency(market) == null) ? market.getQuote() : market.getTradedCurrency(market);
		Amount margin = notional.toBasis(tradedCCY.getBasis(), Remainder.ROUND_CEILING).negate();
		log.debug("FeesUtil:calculatePercentagePerUnit - Calculated notional=" + notional + ", price=" + price + ", amount=" + amount + ", rate=" + rate
				+ ", market=" + market + ", scaledPrice=" + scaledPrice + ", tradedCCY=" + tradedCCY + ", margin=" + margin);
		return margin;

		//      
		//      BigDecimal fees = notional.asBigDecimal().setScale(price.getScale(), BigDecimal.ROUND_UP);
		//      fees = fees.divide(precision, BigDecimal.ROUND_UP);
		//      long feeCount = fees.longValue();
		//      DiscreteAmount newAmount = new DiscreteAmount(feeCount, precision.doubleValue());
		//      return newAmount.negate();

	}

	private static Amount calculateFlatRatePerUnit(Amount price, Amount amount, double rate, Market market) {
		//BigDecimal precision = BigDecimal.valueOf(market.getPriceBasis());

		price = market.getMultiplier(market, price, DecimalAmount.ONE);

		Amount notional = (amount.times(rate, Remainder.ROUND_EVEN).abs());
		Asset tradedCCY = (market.getTradedCurrency(market) == null) ? market.getQuote() : market.getTradedCurrency(market);

		return notional.toBasis(tradedCCY.getBasis(), Remainder.ROUND_CEILING).negate();

		//      
		//      BigDecimal fees = notional.asBigDecimal().setScale(price.getScale(), BigDecimal.ROUND_UP);
		//      fees = fees.divide(precision, BigDecimal.ROUND_UP);
		//      long feeCount = fees.longValue();
		//      DiscreteAmount newAmount = new DiscreteAmount(feeCount, precision.doubleValue());
		//      return newAmount.negate();

	}

	private static Amount calculatePerUnit(Amount amount, double rate, Market market) {

		Amount notional = ((amount.times(rate, Remainder.ROUND_EVEN)).abs());
		Asset tradedCCY = (market.getTradedCurrency(market) == null) ? market.getQuote() : market.getTradedCurrency(market);

		return notional.toBasis(tradedCCY.getBasis(), Remainder.ROUND_CEILING).negate();

		//      
		//      BigDecimal fees = notional.asBigDecimal().setScale(amount.getScale(), BigDecimal.ROUND_UP);
		//      BigDecimal precision = BigDecimal.valueOf(market.getPriceBasis());
		//      fees = fees.divide(precision, BigDecimal.ROUND_UP);
		//      long feeCount = fees.longValue();
		//      DiscreteAmount newAmount = new DiscreteAmount(feeCount, market.getPriceBasis());
		//      return newAmount.negate();

	}

	private static Amount calculatePerUnitOpening(Amount amount, double rate, Market market) {
		return calculatePerUnit(amount, rate, market);
	}

	private static Amount calculatePercentagePerUnitOpening(Amount price, Amount amount, double rate, Market market) {
		return calculatePercentagePerUnit(price, amount, rate, market);
	}

	public static Logger log = LoggerFactory.getLogger(FeesUtil.class);

}
