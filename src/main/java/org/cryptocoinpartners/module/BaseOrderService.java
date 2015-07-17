package org.cryptocoinpartners.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.exceptions.OrderNotFoundException;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Event;
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
import org.cryptocoinpartners.schema.PositionUpdate;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.RemainderHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class BaseOrderService implements OrderService {

    @Override
    public void init() {
        for (Portfolio portfolio : portfolioService.getPortfolios())
            findTriggerOrders(portfolio);

    }

    @Override
    public void placeOrder(Order order) {
        if (!enableTrading) {
            log.info("Trading Mode Disabled");
            return;
        }

        if (order instanceof GeneralOrder) {
            GeneralOrder generalOrder = (GeneralOrder) order;
            handleGeneralOrder(generalOrder);
        } else if (order instanceof SpecificOrder) {
            SpecificOrder specificOrder = (SpecificOrder) order;
            handleSpecificOrder(specificOrder);
        }
        // order.persit();
        //persitOrderFill(order);
        CreateTransaction(order, true);
        updateOrderState(order, OrderState.NEW, true);
        log.info("Created new order " + order);

    }

    public void findTriggerOrders(Portfolio portfolio) {

        //        SELECT  hex(order_update.order)
        //        FROM    order_update
        //                INNER JOIN
        //                (   SELECT  order_update.order as latestorder, MAX(sequence) AS sequence
        //                    FROM    order_update
        //                    GROUP BY order_update.order
        //                ) MaxP
        //                    ON MaxP.latestorder = order_update.order
        //                    AND MaxP.sequence = order_update.sequence
        //                    where  order_update.state=1;
        String queryNativeStr = "select * from general_order where id in (SELECT  order_update.order" + " FROM    order_update"
                + " INNER JOIN  (   SELECT  order_update.order as latestorder, MAX(sequence) AS sequence" + " FROM    order_update GROUP BY order_update.order"
                + ") MaxP ON MaxP.latestorder = order_update.order AND MaxP.sequence = order_update.sequence"
                + " where  order_update.state=1 and general_order.portfolio = ?1)";

        //"SELECT  order_update.order FROM    order_update INNER JOIN (   SELECT  order_update.order as latestorder, MAX(sequence) AS sequence FROM    order_update GROUP BY order_update.order ) MaxP ON MaxP.latestorder = order_update.order AND MaxP.sequence = order_update.sequence where  order_update.state=1";

        //"SELECT  OrderUpdate.order FROM order_update INNER JOIN (SELECT  order_update.order as latestorder, MAX(sequence) AS sequence FROM    order_update GROUP BY order_update.order ) MaxP ON MaxP.latestorder = order_update.order AND MaxP.sequence = order_update.sequence where  order_update.state=1";
        //   String queryStr = "SELECT o as latestorder, MAX(sequence) as sequence FROM GROUP BY o";

        String queryStr = "SELECT  order FROM OrderUpdate INNER JOIN (SELECT  order as latestorder, MAX(sequence) as sequence FROM OrderUpdate GROUP BY latestorder) where OrderUpdate.state==?1";

        List<GeneralOrder> orders = new ArrayList<GeneralOrder>();
        // = PersistUtil.queryNativeList(GeneralOrder.class, queryNativeStr, portfolio);

        //  "select ou from OrderUpdate ou INNER JOIN (SELECT oum.order latestorder, MAX(sequence)  sequence FROM OrderUpdate oum GROUP BY oum.order ) MaxP ON MaxP.latestorder = order_update.order AND MaxP.sequence = order_update.sequence where  ou.state=?1",

        //List<Order> orders = PersistUtil.queryList(Order.class,
        //      "select o from OrderUpdate u left join u.order o where portfolio = ?1 and u.state=?2 group by o", portfolio, OrderState.TRIGGER);
        // " GROUP BY oum.order " + 
        //  List<Order> orders = PersistUtil.queryList(Order.class, "select ou from OrderUpdate ou " + "INNER JOIN "
        //        + "(SELECT oum.order, max(oum.sequence) AS sequence " + "FROM OrderUpdate oum" + ") MaxP " + "ON MaxP.order = ou.order "
        //      + "AND MaxP.sequence= ou.sequence " + "left join ou.order o where portfolio = ?1 and ou.state=?2", portfolio, OrderState.TRIGGER);

        // and sequence=(select max(sequence) from OrderUpdate where order=o)
        //   List<GeneralOrder> orders = PersistUtil.queryList(GeneralOrder.class, "select o from GeneralOrder o left join o.id OrderUpdate");
        // where  sequence=(select max(sequence) from OrderUpdate  ) and portfolio = ?1 group by o",
        //portfolio);
        //Assumes every trigger order has a parent order

        for (Order triggerOrder : orders) {
            addTriggerOrder(triggerOrder);

        }

    }

    @Override
    public void cancelOrder(Order order) {
        //PersitOrderFill(order);
        //CreateTransaction(order);
        //updateOrderState(order, OrderState);
        log.info("Cancelling  order " + order);
        updateOrderState(order, OrderState.CANCELLING, false);

    }

    @Override
    public void handleCancelAllStopOrders(Portfolio portfolio, Market market) {
        Collection<Order> cancelledOrders = new ConcurrentLinkedQueue<>();
        Fill parentFill;
        Order parentOrder;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.getMarket().equals(market))

                    cancelledOrders.add(triggerOrder);
                //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill
                if (triggerOrder.getParentFill() != null)
                    triggerOrder.getParentFill().setStopPriceCount(0);
            }
            triggerOrders.get(parentKey).removeAll(cancelledOrders);
            for (Order cancelledOrder : cancelledOrders)
                updateOrderState(cancelledOrder, OrderState.CANCELLED, false);

            if (triggerOrders.get(parentKey).isEmpty())
                triggerOrders.remove(parentKey);

            //                if(parentKey instanceof Fill) 
            //                      parentFill = (Fill) parentKey;
            //                if(parentKey instanceof Order) 
            //                     parentOrder = (Order) parentKey;
            //                if ((parentFill!=null && parentFill.getMarket().equals(market)) || (parentOrder!=null && parentOrder.getMarket().equals(market)))
            //                    triggerOrders.remove(parentKey);

            //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill

        }
        // }
    }

    @Override
    public void handleCancelGeneralOrder(GeneralOrder order) {
        Collection<Order> cancelledOrders = new ConcurrentLinkedQueue<>();
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.equals(order))

                    cancelledOrders.add(triggerOrder);
                //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill
                if (triggerOrder.getParentFill() != null)
                    triggerOrder.getParentFill().setStopPriceCount(0);
            }
            triggerOrders.get(parentKey).removeAll(cancelledOrders);
            for (Order cancelledOrder : cancelledOrders)
                updateOrderState(cancelledOrder, OrderState.CANCELLED, false);

            if (triggerOrders.get(parentKey).isEmpty())
                triggerOrders.remove(parentKey);

            //                if(parentKey instanceof Fill) 
            //                      parentFill = (Fill) parentKey;
            //                if(parentKey instanceof Order) 
            //                     parentOrder = (Order) parentKey;
            //                if ((parentFill!=null && parentFill.getMarket().equals(market)) || (parentOrder!=null && parentOrder.getMarket().equals(market)))
            //                    triggerOrders.remove(parentKey);

            //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill

        }
        // }
    }

    @Override
    public void adjustStopLoss(Amount price, Amount amount) {
        // synchronized (lock) {
        for (Event parentKey : triggerOrders.keySet()) {
            for (Order triggerOrder : triggerOrders.get(parentKey)) {
                //			    if(myList.get(i).equals("3")){
                //			        myList.remove(i);
                //			        i--;
                //			        myList.add("6");
                //			    }

                if (triggerOrder.isBid()) {
                    long stopPrice = Math.min((triggerOrder.getStopPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
                            (price.plus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
                    DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
                    //  Amount stopAmount = stopDiscrete.minus(triggerOrder.getLimitPrice());

                    triggerOrder.setStopPrice(stopDiscrete);
                    if (triggerOrder.getParentFill() != null)
                        triggerOrder.getParentFill().setStopPriceCount(stopPrice);
                } else if (triggerOrder.isAsk()) {
                    long stopPrice = Math.max((triggerOrder.getStopPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
                            (price.minus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
                    DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
                    //Amount stopAmount = stopDiscrete.minus(triggerOrder.getLimitPrice());
                    triggerOrder.setStopPrice(stopDiscrete);
                    if (triggerOrder.getParentFill() != null)
                        triggerOrder.getParentFill().setStopPriceCount(stopPrice);

                }

                //				if (triggerOrder.getParentOrder().getFillType().equals(FillType.STOP_LOSS)) {
                //					//need to check this
                //					DecimalAmount stopPrice = DecimalAmount.of(triggerOrder.getStopPrice().plus(amount));
                //
                //					triggerOrder.setStopPrice(stopPrice);

            }
        }
        //   }
    }

    // loop over our  tigger orders

    @Override
    public OrderState getOrderState(Order o) {
        OrderState state = orderStateMap.get(o);
        if (state == null)
            throw new IllegalStateException("Untracked order " + o);
        return state;
    }

    @Override
    public boolean getTradingEnabled() {

        return enableTrading;
    }

    @Override
    public void setTradingEnabled(Boolean enableTrading) {
        this.enableTrading = enableTrading;
    }

    @When("@Priority(8) select * from OrderUpdate.std:lastevent()")
    public void handleOrderUpdate(OrderUpdate orderUpdate) {

        OrderState orderState = orderUpdate.getState();
        Order order = orderUpdate.getOrder();
        switch (orderState) {
            case NEW:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);

                //TODO Order persitantce, keep getting TransientPropertyValueException  errors
                //PersitOrderFill(orderUpdate.getOrder());
                break;
            case TRIGGER:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                break;
            case ROUTED:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                break;
            case PLACED:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                break;
            case PARTFILLED:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                break;
            case FILLED:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);
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
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                }

                break;
            case EXPIRED:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);

                break;
            case REJECTED:
                orderStateMap.put(order, orderState);
                if (order.getParentOrder() != null)
                    updateParentOrderState(order.getParentOrder(), order, orderState);

                break;
        }

    }

    @When("@Priority(9) select * from Fill")
    public void handleFill(Fill fill) {
        Order order = fill.getOrder();
        //should have already been persisted by position handler
        // persitOrderFill(fill);
        fill.persit();
        CreateTransaction(fill, true);

        //PersitOrderFill(order);
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
            updateOrderState(order, newState, true);
        }

    }

    private CommonOrderBuilder buildStopLimitOrder(Fill fill) {
        OrderBuilder order = new OrderBuilder(fill.getOrder().getPortfolio(), this);

        if (fill.getOrder().getParentOrder().getStopPrice() != null || fill.getOrder().getParentOrder().getStopAmount() != null) {
            Amount stopPrice;
            Amount targetPrice;
            DiscreteAmount targetPriceDiscrete;
            DiscreteAmount stopPriceDiscrete;
            BigDecimal bdTargetPrice;
            BigDecimal bdStopPrice;
            BigDecimal bdLimitPrice;
            GeneralOrderBuilder generalOrder;
            BigDecimal bdVolume = fill.getVolume().asBigDecimal();
            if (fill.getOrder().getParentOrder().getStopAmount() != null)
                stopPrice = (fill.isLong()) ? fill.getPrice().minus(fill.getOrder().getParentOrder().getStopAmount()) : fill.getPrice().plus(
                        fill.getOrder().getParentOrder().getStopAmount());
            else
                stopPrice = fill.getOrder().getParentOrder().getStopPrice();

            if (fill.getOrder().getParentOrder().getTargetAmount() != null)
                targetPrice = (fill.isLong()) ? fill.getPrice().plus(fill.getOrder().getParentOrder().getTargetAmount()) : fill.getPrice().minus(
                        fill.getOrder().getParentOrder().getTargetAmount());
            else
                targetPrice = fill.getOrder().getParentOrder().getTargetPrice();

            stopPriceDiscrete = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(stopPrice.asBigDecimal(), fill.getMarket().getPriceBasis()), fill
                    .getMarket().getPriceBasis());
            bdStopPrice = stopPriceDiscrete.asBigDecimal();

            bdLimitPrice = (fill.getVolume().isNegative()) ? stopPriceDiscrete.increment(2).asBigDecimal() : stopPriceDiscrete.decrement(2).asBigDecimal();
            if (targetPrice != null) {
                targetPriceDiscrete = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(targetPrice.asBigDecimal(), fill.getMarket().getPriceBasis()),
                        fill.getMarket().getPriceBasis());
                bdTargetPrice = targetPriceDiscrete.asBigDecimal();
                generalOrder = order.create(context.getTime(), fill, fill.getMarket(), bdVolume.negate(), FillType.STOP_LIMIT)
                        .withComment("Stop Order with Price Target").withTargetPrice(bdTargetPrice).withStopPrice(bdStopPrice).withLimitPrice(bdLimitPrice)
                        .withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(ExecutionInstruction.TAKER);
                fill.setTargetPriceCount(targetPriceDiscrete.getCount());
                //  fill.setTargetAmountCount(targetAmountCount);

            } else {

                generalOrder = order.create(context.getTime(), fill, fill.getMarket(), bdVolume.negate(), FillType.STOP_LIMIT).withComment("Stop Order")
                        .withStopPrice(bdStopPrice).withLimitPrice(bdLimitPrice).withPositionEffect(PositionEffect.CLOSE)
                        .withExecutionInstruction(ExecutionInstruction.TAKER);
            }
            generalOrder.getOrder().copyCommonFillProperties(fill);
            fill.setStopPriceCount(stopPriceDiscrete.getCount());
            return generalOrder;
        }
        return null;
    }

    protected void handleGeneralOrder(GeneralOrder generalOrder) {
        Market market;
        SpecificOrder specificOrder;
        generalOrder.persit();

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
                log.info("Order State" + orderStateMap.get(generalOrder).toString());
                updateOrderState(generalOrder, OrderState.ROUTED, true);
                placeOrder(specificOrder);
                break;
            case STOP_LIMIT:
                addTriggerOrder(generalOrder);
                updateOrderState(generalOrder, OrderState.TRIGGER, true);
                if (generalOrder.getStopPrice() != null)
                    log.info("Stop trade Entered at " + generalOrder.getStopPrice());
                if (generalOrder.getTargetPrice() != null)
                    log.info("Target trade Entered at " + generalOrder.getTargetPrice());
                break;
            case TRAILING_STOP_LIMIT:
                addTriggerOrder(generalOrder);
                updateOrderState(generalOrder, OrderState.TRIGGER, true);
                log.info("Trailing Stop trade Entered at " + generalOrder.getStopPrice());
                break;
            case STOP_LOSS:
                specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
                updateOrderState(generalOrder, OrderState.ROUTED, true);
                log.info("Routing Stop Loss order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
                placeOrder(specificOrder);
                break;

        }

    }

    //    @SuppressWarnings("ConstantConditions")
    //    @When("@Priority(9) select * from Trade")
    //    private void handleTrade(Trade t) {
    //
    //    }

    @When("@Priority(2) select * from Book")
    private void handleBook(Book b) {
        updateRestingOrders(b);

        //

        //service.submit(new handleBookRunnable(b));
    }

    private class handleBookRunnable implements Runnable {
        private final Book book;

        // protected Logger log;

        public handleBookRunnable(Book book) {
            this.book = book;

        }

        @Override
        public void run() {
            updateRestingOrders(book);

        }
    }

    @SuppressWarnings("ConstantConditions")
    private void updateRestingOrders(Book b) {

        Offer bid = b.getBestAsk();
        Offer ask = b.getBestBid();
        Offer offer;
        //  synchronized (lock) {

        Iterator<SpecificOrder> itOrder = getPendingOrders().iterator();
        while (itOrder.hasNext()) {
            SpecificOrder pendingOrder = itOrder.next();

            // for (SpecificOrder pendingOrder : getPendingOrders()) {

            if (pendingOrder.getMarket().equals(b.getMarket())
                    && ((pendingOrder.getParentOrder() != null && pendingOrder.getParentOrder().getFillType() == FillType.STOP_LIMIT) || pendingOrder
                            .getPositionEffect() == PositionEffect.CLOSE) && pendingOrder.getUnfilledVolumeCount() != 0) {
                pendingOrder.setPlacementCount(pendingOrder.getPlacementCount() + 1);

                if (pendingOrder.isAsk())
                    offer = (pendingOrder.getExecutionInstruction() == ExecutionInstruction.MAKER) ? quotes.getLastAskForMarket(pendingOrder.getMarket())
                            : quotes.getLastBidForMarket(pendingOrder.getMarket());
                else
                    offer = (pendingOrder.getExecutionInstruction() == ExecutionInstruction.MAKER) ? quotes.getLastBidForMarket(pendingOrder.getMarket())
                            : quotes.getLastAskForMarket(pendingOrder.getMarket());

                DiscreteAmount limitPrice = (pendingOrder.getVolume().isNegative()) ? offer.getPrice().decrement(
                        (long) (Math.pow(pendingOrder.getPlacementCount(), 2))) : offer.getPrice().increment(
                        (long) (Math.pow(pendingOrder.getPlacementCount(), 2)));

                // we neeed to cancle order
                //TODO surround with try catch so we only insert if we cancel
                log.debug("canceling existing order :" + pendingOrder);
                try {
                    handleCancelSpecificOrder(pendingOrder);
                    OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(pendingOrder.getPortfolio()).create(pendingOrder);

                    SpecificOrder order = builder.getOrder();
                    order.setLimitPriceCount(limitPrice.getCount());
                    placeOrder(order);
                    log.debug("submitted new order :" + order);

                } catch (Exception e) {
                    log.debug("attmept to cancel an order that was not pending :" + pendingOrder, e);
                }

                //PersistUtil.merge(pendingOrder);
                //  order.persit();
                //  PersistUtil.merge(pendingOrder);
                // pendingOrder.persit();

            }
        }
        // }

        // synchronized (lock) {
        Iterator<Event> itEvent = triggerOrders.keySet().iterator();
        while (itEvent.hasNext()) {
            // closing position
            Event parentKey = itEvent.next();

            Iterator<Order> itTriggeredOrder = triggerOrders.get(parentKey).iterator();
            while (itTriggeredOrder.hasNext()) {
                // closing position
                Order triggeredOrder = itTriggeredOrder.next();

                if (triggeredOrder.getMarket().equals(b.getMarket())) {
                    if (triggeredOrder.isBid()) {
                        if ((triggeredOrder.getStopPrice() != null && (ask.getPriceCount() >= (triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket()
                                .getPriceBasis(), Remainder.ROUND_EVEN)).getCount()))
                                || (triggeredOrder.getTargetPrice() != null && (ask.getPriceCount() <= (triggeredOrder.getTargetPrice().toBasis(triggeredOrder
                                        .getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()))) {
                            //convert order to specfic order
                            SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) triggeredOrder, triggeredOrder.getMarket());
                            log.info("At " + context.getTime() + " Routing trigger order " + triggeredOrder + " to "
                                    + triggeredOrder.getMarket().getExchange().getSymbol());
                            //TODO need 
                            placeOrder(specificOrder);
                            //   OrderState newState = order.isFilled() ? OrderState.FILLED : OrderState.PARTFILLED;
                            // updateOrderState(triggeredOrder, OrderState.ROUTED);
                            context.route(new PositionUpdate(null, specificOrder.getMarket(), PositionType.SHORT, PositionType.EXITING));
                            triggerOrders.remove(parentKey);

                            //i = Math.min(0, i - 1);
                            //  triggerOrders.remove(triggerOrder);
                            log.debug(triggeredOrder + " triggered as specificOrder " + specificOrder);
                        } else if (triggeredOrder.getTrailingStopPrice() != null) {
                            //current price is less than the stop price so I will update the stop price
                            long stopPrice = Math.min((triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                                    .getCount(), (ask.getPriceCount() + (triggeredOrder.getTrailingStopPrice().toBasis(triggeredOrder.getMarket()
                                    .getPriceBasis(), Remainder.ROUND_EVEN)).getCount()));
                            DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggeredOrder.getMarket().getPriceBasis()));
                            triggeredOrder.setStopAmount(stopDiscrete);
                            triggeredOrder.persit();

                        }

                    }
                    if (triggeredOrder.isAsk()) {
                        if ((triggeredOrder.getStopPrice() != null && (bid.getPriceCount() <= (triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket()
                                .getPriceBasis(), Remainder.ROUND_EVEN)).getCount()))
                                || (triggeredOrder.getTargetPrice() != null && (bid.getPriceCount() >= (triggeredOrder.getTargetPrice().toBasis(triggeredOrder
                                        .getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()))) {
                            //Place order
                            SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) triggeredOrder, triggeredOrder.getMarket());
                            log.info("At " + context.getTime() + " Routing trigger order " + specificOrder + " to "
                                    + specificOrder.getMarket().getExchange().getSymbol());
                            // updateOrderState(triggeredOrder, OrderState.ROUTED);
                            placeOrder(specificOrder);
                            context.route(new PositionUpdate(null, specificOrder.getMarket(), PositionType.LONG, PositionType.EXITING));
                            triggerOrders.remove(parentKey);
                            // portfolioService.publishPositionUpdate(new Position(triggeredOrder.getPortfolio(), triggeredOrder.getMarket().getExchange(), triggeredOrder.getMarket(), triggeredOrder.getMarket().getBase(),
                            //       DecimalAmount.ZERO, DecimalAmount.ZERO));

                            //i = Math.min(0, i - 1);
                            //triggeredOrders.add(triggeredOrder);
                            log.debug(triggeredOrder + " triggered as specificOrder " + specificOrder);

                        } else if (triggeredOrder.getTrailingStopPrice() != null) {
                            //&& ((bid.getPriceCount() + order.getTrailingStopPrice().getCount() > (order.getStopPrice().getCount())))) {
                            //current price is less than the stop price so I will update the stop price
                            long stopPrice = Math.max((triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                                    .getCount(), (bid.getPriceCount() - (triggeredOrder.getTrailingStopPrice().toBasis(triggeredOrder.getMarket()
                                    .getPriceBasis(), Remainder.ROUND_EVEN)).getCount()));
                            DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggeredOrder.getMarket().getPriceBasis()));
                            triggeredOrder.setStopAmount(stopDiscrete);
                            triggeredOrder.persit();

                        }

                    }

                }
            }
        }
        //triggerOrders.removeAll(triggeredOrders);
        //  }

    }

    private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, Market market) {
        DiscreteAmount volume = generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);
        DiscreteAmount discreteLimit;
        DiscreteAmount discreteStop;
        RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
        final DecimalAmount limitPrice = generalOrder.getLimitPrice();
        final DecimalAmount stopPrice = generalOrder.getStopPrice();
        final DecimalAmount trailingStopPrice = generalOrder.getTrailingStopPrice();

        // the volume will already be negative for a sell order
        OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(generalOrder.getPortfolio()).create(generalOrder.getTime(), market, volume, generalOrder,
                generalOrder.getComment());
        builder.withPositionEffect(generalOrder.getPositionEffect());
        builder.withExecutionInstruction(generalOrder.getExecutionInstruction());

        switch (generalOrder.getFillType()) {
            case GOOD_TIL_CANCELLED:
                break;
            case GTC_OR_MARGIN_CAP:
                break;
            case CANCEL_REMAINDER:
                break;
            case LIMIT:
                discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                builder.withLimitPrice(discreteLimit);
                break;
            case STOP_LIMIT:
                //we will put the stop order in at best bid or best ask
                SpecificOrder stopOrder = builder.getOrder();
                //  stopOrder.persit();
                Offer offer = stopOrder.isBid() ? quotes.getLastBidForMarket(stopOrder.getMarket()) : quotes.getLastAskForMarket(stopOrder.getMarket());
                if (offer == null)
                    break;
                discreteStop = offer.getPrice();
                discreteLimit = volume.isNegative() ? discreteStop.decrement(4) : discreteStop.increment(4);
                builder.withLimitPrice(discreteLimit);
                break;
            case TRAILING_STOP_LIMIT:
                break;
            case STOP_LOSS:
                discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                builder.withLimitPrice(discreteLimit);
                break;

        }

        SpecificOrder specificOrder = builder.getOrder();

        specificOrder.copyCommonOrderProperties(generalOrder);
        specificOrder.persit();
        return specificOrder;
    }

    protected void reject(Order order, String message) {
        log.warn("Order " + order + " rejected: " + message);
        updateOrderState(order, OrderState.REJECTED, false);
    }

    protected abstract void handleSpecificOrder(SpecificOrder specificOrder);

    @Override
    public abstract Collection<SpecificOrder> getPendingOrders(Portfolio portfolio);

    @Override
    public abstract Collection<SpecificOrder> getPendingOrders();

    @Override
    public abstract Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio);

    @Override
    public abstract Collection<SpecificOrder> getPendingOpenOrders(Market market, Portfolio portfolio);

    @Override
    public abstract Collection<SpecificOrder> getPendingOpenOrders(Portfolio portfolio);

    @Override
    public Collection<Order> getPendingStopOrders(Portfolio portfolio) {
        Collection<Order> portfolioPendingStopOrders = new ConcurrentLinkedQueue<>();
        Fill parentFill;
        Order parentOrder;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.getPortfolio().equals(portfolio))

                    portfolioPendingStopOrders.add(triggerOrder);
                //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill

            }

        }
        return portfolioPendingStopOrders;
    }

    @Override
    public abstract void handleCancelSpecificOrder(SpecificOrder specificOrder) throws OrderNotFoundException;

    @Override
    public abstract void handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market);

    @Override
    public abstract void handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market);

    @Override
    public abstract void handleCancelAllSpecificOrders(Portfolio portfolio, Market market);

    protected void updateOrderState(Order order, OrderState state, boolean route) {
        OrderState oldState = null;
        if (order != null)
            oldState = orderStateMap.get(order);
        if (oldState != null && oldState.equals(state))
            return;
        if (oldState == null)
            oldState = OrderState.NEW;
        if (order != null)
            orderStateMap.put(order, state);
        // this.getClass()
        // context.route(new OrderUpdate(order, oldState, state));
        OrderUpdate orderUpdate = new OrderUpdate(order, oldState, state);
        //if (route)
        context.route(orderUpdate);
        //else
        //  context.publish(orderUpdate);
        orderUpdate.persit();

        if (order.getParentOrder() != null)
            updateParentOrderState(order.getParentOrder(), order, state);
    }

    private void updateParentOrderState(Order order, Order childOrder, OrderState childOrderState) {
        OrderState oldState = orderStateMap.get(order);
        switch (childOrderState) {
            case NEW:
                updateOrderState(order, childOrderState, true);
                break;
            case TRIGGER:
                //TODO: update state once all children have same state
                updateOrderState(order, childOrderState, true);
                break;
            case ROUTED:
                //TODO: update state once all children have same state
                updateOrderState(order, childOrderState, true);
                break;
            case PLACED:
                //TODO: update state once all children have same state
                updateOrderState(order, childOrderState, true);
                break;
            case PARTFILLED:
                updateOrderState(order, OrderState.PARTFILLED, true);
                break;
            case FILLED:
                //if (oldState == OrderState.CANCELLING) {
                boolean fullyFilled = true;
                for (Order child : order.getChildren()) {
                    if (orderStateMap.get(child).isOpen()) {
                        fullyFilled = false;
                        updateOrderState(order, OrderState.PARTFILLED, true);
                        break;
                    }
                }
                if (fullyFilled)
                    updateOrderState(order, OrderState.FILLED, true);

                break;

            case CANCELLING:
                updateOrderState(order, childOrderState, true);

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
                        updateOrderState(order, OrderState.CANCELLED, true);
                }
                break;
            case REJECTED:
                //TODO: update state once all children have same state
                updateOrderState(order, childOrderState, true);
                reject(order, "Child order was rejected");
                break;
            case EXPIRED:
                if (!childOrder.getExpiration().isEqual(order.getExpiration()))
                    throw new Error("Child order expirations must match parent order expirations");
                updateOrderState(order, OrderState.EXPIRED, true);
                break;
            default:
                log.warn("Unknown order state: " + childOrderState);
                break;
        }
    }

    protected static final void persitOrderFill(Event event) {

        if (event instanceof Order) {
            Order order = (Order) event;
            order.persit();
            //            Order duplicate = PersistUtil.queryZeroOne(Order.class, "select o from Order o where o=?1", order);
            //            if (duplicate == null)
            //                PersistUtil.insert(order);
            //            else
            //                PersistUtil.merge(order);
        }

        else if (event instanceof Fill) {
            Fill fill = (Fill) event;
            //

            //   fill.persit();
            //            Fill duplicate = PersistUtil.queryZeroOne(Fill.class, "select f from Fill f where f=?1", fill);
            //            if (duplicate == null)
            //                fi
            //                PersistUtil.insert(fill);
            //            else
            //                PersistUtil.merge(fill);

        } else { // if not a Trade, persist unconditionally
            try {
                PersistUtil.insert(event);
            } catch (Throwable e) {
                throw new Error("Could not insert " + event, e);
            }
        }
    }

    private void removeTriggerOrder(Order order) {

        for (Event parentKey : triggerOrders.keySet()) {
            for (Order triggerOrder : triggerOrders.get(parentKey)) {

                if (triggerOrder == order) {
                    triggerOrders.get(parentKey).remove(order);
                    // --removeOrder(order);
                }

                // pendingOrders.remove(order);
            }
        }
    }

    private void addTriggerOrder(Order triggerOrder) {
        //If the trigger order is from a fill, we use the fill as the key for mutliple triggers, else we use the parent
        //any one of the multiple triggers can trigger first, but once one is triggered, all others are removed at for either the same fill or same parent
        Event eventKey = (triggerOrder.getParentFill() != null) ? triggerOrder.getParentFill() : triggerOrder.getParentOrder();
        if (eventKey == null)
            eventKey = triggerOrder;
        // synchronized (lock) {
        ConcurrentLinkedQueue<Order> triggerOrderQueue = new ConcurrentLinkedQueue<Order>();
        triggerOrderQueue.add(triggerOrder);
        triggerOrders.put(eventKey, triggerOrderQueue);
        // }

    }

    protected final void CreateTransaction(EntityBase entity, Boolean route) {
        Transaction transaction = null;
        Order order;
        switch (entity.getClass().getSimpleName()) {

            case "SpecificOrder":
                order = (Order) entity;
                try {
                    transaction = new Transaction(order, context.getTime());
                    transaction.persit();
                    // PersistUtil.insert(transaction);
                    log.info("Created new transaction " + transaction);
                    // if (route)
                    context.route(transaction);
                    // else
                    // context.publish(transaction);

                } catch (Exception e1) {
                    log.error("Threw a Execption, full stack trace follows:", e1);

                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

                break;
            case "Fill":
                Fill fill = (Fill) entity;
                try {
                    transaction = new Transaction(fill, context.getTime());
                    transaction.persit();
                    log.info("Created new transaction " + transaction);
                    // if (route)
                    context.route(transaction);
                    //else
                    //context.publish(transaction);

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    log.error("Threw a Execption, full stack trace follows:", e);

                    e.printStackTrace();
                }
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

    @Transient
    public PortfolioService getPortfolioService() {
        return portfolioService;
    }

    protected void setQuotes(QuoteService quotes) {
        this.quotes = quotes;
    }

    @Transient
    public QuoteService getQuotes() {
        return quotes;
    }

    protected void setPortfolioService(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    public BaseOrderService() {
    }

    private static ExecutorService service = Executors.newFixedThreadPool(1);

    @Inject
    protected Context context;

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.orderService");

    protected boolean enableTrading = false;
    protected final Map<Order, OrderState> orderStateMap = new ConcurrentHashMap<>();
    @Inject
    protected transient QuoteService quotes;
    @Inject
    protected transient PortfolioService portfolioService;
    protected final static Map<Event, ConcurrentLinkedQueue<Order>> triggerOrders = new ConcurrentHashMap<Event, ConcurrentLinkedQueue<Order>>();
    private static Object lock = new Object();

}
