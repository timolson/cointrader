package org.cryptocoinpartners.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
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
import org.cryptocoinpartners.schema.OrderBuilder.GeneralOrderBuilder;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.QuoteService;
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
		PersitOrderFill(order);
		CreateTransaction(order);
		updateOrderState(order, OrderState.NEW);
		log.info("Created new order " + order);
		if (order instanceof GeneralOrder) {
			GeneralOrder generalOrder = (GeneralOrder) order;
			handleGeneralOrder(generalOrder);
		} else if (order instanceof SpecificOrder) {
			SpecificOrder specificOrder = (SpecificOrder) order;
			handleSpecificOrder(specificOrder);
		}

	}

	@Override
	public void cancelOrder(Order order) {
		//PersitOrderFill(order);
		//CreateTransaction(order);
		//updateOrderState(order, OrderState);
		log.info("Cancelling  order " + order);
		updateOrderState(order, OrderState.CANCELLING);

	}

	@Override
	public void handleCancelAllStopOrders(Portfolio portfolio, Market market) {
		Collection<Order> cancelledOrders = new ArrayList<>();
		synchronized (lock) {
			for (Iterator<Order> it = triggerOrders.iterator(); it.hasNext();) {
				Order triggerOrder = it.next();
				if (triggerOrder.getMarket().equals(market))
					cancelledOrders.add(triggerOrder);
			}
			triggerOrders.removeAll(cancelledOrders);
		}
	}

	@Override
	public void handleCancelGeneralOrder(GeneralOrder order) {
		Collection<Order> cancelledOrders = new ArrayList<>();
		synchronized (lock) {
			for (Iterator<Order> it = triggerOrders.iterator(); it.hasNext();) {
				Order triggerOrder = it.next();
				if (triggerOrder.equals(order))
					cancelledOrders.add(triggerOrder);
			}

			triggerOrders.removeAll(cancelledOrders);
			updateOrderState(order, OrderState.CANCELLED);
		}
	}

	@Override
	public void adjustStopLoss(Amount price, Amount amount) {
		synchronized (lock) {
			for (int i = 0; i < triggerOrders.size(); i++) {
				//			    if(myList.get(i).equals("3")){
				//			        myList.remove(i);
				//			        i--;
				//			        myList.add("6");
				//			    }

				//for (Iterator<Order> it = triggerOrders.iterator(); it.hasNext();) {
				Order triggerOrder = triggerOrders.get(i);
				if (triggerOrder.isBid()) {
					long stopPrice = Math.min((triggerOrder.getStopPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
							(price.plus(amount).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
					DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
					triggerOrder.setStopPrice(stopDiscrete);
				} else if (triggerOrder.isAsk()) {
					long stopPrice = Math.max((triggerOrder.getStopPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
							(price.minus(amount).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
					DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
					triggerOrder.setStopPrice(stopDiscrete);

				}

				//				if (triggerOrder.getParentOrder().getFillType().equals(FillType.STOP_LOSS)) {
				//					//need to check this
				//					DecimalAmount stopPrice = DecimalAmount.of(triggerOrder.getStopPrice().plus(amount));
				//
				//					triggerOrder.setStopPrice(stopPrice);

			}

		}
	}

	// loop over our  tigger orders

	@Override
	public OrderState getOrderState(Order o) {
		OrderState state = orderStateMap.get(o);
		if (state == null)
			throw new IllegalStateException("Untracked order " + o);
		return state;
	}

	@When("@Priority(6) select * from OrderUpdate")
	public void handleOrderUpdate(OrderUpdate orderUpdate) {
		OrderState orderState = orderUpdate.getState();
		Order order = orderUpdate.getOrder();
		switch (orderState) {
			case NEW:
				//TODO Order persitantce, keep getting TransientPropertyValueException  errors
				//PersitOrderFill(orderUpdate.getOrder());
				break;
			case TRIGGER:
				break;
			case ROUTED:
				break;
			case PLACED:
				//	PersitOrderFill(orderUpdate.getOrder());
				break;
			case PARTFILLED:
				break;
			case FILLED:
				break;
			case CANCELLING:
				if (order instanceof GeneralOrder) {
					GeneralOrder generalOrder = (GeneralOrder) order;
					handleCancelGeneralOrder(generalOrder);
				} else if (order instanceof SpecificOrder) {
					SpecificOrder specificOrder = (SpecificOrder) order;
					handleCancelSpecificOrder(specificOrder);
				}
				break;
			case CANCELLED:
				break;
			case EXPIRED:
				break;
			case REJECTED:
				break;
            default:
                log.warn("Unknown order state: " + orderState);
                break;

		}

	}

	@When("@Priority(9) select * from Fill")
	public void handleFill(Fill fill) {
		Order order = fill.getOrder();
		//PersitOrderFill(order);
		if (order.getParentOrder() != null) {
            FillType fillType = order.getParentOrder().getFillType();
            switch (fillType) {
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
                default:
                    log.warn("Unknown fill type: " + fillType);
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
		PersitOrderFill(fill);
		CreateTransaction(fill);

	}

	private CommonOrderBuilder buildStopLimitOrder(Fill fill) {
		OrderBuilder order = new OrderBuilder(fill.getOrder().getPortfolio(), this);

		if (fill.getOrder().getParentOrder().getStopPrice() != null) {
			BigDecimal bdVolume = fill.getVolume().asBigDecimal();
			BigDecimal bdStopPrice = fill.getOrder().getParentOrder().getStopPrice().asBigDecimal();
			GeneralOrderBuilder generalOrder = order
					.create(context.getTime(), fill.getOrder().getParentOrder(), fill.getMarket(), bdVolume.negate(), FillType.STOP_LIMIT)
					.withComment("Stop Order").withStopPrice(bdStopPrice).withLimitPrice(bdStopPrice).withPositionEffect(PositionEffect.CLOSE);

			generalOrder.getOrder().copyCommonFillProperties(fill);
			return generalOrder;
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

        FillType fillType = generalOrder.getFillType();
        switch (fillType) {
			case GOOD_TIL_CANCELLED:
				throw new NotImplementedException();
			case GTC_OR_MARGIN_CAP:
				throw new NotImplementedException();
			case CANCEL_REMAINDER:
				throw new NotImplementedException();

			case LIMIT:
				specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
				log.info("Routing Limit order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
				log.info("Order State" + orderStateMap.get(generalOrder).toString());
				placeOrder(specificOrder);
				break;
			case STOP_LIMIT:
				addTriggerOrder(generalOrder);
				updateOrderState(generalOrder, OrderState.TRIGGER);
				log.info("Stop trade Entered at " + generalOrder.getStopPrice());
				break;
			case TRAILING_STOP_LIMIT:
				addTriggerOrder(generalOrder);
				updateOrderState(generalOrder, OrderState.TRIGGER);
				log.info("Trailing Stop trade Entered at " + generalOrder.getStopPrice());
				break;
			case STOP_LOSS:
				specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
				log.info("Routing Stop Loss order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
				placeOrder(specificOrder);
				break;
            default:
                log.warn("Unknown fill type: " + fillType);
                break;

        }

	}

	@SuppressWarnings("ConstantConditions")
	@When("@Priority(9) select * from Book")
	private void handleBook(Book b) {
		Offer ask = b.getBestAsk();
		Offer bid = b.getBestBid();
		Collection<Order> triggeredOrders = new ArrayList<>();

		synchronized (lock) {
			for (int i = 0; i < triggerOrders.size(); i++) {
				Order triggeredOrder = triggerOrders.get(i);

				if (triggeredOrder.getMarket().equals(b.getMarket())) {
					if (triggeredOrder.isBid()) {
						if (triggeredOrder.getStopPrice() != null
								&& bid.getPriceCount() >= (triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(),
										Remainder.ROUND_EVEN)).getCount()) {
							//convert order to specfic order
							SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) triggeredOrder, triggeredOrder.getMarket());
							log.info("Routing trigger order " + triggeredOrder + " to " + triggeredOrder.getMarket().getExchange().getSymbol());
							placeOrder(specificOrder);
							triggerOrders.remove(i);
							i--;
							//	triggerOrders.remove(triggerOrder);
							log.debug(triggeredOrder + " triggered as specificOrder " + specificOrder);
						} else if (triggeredOrder.getTrailingStopPrice() != null) {
							//current price is less than the stop price so I will update the stop price
							long stopPrice = Math.min((triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
									.getCount(), (bid.getPriceCount() + (triggeredOrder.getTrailingStopPrice().toBasis(triggeredOrder.getMarket()
									.getPriceBasis(), Remainder.ROUND_EVEN)).getCount()));
							DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggeredOrder.getMarket().getPriceBasis()));
							triggeredOrder.setStopPrice(stopDiscrete);

						}

					}
					if (triggeredOrder.isAsk()) {
						if (triggeredOrder.getStopPrice() != null
								&& ask.getPriceCount() <= (triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(),
										Remainder.ROUND_EVEN)).getCount()) {
							//Place order
							SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) triggeredOrder, triggeredOrder.getMarket());
							log.info("Routing trigger order " + specificOrder + " to " + specificOrder.getMarket().getExchange().getSymbol());
							placeOrder(specificOrder);
							triggerOrders.remove(i);
							i--;
							//triggeredOrders.add(triggeredOrder);
							log.debug(triggeredOrder + " triggered as specificOrder " + specificOrder);

						} else if (triggeredOrder.getTrailingStopPrice() != null) {
							//&& ((bid.getPriceCount() + order.getTrailingStopPrice().getCount() > (order.getStopPrice().getCount())))) {
							//current price is less than the stop price so I will update the stop price
							long stopPrice = Math.max((triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
									.getCount(), (ask.getPriceCount() - (triggeredOrder.getTrailingStopPrice().toBasis(triggeredOrder.getMarket()
									.getPriceBasis(), Remainder.ROUND_EVEN)).getCount()));
							DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggeredOrder.getMarket().getPriceBasis()));
							triggeredOrder.setStopPrice(stopDiscrete);

						}

					}
				}
			}
			//triggerOrders.removeAll(triggeredOrders);
		}
	}

	private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, Market market) {
		DiscreteAmount volume = generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);

		RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
		final DecimalAmount limitPrice = generalOrder.getLimitPrice();
		final DecimalAmount stopPrice = generalOrder.getStopPrice();
		final DecimalAmount trailingStopPrice = generalOrder.getTrailingStopPrice();

		// the volume will already be negative for a sell order
		OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(generalOrder.getPortfolio()).create(generalOrder.getTime(), market, volume, generalOrder,
				generalOrder.getComment());
		builder.withPositionEffect(generalOrder.getPositionEffect());

        FillType fillType = generalOrder.getFillType();
        switch (fillType) {
			case GOOD_TIL_CANCELLED:
				break;
			case GTC_OR_MARGIN_CAP:
				break;
			case CANCEL_REMAINDER:
				break;
			case LIMIT:
				DiscreteAmount discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
				builder.withLimitPrice(discreteLimit);
				break;
			case STOP_LIMIT:
				DiscreteAmount discreteStopLimit = stopPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
				builder.withLimitPrice(discreteStopLimit);
				break;
			case TRAILING_STOP_LIMIT:
				break;
			case STOP_LOSS:
				DiscreteAmount discreteStopLossLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
				builder.withLimitPrice(discreteStopLossLimit);
				break;
            default:
                log.warn("Unknown fill type: " + fillType);
                break;

        }

		SpecificOrder specificOrder = builder.getOrder();
		specificOrder.copyCommonOrderProperties(generalOrder);
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
	public abstract void handleCancelSpecificOrder(SpecificOrder specificOrder);

	@Override
	public abstract void handleCancelAllSpecificOrders(Portfolio portfolio, Market market);

	protected void updateOrderState(Order order, OrderState state) {
		OrderState oldState = orderStateMap.get(order);
		if (oldState == null)
			oldState = OrderState.NEW;

		orderStateMap.put(order, state);
		context.route(new OrderUpdate(order, oldState, state));
		if (order.getParentOrder() != null)
			updateParentOrderState(order.getParentOrder(), order, state);
	}

	private void updateParentOrderState(Order order, Order childOrder, OrderState childOrderState) {
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

	private void removeTriggerOrder(Order order) {
		synchronized (lock) {
			triggerOrders.remove(order);
		}

	}

	private void addTriggerOrder(Order order) {
		synchronized (lock) {
			triggerOrders.add(order);
		}

	}

	protected final void CreateTransaction(EntityBase entity) {
		Transaction transaction = null;
		Order order;
        String simpleName = entity.getClass().getSimpleName();
        switch (simpleName) {

			case "SpecificOrder":
				order = (Order) entity;
				try {
					transaction = new Transaction(order);
					log.info("Created new transaction " + transaction);
					context.publish(transaction);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				break;
			case "Fill":
				Fill fill = (Fill) entity;
				try {
					transaction = new Transaction(fill);
					log.info("Created new transaction " + transaction);
					context.publish(transaction);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
            default:
                log.warn("Unknown simple name: " + simpleName);
                break;

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
	private final List<Order> triggerOrders = new ArrayList<Order>();
	private static Object lock = new Object();

}
