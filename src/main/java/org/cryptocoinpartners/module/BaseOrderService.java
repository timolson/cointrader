package org.cryptocoinpartners.module;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderBuilder;
import org.cryptocoinpartners.schema.OrderState;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.FeesUtil;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.RemainderHandler;
import org.slf4j.Logger;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class BaseOrderService implements OrderService {

	@Override
	public void placeOrder(Order order) {
		updateOrderState(order, OrderState.NEW);
		log.info("Created new order " + order);
		if (order instanceof GeneralOrder) {
			GeneralOrder generalOrder = (GeneralOrder) order;
			handleGeneralOrder(generalOrder);
		} else if (order instanceof SpecificOrder) {
			SpecificOrder specificOrder = (SpecificOrder) order;
			if (specificOrder.getStopPrice() != null) {
				handleStopOrder(specificOrder);
			} else {
				handleSpecificOrder(specificOrder);

			}
			Amount fees = FeesUtil.getExchangeFees(specificOrder);
			specificOrder.setForcastedCommission(fees);

			Transaction transaction;
			try {

				transaction = new Transaction(specificOrder);

				log.info("Created new transaction " + transaction);
				context.publish(transaction);

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	private void handleStopOrder(SpecificOrder specificOrder) {
		triggerOrders.put(specificOrder.getTimestamp(), specificOrder);
		updateOrderState(specificOrder, OrderState.TRIGGER);
		PersitOrderFill(specificOrder);
		log.info("Stop trade Entered at " + specificOrder.getStopPrice());
	}

	@Override
	public OrderState getOrderState(Order o) {
		OrderState state = orderStateMap.get(o);
		if (state == null)
			throw new IllegalStateException("Untracked order " + o);
		return state;
	}

	@When("select * from Fill")
	public void handleFill(Fill fill) {
		Order order = fill.getOrder();
		PersitOrderFill(fill);

		if (log.isInfoEnabled())
			log.info("Received Fill " + fill);
		OrderState state = orderStateMap.get(order);
		if (state == null) {
			log.warn("Untracked order " + order);
			state = OrderState.PLACED;
		}
		if (state == OrderState.NEW)
			log.warn("Fill received for Order in NEW state: skipping PLACED state");
		if (state.isOpen()) {
			OrderState newState = order.isFilled() ? OrderState.FILLED : OrderState.PARTFILLED;
			updateOrderState(order, newState);
		}
		Transaction transaction;
		try {
			transaction = new Transaction(fill);
			log.info("Created new transaction " + transaction);
			context.publish(transaction);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	protected void handleGeneralOrder(GeneralOrder generalOrder) {
		Offer offer = generalOrder.isBid() ? quotes.getBestBidForListing(generalOrder.getListing()) : quotes.getBestAskForListing(generalOrder.getListing());
		if (offer == null) {
			log.warn("No offers on the book for " + generalOrder.getListing());
			reject(generalOrder, "No recent book data for " + generalOrder.getListing() + " so GeneralOrder routing is disabled");
			return;
		}
		Market market = offer.getMarket();
		SpecificOrder specificOrder = convertGeneralOrderToSpecific(generalOrder, market);
		log.info("Routing order " + generalOrder + " to " + market.getExchange().getSymbol());
		handleSpecificOrder(specificOrder);
		PersitOrderFill(generalOrder);
		// todo reserve a Position to pay for the order
		//specificOrder.getPortfolio().reserve(specificOrder,estimateCost(specificOrder));
	}

	@SuppressWarnings("ConstantConditions")
	@When("select * from Book")
	private void handleBook(Book b) {
		List<Fill> fills = new ArrayList<>();
		Offer ask = b.getBestAsk();
		Offer bid = b.getBestBid();

		ConcurrentHashMap<Asset, Amount> balances = new ConcurrentHashMap<Asset, Amount>();
		Iterator<Long> itt = triggerOrders.keySet().iterator();
		while (itt.hasNext()) {
			Long key = itt.next();
			SpecificOrder order = triggerOrders.get(key);

			if (order.getMarket().equals(b.getMarket())) {
				if (order.isBid()) {
					if (order.getStopPrice() != null && ask.getPriceCount() >= order.getStopPrice().getCount()) {
						//convert order to limit order

						handleSpecificOrder(convertStopOrderToLimitOrder(order, bid, ask));
						triggerOrders.remove(order.getTimestamp());
						logTrigger(order, ask);
					} else if (order.getTrailingStopPrice() != null) {
						//		&& ((ask.getPriceCount() + order.getTrailingStopPrice().getCount() < (order.getStopPrice().getCount())))) {
						//current price is less than the stop price so I will update the stop price
						long stopPrice = Math.min(order.getStopPrice().getCount(), (ask.getPriceCount() + order.getTrailingStopPrice().getCount()));
						long trailingStopPrice = (long) (2 * getATR());

						order.setStopPriceCount(stopPrice);
						order.setTrailingStopPriceCount(trailingStopPrice);
					}

				}
				if (order.isAsk()) {
					if (order.getStopPrice() != null && bid.getPriceCount() <= order.getStopPrice().getCount()) {
						//Place order

						handleSpecificOrder(convertStopOrderToLimitOrder(order, bid, ask));
						triggerOrders.remove(order.getTimestamp());
						logTrigger(order, bid);
					} else if (order.getTrailingStopPrice() != null) {
						//&& ((bid.getPriceCount() + order.getTrailingStopPrice().getCount() > (order.getStopPrice().getCount())))) {
						//current price is less than the stop price so I will update the stop price
						long stopPrice = Math.max(order.getStopPrice().getCount(), (bid.getPriceCount() - order.getTrailingStopPrice().getCount()));
						long trailingStopPrice = (long) (2 * getATR());

						order.setStopPriceCount(stopPrice);
						order.setTrailingStopPriceCount(trailingStopPrice);

					}

				}
			}
		}
	}

	private void logTrigger(SpecificOrder order, Offer offer) {
		if (log.isDebugEnabled())
			log.debug("Trigger Order " + order + " with Offer " + offer + ": " + order.getStopPrice());
	}

	private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, Market market) {
		DiscreteAmount volume = generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);

		// the volume will already be negative for a sell order
		OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(generalOrder.getPortfolio()).create(generalOrder.getTime(), market, volume,
				generalOrder.getComment());

		RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
		final DecimalAmount limitPrice = generalOrder.getLimitPrice();
		if (limitPrice != null) {
			DiscreteAmount discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
			builder.withLimitPrice(discreteLimit);
		}
		final DecimalAmount stopPrice = generalOrder.getStopPrice();
		final DecimalAmount trailingStopPrice = generalOrder.getTrailingStopPrice();

		if (stopPrice != null) {
			if (trailingStopPrice != null) {
				DiscreteAmount discreteStop = stopPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
				DiscreteAmount discreteTrailingStop = trailingStopPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
				builder.withTrailingStopPrice(discreteStop, discreteTrailingStop);
			} else {
				DiscreteAmount discreteStop = stopPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
				builder.withStopPrice(discreteStop);
			}
		}

		SpecificOrder specificOrder = builder.getOrder();
		specificOrder.copyCommonOrderProperties(generalOrder);
		specificOrder.setParentOrder(generalOrder);

		return specificOrder;
	}

	private SpecificOrder convertStopOrderToLimitOrder(SpecificOrder stopOrder, Offer bid, Offer ask) {
		//DiscreteAmount volume = generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount limitPrice = stopOrder.isBid() ? ask.getPrice().increment(2) : bid.getPrice().decrement(2);

		stopOrder.removeStopPriceCount();
		stopOrder.removeTrailingStopPriceCount();
		stopOrder.setLimitPriceCount(limitPrice.getCount());
		return stopOrder;
	}

	protected void reject(Order order, String message) {
		log.warn("Order " + order + " rejected: " + message);
		updateOrderState(order, OrderState.REJECTED);
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

	protected abstract void handleSpecificOrder(SpecificOrder specificOrder);

	@Override
	public abstract Collection<SpecificOrder> getPendingOrders(Portfolio portfolio);

	@Override
	public abstract void handleCancelSpecificOrder(SpecificOrder specificOrder);

	@Override
	public abstract void handleCancelAllSpecificOrders(Portfolio portfolio);

	protected void updateOrderState(Order order, OrderState state) {
		OrderState oldState = orderStateMap.get(order);
		if (oldState == null)
			oldState = OrderState.NEW;
		orderStateMap.put(order, state);
		context.publish(new OrderUpdate(order, oldState, state));
		if (order.getParentOrder() != null)
			updateParentOrderState(order.getParentOrder(), order, state);
		//Order(order);
	}

	private void updateParentOrderState(GeneralOrder order, Order childOrder, OrderState childOrderState) {
		OrderState oldState = orderStateMap.get(order);
		switch (childOrderState) {
			case NEW:
				break;
			case TRIGGER:
				break;
			case ROUTED:
				break;
			case PLACED:
				break;
			case PARTFILLED:
				updateOrderState(order, OrderState.PARTFILLED);
				break;
			case FILLED:
				if (order.isFilled())
					updateOrderState(order, OrderState.FILLED);
				break;
			case CANCELLING:
				break;
			case CANCELLED:
				if (oldState == OrderState.CANCELLING) {
					boolean fullyCancelled = true;
					for (SpecificOrder child : order.getChildren()) {
						if (orderStateMap.get(child).isOpen()) {
							fullyCancelled = false;
							break;
						}
					}
					if (fullyCancelled)
						updateOrderState(order, OrderState.CANCELLED);
				}
				break;
			case REJECTED:
				reject(order, "Child order was rejected");
				break;
			case EXPIRED:
				if (!childOrder.getExpiration().isEqual(order.getExpiration()))
					throw new Error("Child order expirations must match parent order expirations");
				updateOrderState(order, OrderState.EXPIRED);
				break;
			default:
				log.warn("Unknown order state: " + childOrderState);
				break;
		}
	}

	protected static final void PersitOrderFill(EntityBase... entities) {

		try {
			PersistUtil.insert(entities);
		} catch (Throwable e) {
			throw new Error("Could not insert " + entities, e);
		}
	}

	// rounding depends on whether it is a buy or sell order.  we round to the "best" price
	private static final RemainderHandler sellHandler = new RemainderHandler() {
		@Override
		public RoundingMode getRoundingMode() {
			return RoundingMode.CEILING;
		}
	};

	private static final RemainderHandler buyHandler = new RemainderHandler() {
		@Override
		public RoundingMode getRoundingMode() {
			return RoundingMode.FLOOR;
		}
	};

	@Inject
	protected Context context;
	@Inject
	private Logger log;
	private final Map<Order, OrderState> orderStateMap = new HashMap<>();
	@Inject
	private QuoteService quotes;

	private final ConcurrentSkipListMap<Long, SpecificOrder> triggerOrders = new ConcurrentSkipListMap<>();

}
