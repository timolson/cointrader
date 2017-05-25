package org.cryptocoinpartners.util;

import javax.inject.Inject;

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
        double rate = fill.getMarket().getFeeRate();
        //* fill.getMarket().getContractSize();
        //* fill.getMarket().getMargin();
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
                return calculatePercentagePerUnit(price, ammount, rate, market);
            case PerUnit:
                return calculatePerUnit(ammount, rate, market);
            case PercentagePerUnitOpening:
                margin = (positionEffect == (PositionEffect.OPEN)) ? calculatePercentagePerUnit(price, ammount, rate, market) : DecimalAmount.ZERO;
                return margin;
            case PerUnitOpening:
                margin = (positionEffect == (PositionEffect.OPEN)) ? calculatePerUnit(ammount, rate, market) : DecimalAmount.ZERO;
                return margin;
            default:
                log.error("No margin fee method calcation for : " + method);
                return DecimalAmount.ZERO;
        }

    }

    public static Amount getMargin(Fill fill) {

        double rate = (fill.getMarket().getMargin() == 0) ? 1 : fill.getMarket().getMargin();
        FeeMethod method = (fill.getMarket().getMarginFeeMethod() == null) ? FeeMethod.PercentagePerUnit : fill.getMarket().getMarginFeeMethod();

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

            Amount price = (order.getLimitPrice() != null) ? order.getLimitPrice() : order.getMarketPrice();

            Amount ammount = order.getVolume().abs();
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
            Amount ammount = order.getVolume().abs();
            Market market = order.getMarket();
            PositionEffect positionEffect = order.getPositionEffect();
            return getMargin(price, ammount, rate, method, market, positionEffect);

        }
        return DecimalAmount.ZERO;

    }

    public static Amount getMargin(Position position) {
        if (position.isOpen()) {

            double rate = (position.getMarket().getMargin() == 0) ? 1 : position.getMarket().getMargin();
            FeeMethod method = (position.getMarket().getMarginFeeMethod() == null) ? FeeMethod.PercentagePerUnit : position.getMarket().getMarginFeeMethod();

            Amount price = (position.isLong()) ? position.getLongAvgPrice() : position.getShortAvgPrice();
            Amount ammount = (position.isLong()) ? position.getLongVolume() : position.getShortVolume();
            Market market = position.getMarket();
            PositionEffect positionEffect = PositionEffect.OPEN;
            return getMargin(price, ammount, rate, method, market, positionEffect);

        }
        return DecimalAmount.ZERO;

    }

    public static Amount getCommission(Amount price, Amount ammount, Market market, PositionEffect postionEffect) {
        double rate = market.getFeeRate();
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
        //  if (market.getContractSize(market) != 1)
        price = market.getMultiplier(market, price, DecimalAmount.ONE);
        notional = ((price.times(amount, Remainder.ROUND_EVEN)).times(rate, Remainder.ROUND_EVEN).abs()).times(market.getContractSize(market),
                Remainder.ROUND_EVEN);
        //     BTC/USD, so seelling BTC and buing $ 450.76 (BTC/USD
        //             buying 1 ETH at 0.02 BTC)
        //     BTC/USD so buying BTC selling $
        Asset tradedCCY = (market.getTradedCurrency(market) == null) ? market.getQuote() : market.getTradedCurrency(market);
        return notional.toBasis(tradedCCY.getBasis(), Remainder.ROUND_CEILING).negate();

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
