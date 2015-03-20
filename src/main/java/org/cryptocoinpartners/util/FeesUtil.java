package org.cryptocoinpartners.util;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class FeesUtil {

    public static Amount getCommission(Fill fill) {
        double rate = fill.getMarket().getFeeRate() * fill.getMarket().getMargin();
        FeeMethod method = fill.getMarket().getFeeMethod();
        Amount price = fill.getPrice();
        Amount ammount = fill.getVolume();
        Amount commission;
        switch (method) {
            case PercentagePerUnit:
                return calculatePercentagePerUnit(price, ammount, rate, fill.getMarket());
            case PerUnit:
                return calculatePerUnit(ammount, rate, fill.getMarket());
            case PercentagePerUnitOpening:
                commission = (fill.getOrder().getPositionEffect().equals(PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate,
                        fill.getMarket()) : DecimalAmount.ZERO;
                return commission;
            case PerUnitOpening:
                commission = (fill.getOrder().equals(PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, fill.getMarket()) : DecimalAmount.ZERO;
                return commission;
            default:
                log.error("No commission fee method calcation for : " + method);
                return DecimalAmount.ZERO;
        }

    }

    public static Amount getMargin(Amount price, Amount ammount, double rate, FeeMethod method, Market market, PositionEffect positionEffect) {
        Amount margin;
        switch (method) {
            case PercentagePerUnit:
                return calculatePercentagePerUnit(price, ammount, rate, market);
            case PerUnit:
                return calculatePerUnit(ammount, rate, market);
            case PercentagePerUnitOpening:
                margin = (positionEffect.equals(PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, market) : DecimalAmount.ZERO;
                return margin;
            case PerUnitOpening:
                margin = (positionEffect.equals(PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, market) : DecimalAmount.ZERO;
                return margin;
            default:
                log.error("No margin fee method calcation for : " + method);
                return DecimalAmount.ZERO;
        }

    }

    public static Amount getMargin(Fill fill) {
        double rate = fill.getMarket().getMargin();
        FeeMethod method = fill.getMarket().getMarginFeeMethod();
        Amount price = fill.getPrice();
        Amount ammount = fill.getVolume();
        Market market = fill.getMarket();
        PositionEffect positionEffect = fill.getOrder().getPositionEffect();

        return getMargin(price, ammount, rate, method, market, positionEffect);

    }

    public static Amount getCommission(Order order) {
        if (order.getMarket() != null) {

            double rate = order.getMarket().getFeeRate();
            FeeMethod method = order.getMarket().getFeeMethod();

            Amount price = order.getLimitPrice();
            Amount ammount = order.getVolume().abs();
            Amount commission;
            switch (method) {
                case PercentagePerUnit:
                    return calculatePercentagePerUnit(price, ammount, rate, order.getMarket());
                case PerUnit:
                    return calculatePerUnit(ammount, rate, order.getMarket());
                case PercentagePerUnitOpening:
                    commission = (order.getPositionEffect().equals(PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, order.getMarket())
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

            double rate = order.getMarket().getMargin();
            FeeMethod method = order.getMarket().getMarginFeeMethod();
            Amount price = order.getLimitPrice();
            Amount ammount = order.getVolume().abs();
            Market market = order.getMarket();
            PositionEffect positionEffect = order.getPositionEffect();
            return getMargin(price, ammount, rate, method, market, positionEffect);

        }
        return DecimalAmount.ZERO;

    }

    public static Amount getMargin(Position position) {
        if (position.isOpen()) {

            double rate = position.getMarket().getMargin();
            FeeMethod method = position.getMarket().getMarginFeeMethod();
            Amount price = (position.isLong()) ? position.getLongAvgPrice() : position.getShortAvgPrice();
            Amount ammount = (position.isLong()) ? position.getLongVolume() : position.getShortVolume();
            Market market = position.getMarket();
            PositionEffect positionEffect = PositionEffect.OPEN;
            return getMargin(price, ammount, rate, method, market, positionEffect);

        }
        return DecimalAmount.ZERO;

    }

    public static Amount getCommission(Amount price, Amount ammount, Market market, PositionEffect postionEffect) {
        double rate = market.getFeeRate() * market.getMargin();
        FeeMethod method = market.getFeeMethod();
        Amount commission;
        switch (method) {
            case PercentagePerUnit:
                return calculatePercentagePerUnit(price, ammount, rate, market);
            case PerUnit:
                return calculatePerUnit(ammount, rate, market);
            case PercentagePerUnitOpening:
                commission = (postionEffect.equals(PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, market) : DecimalAmount.ZERO;
                return commission;
            case PerUnitOpening:
                commission = (postionEffect.equals(PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, market) : DecimalAmount.ZERO;
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
                margin = (postionEffect.equals(PositionEffect.OPEN)) ? calculatePercentagePerUnitOpening(price, amount, rate, market) : DecimalAmount.ZERO;
                return margin;
            case PerUnitOpening:
                margin = (postionEffect.equals(PositionEffect.OPEN)) ? calculatePerUnitOpening(amount, rate, market) : DecimalAmount.ZERO;
                return margin;
            default:
                log.error("No margin fee method calcation for : " + method);
                return DecimalAmount.ZERO;
        }

    }

    private static Amount calculatePercentagePerUnit(Amount price, Amount amount, double rate, Market market) {
        //BigDecimal precision = BigDecimal.valueOf(market.getPriceBasis());

        if (market.getTradedCurrency().equals(market.getBase())) {
            price = price.invert();
            //precision = BigDecimal.valueOf(market.getTradedCurrency().getBasis());
        }
        Amount notional = ((price.times(amount, Remainder.ROUND_EVEN)).times(rate, Remainder.ROUND_EVEN).abs());

        return notional.toBasis(market.getTradedCurrency().getBasis(), Remainder.ROUND_CEILING).negate();

        //      
        //      BigDecimal fees = notional.asBigDecimal().setScale(price.getScale(), BigDecimal.ROUND_UP);
        //      fees = fees.divide(precision, BigDecimal.ROUND_UP);
        //      long feeCount = fees.longValue();
        //      DiscreteAmount newAmount = new DiscreteAmount(feeCount, precision.doubleValue());
        //      return newAmount.negate();

    }

    private static Amount calculatePerUnit(Amount amount, double rate, Market market) {

        Amount notional = ((amount.times(rate, Remainder.ROUND_EVEN)).abs());
        return notional.toBasis(market.getTradedCurrency().getBasis(), Remainder.ROUND_CEILING).negate();

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
