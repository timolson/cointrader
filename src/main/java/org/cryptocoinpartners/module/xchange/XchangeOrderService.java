package org.cryptocoinpartners.module.xchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.exceptions.OrderNotFoundException;
import org.cryptocoinpartners.exceptions.UnknownOrderStateException;
import org.cryptocoinpartners.module.BaseOrderService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.xchange.XchangeData.Helper;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.FillFactory;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.RateLimiter;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.XchangeUtil;
import org.joda.time.Duration;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.okcoin.FuturesContract;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

import si.mazi.rescu.HttpStatusIOException;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * This module routes SpecificOrders through Xchange
 * 
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class XchangeOrderService extends BaseOrderService {

	private final FillFactory fillFactory;
	//  @Inject
	//protected EntityManager entityManager;

	private final Context context;
	private static final HashMap<Market, Long> lastFillTimes = new HashMap<Market, Long>();

	@Inject
	public XchangeOrderService(Context context, Configuration config, FillFactory fillFactory) {
		this.context = context;
		this.fillFactory = fillFactory;
		final String configPrefix = "xchange";
		Set<String> exchangeTags = XchangeUtil.getExchangeTags();

		// now we have all the exchange tags.  process each config group
		for (String tag : exchangeTags) {
			// three configs required:
			// .class the full classname of the Xchange implementation
			// .rate.queries rate limit the number of queries to this many (default: 1)
			// .rate.period rate limit the number of queries during this period of time (default: 1 second)
			// .listings identifies which Listings should be fetched from this exchange
			org.cryptocoinpartners.schema.Exchange exchange = XchangeUtil.getExchangeForTag(tag);
			String prefix = configPrefix + "." + tag + '.';
			if (exchange != null && config.getString(prefix + "apikey", null) != null && config.getString(prefix + "apisecret", null) != null) {

				final String helperClassName = config.getString(prefix + "helper.class", null);
				final String streamingConfigClassName = config.getString(prefix + "streaming.config.class", null);
				int queries = config.getInt(prefix + "rate.queries", 1);
				int retryCount = config.getInt(prefix + "retry", 10);

				Duration period = Duration.millis((long) (2000 * config.getDouble(prefix + "rate.period", 1))); // rate.period in seconds
				final List listings = config.getList(prefix + "listings");

				initExchange(helperClassName, streamingConfigClassName, retryCount, queries, period, exchange, listings);
			} else {
				log.warn("Could not find Exchange for property \"xchange." + tag + ".*\"");
			}
		}
	}

	private void initExchange(@Nullable String helperClassName, @Nullable String streamingConfigClassName, int retryCount, int queries, Duration per,
			Exchange coinTraderExchange, List listings) {
		org.knowm.xchange.Exchange xchangeExchange = XchangeUtil.getExchangeForMarket(coinTraderExchange);
		Helper helper = null;
		if (helperClassName != null && !helperClassName.isEmpty()) {
			if (helperClassName.indexOf('.') == -1)
				helperClassName = XchangeData.class.getPackage().getName() + '.' + helperClassName;
			try {
				final Class<?> helperClass = getClass().getClassLoader().loadClass(helperClassName);
				try {
					helper = (Helper) helperClass.newInstance();
					XchangeUtil.addHelperForExchange(coinTraderExchange, helper);
				} catch (InstantiationException | IllegalAccessException e) {
					log.error("Could not initialize XchangeData because helper class " + helperClassName + " could not be instantiated ", e);
					return;
				} catch (ClassCastException e) {
					log.error("Could not initialize XchangeData because helper class " + helperClassName + " does not implement " + Helper.class);
					return;
				}
			} catch (ClassNotFoundException e) {
				log.error("Could not initialize XchangeData because helper class " + helperClassName + " was not found");
				return;
			}
		}

		List<Market> markets = new ArrayList<>(listings.size());
		Market market;
		//  ExchangeStreamingConfiguration streamingConfiguration = new OkCoinExchangeStreamingConfiguration();
		for (Iterator<List> il = listings.iterator(); il.hasNext();) {
			Object listingSymbol = il.next();
			Listing listing = Listing.forSymbol(listingSymbol.toString().toUpperCase());
			market = context.getInjector().getInstance(Market.class).findOrCreate(coinTraderExchange, listing);
			markets.add(market);
		}

		//    PollingTradeService dataService = xchangeExchange.getPollingTradeService();
		// when 
		RateLimiter rateLimiter = new RateLimiter(queries, per);
		for (Market cointraderMarket : markets) {

			// add to various shared mapps
			lastFillTimes.put(cointraderMarket, 0L);

			rateLimiter.execute(new FetchOrdersRunnable(context, cointraderMarket, rateLimiter, coinTraderExchange, retryCount, helper));
		}

		return;

	}

	@Override
	protected void handleSpecificOrder(SpecificOrder specificOrder) throws Throwable {
		Order.OrderType orderType = null;
		org.knowm.xchange.Exchange exchange = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange());
		TradeService tradeService = exchange.getTradeService();
		if (specificOrder.getLimitPrice() != null && specificOrder.getStopPrice() != null) {
			specificOrder.persit();
			reject(specificOrder, "Stop-limit orders are not supported");
		}
		if (specificOrder.getPositionEffect() == null || specificOrder.getPositionEffect() == PositionEffect.OPEN)
			orderType = specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
		else if (specificOrder.getPositionEffect() == PositionEffect.CLOSE)
			// if order volume is < 0 && it is closing, then I am exiting Bid, else I am exit bid
			//  orderType = specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
			orderType = specificOrder.isAsk() ? Order.OrderType.EXIT_BID : Order.OrderType.EXIT_ASK;
		BigDecimal tradeableVolume = specificOrder.getVolume().abs().asBigDecimal();
		CurrencyPair currencyPair = XchangeUtil.getCurrencyPairForListing(specificOrder.getMarket().getListing());
		String id = specificOrder.getId().toString();
		Date timestamp = specificOrder.getTime().toDate();
		if ((specificOrder.getFillType() == null || (specificOrder.getFillType() != null && !specificOrder.getFillType().equals(FillType.MARKET)))
				&& specificOrder.getLimitPrice() != null) {
			if (XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()) != null) {
				log.info(this.getClass().getSimpleName() + ":handleSpecificOrder Adjusting specificOrder " + specificOrder + " with helper "
						+ XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).getClass().getSimpleName() + ":adjustOrder");

				XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).adjustOrder(specificOrder);
			}

			LimitOrder limitOrder = new LimitOrder(orderType, tradeableVolume, currencyPair, "", null, specificOrder.getLimitPrice().asBigDecimal());
			if (XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()) != null) {
				//Ajust Order
				log.info(this.getClass().getSimpleName() + ":handleSpecificOrder Adjusting limit specificOrder " + specificOrder
						+ " and limitOrder xchange order " + limitOrder + " with helper "
						+ XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).getClass().getSimpleName() + ":adjustOrder");

				XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).adjustOrder(specificOrder, limitOrder);
			}

			//   limitOrder.addOrderFlag(PoloniexOrderFlags.MARGIN);
			// todo put on a queue
			try {
				//TODO if this failed like we place a clsoing order it jsut get's rejected but we have not reverted teh stack correctly
				synchronized (tradeService) {

					specificOrder.setRemoteKey(tradeService.placeLimitOrder(limitOrder));
				}
				updateOrderState(specificOrder, OrderState.PLACED, true);
			} catch (ExchangeException ex) {
				//Let's try placing it as a market order!
				log.warn(this.getClass().getSimpleName() + ":handleSpecificOrder Attempting to place market order as unable to place limit order "
						+ specificOrder + " with last known state " + orderStateMap.get(specificOrder) + " as xchange order " + limitOrder);
				specificOrder.setFillType(FillType.MARKET);
				specificOrder.setExecutionInstruction(ExecutionInstruction.TAKER);
				specificOrder.setLimitPriceCount(0);
				placeOrder(specificOrder);

				// todo retry until expiration or reject as invalid
			} catch (Exception | Error e) {

				specificOrder.persit();
				if (specificOrder.isInternal()) {
					log.error(this.getClass().getSimpleName() + ":handleSpecificOrder Unable to place limit order " + specificOrder + " with last known state "
							+ orderStateMap.get(specificOrder) + " as xchange order " + limitOrder + ". Threw a Execption, full stack trace follows:", e);
					error(specificOrder, "handleSpecificOrder: unable to place limit order: " + specificOrder.getId() + " error " + e);
					// Throw the execption as we don't know if the order actually got to the exchange, but we market it as error.
					throw new UnknownOrderStateException("Unknown state of limit order " + specificOrder.getId(), e);
				} else
					updateOrderState(specificOrder, OrderState.PLACED, true);
			} finally {
				specificOrder.merge();
			}
		} else {
			if (XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()) != null) {
				log.info(this.getClass().getSimpleName() + ":handleSpecificOrder Adjusting market specificOrder " + specificOrder + " with helper "
						+ XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).getClass().getSimpleName() + ":adjustOrder");

				XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).adjustOrder(specificOrder);
			}

			MarketOrder marketOrder = new MarketOrder(orderType, tradeableVolume, currencyPair, "", null);
			if (XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()) != null) {
				log.info(this.getClass().getSimpleName() + ":handleSpecificOrder Adjusting specificOrder " + specificOrder + " and market xchange order "
						+ marketOrder + " with helper " + XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).getClass().getSimpleName()
						+ ":adjustOrder");

				//Ajust Order
				XchangeUtil.getHelperForExchange(specificOrder.getMarket().getExchange()).adjustOrder(specificOrder, marketOrder);
			}

			//			MarketOrder marketOrder = new MarketOrder(orderType, tradeableVolume, currencyPair, id, timestamp);
			// todo put on a queue
			try {
				synchronized (tradeService) {

					specificOrder.setRemoteKey(tradeService.placeMarketOrder(marketOrder));
				}
				updateOrderState(specificOrder, OrderState.PLACED, true);
			} catch (NotYetImplementedForExchangeException e) {
				specificOrder.persit();
				log.warn("XChange adapter " + exchange + " does not support this order: " + specificOrder, e);
				reject(specificOrder, "XChange adapter " + exchange + " does not support this order");
				throw e;
			} catch (ExchangeException ex) {
				log.warn(this.getClass().getSimpleName() + ":handleSpecificOrder Attempting to place limit order as unable to place market order "
						+ specificOrder + " with last known state " + orderStateMap.get(specificOrder) + " as xchange order " + marketOrder);

				specificOrder.setFillType(FillType.LIMIT);
				specificOrder.setExecutionInstruction(ExecutionInstruction.MAKER);
				placeOrder(specificOrder);

			} catch (Exception | Error e) {
				specificOrder.persit();
				if (specificOrder.getId().toString().equals(specificOrder.getRemoteKey())) {
					log.error(this.getClass().getSimpleName() + ":handleSpecificOrder Unable to place market order " + specificOrder
							+ " with last known state " + orderStateMap.get(specificOrder) + " as xchange order " + marketOrder
							+ ". Threw a Execption, full stack trace follows:", e);

					error(specificOrder, "handleSpecificOrder: unable to place market order: " + specificOrder.getId() + " error " + e);
					throw new UnknownOrderStateException("Unknown state of market order " + specificOrder.getId(), e);
				} else
					updateOrderState(specificOrder, OrderState.PLACED, true);
			} finally {
				specificOrder.merge();
			}
		}

	}

	@Transient
	protected Collection<SpecificOrder> getPendingXchangeOrders(Market market, Portfolio portfolio) {
		Collection<SpecificOrder> pendingOrders = new ArrayList<SpecificOrder>();
		org.knowm.xchange.Exchange exchange;
		try {

			exchange = XchangeUtil.getExchangeForMarket(market.getExchange());
		} catch (Error err) {
			log.info("market:" + market + " not found");
			return pendingOrders;
		}
		TradeService tradeService = exchange.getTradeService();
		SpecificOrder specificOrder;
		boolean exists = false;
		//TODO: need to check prompts to ensure they have the full OKCOIN_THISWEEK:BTC.USD.THISWEEK not just OKCOIN_THISWEEK:BTC.USD
		try {
			OpenOrders openOrders;
			synchronized (tradeService) {

				openOrders = tradeService.getOpenOrders();
			}
			for (LimitOrder xchangeOrder : openOrders.getOpenOrders()) {
				for (org.cryptocoinpartners.schema.Order cointraderOrder : orderStateMap.keySet()) {
					if (cointraderOrder instanceof SpecificOrder) {
						specificOrder = (SpecificOrder) cointraderOrder;
						if (xchangeOrder.getId().equals(specificOrder.getRemoteKey()) && specificOrder.getMarket().equals(market)) {
							Fill fill = createFill(xchangeOrder, specificOrder);
							if (fill != null)
								handleFillProcessing(fill);

							if (!adaptOrderState(xchangeOrder.getStatus()).equals(orderStateMap.get(specificOrder)))
								if (adaptOrderState(xchangeOrder.getStatus()) != OrderState.FILLED
										|| adaptOrderState(xchangeOrder.getStatus()) != OrderState.PARTFILLED)
									updateOrderState(specificOrder, adaptOrderState(xchangeOrder.getStatus()), true);

							pendingOrders.add(specificOrder);
							exists = true;
							break;
						}
					}
				}

				/*
				 * if (!exists) { Date time = (xchangeOrder.getTimestamp() != null) ? xchangeOrder.getTimestamp() : new Date(); specificOrder = new
				 * SpecificOrder(xchangeOrder, exchange, portfolio, time); specificOrder.persit(); updateOrderState(specificOrder,
				 * adaptOrderState(xchangeOrder.getStatus()), false); Fill fill = createFill(xchangeOrder, specificOrder); if (fill != null)
				 * handleFillProcessing(fill); // need to create fills if these are not the same pendingOrders.add(specificOrder); }
				 */
			}

		} catch (IOException e) {
			log.error("Threw a Execption, full stack trace follows:", e);

			e.printStackTrace();

		}
		return pendingOrders;

	}

	//Need to ensure that each exchange implments the getOrder method
	// once orders are got, we will have a exchange specfic helper that will create the fills for each excahnges
	// this meeans we get nice fat fills that are not tiny 0.00000001 of a lot and reduced overhead.
	protected void getOrders(@Nullable Helper helper, Market market, CurrencyPair pair, long lastTradeTime, long lastTradeId, FuturesContract contract,
			boolean firstRun, Exchange coinTraderExchange, int restartCount) throws Throwable {
		int tradeFailureCount = 0;
		Set<org.cryptocoinpartners.schema.Order> cointraderOpenOrders = new HashSet<org.cryptocoinpartners.schema.Order>();
		Set<org.knowm.xchange.dto.Order> xchangeOpenOrders = new HashSet<org.knowm.xchange.dto.Order>();
		Set<org.knowm.xchange.dto.Order> cointraderXchangeOrders = new HashSet<org.knowm.xchange.dto.Order>();
		TradeService tradePollingService = XchangeUtil.getExchangeForMarket(coinTraderExchange).getTradeService();
		try {
			Object params[];
			if (helper != null)
				params = helper.getTradesParameters(pair, lastTradeTime, lastTradeId);
			else {
				if (contract == null)
					params = new Object[] {};
				else
					params = new Object[] { contract };

			}
			log.trace("Attempting to get trades from data service");
			List<String> openOrdersXchangeIds = new ArrayList<String>();

			if (stateOrderMap.get(OrderState.PLACED) != null)
				for (org.cryptocoinpartners.schema.Order placedOrder : stateOrderMap.get(OrderState.PLACED))
					if (XchangeUtil.getCurrencyPairForListing(placedOrder.getMarket().getListing()).equals(pair)
							&& placedOrder.getMarket().getExchange().equals(coinTraderExchange))
						cointraderOpenOrders.add(placedOrder);
			if (stateOrderMap.get(OrderState.PARTFILLED) != null)
				for (org.cryptocoinpartners.schema.Order partfilledOrder : stateOrderMap.get(OrderState.PARTFILLED))
					if (XchangeUtil.getCurrencyPairForListing(partfilledOrder.getMarket().getListing()).equals(pair)
							&& partfilledOrder.getMarket().getExchange().equals(coinTraderExchange))
						cointraderOpenOrders.add(partfilledOrder);
			if (stateOrderMap.get(OrderState.ROUTED) != null)
				for (org.cryptocoinpartners.schema.Order routedOrder : stateOrderMap.get(OrderState.ROUTED))
					if (XchangeUtil.getCurrencyPairForListing(routedOrder.getMarket().getListing()).equals(pair)
							&& routedOrder.getMarket().getExchange().equals(coinTraderExchange))
						cointraderOpenOrders.add(routedOrder);
			if (stateOrderMap.get(OrderState.CANCELLING) != null)
				for (org.cryptocoinpartners.schema.Order cancellingOrder : stateOrderMap.get(OrderState.CANCELLING))
					if (XchangeUtil.getCurrencyPairForListing(cancellingOrder.getMarket().getListing()).equals(pair)
							&& cancellingOrder.getMarket().getExchange().equals(coinTraderExchange))
						cointraderOpenOrders.add(cancellingOrder);
			for (org.cryptocoinpartners.schema.Order openOrder : cointraderOpenOrders) {
				SpecificOrder openSpecificOrder;
				if (openOrder instanceof SpecificOrder) {
					openSpecificOrder = (SpecificOrder) openOrder;
					if (openSpecificOrder.isExternal())
						openOrdersXchangeIds.add(openSpecificOrder.getRemoteKey());
				}
			}

			// order based
			//1 - Query exchange for the order id's we know about, create any fills to account of differnece in orders

			//2 - Check exchange any orders we don't know about
			// 3 - so what if we ahve an order placed but it is not in open order, nor in fille orders.

			//trades based
			// 1- get trades from exchange loop over them and check if we have the fill for the given order

			// 2 - if we have an order we don't know about, deal with it as unknow order
			// 2- Check any exchange orders we don't know about.
			// 

			//for all the order we know about, we need to see if we had any fills for these orders

			//so we just need to blend 1 and generate unknow orders!

			Collection<Order> exchangeOrders = new ArrayList<Order>();
			if (!openOrdersXchangeIds.isEmpty()) {
				// so for the open orders we have get the state, then create fill for differnence.
				try {
					/*
					 * for (String openOrdersXchangeId : openOrdersXchangeIds) { try { Collection<Order> exchangeOrderList =
					 * tradePollingService.getOrder(openOrdersXchangeId); if (exchangeOrderList != null && !exchangeOrderList.isEmpty())
					 * exchangeOrders.addAll(exchangeOrderList); } catch (Throwable e) { log.error("getOrders: called from class " +
					 * Thread.currentThread().getStackTrace()[2] + " unable to get order status from trader service " + tradePollingService.hashCode() +
					 * " order:" + openOrdersXchangeId + " " + e); continue; } }
					 */synchronized (tradePollingService) {

						exchangeOrders = tradePollingService.getOrder(openOrdersXchangeIds.toArray(new String[openOrdersXchangeIds.size()]));
					}

					//  exchangeOrders = tradePollingService.getOrder(openOrdersXchangeIds.toArray(new String[openOrdersXchangeIds.size()]));
					Boolean orderFound = false;
					if (exchangeOrders != null)
						for (Order exchangeOrder : exchangeOrders) {
							cointraderXchangeOrders.add(exchangeOrder);
							// so let's check these are in the ordermap
							for (org.cryptocoinpartners.schema.Order openOrder : cointraderOpenOrders) {

								SpecificOrder specificOpenOrder = null;
								if (openOrder instanceof SpecificOrder) {
									specificOpenOrder = (SpecificOrder) openOrder;
									if (specificOpenOrder.getRemoteKey().equals(exchangeOrder.getId())) {
										orderFound = true;
										Fill fill = createFill(exchangeOrder, specificOpenOrder);
										if (fill != null) {
											handleFillProcessing(fill);
											if (fill.getTimestamp() > lastFillTimes.get(market))
												lastFillTimes.put(market, fill.getTimestamp());
										}

										if (!adaptOrderState(exchangeOrder.getStatus()).equals(orderStateMap.get(openOrder)))
											if (adaptOrderState(exchangeOrder.getStatus()) != OrderState.FILLED
													|| adaptOrderState(exchangeOrder.getStatus()) != OrderState.PARTFILLED)
												updateOrderState(openOrder, adaptOrderState(exchangeOrder.getStatus()), true);
									}
								}
							}

						}
				} catch (NotYetImplementedForExchangeException nyie) {

					TradeHistoryParams historyParams = null;
					if (helper != null)
						historyParams = helper.getTradeHistoryParameters(pair, lastTradeTime, lastTradeId);
					if (historyParams == null)
						throw nyie;
					List<UserTrade> xchangeFills;
					synchronized (tradePollingService) {

						xchangeFills = tradePollingService.getTradeHistory(historyParams).getUserTrades();
					}
					log.trace("sorting trades by oldest first : " + xchangeFills);

					Collections.sort(xchangeFills, timeOrderIdComparator);

					log.debug("recivied urderOrders:" + xchangeFills);
					HashMap<String, List<UserTrade>> userOrdersMap = new HashMap<String, List<UserTrade>>();
					for (UserTrade trade : xchangeFills) {
						if (!userOrdersMap.containsKey(trade.getOrderId())) {
							List<UserTrade> fills = new ArrayList<UserTrade>();
							fills.add(trade);
							userOrdersMap.put(trade.getOrderId(), fills);
						}

						else
							userOrdersMap.get(trade.getOrderId()).add(trade);
					}
					log.debug("created userOrdersMap:" + userOrdersMap);
					OpenOrders openOrders;
					synchronized (tradePollingService) {
						openOrders = tradePollingService.getOpenOrders();
					}
					for (Order xchangeOrder : openOrders.getOpenOrders()) {

						xchangeOpenOrders.add(xchangeOrder);
					}
					for (org.cryptocoinpartners.schema.Order openOrder : cointraderOpenOrders) {

						SpecificOrder specificOpenOrder = null;
						if (openOrder instanceof SpecificOrder) {
							specificOpenOrder = (SpecificOrder) openOrder;
							// So let's check the fills for this order.
							boolean addTrade = false;
							if (userOrdersMap.get(specificOpenOrder.getRemoteKey()) != null) {
								for (UserTrade trade : userOrdersMap.get(specificOpenOrder.getRemoteKey())) {
									if (specificOpenOrder.getFills() == null || specificOpenOrder.getFills().isEmpty()) {
										Fill newFill = createFill(trade, specificOpenOrder);
										if (newFill != null) {
											handleFillProcessing(newFill);
											if (newFill.getTimestamp() > lastFillTimes.get(market))
												lastFillTimes.put(market, newFill.getTimestamp());
										}

									} else {
										addTrade = true;
										for (Fill fill : specificOpenOrder.getFills()) {
											if (fill.getRemoteKey().equals(trade.getId())) {
												addTrade = false;
												break;
											}
										}
										if (addTrade) {
											Fill newFill = createFill(trade, specificOpenOrder);
											if (newFill != null) {
												handleFillProcessing(newFill);
												if (newFill.getTimestamp() > lastFillTimes.get(market))
													lastFillTimes.put(market, newFill.getTimestamp());

											}
											//need to set state to filly filled somehow if the trade is filled.
										}

									}
									// the amount filled is not the same as the order quanity, so we need to assume if the xchange Order ID is not in the openOrderId list it is fully fillled and we have new fills for it.
									// else it could have been canclled not fully filled.

								}
							}

							//lets also check if cointrader knows about any open orders
							boolean isOpen = false;
							for (Order xchangeOrder : openOrders.getOpenOrders()) {
								if (specificOpenOrder != null && specificOpenOrder.getRemoteKey().equals(xchangeOrder.getId())) {

									if (specificOpenOrder.getVolume().asBigDecimal().compareTo(xchangeOrder.getOriginalAmount()) != 0) {
										long updateVolumeCount = DiscreteAmount.roundedCountForBasis(xchangeOrder.getOriginalAmount(), market.getVolumeBasis());
										specificOpenOrder.setVolumeCount(updateVolumeCount);
									}
									cointraderXchangeOrders.add(xchangeOrder);
									isOpen = true;
								}
							}
							if (!isOpen && specificOpenOrder.hasFills())
								updateOrderState(specificOpenOrder, OrderState.FILLED, true);
						}
					}
				}

				//We need to update the orders to fully filled.

				/*
				 * trade.get
				 * @param type The trade type (BID side or ASK side)
				 * @param tradableAmount The depth of this trade
				 * @param currencyPair The exchange identifier (e.g. "BTC/USD")
				 * @param price The price (either the bid or the ask)
				 * @param timestamp The timestamp of the trade
				 * @param id The id of the trade
				 * @param orderId The id of the order responsible for execution of this trade
				 * @param feeAmount The fee that was charged by the exchange for this trade
				 * @param feeCurrency The symbol of the currency in which the fee was charged
				 *///   String myorderId = trade.getOrderId();

			}

			//   exchangeOrders = tradePollingService.getTradeHistory(historyParams);

			// if we have any order that are open at exchange but not known to cointrader, raise unknown order execption i.e orders in xchangeOpenOrders but not in cointraderXchangeOrders
			//cointraderXchangeOrders is exchange orders that cointrader knows about
			//unknownOrders are exchnge order we don't know.
			//all xchange open orders that are not in cointrader.
			SetView<Order> unknownOrders = Sets.difference(xchangeOpenOrders, cointraderXchangeOrders);
			if (!ignoreUnknownOrders) {
				for (Order unknowOrder : unknownOrders) {
					// we are going to log it and cancel them, but they might have been filled!
					//let's create a specifc order for them
					Collection<Portfolio> portfolios = context.getInjector().getInstance(PortfolioService.class).getPortfolios();
					Portfolio lastPortfolio = null;
					for (Portfolio porfolio : portfolios)
						for (Tradeable tradeable : porfolio.getMarkets())
							if (!tradeable.isSynthetic()) {
								Market portfolioMarket = (Market) tradeable;
								if (portfolioMarket.equals(market))
									lastPortfolio = porfolio;
							}
					if (lastPortfolio == null)
						continue;
					SpecificOrder specificOrder = specificOrderFactory.create(unknowOrder, market, lastPortfolio);

					// add the order to the state cache as placed
					specificOrder.persit();
					updateOrderState(specificOrder, OrderState.PLACED, true);
					// then cancel the order to check if it is filled.
					try {
						if (cancelUnknownOrders && handleCancelSpecificOrder(specificOrder))

							log.warn(this.getClass().getSimpleName() + ":getOrders - Cancelled Unkown order" + specificOrder);
					} catch (Throwable e) {
						// TODO Auto-generated catch block
						log.error("getOrders: called from class " + Thread.currentThread().getStackTrace()[2] + " unable to cancel order:" + specificOrder
								+ " " + e);
						throw new OrderNotFoundException("Unknown exchnage order " + unknowOrder);
					}

				}
			}
			tradeFailureCount = 0;
			return;
		}

		catch (Exception | Error e) {

			tradeFailureCount++;
			log.error(this.getClass().getSimpleName() + ":getOrders unable to get orders for market  " + market + " pair " + pair + ".  Failure "
					+ tradeFailureCount + " of " + restartCount + ". Full Stack Trace: " + e);
			if (restartCount != 0 && tradeFailureCount >= restartCount) {
				//try {
				//  if (rateLimiter.getRunnables() == null || rateLimiter.getRunnables().isEmpty() || rateLimiter.remove(this)) {

				log.error(this.getClass().getSimpleName() + ":getOrders unable to get orders for " + market + " pair " + pair + " for " + tradeFailureCount
						+ " of " + restartCount + " time. Resetting Trade Service Connection.");
				org.knowm.xchange.Exchange xchangeExchange = XchangeUtil.resetExchange(coinTraderExchange);
				//requeried for nonces to sync to nearest minute
				// Thread.sleep(1000);
				// dataService = xchangeExchange.getPollingMarketDataService();
				tradeFailureCount = 0;
				throw e;
				//}

			}
			return;
		}

	}

	//  FetchOrdersRunnable.

	private class FetchOrdersRunnable implements Runnable {

		private final Helper helper;
		DateFormat dateFormat = new SimpleDateFormat("ddMMyy");
		private boolean firstRun;

		public FetchOrdersRunnable(Context context, Market market, RateLimiter rateLimiter, Exchange coinTraderExchange, int restartCount,
				@Nullable Helper helper) {
			this.context = context;
			this.market = market;
			this.rateLimiter = rateLimiter;
			this.coinTraderExchange = coinTraderExchange;
			//   this.tradeService = tradeService;
			this.helper = helper;
			this.prompt = market.getListing().getPrompt();
			pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
			contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());
			this.restartCount = restartCount;
			lastTradeTime = 0;
			lastTradeId = 0;
			this.firstRun = true;
			;
			// EntityManager entityManager = PersistUtil.createEntityManager();

		}

		public FetchOrdersRunnable(Context context, Market market, Exchange coinTraderExchange) {
			this.context = context;
			this.market = market;
			this.rateLimiter = null;
			this.coinTraderExchange = coinTraderExchange;
			//   this.tradeService = tradeService;
			this.helper = XchangeUtil.getHelperForExchange(coinTraderExchange);
			this.prompt = market.getListing().getPrompt();
			pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
			contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());
			this.restartCount = 0;
			lastTradeTime = 0;
			lastTradeId = 0;
			resubmitable = false;
			this.firstRun = false;
			;
			// EntityManager entityManager = PersistUtil.createEntityManager();

		}

		@Override
		public void run() {
			try {
				if (resubmitable)
					rateLimiter.execute(this); // requeue in case we die!
				if (!getTradingEnabled())
					return;
				if (lastFillTimes.get(market) == null || lastFillTimes.get(market) == 0 || lastFillTimes.get(market) == null || lastFillTimes.get(market) == 0) {
					try {

						List<org.cryptocoinpartners.schema.Fill> results = EM.queryList(org.cryptocoinpartners.schema.Fill.class,
								"select f from Fill f where market=?1 and time=(select max(time) from Fill where market=?1)", market);

						for (org.cryptocoinpartners.schema.Fill fill : results) {
							if (fill.getTime().getMillis() > lastFillTimes.get(market))
								lastFillTimes.put(market, fill.getTime().getMillis());

						}
					} catch (Exception | Error e) {
						log.error(this.getClass().getSimpleName() + ":getOrders Unabel to query last fill time", e);
					}
				}

				getOrders(helper, market, pair, lastFillTimes.get(market), lastTradeId, contract, firstRun, coinTraderExchange, restartCount);
			} catch (Throwable e) {
				log.error(this.getClass().getSimpleName() + ":run. Unable to retrive order statuses for market:" + market);
				//Thread.currentThread().
				// throw e;
				//Thread.currentThread().interrupt();
				//throw e;

			} finally {
				// getTradesNext = !getTradesNext;
				firstRun = false;
				// return "Success";

				// run again. requeue

			}
		}

		/*
		 * protected Fill createFill(org.knowm.xchange.dto.Order exchangeOrder, SpecificOrder order) { Fill fill = null; // new
		 * DiscreteAmount(DiscreteAmount.roundedCountForBasis(stopPrice.asBigDecimal(), fill.getMarket().getPriceBasis()), fill //
		 * .getMarket().getPriceBasis()); DiscreteAmount exchangeVolume = new
		 * DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getTradableAmount(), order.getMarket() .getVolumeBasis()),
		 * order.getMarket().getVolumeBasis()); // DiscreteAmount exchnageVolume =
		 * DecimalAmount.of(exchangeOrder.getTradableAmount()).toBasis(order.getMarket().getVolumeBasis(), Remainder.DISCARD); DiscreteAmount
		 * exchangeFilledVolume = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getCumulativeAmount(), order.getMarket()
		 * .getVolumeBasis()), order.getMarket().getVolumeBasis()); DiscreteAmount exchangeUnfilledVolume = (DiscreteAmount)
		 * exchangeVolume.minus(exchangeFilledVolume); DecimalAmount averagePrice = new DecimalAmount(exchangeOrder.getAveragePrice()); Amount fillVolume
		 * = (order.getUnfilledVolume().minus(exchangeUnfilledVolume)); // new
		 * DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getAveragePrice(), order.getMarket().getPriceBasis()); DecimalAmount fillPrice
		 * = DecimalAmount.ZERO; //exchangeOrder.getAveragePrice(); //order.getAverageFillPrice() if (!fillVolume.isZero()) { fillPrice =
		 * ((exchangeFilledVolume.times(averagePrice, Remainder.ROUND_EVEN)).minus((order.getVolume().minus(order.getUnfilledVolume()).times(
		 * order.getAverageFillPrice(), Remainder.ROUND_EVEN)))).divide(fillVolume.asBigDecimal(), Remainder.ROUND_EVEN); fill = fillFactory.create(order,
		 * context.getTime(), context.getTime(), order.getMarket(), fillPrice.toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount(),
		 * fillVolume.toBasis(order.getMarket().getVolumeBasis(), Remainder.ROUND_EVEN).getCount(),
		 * Long.toString(exchangeOrder.getTimestamp().getTime())); // this.priceCount = DiscreteAmount.roundedCountForBasis(fillPrice,
		 * market.getPriceBasis()); } return fill; // -(order.getAverageFillPrice().times(order.getVolume().minus(order.getUnfilledVolume()),
		 * Remainder.ROUND_EVEN)); // DiscreteAmount volume; // If the unfilled volume of specifcOrder > tradedAmount-Cumalitve quanity, create fill //
		 * fill volume= (specficOrder.unfiledVolume) - (xchangeOrder.tradableAmount - xchangeOrder.cumlativeQuanity) // price =
		 * (exchangeFilledVolumeCount*) //Case 2 //Specfic Order is created after the xchange order is filled or part filled // //
		 * specificOpenOrder.getVolume().compareTo(o)(exchnageVolume)!=0) // if (order.getUnfilledVolume().compareTo(exchnageUnfilledVolume) == 0 ||
		 * order.getVolume().compareTo(exchnageVolume) != 0) { // Amount fillVolume = exchnageUnfilledVolume.minus(order.getUnfilledVolume()); // Fill
		 * fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(), order.getPriceCount(), fillVolume, //
		 * exchangeOrder.getTimestamp().getTime()); //} // volume = exchnageUnfilledVolume; // we need to create a fill //return null; }
		 */

		// private final Book.Builder bookBuilder = new Book.Builder();
		private final boolean getTradesNext = true;
		private final RateLimiter rateLimiter;
		private final Exchange coinTraderExchange;
		private final int restartCount;
		private final int tradeFailureCount = 0;
		boolean resubmitable = true;
		private final Context context;
		private final Market market;
		private final CurrencyPair pair;
		private final FuturesContract contract;
		private final long lastTradeTime;
		private final Prompt prompt;
		private final long lastTradeId;
	}

	protected OrderState adaptOrderState(OrderStatus state) {
		switch (state) {

			case PENDING_NEW:
				return OrderState.NEW;
			case NEW:
				return OrderState.PLACED;
			case PARTIALLY_FILLED:
				return OrderState.PARTFILLED;
			case FILLED:
				return OrderState.FILLED;

			case CANCELED:
				return OrderState.CANCELLED;

			case PENDING_REPLACE:
				return OrderState.NEW;
			case REPLACED:
				return OrderState.PLACED;
			case STOPPED:
				return OrderState.TRIGGER;
			case REJECTED:
				return OrderState.REJECTED;
			case EXPIRED:

				return OrderState.EXPIRED;

			default:
				return OrderState.PLACED;
		}
	}

	protected Fill createFill(org.knowm.xchange.dto.Order exchangeOrder, SpecificOrder order) {
		Fill fill = null;
		// new DiscreteAmount(DiscreteAmount.roundedCountForBasis(stopPrice.asBigDecimal(), fill.getMarket().getPriceBasis()), fill
		//      .getMarket().getPriceBasis());
		DiscreteAmount exchangeVolume = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getOriginalAmount(), order.getMarket()
				.getVolumeBasis()), order.getMarket().getVolumeBasis());
		// DiscreteAmount exchnageVolume = DecimalAmount.of(exchangeOrder.getTradableAmount()).toBasis(order.getMarket().getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount exchangeFilledVolume = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getCumulativeAmount(), order.getMarket()
				.getVolumeBasis()), order.getMarket().getVolumeBasis());
		DiscreteAmount exchangeUnfilledVolume = (DiscreteAmount) exchangeVolume.minus(exchangeFilledVolume);
		DecimalAmount averagePrice = new DecimalAmount(exchangeOrder.getAveragePrice());

		Amount fillVolume = (order.isAsk()) ? (order.getUnfilledVolume().abs().minus(exchangeUnfilledVolume)).negate() : (order.getUnfilledVolume().abs()
				.minus(exchangeUnfilledVolume));
		//  if (order.isAsk())

		//  new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getAveragePrice(), order.getMarket().getPriceBasis());

		DecimalAmount fillPrice = DecimalAmount.ZERO;
		//exchangeOrder.getAveragePrice();
		//order.getAverageFillPrice()
		if (!fillVolume.isZero()) {
			fillPrice = ((exchangeFilledVolume.times(averagePrice, Remainder.ROUND_EVEN)).minus((order.getVolume().abs().minus(order.getUnfilledVolume().abs())
					.times(order.getAverageFillPrice(), Remainder.ROUND_EVEN)))).divide(fillVolume.abs().asBigDecimal(), Remainder.ROUND_EVEN);
			fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(),
					fillPrice.toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount(),
					fillVolume.toBasis(order.getMarket().getVolumeBasis(), Remainder.ROUND_EVEN).getCount(),
					Long.toString(exchangeOrder.getTimestamp().getTime()));

			// this.priceCount = DiscreteAmount.roundedCountForBasis(fillPrice, market.getPriceBasis());

		}

		return fill;
		// -(order.getAverageFillPrice().times(order.getVolume().minus(order.getUnfilledVolume()), Remainder.ROUND_EVEN));
		// DiscreteAmount volume;
		// If the unfilled volume of specifcOrder > tradedAmount-Cumalitve quanity, create fill
		// fill volume= (specficOrder.unfiledVolume) - (xchangeOrder.tradableAmount - xchangeOrder.cumlativeQuanity)

		//  price = (exchangeFilledVolumeCount*)

		//Case 2
		//Specfic Order is created after the xchange order is filled or part filled

		//     // specificOpenOrder.getVolume().compareTo(o)(exchnageVolume)!=0)
		//   if (order.getUnfilledVolume().compareTo(exchnageUnfilledVolume) == 0 || order.getVolume().compareTo(exchnageVolume) != 0) {
		//     Amount fillVolume = exchnageUnfilledVolume.minus(order.getUnfilledVolume());

		// Fill fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(), order.getPriceCount(), fillVolume,
		//       exchangeOrder.getTimestamp().getTime());

		//}
		// volume = exchnageUnfilledVolume;
		// we need to create a fill 
		//return null;

	}

	protected Fill createFill(org.knowm.xchange.dto.trade.UserTrade exchangeTrade, SpecificOrder order) {
		Fill fill = null;

		if (exchangeTrade.getOriginalAmount().compareTo(BigDecimal.ZERO) != 0) {

			long volume = exchangeTrade.getType().equals(OrderType.ASK) ? DiscreteAmount.roundedCountForBasis(exchangeTrade.getOriginalAmount(), order
					.getMarket().getVolumeBasis())
					* -1 : DiscreteAmount.roundedCountForBasis(exchangeTrade.getOriginalAmount(), order.getMarket().getVolumeBasis());
			fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(),
					DiscreteAmount.roundedCountForBasis(exchangeTrade.getPrice(), order.getMarket().getPriceBasis()), volume, exchangeTrade.getId());

			// this.priceCount = DiscreteAmount.roundedCountForBasis(fillPrice, market.getPriceBasis());

		}

		return fill;
	}

	// -(order.getAve
	protected OrderStatus adaptOrderState(OrderState state) {

		switch (state) {
			case NEW:
				return OrderStatus.PENDING_NEW;
			case PLACED:
				return OrderStatus.NEW;
			case FILLED:
				return OrderStatus.FILLED;
			case CANCELLING:
				return OrderStatus.PENDING_CANCEL;
			case CANCELLED:
				return OrderStatus.CANCELED;
			case REJECTED:
				return OrderStatus.REJECTED;
			case EXPIRED:
				return OrderStatus.EXPIRED;

			default:
				return OrderStatus.NEW;
		}

	}

	protected static final Collection<SpecificOrder> pendingOrders = new ArrayList<SpecificOrder>();
	protected static final HashBiMap<org.knowm.xchange.dto.Order, org.knowm.xchange.dto.Order> externalOrderMap = HashBiMap.create();

	@Override
	public void init() {
		super.init();
		// do we need to get teh status of orders from the xchange?
		// TODO Auto-generated method stub

	}

	private static final Comparator<UserTrade> timeOrderIdComparator = new Comparator<UserTrade>() {
		@Override
		public int compare(UserTrade event, UserTrade event2) {
			int sComp = event.getTimestamp().compareTo(event2.getTimestamp());
			if (sComp != 0) {
				return sComp;
			} else {
				return (event.getId().compareTo(event2.getId()));

			}
		}
	};

	@SuppressWarnings("finally")
	@Override
	protected boolean cancelSpecificOrder(SpecificOrder order) throws Throwable {
		org.knowm.xchange.Exchange exchange;
		exchange = XchangeUtil.getExchangeForMarket(order.getMarket().getExchange());
		TradeService tradeService = exchange.getTradeService();

		boolean deleted = false;
		if (orderStateMap.get(order).isNew()) {
			log.error("Cancelling new order " + orderStateMap.get(order) + " :" + order);
			updateOrderState(order, OrderState.CANCELLED, true);

			deleted = true;

			return deleted;
		} else if (!orderStateMap.get(order).isWorking()) {
			log.error("Unable to cancel order as is " + orderStateMap.get(order) + " :" + order);
			deleted = true;
			return deleted;
		}
		try {

			synchronized (tradeService) {
				tradeService.cancelOrder(order.getRemoteKey());
			}

			/*
			 * this.context = context; this.market = market; this.rateLimiter = null; this.coinTraderExchange = coinTraderExchange; // this.tradeService =
			 * tradeService; this.helper = exchangeHelpers.get(coinTraderExchange); this.prompt = market.getListing().getPrompt(); pair =
			 * XchangeUtil.getCurrencyPairForListing(market.getListing()); FuturesContract contract = order.getMarket().getListing().getPrompt() == null ?
			 * null : XchangeUtil.getContractForListing(order.getMarket().getListing()); this.restartCount = 0; lastTradeTime = 0; lastTradeId = 0;
			 * resubmitable = false; this.firstRun = false;
			 */
			CurrencyPair pair = XchangeUtil.getCurrencyPairForListing(order.getMarket().getListing());
			FuturesContract contract = order.getMarket().getListing().getPrompt() == null ? null : XchangeUtil.getContractForListing(order.getMarket()
					.getListing());

			// lets kick of a new thread to check that it is canclled we need to wait for this to complete before returning
			getOrders(XchangeUtil.getHelperForExchange(order.getMarket().getExchange()), order.getMarket(), pair, 0, 0, contract, false, order.getMarket()
					.getExchange(), 0);
			if (orderStateMap.get(order) != null && orderStateMap.get(order).equals(OrderState.CANCELLED))
				deleted = true;
			// } else {

			/// if (!pendingOrders.contains(order)) {
			//TODO check if order is on exchange
			// log.error("Unable to cancel order as not present in exchange order book. Order:" + order);
			// deleted = false;
			// }

			// }
			return deleted;
		} catch (HttpStatusIOException hse) {
			log.error(this.getClass().getSimpleName() + "cancelSpecificOrder: Unable to cancel order :" + order + "with trade service"
					+ tradeService.hashCode() + " due to " + hse + ". Resetting Trade Service Connection.");
			org.knowm.xchange.Exchange xchangeExchange = XchangeUtil.resetExchange(order.getMarket().getExchange());
			return deleted;

		} catch (Error | Exception e) {
			log.error("Unable to cancel order :" + order + "with trade service" + tradeService.hashCode() + " due to " + e);
			throw e;
		}

	}

	@Override
	protected OrderState getOrderStateFromOrderService(org.cryptocoinpartners.schema.Order order) throws Throwable {
		OrderUpdate orderUpdate = null;
		// so let's get order from exchange    protected Collection<SpecificOrder> getPendingXchangeOrders(Market market, Portfolio portfolio) {
		List<String> openOrders = new ArrayList<String>();
		Boolean exchangeOrderFound = false;

		if (order instanceof SpecificOrder) {
			SpecificOrder specificOrder = (SpecificOrder) order;

			openOrders.add(specificOrder.getRemoteKey());
			Collection<Order> exchangeOrders = null;
			if (!openOrders.isEmpty()) {
				TradeService tradeService = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange()).getTradeService();
				//TODO: need to check prompts to ensure they have the full OKCOIN_THISWEEK:BTC.USD.THISWEEK not just OKCOIN_THISWEEK:BTC.USD
				try {
					synchronized (tradeService) {
						exchangeOrders = tradeService.getOrder(openOrders.toArray(new String[openOrders.size()]));
					}
					for (Order exchangeOrder : exchangeOrders) {
						if (specificOrder.getRemoteKey().equals(exchangeOrder.getId())) {
							exchangeOrderFound = true;
							return adaptOrderState(exchangeOrder.getStatus());
						}
					}

				} catch (IllegalArgumentException iae) {
					return OrderState.REJECTED;
				}

				catch (Exception | Error e) {
					log.error(this.getClass().getSimpleName() + ":getOrderStateFromOrderService unable to get order: " + specificOrder + " from market: "
							+ specificOrder.getMarket() + ".  Failure. Full Stack Trace: ", e);
					throw e;
				}
			}
		}
		if (!exchangeOrderFound) {
			// if this fails let's get it from DB
			orderUpdate = EM.namedQueryOne(OrderUpdate.class, "orderUpdate.findStateByOrder", order);

			if (orderUpdate != null)
				return orderUpdate.getState();

		}
		return null;

	}

	@Override
	public void updateWorkingOrderQuantity(org.cryptocoinpartners.schema.Order order, Amount quantity) {
		// TODO Auto-generated method stub

	}
}
