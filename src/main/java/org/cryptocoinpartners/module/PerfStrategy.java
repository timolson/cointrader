package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderBuilder;
import org.cryptocoinpartners.schema.OrderBuilder.CommonOrderBuilder;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.util.Remainder;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * This simple Strategy first waits for Book data to arrive about the target Market, then it places a buy order
 * at demostrategy.spread below the current bestAsk.  Once it enters the trade, it places a sell order at
 * demostrategy.spread above the current bestBid.
 * This strategy ignores the available Positions in the Portfolio and always trades the amount set by demostrategy.volume on
 * the Market specified by demostrategy.market
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class PerfStrategy extends SimpleStatefulStrategy {
    double percentEquityRisked = 0.01;

    Amount maxAssetPosition = DecimalAmount.of("12");

    private Portfolio portfolio;
    @Inject
    private Market markets;

    private final Market market;

    @Inject
    public PerfStrategy(Context context, Configuration config) {

        // String marketSymbol = config.getString("demostrategy.market","BITFINEX:BTC.USD");
        String marketSymbol = ("OKCOIN:BTC.USD.THIS_WEEK");
        //csvReadTicks();

        market = (Market) markets.forSymbol(marketSymbol);
        if (market == null)
            throw new Error("Could not find Market for symbol " + marketSymbol);
        // = DiscreteAmount.roundedCountForBasis(volumeBD, market.getVolumeBasis());
    }

    public static class DataSubscriber {

        protected static Logger logger = Logger.getLogger(DataSubscriber.class.getName());

        public void update(double avgValue, double countValue, double minValue, double maxValue) {
            logger.info(avgValue + "," + countValue + "," + minValue + "," + maxValue);

        }

        public void update(long time, double high) {

            logger.info("time:" + time + " high:" + high);
        }

        public void update(double time, long high) {

            logger.info("time:" + time + " high:" + high);
        }

        public void update(Trade trade) {

            logger.info("Trade:" + trade.toString());
        }

        public void update(double maxValue, double minValue, double atr) {
            logger.info("High:" + maxValue + " Low:" + minValue + " ATR:" + atr);
        }

        public void update(double maxValue, double minValue, long timestamp) {
            logger.info("Trade Price:" + maxValue + " Prevoius Max:" + minValue + " Timestamp:" + timestamp);
        }

        public void update(Bar firstBar, Bar lastBar) {
            logger.info("LastBar:" + lastBar.toString());
        }

        public void update(double atr) {
            logger.info("ATR" + atr);
        }

        @SuppressWarnings("rawtypes")
        public void update(Map insertStream, Map removeStream) {

            logger.info("high:" + insertStream.get("high") + " Low:" + insertStream.get("low"));
        }

    }

    @When("select low from ShortLowIndicator")
    void handleShortLowIndicator(double d) {
        //		counter++;
        //		log.info(String.valueOf(counter));
        //		//		//new high price so we will enter a long trades
        OrderBuilder.CommonOrderBuilder orderBuilder = buildExitLongOrder();

        placeOrder(orderBuilder);

        //		log.info("Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset() + "):"
        //				+ portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:" + portfolioService.getCashBalance()
        //				+ " Open Trade Equity:" + portfolioService.getMarketValue() + ")");
    }

    @When("select high from ShortHighIndicator")
    void handleShortHighIndicator(double d) {
        //new high price so we will enter a long trades
        //		counter++;
        //		log.info(String.valueOf(counter));

        OrderBuilder.CommonOrderBuilder orderBuilder = buildExitShortOrder();
        placeOrder(orderBuilder);
        //	log.info("Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset() + "):"
        //		+ portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:" + portfolioService.getCashBalance()
        //	+ " Open Trade Equity:" + portfolioService.getMarketValue() + ")");
    }

    //@When("select low from LowIndicator")
    @When("select high from LongHighIndicator")
    void handleLongHighIndicator(double d) {
        //new high price so we will enter a long trades
        //		counter++;
        //		log.info(String.valueOf(counter));
        //		if (counter == 337) {
        //			log.info("here is my errro");
        //		}

        OrderBuilder.CommonOrderBuilder orderBuilder = buildEnterLongOrder(getATR());
        placeOrder(orderBuilder);
        //		log.info("Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset() + "):"
        //				+ portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:" + portfolioService.getCashBalance()
        //				+ " Open Trade Equity:" + portfolioService.getMarketValue() + ")");
    }

    @When("select low from LongLowIndicator")
    void handleLongLowIndicator(double d) {
        //new high price so we will enter a long trades
        OrderBuilder.CommonOrderBuilder orderBuilder = buildEnterShortOrder(getATR());
        placeOrder(orderBuilder);

        //		log.info("Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset() + "):"
        //				+ portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:" + portfolioService.getCashBalance()
        //				+ " Open Trade Equity:" + portfolioService.getMarketValue() + ")");
    }

    private void placeOrder(CommonOrderBuilder orderBuilder) {
        Order entryOrder;
        if (orderBuilder != null) {
            entryOrder = orderBuilder.getOrder();
            log.info("Entering trade with order " + entryOrder);
            try {
                orderService.placeOrder(entryOrder);
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Transient
    public double getATR() {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @When("select * from Fill")
    void handleFills(Fill fill) {
    }

    //	@When("select * from Book")
    //@When("select talib(\"rsi\", askPriceAsDouble, 14) as value from Book")
    void handleBook(Book b) {
        //log.info("RSI" + v);
        if (b.getMarket().equals(market)) {
            //	log.info("Market:" + b.getMarket() + " Price:" + b.getAskPriceCountAsDouble().toString() + " Timestamp" + b.getTime().toString());

            List<Object> events = null;
            //log.info(b.getAskPrice().toString() + "Timestamp" + b.getTime().toString());

            //log.info(b.getAskPriceCountAsDouble().toString());
            try {
                events = context.loadStatementByName("MOVING_AVERAGE");
                if (events.size() > 0) {
                    HashMap value = ((HashMap) events.get(events.size() - 1));
                    if (value.get("minValue") != null) {
                        double minDouble = (double) value.get("minValue");
                        double maxDouble = (double) value.get("maxValue");
                        long count = (long) value.get("countValue");

                        log.info(b.getTime().toString() + "," + b.getAskPriceAsDouble().toString() + "," + String.valueOf(minDouble) + ","
                                + String.valueOf(maxDouble) + "," + String.valueOf(count));
                        //+ "Max Vlaue:" + maxDouble));
                        //return (trade.getPrice());
                    }

                }

            } catch (ParseException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (DeploymentException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }

        //			try {
        //				//events = context.loadStatementByName("MOVING_AVERAGE");
        //
        //				if (events.size() > 0) {
        //					//Trade trade = ((Trade) events.get(events.size() - 1));
        //					log.info("AvgPrice:" + events.get(events.size() - 1).toString());
        //
        //				}
        //			} catch (ParseException | DeploymentException | IOException e) {
        //				// TODO Auto-generated catch block
        //				e.printStackTrace();
        //			}
        //		if (b.getMarket().equals(market)) {
        //
        //			bestBid = b.getBestBid();
        //			bestAsk = b.getBestAsk();
        //			if (bestBid != null && bestAsk != null) {
        //				ready();
        //				enterTrade();
        //				exitTrade();
        //				log.info("Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset() + "):"
        //						+ portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:" + portfolioService.getCashBalance()
        //						+ " Open Trade Equity:" + portfolioService.getMarketValue() + ")");
        //			}
        //		}
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildEntryOrder(Market market) {
        Offer bestBid = quotes.getLastBidForMarket(market);

        if (bestBid == null)
            return null;
        DiscreteAmount limitPrice = bestBid.getPrice().decrement();
        // bUy 1% OF CASH BALANCE
        return order.create(context.getTime(), market, volumeCount, "Entry Order").withLimitPrice(limitPrice);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildExitOrder(Order entryOrder) {
        Offer bestAsk = quotes.getLastAskForMarket(market);

        if (bestAsk == null)
            return null;
        DiscreteAmount limitPrice = bestAsk.getPrice().increment();
        return order.create(context.getTime(), market, -volumeCount, "Exit Order").withLimitPrice(limitPrice);
    }

    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildExitLongOrder() {

        DiscreteAmount volume = portfolioService.getNetPosition(market.getBase(), market.getExchange());

        volume = new DiscreteAmount(volume.getCount(), market.getVolumeBasis());
        if (volume.isNegative()) {
            log.debug("overfilled");
        }
        Offer bestAsk = quotes.getLastAskForMarket(market);
        if (bestAsk == null || bestAsk.getPriceCount() == 0 || volume.isNegative() || volume.isZero())
            return null;
        DiscreteAmount limitPrice = bestAsk.getPrice().decrement(2);
        orderService.handleCancelAllSpecificOrders(portfolio, market);
        orderService.handleCancelAllLongStopOrders(portfolio, market);
        return order.create(context.getTime(), market, volume.negate(), "Long Exit Order").withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE);
    }

    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildExitShortOrder() {

        DiscreteAmount volume = portfolioService.getNetPosition(market.getBase(), market.getExchange());
        volume = new DiscreteAmount(volume.getCount(), market.getVolumeBasis());

        Offer bestBid = quotes.getLastBidForMarket(market);
        if (bestBid == null || bestBid.getPriceCount() == 0 || volume.isPositive() || volume.isZero())
            return null;
        DiscreteAmount limitPrice = bestBid.getPrice().increment(2);
        orderService.handleCancelAllSpecificOrders(portfolio, market);
        orderService.handleCancelAllShortStopOrders(portfolio, market);
        return order.create(context.getTime(), market, volume.negate(), "Short Exit Order").withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE);
    }

    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildEnterLongOrder(double atr) {
        Offer bestBid = quotes.getLastBidForMarket(market);
        if (bestBid == null || bestBid.getPriceCount() == 0)
            return null;
        Collection<Position> postions = portfolioService.getPositions(((Market) bestBid.getMarket()).getBase(), ((Market) bestBid.getMarket()).getExchange());
        Iterator<Position> itp = postions.iterator();
        while (itp.hasNext()) {
            for (Fill pos : itp.next().getFills()) {

                if (!pos.getVolume().isZero())
                    return null;
                //return buildExitShortOrder();
            }
        }
        DiscreteAmount postion = portfolioService.getNetPosition(((Market) bestBid.getMarket()).getBase(), ((Market) bestBid.getMarket()).getExchange());
        postion = new DiscreteAmount(postion.getCount(), market.getVolumeBasis());
        DiscreteAmount limitPrice = bestBid.getPrice();
        double multiplier = (market.getMultiplier(market, limitPrice, DecimalAmount.ONE)).asDouble();
        // we need to get the balance of the traded currency on this exchange to  to make sure we have enough cash, not balance for whole strategy
        Amount cashBal = portfolioService.getCashBalance(market.getTradedCurrency(market));
        // we need to get the balance of the traded currency on this exchange to  to make sure we have enough cash, not balance for whole strategy
        Amount totalBal = cashBal.plus(portfolioService.getUnrealisedPnL(market.getTradedCurrency(market)));
        DiscreteAmount atrDiscrete = (new DiscreteAmount((long) (atr), ((Market) bestBid.getMarket()).getPriceBasis()));

        Listing listing = Listing.forPair(((Market) bestBid.getMarket()).getQuote(), getPortfolio().getBaseAsset());
        Listing tradedListing = Listing.forPair(((Market) bestBid.getMarket()).getTradedCurrency(market), getPortfolio().getBaseAsset());
        Offer rate = quotes.getImpliedBestBidForListing(listing);
        Offer tradedRate = quotes.getImpliedBestBidForListing(tradedListing);
        DiscreteAmount stopDiscrete = new DiscreteAmount(limitPrice.getCount() - (long) (2 * atr), ((Market) bestBid.getMarket()).getPriceBasis());

        DiscreteAmount atrUSDDiscrete = (DiscreteAmount) atrDiscrete.times(rate.getPrice(), Remainder.ROUND_EVEN);
        Amount totalBalUSD = (totalBal.times(tradedRate.getPrice(), Remainder.ROUND_EVEN));
        if (atrUSDDiscrete.isZero())
            return null;
        Amount unitSize = (totalBalUSD.times(percentEquityRisked, Remainder.ROUND_EVEN)).dividedBy((atrUSDDiscrete.times(multiplier, Remainder.ROUND_EVEN)),
                Remainder.ROUND_EVEN);
        //if (portfolioService.getPositions().isEmpty()
        //	|| (totalBal.minus(portfolioService.getPositions().get(0).getVolume().abs().times(bestBid.getPrice(), Remainder.ROUND_EVEN))).isPositive()) {
        Amount positionUnits = postion.dividedBy(unitSize, Remainder.ROUND_EVEN);

        if (unitSize.isPositive()
                && (((unitSize.times(bestBid.getPrice(), Remainder.ROUND_EVEN)).divide(market.getMargin(), Remainder.ROUND_EVEN)).compareTo(cashBal) <= 0)
                && (positionUnits.compareTo(maxAssetPosition) <= 0)) {
            DiscreteAmount orderDiscrete = unitSize.toBasis(((Market) bestBid.getMarket()).getVolumeBasis(), Remainder.ROUND_EVEN);
            if (orderDiscrete.isZero())
                return null;
            orderService.handleCancelAllSpecificOrders(portfolio, market);

            //DecimalAmount stopAdjustment = DecimalAmount.of(atrDiscrete.dividedBy(2, Remainder.ROUND_EVEN));
            //orderService.adjustStopLoss(stopAdjustment);
            return order.create(context.getTime(), market, orderDiscrete.asBigDecimal(), FillType.STOP_LOSS).withComment("Long Entry Order")
                    .withLimitPrice(limitPrice.asBigDecimal()).withStopAmount(stopDiscrete.asBigDecimal()).withPositionEffect(PositionEffect.OPEN);

        } else {
            return null;
        }

    }

    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildEnterShortOrder(double atr) {
        Offer bestAsk = quotes.getLastAskForMarket(market);
        if (bestAsk == null || bestAsk.getPriceCount() == 0)
            return null;

        Collection<Position> postions = portfolioService.getPositions(((Market) bestAsk.getMarket()).getBase(), ((Market) bestAsk.getMarket()).getExchange());
        Iterator<Position> itp = postions.iterator();
        while (itp.hasNext()) {
            for (Fill pos : itp.next().getFills()) {
                if (!pos.getVolume().isZero())
                    return null;
            }
        }
        DiscreteAmount postion = portfolioService.getNetPosition(((Market) bestAsk.getMarket()).getBase(), ((Market) bestAsk.getMarket()).getExchange());
        postion = new DiscreteAmount(postion.getCount(), market.getVolumeBasis());

        if (postion.isPositive())
            return buildExitLongOrder();
        DiscreteAmount limitPrice = bestAsk.getPrice();
        double multiplier = (market.getMultiplier(market, limitPrice, DecimalAmount.ONE)).asDouble();
        if (multiplier == 1)
            return null;
        Amount cashBal = portfolioService.getCashBalance(market.getTradedCurrency(market));
        Amount totalBal = cashBal.plus(portfolioService.getUnrealisedPnL(market.getTradedCurrency(market)));
        DiscreteAmount atrDiscrete = (new DiscreteAmount((long) (atr), ((Market) bestAsk.getMarket()).getPriceBasis()));

        DiscreteAmount stopDiscrete = new DiscreteAmount(limitPrice.getCount() + (long) (2 * atr), ((Market) bestAsk.getMarket()).getPriceBasis());

        Listing listing = Listing.forPair(((Market) bestAsk.getMarket()).getQuote(), getPortfolio().getBaseAsset());
        Listing tradedListing = Listing.forPair(((Market) bestAsk.getMarket()).getTradedCurrency(market), getPortfolio().getBaseAsset());

        Offer rate = quotes.getImpliedBestAskForListing(listing);
        Offer tradedRate = quotes.getImpliedBestAskForListing(tradedListing);

        DiscreteAmount atrUSDDiscrete = (DiscreteAmount) atrDiscrete.times(rate.getPrice(), Remainder.ROUND_EVEN);
        Amount totalBalUSD = (totalBal.times(tradedRate.getPrice(), Remainder.ROUND_EVEN));
        if (atrUSDDiscrete.isZero())
            return null;
        Amount unitSize = (totalBalUSD.times(percentEquityRisked, Remainder.ROUND_EVEN)).dividedBy((atrUSDDiscrete.times(multiplier, Remainder.ROUND_EVEN)),
                Remainder.ROUND_EVEN);

        //if (portfolioService.getPositions().isEmpty()
        //	|| (totalBal.minus(portfolioService.getPositions().get(0).getVolume().abs().times(bestAsk.getPrice(), Remainder.ROUND_EVEN))).isPositive()) {
        Amount positionUnits = postion.dividedBy(unitSize, Remainder.ROUND_EVEN);
        if (unitSize.isPositive()
                && ((unitSize.times(bestAsk.getPrice(), Remainder.ROUND_EVEN).divide(market.getMargin(), Remainder.ROUND_EVEN)).compareTo(cashBal) <= 0)
                && (positionUnits.compareTo(maxAssetPosition.negate()) >= 0)) {
            DiscreteAmount orderDiscrete = unitSize.toBasis(((Market) bestAsk.getMarket()).getVolumeBasis(), Remainder.ROUND_CEILING);
            if (orderDiscrete.isZero())
                return null;
            orderService.handleCancelAllSpecificOrders(portfolio, market);
            //orderService.adjustStopLoss((atrDiscrete.dividedBy(2, Remainder.ROUND_EVEN)).negate());

            return order.create(context.getTime(), market, orderDiscrete.asBigDecimal().negate(), FillType.STOP_LOSS).withComment("Short Entry Order")
                    .withLimitPrice(limitPrice.asBigDecimal()).withStopAmount(stopDiscrete.asBigDecimal()).withPositionEffect(PositionEffect.OPEN);
        } else {
            return null;
        }

    }

    //
    //private Offer bestBid;
    //private Offer bestAsk;
    private final long volumeCount = 0;

    //int counter = 0;

    @Override
    @Nullable
    protected CommonOrderBuilder buildStopOrder(Fill fill) {
        //		if (fill.getOrder().getExitPrice() != null) {
        //			ArrayList<Order> linkedOrders = new ArrayList<Order>();
        //			linkedOrders.add(fill.getOrder());
        //			return order.create(context.getTime(), market, fill.getVolumeCount() * -1, "Stop Order").withStopPrice(fill.getOrder().getExitPrice())
        //					.withLinkedOrders(linkedOrders);
        //		} else {
        return null;
        //		}
    }

}
