package org.cryptocoinpartners.module;

import java.math.BigDecimal;
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

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.FillType;
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
import org.cryptocoinpartners.schema.OrderBuilder.CommonOrderBuilder;
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
			handleSpecificOrder(specificOrder);
		}
		Amount fees = FeesUtil.getExchangeFees(order);
		order.setForcastedCommission(fees);
		Transaction transaction;
		try {

			transaction = new Transaction(order);
			log.info("Created new transaction " + transaction);
			context.publish(transaction);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void handleCancelAllStopOrders(Portfolio portfolio) {

		Iterator<Long> ito = triggerOrders.keySet().iterator();
		while (ito.hasNext()) {
			Long timestamp = ito.next();
			if (triggerOrders.get(timestamp).getFillType().equals(FillType.STOP_LIMIT)
					|| triggerOrders.get(timestamp).getFillType().equals(FillType.TRAILING_STOP_LIMIT))
				ito.remove();
		}
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
		if (order.getParentOrder() != null) {
			switch (order.getParentOrder().getFillType()) {
				case GOOD_TIL_CANCELLED:
					break;
				case GTC_OR_MARGIN_CAP:
					break;
				case CANCEL_REMAINDER:
					break;
				case LIMIT:
					break;
				case STOP_LIMIT:
					break;
				case TRAILING_STOP_LIMIT:
					break;
				case STOP_LOSS:
					//Place a stop order at the stop price
					OrderBuilder.CommonOrderBuilder orderBuilder = buildStopLimitOrder(fill);
					if (orderBuilder != null) {
						Order stopOrder = orderBuilder.getOrder();
						placeOrder(stopOrder);
					}
					break;

			}
		}

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

	private CommonOrderBuilder buildStopLimitOrder(Fill fill) {
		OrderBuilder order = new OrderBuilder(fill.getOrder().getPortfolio(), this);

		if (fill.getOrder().getParentOrder().getStopPrice() != null) {
			BigDecimal bdVolume = fill.getVolume().asBigDecimal();
			BigDecimal bdStopPrice = fill.getOrder().getParentOrder().getStopPrice().asBigDecimal();

			return order.create(context.getTime(), fill.getOrder().getParentOrder(), fill.getMarket(), bdVolume.negate(), FillType.STOP_LIMIT)
					.withComment("Stop Order").withStopPrice(bdStopPrice).withLimitPrice(bdStopPrice);

		}
		return null;
	}

	protected void handleGeneralOrder(GeneralOrder generalOrder) {
		Market market;
		SpecificOrder specificOrder;
		if (generalOrder.getMarket() == null) {
			Offer offer = generalOrder.isBid() ? quotes.getBestBidForListing(generalOrder.getListing()) : quotes
					.getBestAskForListing(generalOrder.getListing());
			if (offer == null) {
				log.warn("No offers on the book for " + generalOrder.getListing());
				reject(generalOrder, "No recent book data for " + generalOrder.getListing() + " so GeneralOrder routing is disabled");
				return;
			}
			generalOrder.setMarket(offer.getMarket());
		}

		switch (generalOrder.getFillType()) {
			case GOOD_TIL_CANCELLED:
				throw new NotImplementedException();
			case GTC_OR_MARGIN_CAP:
				throw new NotImplementedException();
			case CANCEL_REMAINDER:
				throw new NotImplementedException();
			case LIMIT:
				specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
				log.info("Routing Limit order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
				handleSpecificOrder(specificOrder);
				PersitOrderFill(generalOrder);
				break;
			case STOP_LIMIT:
				triggerOrders.put(generalOrder.getTimestamp(), generalOrder);
				updateOrderState(generalOrder, OrderState.TRIGGER);
				PersitOrderFill(generalOrder);
				log.info("Stop trade Entered at " + generalOrder.getStopPrice());
				break;
			case TRAILING_STOP_LIMIT:
				triggerOrders.put(generalOrder.getTimestamp(), generalOrder);
				updateOrderState(generalOrder, OrderState.TRIGGER);
				PersitOrderFill(generalOrder);
				log.info("Trailing Stop trade Entered at " + generalOrder.getStopPrice());
				break;
			case STOP_LOSS:
				PersitOrderFill(generalOrder);
				specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
				log.info("Routing Stop Loss order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
				handleSpecificOrder(specificOrder);
				break;

		}

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
			Order order = triggerOrders.get(key);

			if (order.getMarket().equals(b.getMarket())) {
				if (order.isBid()) {
					if (order.getStopPrice() != null
							&& ask.getPriceCount() >= (order.getStopPrice().toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()) {
						//convert order to specfic order
						SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) order, order.getMarket());
						log.info("Routing order " + order + " to " + order.getMarket().getExchange().getSymbol());
						handleSpecificOrder(specificOrder);
						triggerOrders.remove(order.getTimestamp());
						log.debug(order + " triggered as specificOrder " + specificOrder);
					} else if (order.getTrailingStopPrice() != null) {
						//current price is less than the stop price so I will update the stop price
						long stopPrice = Math.min((order.getStopPrice().toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(), (ask
								.getPriceCount() + (order.getTrailingStopPrice().toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()));
						DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, order.getMarket().getPriceBasis()));
						order.setStopPrice(stopDiscrete);

					}

				}
				if (order.isAsk()) {
					if (order.getStopPrice() != null
							&& bid.getPriceCount() <= (order.getStopPrice().toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()) {
						//Place order
						SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) order, order.getMarket());
						log.info("Routing order " + order + " to " + order.getMarket().getExchange().getSymbol());
						handleSpecificOrder(specificOrder);
						triggerOrders.remove(order.getTimestamp());
						log.debug(order + " triggered as specificOrder " + specificOrder);

					} else if (order.getTrailingStopPrice() != null) {
						//&& ((bid.getPriceCount() + order.getTrailingStopPrice().getCount() > (order.getStopPrice().getCount())))) {
						//current price is less than the stop price so I will update the stop price
						long stopPrice = Math.max((order.getStopPrice().toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(), (bid
								.getPriceCount() - (order.getTrailingStopPrice().toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()));
						DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, order.getMarket().getPriceBasis()));
						order.setStopPrice(stopDiscrete);

					}

				}
			}
		}
	}

	private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, Market market) {
		DiscreteAmount volume = generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);

		RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
		final DecimalAmount limitPrice = generalOrder.getLimitPrice();
		final DecimalAmount stopPrice = generalOrder.getStopPrice();
		final DecimalAmount trailingStopPrice = generalOrder.getTrailingStopPrice();

		// the volume will already be negative for a sell order
		OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(generalOrder.getPortfolio()).create(generalOrder.getTime(), market, volume,
				generalOrder.getComment());

		if (limitPrice != null) {
			DiscreteAmount discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
			builder.withLimitPrice(discreteLimit);
		}

		SpecificOrder specificOrder = builder.getOrder();
		specificOrder.copyCommonOrderProperties(generalOrder);
		specificOrder.setParentOrder(generalOrder);

		return specificOrder;
	}

	protected void reject(Order order, String message) {
		log.warn("Order " + order + " rejected: " + message);
		updateOrderState(order, OrderState.REJECTED);
	}

	protected abstract void handleSpecificOrder(SpecificOrder specificOrder);

	@Override
	public abstract Collection<SpecificOrder> getPendingOrders(Portfolio portfolio);

	@Override
	public abstract void handleCancelSpecificOrders(SpecificOrder specificOrder);

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
					for (Order child : order.getChildren()) {
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

	private final ConcurrentSkipListMap<Long, GeneralOrder> triggerOrders = new ConcurrentSkipListMap<>();

}
