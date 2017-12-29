package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.Remainder;

/**
 * MockOrderService simulates the Filling of Orders by looking at broadcast Book data for price and volume information.
 * 
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class MockOrderService extends BaseOrderService {
	private static ExecutorService mockOrderService = Executors.newFixedThreadPool(1);
	// static Double doubleSlippage = ConfigUtil.combined().getDouble("mock.exchange.slippage", 0.02);
	private static double slippage = ConfigUtil.combined().getDouble("mock.exchange.slippage", 0.002);
	protected final Lock updateOrderBookLock = new ReentrantLock();

	// Object orderProcessingLock;

	// protected final Lock orederMatchingLock = new ReentrantLock();

	@Override
	protected void handleSpecificOrder(SpecificOrder specificOrder) {
		if (specificOrder.getStopPrice() != null) {

			reject(specificOrder, "Stop prices unsupported");
		}
		specificOrder.setEntryTime(context.getTime());
		//if we are trading the cash market, let's take off the fees.
		if (specificOrder.getMarket().getListing().getPrompt() == null) {
			long netAmountCount = specificOrder.getVolume().minus(specificOrder.getForcastedCommission().abs())
					.toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.ROUND_UP).getCount();
			specificOrder.setVolumeCount(netAmountCount);
		}
		addOrder(specificOrder);

		updateOrderState(specificOrder, OrderState.PLACED, true);
		updateBook((quotes.getLastBook(specificOrder.getMarket()) == null ? quotes.getLastTrade(specificOrder.getMarket()) : quotes.getLastBook(specificOrder
				.getMarket())));
		specificOrder.merge();

		//TODO when placing the order it is on the same listener so it needs to be routed.

	}

	private class updateBookRunnable implements Runnable {
		private final Event event;

		// protected Logger log;

		public updateBookRunnable(Event event) {
			this.event = event;

		}

		@Override
		public void run() {
			updateBook(event);

		}
	}

	@SuppressWarnings("ConstantConditions")
	// @When("@Priority(9) select * from Book(Book.market in (TrendStrategy.getMarkets()), TrendStrategy.getMarketAllocation(Book.market)>0, Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0 )")
	// @When("@Priority(9) select * from Book")
	@When("@Priority(8) @Audit select * from LastBookWindow(market.synthetic=false)")
	private void handleBook(Book b) {
		log.trace("handleBook: Book Recieved: " + b);
		//mockOrderService.submit(new updateBookRunnable(b));
		updateBook(b);
		//mockOrderService.submit(new updateBookRunnable(b));

	}

	@SuppressWarnings("ConstantConditions")
	@When("@Priority(8) @Audit select * from LastTradeWindow(market.synthetic=false)")
	private void handleTrade(Trade t) {
		log.trace("handleTrade: Book Recieved: " + t);
		updateBook(t);
		//mockOrderService.submit(new updateBookRunnable(t));
	}

	@SuppressWarnings("ConstantConditions")
	private void updateBook(Event event) {
		//TODO sync on even rather than bids/asks.
		if (event == null)
			return;
		Book b = null;
		Trade t = null;
		Tradeable market = null;
		if (event instanceof Book) {
			b = (Book) event;
			if (b.getMarket().isSynthetic())
				return;
			market = b.getMarket();
		}
		if (event instanceof Trade) {
			t = (Trade) event;
			if (t.getMarket().isSynthetic())
				return;
			market = t.getMarket();
		}
		if (market == null)
			return;

		//   log.trace(this.getClass().getSimpleName() + " : updateBook to called from stack " + Thread.currentThread().getStackTrace()[2]);
		synchronized (pendingOrders.get(market) == null ? this : pendingOrders.get(market)) {

			List<Offer> asks = new ArrayList<>();

			List<Offer> bids = new ArrayList<>();
			if (b != null) {

				asks = b.getAsks();
				bids = b.getBids();
			}

			if (t != null) {
				t = (Trade) event;

				//if Trade is a sell then it must have big the ask
				if (t.getVolume().isNegative()) {
					Offer bestBid = new Offer(market, t.getTime(), t.getTimeReceived(), t.getPrice().getCount(), t.getVolume().negate().getCount());
					asks.add(bestBid);
				} else {
					Offer bestAsk = new Offer(market, t.getTime(), t.getTimeReceived(), t.getPrice().getCount(), t.getVolume().negate().getCount());
					bids.add(bestAsk);

				}
			}

			List<Fill> fills = Collections.synchronizedList(new ArrayList<Fill>());

			// todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
			// we will loop over the orders that are looking to buy against the sell orders from the book i.e. the asks.
			if (pendingOrders != null && pendingOrders.get(market) != null && pendingOrders.get(market).get(TransactionType.BUY) != null) {
				try {

					synchronized (pendingOrders.get(market).get(TransactionType.BUY)) {
						Set<SpecificOrder> buyOrdersToRemove = new HashSet<SpecificOrder>();

						BIDORDERSLOOP: for (SpecificOrder order : pendingOrders.get(market).get(TransactionType.BUY)) {
							synchronized (order) {
								if (bids == null || bids.isEmpty())
									break;
								if (order.getUnfilledVolumeCount() == 0) {
									buyOrdersToRemove.add(order);

									continue;
								}
								if (order.getMarket().equals(market) && (order.getTimestamp() <= event.getTimestamp())) {
									log.trace(this.getClass().getSimpleName() + ":UpdateBook - determining fills for buy order " + order.getId()
											+ " with working volume " + order.getUnfilledVolumeCount());
									synchronized (event) {
										Set<Offer> asksToRemove = new HashSet<Offer>();
										synchronized (asks) {
											ASKSLOOP: for (Offer ask : asks) {
												if (ask == null
														|| (order.getLimitPrice() != null && ask != null && order.getLimitPrice().getCount() < ask
																.getPriceCount())) {
													log.trace(this.getClass().getSimpleName() + ":UpdateBook - ask price " + ask + " greater than limit price "
															+ order.getLimitPrice() + " for order " + order.getId());
													break BIDORDERSLOOP;
												}
												if (t != null) {
													log.debug("filled by a trade");
												}
												long buyFillVolume = Math.min(Math.abs(ask.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount()));
												DiscreteAmount vol = ask.getVolume();
												Long count = ask.getVolumeCount();
												long volbycount = ask.getVolume().getCount();
												count = ask.getVolumeCount();
												if (buyFillVolume == 0) {
													log.trace(this.getClass().getSimpleName() + ":UpdateBook fill voume zero for ask" + ask + " and order "
															+ order.getId());
													continue ASKSLOOP;
												}
												long slippageDiff = Math.round(ask.getPriceCount() * slippage);
												long fillPriceCount = Math.min(order.getLimitPrice().getCount(), (ask.getPriceCount() + slippageDiff));
												log.debug(this.getClass().getSimpleName() + ":updateBook - Creating fill with ask " + ask);
												Fill fill = fillFactory.create(order, ask.getTime(), ask.getTime(), order.getMarket(), fillPriceCount,
														buyFillVolume, Long.toString(ask.getTime().getMillis()));
												logFill(order, ask, fill);
												log.debug(this.getClass().getSimpleName() + ":UpdateBook - set askVolume " + ask.getVolumeCount() + " to "
														+ (ask.getVolumeCount() + buyFillVolume));
												ask.setVolumeCount(ask.getVolumeCount() + buyFillVolume);
												if (ask.getVolumeCount() == 0)
													asksToRemove.add(ask);

												if (order.getUnfilledVolumeCount() == 0) {
													buyOrdersToRemove.add(order);

												}
												if (fill.getVolume() == null || (fill.getVolume() != null && fill.getVolume().isZero()))
													log.debug("fill " + fill.getId() + " zero lots " + (order.getUnfilledVolumeCount()));
												if (fill.getVolume().abs().compareTo(order.getVolume().abs()) > 0)
													log.debug("overfilled " + fill.getId() + " " + (order.getUnfilledVolumeCount()));
												handleFillProcessing(fill);
												if (order.getUnfilledVolumeCount() == 0) {
													continue BIDORDERSLOOP;
												}
											}
											asks.removeAll(asksToRemove);
										}
									}
								}
							}
						}
						pendingOrders.get(market).get(TransactionType.BUY).removeAll(buyOrdersToRemove);
					}
				} catch (Exception e) {
					log.error(this.getClass().getSimpleName() + ": updateBook - Unable to itterate over buy mock order book " + " stack trace: ", e);
				}
			}

			if (pendingOrders != null && pendingOrders.get(market) != null && pendingOrders.get(market).get(TransactionType.SELL) != null) {
				try {
					synchronized (pendingOrders.get(market).get(TransactionType.SELL)) {
						Set<SpecificOrder> sellOrdersToRemove = new HashSet<SpecificOrder>();

						ASKORDERSLOOP: for (SpecificOrder order : pendingOrders.get(market).get(TransactionType.SELL)) {
							synchronized (order) {
								if (bids == null || bids.isEmpty())
									break;
								if (order.getUnfilledVolumeCount() == 0) {
									sellOrdersToRemove.add(order);
									continue ASKORDERSLOOP;
								}
								if (order.getMarket().equals(market) && (order.getTimestamp() <= event.getTimestamp())) {
									log.trace(this.getClass().getSimpleName() + ":UpdateBook - determining fills for sell order " + order.getId()
											+ " with working volume " + order.getUnfilledVolumeCount());
									synchronized (event) {
										synchronized (bids) {
											Set<Offer> bidsToRemove = new HashSet<Offer>();
											BIDSLOOP: for (Offer bid : bids) {
												if (bid == null
														|| (order.getLimitPrice() != null && bid != null && order.getLimitPrice().getCount() > bid
																.getPriceCount())) {
													log.trace(this.getClass().getSimpleName() + ":UpdateBook - bid price " + bid + " greater than limit price "
															+ order.getLimitPrice() + " for order " + order.getId());
													break ASKORDERSLOOP;
												}
												long askFillVolume = -Math.min(Math.abs(bid.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount()));
												if (askFillVolume == 0) {
													log.trace(this.getClass().getSimpleName() + ":UpdateBook fill volume zero for bid" + bid + " and order "
															+ order.getId());
													if (bid.getVolumeCount() == 0)
														bidsToRemove.add(bid);
													continue BIDSLOOP;
												}
												long slippageDiff = Math.round(bid.getPriceCount() * slippage);
												long fillPriceCount = Math.max(order.getLimitPrice().getCount(), (bid.getPriceCount() - slippageDiff));
												log.debug(this.getClass().getSimpleName() + ":updateBook - Creating fill with bid " + bid);
												Fill fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(), fillPriceCount,
														askFillVolume, Long.toString(bid.getTime().getMillis()));
												logFill(order, bid, fill);
												log.debug(this.getClass().getSimpleName() + ":UpdateBook - set bidVolume " + bid.getVolumeCount() + " to "
														+ (bid.getVolumeCount() - askFillVolume));
												bid.setVolumeCount(bid.getVolumeCount() - askFillVolume);
												if (bid.getVolumeCount() == 0)
													bidsToRemove.add(bid);
												if (order.getUnfilledVolumeCount() == 0) {
													sellOrdersToRemove.add(order);
												}
												if (fill.getVolume() == null || (fill.getVolume() != null && fill.getVolume().isZero()))
													log.debug("fill zero lots " + fill.getId() + " " + (order.getUnfilledVolumeCount()));
												if (fill.getVolume().abs().compareTo(order.getVolume().abs()) > 0)
													log.debug("overfilled " + fill.getId());
												handleFillProcessing(fill);
												if (order.getUnfilledVolumeCount() == 0) {
													continue ASKORDERSLOOP;
												}
											}
											bids.removeAll(bidsToRemove);
										}
									}
								}
							}
						}
						pendingOrders.get(market).get(TransactionType.SELL).removeAll(sellOrdersToRemove);
					}
				} catch (Exception e) {
					log.error(this.getClass().getSimpleName() + ": addOrder - Unable to itterate over sell mock order book " + " stack trace: ", e);
				}
			}
		}
	}

	@SuppressWarnings("finally")
	@Override
	protected boolean cancelSpecificOrder(SpecificOrder order) {
		boolean deleted = false;
		if (orderStateMap.get(order).isNew()) {
			log.error("Cancelling new order " + orderStateMap.get(order) + " :" + order);
			updateOrderState(order, OrderState.CANCELLED, true);

			deleted = true;

			return deleted;
		}

		else if (!orderStateMap.get(order).isOpen()) {
			log.error("Unable to cancel order as is " + orderStateMap.get(order) + " :" + order.getId());
			deleted = true;
			return deleted;

		}

		try {
			if (pendingOrders == null || pendingOrders.get(order.getMarket()) == null
					|| pendingOrders.get(order.getMarket()).get(order.getTransactionType()) == null)
				return deleted;
			log.trace(this.getClass().getSimpleName() + ":cancelSpecificOrder - removing order(" + order.hashCode() + ") " + order + " from orderbook ");

			// synchronized (pendingOrders.get(order.getMarket()).get(order.getTransactionType())) {
			//   } catch (Exception e) {
			//     log.error(this.getClass().getSimpleName() + ": addOrder - Unable to itterate over order book " + pendingOrders.get(market).get(TransactionType.BUY) + " stack trace: ", e);
			// } finally {
			//   updateOrderBookLock.unlock();

			//}  
			//      log.debug(this.getClass().getSimpleName() + ":cancelSpecificOrder Locking updateOrderBookLock");
			//    updateOrderBookLock.lock();

			synchronized (pendingOrders.get(order.getMarket()).get(order.getTransactionType())) {
				if (pendingOrders.get(order.getMarket()).get(order.getTransactionType()).remove(order)) {
					log.debug(this.getClass().getSimpleName() + ":cancelSpecificOrder - removed order(" + order.hashCode() + ") " + order + " from orderbook ");

					updateOrderState(order, OrderState.CANCELLED, true);

					deleted = true;

				} else {
					//       if (!pendingOrders.get(order.getMarket()).get(order.getTransactionType()).contains(order)) {
					log.error("Unable to cancel order as not present in mock order book. Order:" + order + " order book ");
					updateOrderState(order, OrderState.REJECTED, true);
					deleted = true;
				}
			}

			//  }
			//   }
			// }
		} catch (Error | Exception e) {
			log.error("Unable to cancel order :" + order + ". full stack trace", e);

		} finally {
			//log.debug(this.getClass().getSimpleName() + ":cancelSpecificOrder unlocking updateOrderBookLock");

			//  updateOrderBookLock.unlock();

			return deleted;
		}

	}

	private synchronized void addOrder(SpecificOrder order) {
		//   targetDiscrete = (DiscreteAmount) (limitPrice.minus(new DiscreteAmount((long) (targetPrice), market.getPriceBasis()))).abs();
		//   targetDiscrete = (DiscreteAmount) (limitPrice.minus(new DiscreteAmount((long) (targetPrice), market.getPriceBasis()))).abs();

		try {
			//   log.debug(this.getClass().getSimpleName() + ":addOrder Locking updateOrderBookLock");

			//   updateOrderBookLock.lock();

			if (pendingOrders.get(order.getMarket()) == null
					|| (pendingOrders.get(order.getMarket()) != null && pendingOrders.get(order.getMarket()).isEmpty())) {
				ConcurrentSkipListSet<SpecificOrder> orders = new ConcurrentSkipListSet<SpecificOrder>(order.isBid() ? descendingPriceComparator
						: ascendingPriceComparator);

				//  ArrayList<SpecificOrder> orders = new ArrayList<SpecificOrder>();
				orders.add(order);
				Map<TransactionType, ConcurrentSkipListSet<SpecificOrder>> orderBook = new ConcurrentHashMap<TransactionType, ConcurrentSkipListSet<SpecificOrder>>();
				orderBook.put(order.getTransactionType(), orders);
				pendingOrders.put(order.getMarket(), orderBook);
				log.trace(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + "): " + order.getId() + " added to mock order book ");
				return;

			} else if (pendingOrders.get(order.getMarket()).get(order.getTransactionType()) == null
					|| (pendingOrders.get(order.getMarket()).get(order.getTransactionType()) != null && pendingOrders.get(order.getMarket())
							.get(order.getTransactionType()).isEmpty())) {
				ConcurrentSkipListSet<SpecificOrder> orders = new ConcurrentSkipListSet<SpecificOrder>(order.isBid() ? descendingPriceComparator
						: ascendingPriceComparator);
				orders.add(order);
				pendingOrders.get(order.getMarket()).put(order.getTransactionType(), orders);
				log.trace(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + "): " + order.getId() + " added to mock order book ");
				return;

			} else {
				//      synchronized (pendingOrders.get(order.getMarket()).get(order.getTransactionType())) {
				if (pendingOrders.get(order.getMarket()).get(order.getTransactionType()).add(order)) {
					//orderBook.add(order);
					// Comparator<SpecificOrder> bookComparator = order.isBid() ? bidComparator : askComparator;

					//   Collections.sort(pendingOrders.get(order.getMarket()).get(order.getTransactionType()), order.isBid() ? bidComparator : askComparator);
					log.trace(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + "):  -" + order.getId() + " added to mock order book.");

					return;
				} else {
					log.error(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + ") -" + order.getId()
							+ " unable to add order to mock order book ");
					pendingOrders.get(order.getMarket()).get(order.getTransactionType()).add(order);
				}
				//       }

				// askComparator
			}

		} catch (Exception e) {
			log.error(this.getClass().getSimpleName() + ": addOrder - Unable to add order " + order + "stack trace: ", e);
		} finally {
			//       log.debug(this.getClass().getSimpleName() + ":addOrder Unlocking updateOrderBookLock");
			//     updateOrderBookLock.unlock();

		}
		// }
		//  }

	}

	private void logFill(SpecificOrder order, Offer offer, Fill fill) {
		//  if (log.isDebugEnabled())
		if (order != null && offer != null && fill != null)
			log.info("Mock fill of Order " + order.getId() + " with Offer " + offer + ": " + fill);
	}

	// private static Object lock = new Object();
	// private static ConcurrentHashMap<Market, ConcurrentHashMap<TransactionType, ArrayList<SpecificOrder>>> pendingOrders = new ConcurrentHashMap<Market, ConcurrentHashMap<TransactionType, ArrayList<SpecificOrder>>>();
	private static transient Map<Market, Map<TransactionType, ConcurrentSkipListSet<SpecificOrder>>> pendingOrders = new ConcurrentHashMap<Market, Map<TransactionType, ConcurrentSkipListSet<SpecificOrder>>>();

	//new ConcurrentSkipListSet<>
	//  new ConcurrentLinkedQueue<SpecificOrder>();

	// new CopyOnWriteArrayList<SpecificOrder>();

	//private QuoteService quotes;

	//  @Override

	// }

	@Override
	public void init() {
		Set<org.cryptocoinpartners.schema.Order> cointraderOpenOrders = new HashSet<org.cryptocoinpartners.schema.Order>();

		super.init();
		// Once we have all the order loaded, let's add all the open specific orders to the mock order book (pendingOrders)
		//if (stateOrderMap.get(OrderState.NEW) != null)
		//    cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.NEW));
		if (stateOrderMap.get(OrderState.PLACED) != null)
			cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PLACED));
		if (stateOrderMap.get(OrderState.PARTFILLED) != null)
			cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PARTFILLED));
		if (stateOrderMap.get(OrderState.ROUTED) != null)
			cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.ROUTED));
		if (stateOrderMap.get(OrderState.CANCELLING) != null)
			cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.CANCELLING));
		for (org.cryptocoinpartners.schema.Order openOrder : cointraderOpenOrders) {
			if (openOrder instanceof SpecificOrder)
				addOrder((SpecificOrder) openOrder);
		}
	}

	@Override
	protected OrderState getOrderStateFromOrderService(Order order) throws Throwable {
		// so let's get order from database
		log.debug(this.getClass().getSimpleName() + ":getOrderStateFromOrderService - Loading order update from DB for " + order);
		OrderUpdate orderUpdate = EM.namedQueryOne(OrderUpdate.class, "orderUpdate.findStateByOrder", order);

		log.debug(this.getClass().getSimpleName() + ":getOrderStateFromOrderService - Loaded order update " + orderUpdate);
		if (orderUpdate != null)
			return orderUpdate.getState();
		else
			return null;

	}

	@Override
	public void updateWorkingOrderQuantity(Order order, Amount quantity) {
		// TODO Auto-generated method stub

	}

}
