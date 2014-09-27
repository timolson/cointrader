package org.cryptocoinpartners.module;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderBuilder;
import org.cryptocoinpartners.schema.OrderBuilder.CommonOrderBuilder;
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
public class TurtelsStrategy extends SimpleStatefulStrategy {

	@Inject
	public TurtelsStrategy(Context context, Configuration config) {

		// String marketSymbol = config.getString("demostrategy.market","BITFINEX:BTC.USD");
		String marketSymbol = ("BITSTAMP:BTC.USD");
		//csvReadTicks();

		market = Market.forSymbol(marketSymbol);
		if (market == null)
			throw new Error("Could not find Market for symbol " + marketSymbol);
		BigDecimal volumeBD = new BigDecimal("1");// 100 satoshis
		// = DiscreteAmount.roundedCountForBasis(volumeBD, market.getVolumeBasis());
	}

	public static class DataSubscriber {

		private static Logger logger = Logger.getLogger(DataSubscriber.class.getName());

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

	//@When("select low from LowIndicator")
	@When("select high from LongHighIndicator")
	void handleLongHighIndicator(double d) {
		//new high price so we will enter a long trades

		OrderBuilder.CommonOrderBuilder orderBuilder = buildEnterLongOrder(getATR());
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
			orderService.placeOrder(entryOrder);
		}
	}

	private double getATR() {
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
		log.info(" ATR:" + atr);
		return atr;

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

	@When("select low from ShortLowIndicator")
	void handleShortLowIndicator(double d) {
		//new high price so we will enter a long trades
		OrderBuilder.CommonOrderBuilder orderBuilder = buildExitLongOrder();

		placeOrder(orderBuilder);

		//		log.info("Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset() + "):"
		//				+ portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:" + portfolioService.getCashBalance()
		//				+ " Open Trade Equity:" + portfolioService.getMarketValue() + ")");
	}

	@When("select high from ShortHighIndicator")
	void handleShortHighIndicator(double d) {
		//new high price so we will enter a long trades
		OrderBuilder.CommonOrderBuilder orderBuilder = buildExitShortOrder();
		placeOrder(orderBuilder);
		//	log.info("Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset() + "):"
		//		+ portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:" + portfolioService.getCashBalance()
		//	+ " Open Trade Equity:" + portfolioService.getMarketValue() + ")");
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

	@Transient
	public static Market getMarket() {
		return market;
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	protected OrderBuilder.CommonOrderBuilder buildEntryOrder() {
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
		orderService.handleCancelAllSpecificOrders(portfolio);
		long volume;
		ArrayList<Position> positions = portfolioService.getPositions();
		if (positions.isEmpty()) {
			return null;
		} else {
			volume = positions.get(0).getVolume().getCount();
		}
		Offer bestAsk = quotes.getLastAskForMarket(market);
		if (bestAsk == null || volume <= 0)
			return null;
		DiscreteAmount limitPrice = bestAsk.getPrice().decrement(2);
		return order.create(context.getTime(), market, -volume, "Long Exit Order").withLimitPrice(limitPrice);
	}

	@SuppressWarnings("ConstantConditions")
	protected OrderBuilder.CommonOrderBuilder buildExitShortOrder() {
		orderService.handleCancelAllSpecificOrders(portfolio);

		long volume;
		ArrayList<Position> positions = portfolioService.getPositions();
		if (positions.isEmpty()) {
			return null;
		} else {
			volume = positions.get(0).getVolume().getCount();
		}
		Offer bestBid = quotes.getLastBidForMarket(market);
		if (bestBid == null || volume >= 0)
			return null;
		DiscreteAmount limitPrice = bestBid.getPrice().increment(2);

		return order.create(context.getTime(), market, -volume, "Short Exit Order").withLimitPrice(limitPrice);
	}

	@SuppressWarnings("ConstantConditions")
	protected OrderBuilder.CommonOrderBuilder buildEnterLongOrder(double atr) {
		orderService.handleCancelAllSpecificOrders(portfolio);
		Offer bestBid = quotes.getLastBidForMarket(market);
		if (bestBid == null)
			return null;
		DiscreteAmount limitPrice = bestBid.getPrice().decrement();
		Amount cashBal = portfolioService.getCashBalance().plus(portfolioService.getMarketValue());
		Amount orderSize = (cashBal.times(0.01, Remainder.ROUND_EVEN)).dividedBy(limitPrice, Remainder.ROUND_EVEN);
		if (orderSize.isPositive()) {
			DiscreteAmount orderDiscrete = orderSize.toBasis(bestBid.getMarket().getBase().getBasis(), Remainder.ROUND_EVEN);
			return order.create(context.getTime(), market, orderDiscrete.getCount(), "Long Entry Order").withLimitPrice(limitPrice);
		} else {
			return null;
		}
	}

	@SuppressWarnings("ConstantConditions")
	protected OrderBuilder.CommonOrderBuilder buildEnterShortOrder(double atr) {
		orderService.handleCancelAllSpecificOrders(portfolio);
		Offer bestAsk = quotes.getLastAskForMarket(market);
		if (bestAsk == null)
			return null;
		DiscreteAmount limitPrice = bestAsk.getPrice().increment();
		Amount cashBal = portfolioService.getCashBalance().plus(portfolioService.getMarketValue());
		Amount orderSize = (cashBal.times(0.01, Remainder.ROUND_EVEN)).dividedBy(limitPrice, Remainder.ROUND_EVEN);
		if (orderSize.isPositive()) {
			DiscreteAmount orderDiscrete = orderSize.toBasis(bestAsk.getMarket().getBase().getBasis(), Remainder.ROUND_EVEN);
			return order.create(context.getTime(), market, -orderDiscrete.getCount(), "Short Entry Order").withLimitPrice(limitPrice);
		} else {
			return null;
		}
	}

	//
	//private Offer bestBid;
	//private Offer bestAsk;
	private static Market market;
	private final long volumeCount = 0;

	@Override
	@Nullable
	protected CommonOrderBuilder buildStopOrder(Fill fill) {
		//double atr = getATR();
		//double stopAmount = (Math.max(2 * atr, fill.getPrice().getBasis()));
		//stopAmount = (stopAmount) / fill.getPrice().getBasis();

		DiscreteAmount stopPrice;
		if (fill.getVolumeCount() < 0) {
			//sell order
			stopPrice = new DiscreteAmount((long) (fill.getPriceCount() * 1.15), fill.getMarket().getPriceBasis());

			//.times(1.1, Remainder.ROUND_CEILING);
		} else {
			stopPrice = new DiscreteAmount((long) (fill.getPriceCount() * 0.85), fill.getMarket().getPriceBasis());

		}
		DiscreteAmount trailingStopPrice = new DiscreteAmount((long) (fill.getPriceCount() * 0.15), fill.getMarket().getPriceBasis());

		return order.create(context.getTime(), market, fill.getVolumeCount() * -1, "Trailing Stop Order").withTrailingStopPrice(stopPrice, trailingStopPrice);

	}
}
