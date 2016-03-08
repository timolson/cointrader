package org.cryptocoinpartners.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.cryptocoinpartners.exceptions.TradingDisabledException;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.FillFactory;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.GeneralOrderFactory;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.OrderUpdateFactory;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.PositionUpdate;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.SpecificOrderFactory;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.EM;
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
    static {
        service = Executors.newFixedThreadPool(1);

    }

    @Override
    public void init() {
        for (Portfolio portfolio : portfolioService.getPortfolios())
            findTriggerOrders(portfolio);

    }

    @Override
    public void placeOrder(Order order) throws Throwable {
        if (!enableTrading) {
            log.info("Trading Mode Disabled");
            throw new TradingDisabledException("Trading Mode Disabled");
            // return;
        }
        order.persit();
        updateOrderState(order, OrderState.NEW, true);

        //   updateOrderState(order, OrderState.NEW, true);
        if (order instanceof GeneralOrder) {
            GeneralOrder generalOrder = (GeneralOrder) order;

            log.info("new general order recieved " + generalOrder);
            handleGeneralOrder(generalOrder);
        } else if (order instanceof SpecificOrder) {
            SpecificOrder specificOrder = (SpecificOrder) order;
            log.info("new specific order recieved " + specificOrder);
            try {
                handleSpecificOrder(specificOrder);
            } catch (Throwable ex) {
                throw ex;
            }
        }
        // updateOrderState(order, OrderState.NEW, true);

        // order.persit();
        //persitOrderFill(order);
        CreateTransaction(order, true);

    }

    public synchronized void findTriggerOrders(Portfolio portfolio) {

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
        Map orderHints = new HashMap();
        // ConcurrentHashMap<Portfolio, Collection<Order>> portfolioOrders = new ConcurrentHashMap<Portfolio, Collection<Order>>();

        // orderHints.put("javax.persistence.fetchgraph", "orderUpdateWithTransactions");
        // orderHints.put("javax.persistence.fetchgraph", "orderUpdateWithFills");
        List<OrderState> openOrderStates = new ArrayList<OrderState>();
        openOrderStates.add(OrderState.TRIGGER);
        openOrderStates.add(OrderState.PARTFILLED);
        openOrderStates.add(OrderState.NEW);
        openOrderStates.add(OrderState.PLACED);
        openOrderStates.add(OrderState.CANCELLING);
        openOrderStates.add(OrderState.ROUTED);
        // Collection<OrderUpdate> triggerOrderUpdates = EM.namedQueryList(OrderUpdate.class, "orderUpdate.findOrders", OrderState.TRIGGER);
        //  Collection<OrderUpdate> partfilledOrderUpdates = EM.namedQueryList(OrderUpdate.class, "orderUpdate.findOrders", OrderState.PARTFILLED);
        //Collection<OrderUpdate> newOrderUpdates = EM.namedQueryList(OrderUpdate.class, "orderUpdate.findOrders", OrderState.NEW);
        // Collection<OrderUpdate> placedOrderUpdates = EM.namedQueryList(OrderUpdate.class, "orderUpdate.findOrders", OrderState.PLACED);
        //Collection<OrderUpdate> cancellingOrderUpdates = EM.namedQueryList(OrderUpdate.class, "orderUpdate.findOrders", OrderState.CANCELLING);
        Collection<OrderUpdate> workingOrderUpdates = EM.namedQueryList(OrderUpdate.class, "orderUpdate.findOrders", openOrderStates);

        Collection<Order> portfolioOrders = portfolio.getAllOrders();

        // Collection<Fill> portfolioFills = 
        // portfolioOrders.put(orderUpdate.getOrder().getPortfolio(), orderList);
        workingOrderUpdates.removeAll(Collections.singleton(null));
        Map<Order, Order> orderServiceOrders = new HashMap<Order, Order>();
        for (Order order : portfolio.getAllOrders()) {
            orderServiceOrders.put(order, order);
        }

        Map<Fill, Fill> orderServiceFills = new HashMap<Fill, Fill>();
        for (Fill fill : portfolio.getAllFills()) {
            orderServiceFills.put(fill, fill);
        }

        // lets srippp of any emmpty positions.
        for (OrderUpdate orderUpdate : workingOrderUpdates) {
            // if (orderUpdate.getOrder().getPortfolio() == null | portfolioOrders != null && !portfolioOrders.isEmpty())
            //   continue;
            boolean orderInPortfolio = false;
            // context.getInjector().injectMembers(orderUpdate);
            // context.getInjector().injectMembers(orderUpdate.getOrder());
            for (Order portfolioOrder : orderServiceOrders.keySet()) {
                if (orderUpdate.getOrder().equals(portfolioOrder)) {
                    orderUpdate.setOrder(portfolioOrder);
                    orderInPortfolio = true;
                    break;
                }
            }
            if (!orderInPortfolio && !orderServiceOrders.containsKey(orderUpdate.getOrder()))
                orderUpdate.getOrder().loadAllChildOrdersByParentOrder(orderUpdate.getOrder(), orderServiceOrders, orderServiceFills);

            //  ArrayList<Order> orderList = (portfolioOrders==null || portfolioOrders.get(orderUpdate.getOrder().getPortfolio())==null || portfolioOrders.get(orderUpdate.getOrder().getPortfolio()).isEmpty()) ? new ArrayList<Order>() : portfolioOrders.get(orderUpdate.getOrder().getPortfolio());
            //portfolioService= context.getInjector().getInstance(BasicPortfolioService.class);
            //portfolio.getBaseAsset();
            //for (Portfolio portfolio : portfolioService.getPortfolios()) {
            if (orderUpdate.getOrder().getPortfolio().equals(portfolio))

                orderUpdate.getOrder().setPortfolio(portfolio);

            //}
            // context.getInjector().injectMembers(orderUpdate.getOrder());

            orderStateMap.put(orderUpdate.getOrder(), orderUpdate.getState());
            if (stateOrderMap.get(orderUpdate.getState()) == null) {
                Set<Order> orderSet = new HashSet<Order>();
                // orderSet.add(order);
                stateOrderMap.put(orderUpdate.getState(), orderSet);

            }
            stateOrderMap.get(orderUpdate.getState()).add(orderUpdate.getOrder());
            if (orderUpdate.getState() == (OrderState.TRIGGER))
                addTriggerOrder(orderUpdate.getOrder());

        }
        //orderUpdate.getOrder().loadAllChildOrdersByParentOrder(orderUpdate.getOrder());

        // so we have loaded all the order, but these orders might already be in the portfolio
        // if they are we need to set the refernce equalt to that of the portfolio
        // for (Order order: portfolio.getAllOrders())
        // we should not query these againg here 

        // Now need to load all order where is open (  return this == OrderState.NEW || this == OrderState.TRIGGER || this == OrderState.ROUTED || this == OrderState.PLACED || this == OrderState.PARTFILLED)
        // if we have not already loaded them we meed to add them to the order map
        //   Portfolio
        //     - Positions
        //       -Fills
        //         - Child orders
        //           -Fills
        //             ......
        //   so to get all the orders, itterate over all the posiotns, then for each postion, load the order from the fill, then any child orders in the fills, and so on.
        // thne if there is an order in the porfolio, add this to the memory map not the one loaded from the DB.     

        //  Order order = EM.namedQueryZeroOne(Order.class, "Order.findOrders", orderHints, orderUpdate.getOrder().getId());

    }

    //   position.getFills().removeAll(Collections.singleton(null));

    // Portfolio myPort = EM.find(Portfolio.class, portfolioID, hints);
    /*        return myPort;
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
    */
    //   }
    @Override
    public Collection<Order> getPendingOrders() {
        //PersitOrderFill(order);
        Set<Order> cointraderOpenOrders = new HashSet<Order>();
        for (Order order : orderStateMap.keySet())
            if (orderStateMap.get(order).isOpen())
                cointraderOpenOrders.add(order);

        /*      if (stateOrderMap.get(OrderState.NEW) != null)
                  cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.NEW));
              if (stateOrderMap.get(OrderState.PLACED) != null)
                  cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PLACED));
              if (stateOrderMap.get(OrderState.PARTFILLED) != null)
                  cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PARTFILLED));
              if (stateOrderMap.get(OrderState.ROUTED) != null)
                  cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.ROUTED));
              if (stateOrderMap.get(OrderState.CANCELLING) != null)
                  cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.CANCELLING));
        */return cointraderOpenOrders;

    }

    @Override
    public synchronized void cancelOrder(Order order) {
        //PersitOrderFill(order);
        //CreateTransaction(order);
        //updateOrderState(order, OrderState);
        log.info("Cancelling  order " + order);
        updateOrderState(order, OrderState.CANCELLING, false);

    }

    @Override
    public synchronized void updateWorkingOrderQuantity(Order order, Amount quantity) {
        if (quantity.isZero()) {
            cancelOrder(order);
            return;
        }

        if (order instanceof GeneralOrder) {
            GeneralOrder generalOrder = (GeneralOrder) order;
            log.info("updateing quanity of general order: " + order + " from: " + order.getVolume() + " to: " + quantity);
            generalOrder.setVolumeDecimal(quantity.asBigDecimal());

            generalOrder.getVolume();
        } else if (order instanceof SpecificOrder) {
            SpecificOrder specifcOrder = (SpecificOrder) order;
            log.info("updateing quanity of specific order: " + order + " from: " + order.getVolume() + " to: " + quantity);

            handleUpdateSpecificOrderWorkingQuantity(specifcOrder, quantity.toBasis(specifcOrder.getMarket().getVolumeBasis(), Remainder.ROUND_EVEN));

            specifcOrder.getVolume();

        }
        //PersitOrderFill(order);
        //CreateTransaction(order);
        //updateOrderState(order, OrderState);

    }

    @Override
    public synchronized void handleCancelAllShortStopOrders(Portfolio portfolio, Market market) {
        Collection<Order> cancelledOrders = new ConcurrentLinkedQueue<>();
        boolean found = false;
        Fill parentFill;
        Order parentOrder;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.getMarket().equals(market) && triggerOrder.isBid() && triggerOrder.getStopPrice() != null)

                    cancelledOrders.add(triggerOrder);
                Collection<SpecificOrder> closingOrders = null;
                if (triggerOrder.getParentFill() != null) {
                    triggerOrder.getParentFill().setPositionType(triggerOrder.getParentFill().getVolumeCount() > 0 ? PositionType.LONG : PositionType.SHORT);

                    if (triggerOrder.isAsk())
                        closingOrders = getPendingLongCloseOrders(triggerOrder.getPortfolio(), triggerOrder.getExecutionInstruction());
                    if (triggerOrder.isBid())
                        closingOrders = getPendingShortCloseOrders(triggerOrder.getPortfolio(), triggerOrder.getExecutionInstruction());

                    for (Order closingOrder : closingOrders)
                        if (closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                            found = true;
                            break;
                        }
                    // if (closingOrders == null || closingOrders.isEmpty() || !found)
                    //   triggerOrder.getParentFill().setStopPriceCount(0);

                }

                //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill
                //   if (triggerOrder.getParentFill() != null)
                //     triggerOrder.getParentFill().setStopPriceCount(0);
            }
            triggerOrders.get(parentKey).removeAll(cancelledOrders);
            for (Order cancelledOrder : cancelledOrders) {
                updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
                log.info("Cancelled Short Stop Trigger Order: " + cancelledOrder);
            }

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
    public synchronized void handleCancelAllTriggerOrdersByParentFill(Fill parentFill) {

        if (parentFill == null)
            return;
        if (triggerOrders.remove(parentFill) != null)
            log.info("Cancelled Trigger Orders for : " + parentFill);
        else
            log.info("No Trigger Orders for : " + parentFill);

        // }
    }

    @Override
    public synchronized void handleCancelAllLongStopOrders(Portfolio portfolio, Market market) {
        Collection<Order> cancelledOrders = new ConcurrentLinkedQueue<>();
        boolean found = false;
        Fill parentFill;
        Order parentOrder;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.getMarket().equals(market) && triggerOrder.isAsk() && triggerOrder.getStopPrice() != null)

                    cancelledOrders.add(triggerOrder);
                Collection<SpecificOrder> closingOrders = null;
                if (triggerOrder.getParentFill() != null) {
                    triggerOrder.getParentFill().setPositionType(triggerOrder.getParentFill().getVolumeCount() > 0 ? PositionType.LONG : PositionType.SHORT);

                    if (triggerOrder.isAsk())
                        closingOrders = getPendingLongCloseOrders(triggerOrder.getPortfolio(), triggerOrder.getExecutionInstruction());
                    if (triggerOrder.isBid())
                        closingOrders = getPendingShortCloseOrders(triggerOrder.getPortfolio(), triggerOrder.getExecutionInstruction());

                    for (Order closingOrder : closingOrders)
                        if (closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                            found = true;
                            break;
                        }
                    //   if (closingOrders == null || closingOrders.isEmpty() || !found)
                    //     triggerOrder.getParentFill().setStopPriceCount(0);

                }
            }
            triggerOrders.get(parentKey).removeAll(cancelledOrders);
            for (Order cancelledOrder : cancelledOrders) {
                updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
                log.info("Cancelled Long Stop Trigger Order: " + cancelledOrder);

            }

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
    public synchronized void handleCancelGeneralOrder(GeneralOrder order) {
        Collection<Order> cancelledOrders = new ConcurrentLinkedQueue<>();
        boolean found = false;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.equals(order)) {

                    cancelledOrders.add(triggerOrder);
                    //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill

                }
            }
            triggerOrders.get(parentKey).removeAll(cancelledOrders);
            if (triggerOrders.get(parentKey).isEmpty())
                triggerOrders.remove(parentKey);
            for (Order cancelledOrder : cancelledOrders) {
                updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
                log.info("Cancelled General  Trigger Order: " + cancelledOrder);
                // if (cancelledOrder.)
                Collection<SpecificOrder> closingOrders = null;
                if (cancelledOrder.getParentFill() != null) {
                    cancelledOrder.getParentFill()
                            .setPositionType(cancelledOrder.getParentFill().getVolumeCount() > 0 ? PositionType.LONG : PositionType.SHORT);

                    if (cancelledOrder.isAsk())
                        closingOrders = getPendingLongCloseOrders(cancelledOrder.getPortfolio(), cancelledOrder.getExecutionInstruction());
                    if (cancelledOrder.isBid())
                        closingOrders = getPendingShortCloseOrders(cancelledOrder.getPortfolio(), cancelledOrder.getExecutionInstruction());

                    for (Order closingOrder : closingOrders)
                        if (closingOrder.getParentFill() != null && closingOrder.getParentFill().equals(cancelledOrder.getParentFill())) {
                            found = true;
                            break;
                        }
                    // if (closingOrders == null || closingOrders.isEmpty() || !found)
                    // cancelledOrder.getParentFill().setStopPriceCount(0);

                }

                // only set the Stop price to zero is no related orders.
                // if it is  a buy order, set the stop price to the highest of all closing orders for this parent
                // so I could have a close order for this fill in the market or I could have another trigger order for this fill in the market.
                // for(this.get.getPendingClosingOrders().
                // if it is a sell order, set the stp price to lowest of all closing orders fo this parent.
                //cancelledOrder.getParentFill().setStopPriceCount(0);

            }

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
    public void adjustShortStopLoss(Amount price, Amount amount, Boolean force) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();
                if (triggerOrder.isBid()) {
                    log.debug("Determining to adjust stops for trigger order stop price" + triggerOrder.getStopPrice() + " for stop price: "
                            + price.plus(amount.abs()));

                    long stopPrice = (force) ? (price.plus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()
                            : Math.min((triggerOrder.getStopPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
                                    (price.plus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
                    DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
                    if (stopDiscrete.compareTo(triggerOrder.getStopPrice()) == 0)
                        return;
                    log.debug(this.getClass().getSimpleName() + ":adjustShortStopLoss Updsting stop price from " + triggerOrder.getStopPrice() + " to "
                            + stopDiscrete + " for " + triggerOrder);

                    triggerOrder.setStopPrice(stopDiscrete);
                    if (triggerOrder.getParentFill() != null) {
                        triggerOrder.getParentFill().setStopPriceCount(stopPrice);
                        //   triggerOrder.getParentFill().merge();
                    }
                    triggerOrder.merge();

                }
            }
        }
    }

    @Override
    public void adjustLongStopLoss(Amount price, Amount amount, Boolean force) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();
                if (triggerOrder.isAsk()) {
                    log.debug("Determining to adjust stops for trigger order stop price " + triggerOrder.getStopPrice() + " for stop price: "
                            + price.minus(amount.abs()));
                    long stopPrice = (force) ? (price.minus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()
                            : Math.max((triggerOrder.getStopPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
                                    (price.minus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
                    DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
                    if (stopDiscrete.compareTo(triggerOrder.getStopPrice()) == 0)
                        return;
                    log.debug(this.getClass().getSimpleName() + ":adjustLongStopLoss Updsting stop price from " + triggerOrder.getStopPrice() + " to "
                            + stopDiscrete + " for " + triggerOrder);
                    triggerOrder.setStopPrice(stopDiscrete);

                    if (triggerOrder.getParentFill() != null) {
                        triggerOrder.getParentFill().setStopPriceCount(stopPrice);
                        //  triggerOrder.getParentFill().merge();
                    }
                    triggerOrder.merge();
                }
            }
        }
    }

    @Override
    public void adjustLongTargetPrices(Amount price, Amount amount) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();
                //TODO: should this be the max for bid and the min for shorts
                if (triggerOrder.isAsk()) {
                    long targetPrice = Math.max(
                            (triggerOrder.getTargetPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
                            (price.plus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
                    DecimalAmount targetDiscrete = DecimalAmount.of(new DiscreteAmount(targetPrice, triggerOrder.getMarket().getPriceBasis()));
                    triggerOrder.setTargetPrice(targetDiscrete);
                    if (triggerOrder.getParentFill() != null)
                        triggerOrder.getParentFill().setTargetPriceCount(targetPrice);
                }
            }
        }

    }

    @Override
    public void adjustShortTargetPrices(Amount price, Amount amount) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();
                //TODO: should this be the max for bid and the min for shorts
                if (triggerOrder.isBid()) {
                    long targetPrice = Math.min(
                            (triggerOrder.getTargetPrice().toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount(),
                            (price.minus(amount.abs()).toBasis(triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount());
                    DecimalAmount TargetDiscrete = DecimalAmount.of(new DiscreteAmount(targetPrice, triggerOrder.getMarket().getPriceBasis()));
                    triggerOrder.setTargetPrice(TargetDiscrete);
                    if (triggerOrder.getParentFill() != null)
                        triggerOrder.getParentFill().setTargetPriceCount(targetPrice);
                }
            }
        }

    }

    // loop over our  tigger orders

    @Override
    public OrderState getOrderState(Order o) {
        OrderState state = orderStateMap.get(o);
        if (state == null) {
            state = orderStateMap.get(o);

            throw new IllegalStateException("Untracked order " + o);
        }
        return state;
    }

    public Collection<SpecificOrder> getWorkingOrdersOrderFromStateMap() {
        ArrayList<SpecificOrder> orders = new ArrayList<SpecificOrder>();
        for (Order order : orderStateMap.keySet())
            if (order instanceof SpecificOrder && orderStateMap.get(order).isOpen())
                orders.add((SpecificOrder) order);

        return orders;
    }

    public SpecificOrder getSpecifcOrderFromStateMap(UUID id) {
        boolean found = false;
        for (Order order : orderStateMap.keySet()) {
            if (order.getId().equals(id)) {
                found = true;
                if (order instanceof SpecificOrder)
                    return (SpecificOrder) order;
            }

        }
        if (!found)
            throw new IllegalStateException("Untracked order " + id);
        return null;

    }

    @Override
    public boolean getTradingEnabled() {

        return enableTrading;
    }

    @Override
    public void setTradingEnabled(Boolean enableTrading) {
        this.enableTrading = enableTrading;
    }

    @When("@Priority(8) select * from OrderUpdate")
    public synchronized void handleOrderUpdate(OrderUpdate orderUpdate) {
        //TOOD somethig is up in here causing the states of stop orders to be changed when they are still resting
        OrderState oldState = orderStateMap.get(orderUpdate.getOrder());
        OrderState orderState = orderUpdate.getState();
        Order order = orderUpdate.getOrder();
        if (stateOrderMap.get(orderState) == null) {
            Set<Order> orderSet = new HashSet<Order>();
            // orderSet.add(order);
            if (oldState != null)
                stateOrderMap.get(oldState).remove(order);
            stateOrderMap.put(orderState, orderSet);

        }
        switch (orderState) {
            case NEW:
                stateOrderMap.get(orderState).add(order);

                orderStateMap.put(order, orderState);

                if (order.getParentOrder() != null
                        && (order.getFillType() != FillType.ONE_CANCELS_OTHER || order.getFillType() != FillType.COMPLETED_CANCELS_OTHER))
                    updateParentOrderState(order.getParentOrder(), order, orderState);

                //TODO Order persitantce, keep getting TransientPropertyValueException  errors
                //PersitOrderFill(orderUpdate.getOrder());
                break;
            case TRIGGER:
                orderStateMap.put(order, orderState);
                stateOrderMap.get(orderState).add(order);
                // if all children have same state, set parent state.
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)
                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;
            case ROUTED:
                orderStateMap.put(order, orderState);
                stateOrderMap.get(orderState).add(order);
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)
                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;
            case PLACED:
                orderStateMap.put(order, orderState);
                stateOrderMap.get(orderState).add(order);
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)
                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;
            case PARTFILLED:
                orderStateMap.put(order, orderState);
                stateOrderMap.get(orderState).add(order);
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)
                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;
            case FILLED:
                orderStateMap.put(order, orderState);
                stateOrderMap.get(orderState).add(order);
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)

                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;
            case CANCELLING:
                if (order instanceof GeneralOrder) {
                    GeneralOrder generalOrder = (GeneralOrder) order;
                    handleCancelGeneralOrder(generalOrder);
                } else if (order instanceof SpecificOrder) {
                    SpecificOrder specificOrder = (SpecificOrder) order;
                    try {
                        handleCancelSpecificOrder(specificOrder);
                    } catch (OrderNotFoundException onf)

                    {
                        log.info("Order " + specificOrder + " Not found to cacnel");
                    }

                }
                break;
            case CANCELLED:
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)
                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;

            case EXPIRED:
                orderStateMap.put(order, orderState);
                stateOrderMap.get(orderState).add(order);
                ;
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)
                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;
            case REJECTED:
                orderStateMap.put(order, orderState);
                stateOrderMap.get(orderState).add(order);
                ;
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && order.getParentOrder().getOrderChildren().isEmpty()) {
                    updateParentOrderState(order.getParentOrder(), order, orderState);
                    break;
                }
                if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null && !order.getParentOrder().getOrderChildren().isEmpty())
                    for (Order childOrder : order.getParentOrder().getOrderChildren()) {
                        OrderState childState = getOrderState(childOrder);
                        if (childState != orderState)
                            break;

                        // if (order.getParentOrder() != null)
                        updateParentOrderState(order.getParentOrder(), order, orderState);
                        break;
                    }
                break;
        }

    }

    //@When("@Priority(9) select * from Fill")
    public void handleFillProcessing(Fill fill) {
        log.debug("BaseOrderService:handleFillProcessing Fill Recieved: " + fill);
        handleFill(fill);
        // service.submit(new handleFillRunnable(fill));
    }

    public void handleFill(Fill fill) {
        //  log.debug("BaseOrderService:handleFill Fill Recieved: " + fill);

        // UUID orderID = UUID.fromString(fill.getRemoteKey());
        SpecificOrder order = fill.getOrder();
        // = getSpecifcOrderFromStateMap(orderID);
        // if (order == null)
        //   return;

        //fill.setOrder(order);
        //order.addFill(fill);
        log.debug("persiting fill" + fill);
        fill.persit();
        fill.getPortfolio().merge(fill);
        log.debug("merging fill" + fill);
        fill.merge();
        //   portfolio.merge
        // portfolio.modifyPosition(transaction.getFill(), new Authorization("Fill for " + transaction.toString()));

        //  
        //TODO I need to update the stop order with the filled or closed out ammount. Might not be fully closed out, we only cancel the stop order when hte other order is fully filled

        //should have already been persisted by position handler
        // persitOrderFill(fill);

        //PersitOrderFill(order);
        FillType fillType = null;
        List<Order> allChildOrders = Collections.synchronizedList(new ArrayList<Order>());

        if (order.getParentOrder() != null || (order.getOrderChildren() != null && !order.getOrderChildren().isEmpty())) {
            fillType = order.getFillType();
            if (fillType == null)
                fillType = (order.getParentOrder() != null) ? order.getParentOrder().getFillType() : order.getFillType();

            //if we are filled and we have a stop order, we need to update the quanityt of the stop order to the unfilled quantity.
            //  F

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
                case ONE_CANCELS_OTHER:
                    SpecificOrder otherOrder;
                    GeneralOrder otherGeneralOrder;
                    if (order.getParentOrder() != null)

                        for (Order pairOrder : order.getParentOrder().getOrderChildren()) {
                            //for (Order pairSpecifcOrder : pairOrder.getChildren()) {

                            if (pairOrder instanceof SpecificOrder && pairOrder != order) {
                                otherOrder = (SpecificOrder) pairOrder;
                                if ((getOrderState(otherOrder) != OrderState.CANCELLED || getOrderState(otherOrder) != OrderState.CANCELLING))
                                    try {
                                        handleCancelSpecificOrder(otherOrder);
                                    } catch (OrderNotFoundException onfe) {
                                        log.info("Order Not Found:" + otherOrder);
                                    }
                            }

                            if (pairOrder instanceof GeneralOrder && pairOrder != order) {
                                otherGeneralOrder = (GeneralOrder) pairOrder;
                                try {
                                    if ((getOrderState(otherGeneralOrder) != OrderState.CANCELLED || getOrderState(otherGeneralOrder) != OrderState.CANCELLING))

                                        handleCancelGeneralOrder(otherGeneralOrder);
                                } catch (OrderNotFoundException onfe) {
                                    log.info("Order Not Found:" + otherGeneralOrder);
                                } catch (IllegalStateException onfe) {
                                    log.info("Order Not Placed:" + otherGeneralOrder);

                                }
                            }
                            //}
                            if (order.getParentOrder().getParentOrder() != null)
                                for (Order childPairOrder : order.getParentOrder().getParentOrder().getOrderChildren()) {
                                    // for (Order pairSpecifcOrder : pairOrder.getChildren()) {

                                    if (childPairOrder instanceof SpecificOrder && childPairOrder != order) {
                                        otherOrder = (SpecificOrder) childPairOrder;
                                        if ((getOrderState(otherOrder) != OrderState.CANCELLED || getOrderState(otherOrder) != OrderState.CANCELLING))

                                            try {
                                                handleCancelSpecificOrder(otherOrder);
                                            } catch (OrderNotFoundException onfe) {
                                                log.info("Order Not Found:" + otherOrder);
                                            }
                                    }
                                    if (childPairOrder instanceof GeneralOrder && childPairOrder != order.getParentOrder()) {
                                        otherGeneralOrder = (GeneralOrder) childPairOrder;
                                        if ((getOrderState(childPairOrder) != OrderState.CANCELLED || getOrderState(childPairOrder) != OrderState.CANCELLING))

                                            try {
                                                handleCancelGeneralOrder(otherGeneralOrder);
                                            } catch (OrderNotFoundException onfe) {
                                                log.info("Order Not Found:" + otherGeneralOrder);
                                            }
                                    }
                                    // }
                                }
                            break;

                        }
                    break;
                case COMPLETED_CANCELS_OTHER:
                    // GeneralOrder{id=99fc7f08-a48a-4701-8e84-7455e1f2b419, parentOrder=null, parentFill=a4276654-cc17-4b4e-ab05-8a0e93021f05, listing=BTC.USD.THISWEEK, volume=-44, unfilled volume=-44, limitPrice=233.62, comment=Stop Order with Price Target, position effect=CLOSE, type=STOP_LIMIT, execution instruction=MAKER, stop price=233.64, target price=244.46} Stop trade Entered at 233.64
                    // needs to be cancelled when this is filled
                    // SpecificOrder{ time=2015-02-16 06:22:31,id=7461a1d7-6230-4927-afc1-c4ccfa5498f5,remote key=7461a1d7-6230-4927-afc1-c4ccfa5498f5,parentOrder=99fc7f08-a48a-4701-8e84-7455e1f2b419,parentFill=
                    //{Id=a4276654-cc17-4b4e-ab05-8a0e93021f05,time=2015-02-16 06:21:51,PositionType=EXITING,Market=OKCOIN_THISWEEK:BTC.USD.THISWEEK,Price=235.44,Volume=44,Open Volume=44,Position Effect=OPEN,Comment=Long Entry Order,Order=eeff7554-6269-4a15-9c42-3132338bf14a,Parent Fill=}
                    //,portfolio=MarketMakerStrategy,market=OKCOIN_THISWEEK:BTC.USD.THISWEEK,unfilled volume=0,volumeCount=-44,limitPriceCount=235.61,PlacementCount=1,Comment=Long Exit with resting stop,Order Type=COMPLETED_CANCELS_OTHER,Position Effect=CLOSE,Execution Instruction=MAKER,averageFillPrice=235.9897727272727272727272727272727}

                    order.getParentFill().getAllOrdersByParentFill(allChildOrders);
                    for (Order childOrder : allChildOrders)
                        if (childOrder != order && childOrder.getFillType() == (FillType.COMPLETED_CANCELS_OTHER))
                            if ((getOrderState(childOrder) != OrderState.CANCELLED || getOrderState(childOrder) != OrderState.CANCELLING)
                                    && order.getUnfilledVolumeCount() == 0)
                                if (childOrder instanceof SpecificOrder) {
                                    SpecificOrder pairSpecificOrder = (SpecificOrder) childOrder;

                                    try {
                                        handleCancelSpecificOrder(pairSpecificOrder);
                                    } catch (OrderNotFoundException onfe) {
                                        log.info("Order Not Found:" + pairSpecificOrder);
                                    }
                                }

                                else if (childOrder instanceof GeneralOrder) {
                                    GeneralOrder pairGeneralOrder = (GeneralOrder) childOrder;

                                    try {
                                        handleCancelGeneralOrder(pairGeneralOrder);
                                    } catch (OrderNotFoundException onfe) {
                                        log.info("Order Not Found:" + pairGeneralOrder);
                                    } catch (IllegalStateException onfe) {
                                        log.info("Order Not Placed:" + pairGeneralOrder);
                                    }

                                }

                    break;

                case STOP_LOSS:
                    //Place a stop order at the stop price

                    break;
                default:
                    break;

            }
        }
        // always create teh stops
        GeneralOrder stopOrder = buildStopLimitOrder(fill);
        //    stopOrder.persit();
        if (stopOrder != null && order.getPositionEffect() == (PositionEffect.OPEN)) {

            try {
                placeOrder(stopOrder);
                log.info("Placed Stop order " + stopOrder);
            } catch (Throwable e) {
                log.info("Unable to place Stop order " + stopOrder + ". Full stack trace" + e);
            }

        }
        /*        TransactionType oppositeSide = (order.getParentFill() != null && order.getParentFill().isLong()) ? TransactionType.SELL : TransactionType.BUY;
                if ((order.getPositionEffect() == null && order.getTransactionType() == oppositeSide) || order.getPositionEffect() == PositionEffect.CLOSE) {
                    // we know this is a closing trade
                    // need to update any stop orders with new quanity
                    ArrayList<Order> allChildOrders = new ArrayList<Order>();

                    getAllOrdersByParentFill(order.getParentFill(), allChildOrders);
                    for (Order childOrder : allChildOrders)
                        if (childOrder != order) {
                            if ((childOrder.getPositionEffect() == null && childOrder.getTransactionType() == oppositeSide)
                                    || childOrder.getPositionEffect() == PositionEffect.CLOSE) {

                                log.info("updating quanity to : " + order.getUnfilledVolume() + " for  order: " + childOrder);
                                updateOrderQuantity(childOrder, order.getUnfilledVolume());

                            }
                        }

                }*/
        // 

        if (log.isInfoEnabled())
            log.info("Received Fill " + fill);
        OrderState state = orderStateMap.get(order);
        if (state == null) {
            log.warn("Untracked order " + order);
            state = OrderState.PLACED;
        }
        if (state == (OrderState.NEW))
            log.warn("Fill received for Order in NEW state: skipping PLACED state");
        if (state.isOpen()) {
            OrderState newState;
            if (order.isFilled()) {
                PositionType newFillState = (fill.getVolumeCount() > 0) ? PositionType.LONG : PositionType.SHORT;
                fill.setPositionType(newFillState);
                if (order.getParentFill() != null && (order.getVolume().compareTo(order.getParentFill().getVolume().negate()) == 0))
                    order.getParentFill().setPositionType(PositionType.FLAT);

                ArrayList<Order> triggerOrderByParentFill = null;
                if (order.getParentFill() != null) {
                    //  for (Order childOrder : order.getParentFill().getFillChildOrders())
                    //    if (childOrder.getUnfilledVolume().isZero())
                    triggerOrderByParentFill = triggerOrders.get(order.getParentFill());
                    if (triggerOrderByParentFill != null)
                        for (Order triggerOrder : triggerOrderByParentFill)
                            if (triggerOrder.getUnfilledVolume().isZero()) {
                                removeTriggerOrder(triggerOrder);

                                updateOrderState(triggerOrder, OrderState.EXPIRED, true);
                            }
                }
                // let;s pull any stop order with this fill
                // so 
                // handleCancelAllTriggerOrdersByParentFill(order.getParentFill());
                newState = OrderState.FILLED;
            } else
                newState = OrderState.PARTFILLED;
            updateOrderState(order, newState, true);
        }
        context.route(fill);
        CreateTransaction(fill, true);

    }

    private static final Comparator<Fill> timeOrderIdComparator = new Comparator<Fill>() {
        @Override
        public int compare(Fill event, Fill event2) {
            int sComp = event.getTime().compareTo(event2.getTime());
            if (sComp != 0) {
                return sComp;
            } else {
                return (event.getRemoteKey().compareTo(event2.getRemoteKey()));

            }
        }
    };

    private class handleFillRunnable implements Runnable {
        private final Fill fill;

        // protected Logger log;

        public handleFillRunnable(Fill fill) {
            this.fill = fill;

        }

        @Override
        public void run() {
            handleFill(fill);

        }
    }

    private GeneralOrder buildStopLimitOrder(Fill fill) {

        //      GeneralOrder order = generalOrderFactory.create(fill.getOrder().getPortfolio(), this));

        //        OrderBuilder stoporder = new OrderBuilder(fill.getOrder().getPortfolio(), this);

        if ((fill.getPositionEffect() != null && fill.getPositionEffect() != PositionEffect.CLOSE)
                && (fill.getOrder().getParentOrder().getStopPrice() != null || fill.getOrder().getParentOrder().getStopAmount() != null || !fill
                        .getUnfilledVolume().isZero())) {
            Amount stopPrice;
            Amount targetPrice;
            DiscreteAmount targetPriceDiscrete;
            DiscreteAmount stopPriceDiscrete;
            BigDecimal bdTargetPrice;
            BigDecimal bdStopPrice;
            BigDecimal bdLimitPrice;
            GeneralOrder stopOrder;
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
            // String comment = (fill.isLong()? "")

            bdLimitPrice = (fill.getVolume().isNegative()) ? stopPriceDiscrete.increment(2).asBigDecimal() : stopPriceDiscrete.decrement(2).asBigDecimal();
            if (targetPrice != null) {
                targetPriceDiscrete = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(targetPrice.asBigDecimal(), fill.getMarket().getPriceBasis()),
                        fill.getMarket().getPriceBasis());
                bdTargetPrice = targetPriceDiscrete.asBigDecimal();

                stopOrder = generalOrderFactory.create(context.getTime(), fill, fill.getMarket(), bdVolume.negate(), FillType.STOP_LIMIT);

                String comment = (bdVolume.compareTo(BigDecimal.ZERO) > 0) ? "Long Stop Order with Price Target" : "Short Stop Order with Price Target";
                stopOrder.withComment(comment).withTargetPrice(bdTargetPrice).withStopPrice(bdStopPrice).withLimitPrice(bdLimitPrice)
                        .withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(fill.getOrder().getExecutionInstruction());
                fill.setTargetPriceCount(targetPriceDiscrete.getCount());
                //  fill.setTargetAmountCount(targetAmountCount);

            } else {

                stopOrder = generalOrderFactory.create(context.getTime(), fill, fill.getMarket(), bdVolume.negate(), FillType.STOP_LIMIT);
                String comment = (bdVolume.compareTo(BigDecimal.ZERO) > 0) ? "Long Stop Order" : "Short Stop Order";

                stopOrder.withComment(comment).withStopPrice(bdStopPrice).withLimitPrice(bdLimitPrice).withPositionEffect(PositionEffect.CLOSE)
                        .withExecutionInstruction(fill.getOrder().getExecutionInstruction());
            }
            stopOrder.copyCommonFillProperties(fill);
            fill.setStopPriceCount(stopPriceDiscrete.getCount());

            Collection<SpecificOrder> parentFillOrders = Collections.synchronizedList(new ArrayList<SpecificOrder>());
            fill.getAllSpecificOrdersByParentFill(fill, parentFillOrders);
            for (Order childOrder : parentFillOrders) {
                if (childOrder instanceof SpecificOrder) {
                    stopOrder.addChildOrder(childOrder);
                    childOrder.setParentOrder(stopOrder);
                }
            }
            return stopOrder;
        }
        return null;
    }

    protected void handleGeneralOrder(GeneralOrder generalOrder) {
        Market market;
        SpecificOrder specificOrder;
        // generalOrder.persit();

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
        try {
            switch (generalOrder.getFillType()) {
                case GOOD_TIL_CANCELLED:
                    throw new NotImplementedException();
                case GTC_OR_MARGIN_CAP:
                    throw new NotImplementedException();
                case CANCEL_REMAINDER:
                    throw new NotImplementedException();
                case ONE_CANCELS_OTHER:
                    specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
                    if (specificOrder == null)
                        break;
                    updateOrderState(generalOrder, OrderState.ROUTED, true);
                    log.info("Routing OCO order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
                    placeOrder(specificOrder);

                    break;
                case COMPLETED_CANCELS_OTHER:
                    specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
                    if (specificOrder == null)
                        break;
                    updateOrderState(generalOrder, OrderState.ROUTED, true);
                    log.info("Routing CCO order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
                    placeOrder(specificOrder);

                    break;
                case LIMIT:
                    specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
                    if (specificOrder == null)
                        break;
                    updateOrderState(generalOrder, OrderState.ROUTED, true);
                    log.info("Routing Limit order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
                    placeOrder(specificOrder);
                    break;
                case MARKET:
                    specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
                    if (specificOrder == null)
                        break;
                    updateOrderState(generalOrder, OrderState.ROUTED, true);
                    log.info("Routing Limit order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
                    placeOrder(specificOrder);
                    break;
                case STOP_LIMIT:
                    addTriggerOrder(generalOrder);
                    updateOrderState(generalOrder, OrderState.TRIGGER, true);
                    if (generalOrder.getStopPrice() != null)
                        log.info(generalOrder + " Stop trade Entered at " + generalOrder.getStopPrice());
                    if (generalOrder.getTargetPrice() != null)
                        log.info("Target trade Entered at " + generalOrder.getTargetPrice());
                    break;
                case TRAILING_STOP_LIMIT:
                    addTriggerOrder(generalOrder);
                    updateOrderState(generalOrder, OrderState.TRIGGER, true);
                    log.info("Trailing Stop trade Entered at " + generalOrder.getStopPrice());
                    break;
                case STOP_LOSS:
                    if (generalOrder.getTargetPrice() != null) {
                        addTriggerOrder(generalOrder);
                        updateOrderState(generalOrder, OrderState.TRIGGER, true);
                        log.info("Trigger Order Entered at " + generalOrder.getTargetPrice());
                        break;
                    } else {
                        specificOrder = convertGeneralOrderToSpecific(generalOrder, generalOrder.getMarket());
                        if (specificOrder == null)
                            break;
                        updateOrderState(generalOrder, OrderState.ROUTED, true);
                        log.info("Routing Stop Loss order " + generalOrder + " to " + generalOrder.getMarket().getExchange().getSymbol());
                        placeOrder(specificOrder);
                        break;
                    }
            }
        } catch (Throwable e) {
            log.info("Unable to place general order  " + generalOrder + ". Full stack trace" + e);
        }

    }

    //    @SuppressWarnings("ConstantConditions")
    //    @When("@Priority(9) select * from Trade")
    //    private void handleTrade(Trade t) {
    //
    //    }

    @When("@Priority(6) select * from Book(Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    private void handleBook(Book b) {
        log.trace("BasedOrderSerivce: handleBook: Book Recieved: " + b);
        updateRestingOrders(b);

        //  service.submit(new handleBookRunnable(b));
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
    private synchronized void updateRestingOrders(Book b) {
        if (!getTradingEnabled()) {
            log.info(this.getClass().getSimpleName() + ":updateRestingOrders Not Updating orders as trading mode disabled");
            return;
        }

        Offer offer;
        //  synchronized (lock) {
        Offer ask = b.getBestAsk();

        Offer bid = b.getBestBid();
        if (bid == null || bid.getPrice().isZero() || ask == null || ask.getPrice().isZero())
            return;
        log.trace("Bid price for trigger: " + bid.getPrice() + ". Ask price for trigger: " + ask.getPrice());

        Iterator<Order> itOrder = getPendingOrders().iterator();
        while (itOrder.hasNext()) {
            Order order = itOrder.next();
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;
                if (pendingOrder.getExpiryTime() != null && context.getTime().isAfter(pendingOrder.getExpiryTime())) {
                    try {
                        if (getOrderState(pendingOrder).isOpen()) {
                            log.info("Order Expired with state " + getOrderState(pendingOrder) + ". Cancelling Specifc Order: " + pendingOrder);
                            handleCancelSpecificOrder(pendingOrder);
                        }
                        //cancelled = false;
                    } catch (Exception e) {
                        log.error("attmept to cancel an order that was not pending :" + pendingOrder, e);
                    }

                }
            }
            // for (SpecificOrder pendingOrder : getPendingOrders()) {

            /*            if (pendingOrder.getMarket().equals(b.getMarket())
                                &&
                                //pendingOrder.getParentOrder().getFillType() == FillType.STOP_LIMIT) 
                                (pendingOrder.getPositionEffect() == PositionEffect.CLOSE) && (pendingOrder.getUnfilledVolumeCount() != 0)
                                && pendingOrder.getExecutionInstruction() == ExecutionInstruction.TAKER) {
                            boolean cancelled = true;
                            pendingOrder.setPlacementCount(pendingOrder.getPlacementCount() + 1);

                            if (pendingOrder.isAsk())
                                offer = (pendingOrder.getExecutionInstruction() == ExecutionInstruction.MAKER) ? quotes.getLastAskForMarket(pendingOrder.getMarket())
                                        : quotes.getLastBidForMarket(pendingOrder.getMarket());
                            else
                                offer = (pendingOrder.getExecutionInstruction() == ExecutionInstruction.MAKER) ? quotes.getLastBidForMarket(pendingOrder.getMarket())
                                        : quotes.getLastAskForMarket(pendingOrder.getMarket());

                            DiscreteAmount limitPrice = (pendingOrder.getVolume().isNegative()) ? bid.getPrice().decrement(
                                    (long) (Math.pow(pendingOrder.getPlacementCount(), 4))) : ask.getPrice().increment(
                                    (long) (Math.pow(pendingOrder.getPlacementCount(), 4)));

                            // we neeed to cancle order
                            //TODO surround with try catch so we only insert if we cancel
                            //TODO  we only need to do this is the best bid/best ask has changed vs previous
                            log.debug("canceling existing order :" + pendingOrder);
                            try {
                                if (!getOrderState(pendingOrder).isCancelled()) {
                                    log.info("Canceling Closing Orders " + pendingOrder);
                                    handleCancelSpecificOrder(pendingOrder);
                                    //cancelled = false;
                                }
                                // if (cancelled) {

                                // OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(pendingOrder.getPortfolio()).create(pendingOrder);
                                SpecificOrder order = specificOrderFactory.create(pendingOrder);
                              //  order.setLimitPriceCount(limitPrice.getCount());
                                order.setPlacementCount(pendingOrder.getPlacementCount());
                                DiscreteAmount volume;
                                if (pendingOrder.getParentOrder() != null) {
                                    volume = (pendingOrder.getParentOrder().getUnfilledVolume().compareTo(pendingOrder.getVolume()) < 0) ? pendingOrder.getParentOrder()
                                            .getUnfilledVolume().toBasis(pendingOrder.getMarket().getVolumeBasis(), Remainder.DISCARD) : pendingOrder.getVolume().toBasis(
                                            pendingOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);

                                    // DiscreteAmount volume = (generalOrder.getParentFill() == null) ? generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD)
                                    //       : generalOrder.getParentFill().getOpenVolume().negate().toBasis(market.getVolumeBasis(), Remainder.DISCARD);
                                    order.setVolumeCount(volume.getCount());
                                }

                                placeOrder(order);
                                log.info("submitted new order :" + order);
                                // }

                            } catch (Exception e) {
                                log.error("attmept to cancel an order that was not pending :" + pendingOrder, e);
                            }

                            //PersistUtil.merge(pendingOrder);
                            //  order.persit();
                            //  PersistUtil.merge(pendingOrder);
                            // pendingOrder.persit();

                        }*/
        }
        // }

        // synchronized (lock) {
        /*        ArrayList<Event> triggeredParents = new ArrayList<Event>();
                for (Event parentKey : triggerOrders.keySet()) {
                    ArrayList<Order> triggeredOrders = new ArrayList<Order>();
                    for (Order triggeredOrder : triggerOrders.get(parentKey)) {
                        log.trace("determining to trigger order:" + triggeredOrder);
                        if (triggeredOrder.getExpiryTime() != null && context.getTime().isAfter(triggeredOrder.getExpiryTime())) {
                            log.info("Expired Trigger order:" + triggeredOrder);
                            triggeredOrders.add(triggeredOrder);
                        }
                    }
                    triggerOrders.get(parentKey).removeAll(triggeredOrders);
                    if (triggerOrders.get(parentKey).isEmpty())
                        triggeredParents.add(parentKey);
                }
                for (Event parent : triggeredParents)
                    triggerOrders.remove(parent);*/

        List<Event> triggeredParents = Collections.synchronizedList(new ArrayList<Event>());
        for (Event parentKey : triggerOrders.keySet()) {
            //  Iterator<Event> itEvent = triggerOrders.keySet().iterator();
            //while (itEvent.hasNext()) {
            // closing position
            //  Event parentKey = itEvent.next();
            ArrayList<Order> triggeredOrders = new ArrayList<Order>();
            for (Order triggeredOrder : triggerOrders.get(parentKey)) {
                log.trace("determining to trigger order:" + triggeredOrder);
                if (triggeredOrder.getExpiryTime() != null && context.getTime().isAfter(triggeredOrder.getExpiryTime())) {
                    log.info("Trigger order Expired, cancelling general Order:" + triggeredOrder);
                    triggeredOrders.add(triggeredOrder);
                    continue;
                }
                if (triggeredOrder.getMarket() != null && b.getMarket() != null && triggeredOrder.getMarket().equals(b.getMarket())) {
                    if (triggeredOrder.isBid()) {
                        Long triggerPrice = (triggeredOrder.getExecutionInstruction() == (ExecutionInstruction.MAKER)) ? bid.getPriceCount() : ask
                                .getPriceCount();

                        if ((triggeredOrder.getStopPrice() != null && (triggerPrice >= (triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket()
                                .getPriceBasis(), Remainder.ROUND_EVEN)).getCount()))
                                || (triggeredOrder.getTargetPrice() != null && (((triggeredOrder.getPositionEffect() == null || triggeredOrder
                                        .getPositionEffect() == (PositionEffect.CLOSE)) && triggerPrice <= (triggeredOrder.getTargetPrice().toBasis(
                                        triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()) || (triggeredOrder.getPositionEffect() == (PositionEffect.OPEN) && triggerPrice >= (triggeredOrder
                                        .getTargetPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount())))) {
                            //convert order to specfic order

                            log.info("triggered order:" + triggeredOrder);
                            if (triggeredOrder.getParentFill() != null)
                                log.info("triggered order:"
                                        + triggeredOrder
                                        + " parent fill unfilled volume:"
                                        + (triggeredOrder.getParentFill().getUnfilledVolume() != null ? (triggeredOrder.getParentFill().getUnfilledVolume() + " parent fill position type:")
                                                : "")

                                        //  triggeredOrder.getParentFill().getUnfilledVolume() + " parent fill position type:"
                                        + (triggeredOrder.getParentFill().getPositionType() != null ? (triggeredOrder.getParentFill().getPositionType() + " parent Fill child orders: ")
                                                : "")

                                        //   + triggeredOrder.getParentFill().getPositionType() + " parent Fill child orders: "
                                        + (triggeredOrder.getParentFill().getFillChildOrders() != null ? (triggeredOrder.getParentFill().getFillChildOrders())
                                                : ""));

                            //  + triggeredOrder.getParentFill().getFillChildOrders());
                            //  itTriggeredOrder.remove();
                            //   triggerOrders.get(parentKey).remove(triggeredOrder);
                            // if (triggerOrders.get(parentKey).isEmpty())
                            //   triggeredParents.add(parentKey);

                            SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) triggeredOrder, triggeredOrder.getMarket());

                            if (specificOrder == null) {
                                log.info("triggered order:" + triggeredOrder + " not convereted to specific order");
                                triggeredOrders.add(triggeredOrder);
                                updateOrderState(triggeredOrder, OrderState.REJECTED, true);
                                continue;
                            }
                            specificOrder.persit();
                            log.info("triggered order:" + triggeredOrder + " convereted to specific order " + specificOrder);

                            //   DiscreteAmount volume = (triggeredOrder.getUnfilledVolume().compareTo(specificOrder.getVolume()) < 0) ? : specificOrder.getVolume()
                            //         .toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);

                            // DiscreteAmount volume = (generalOrder.getParentFill() == null) ? generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD)
                            //       : generalOrder.getParentFill().getOpenVolume().negate().toBasis(market.getVolumeBasis(), Remainder.DISCARD);
                            if (!specificOrder.getVolume().equals(
                                    triggeredOrder.getUnfilledVolume().toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD)))
                                log.debug("Unfilled Volume: " + triggeredOrder + " not the same as trigger volume:" + specificOrder);

                            specificOrder.setVolumeCount(triggeredOrder.getUnfilledVolume()
                                    .toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD).getCount());
                            specificOrder.setExecutionInstruction(ExecutionInstruction.TAKER);

                            if (triggeredOrder.getPositionEffect() == (PositionEffect.OPEN)) {
                                specificOrder.setPositionEffect(PositionEffect.OPEN);
                                specificOrder.setFillType(FillType.LIMIT);
                                //specificOrder.setFillType(FillType.MARKET);
                                //specificOrder.setLimitPriceCount(0);
                            } else {
                                specificOrder.setPositionEffect(PositionEffect.CLOSE);
                                specificOrder.setFillType(FillType.MARKET);
                                specificOrder.setLimitPriceCount(0);
                            }
                            // we need to set the sepecifc order children to any working open orders.
                            //TODO: loop over open order by parent fill and link them so we know to cancel them is this get's filled first.
                            // specificOrder.addChild(this);
                            // this.setParentOrder(specificOrder);
                            //specificOrder.setFillType(FillType.MARKET);
                            if (triggeredOrder.getStopPrice() != null
                                    && (triggerPrice >= (triggeredOrder.getStopPrice()
                                            .toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount())) {

                                specificOrder.setComment("Short Stop Order with Stop Price");
                                //specificOrder.setExecutionInstruction(ExecutionInstruction.TAKER);
                            } else if (triggeredOrder.getPositionEffect() == (PositionEffect.CLOSE))
                                specificOrder.setComment("Short Stop Order with Target Price");
                            else if (triggeredOrder.getPositionEffect() == (PositionEffect.OPEN))
                                specificOrder.setComment("Long Entry Order with Target Price");
                            specificOrder.persit();
                            log.info("At " + context.getTime() + " Routing trigger order " + triggeredOrder + " to "
                                    + triggeredOrder.getMarket().getExchange().getSymbol());

                            //TODO need 
                            // placeOrder(specificOrder);
                            //   OrderState newState = order.isFilled() ? OrderState.FILLED : OrderState.PARTFILLED;
                            // updateOrderState(triggeredOrder, OrderState.ROUTED);
                            //context.route(new PositionUpdate(null, specificOrder.getMarket(), PositionType.SHORT, PositionType.EXITING));

                            if (specificOrder.getVolume() != null && !specificOrder.getVolume().isZero() && specificOrder.getVolume().isPositive()) {
                                // need to cancel any order with same parent fill id
                                log.info("Cancelling working orders for parent fill: " + triggeredOrder.getParentFill());
                                try {
                                    handleCancelSpecificOrderByParentFill(triggeredOrder.getParentFill());
                                } catch (OrderNotFoundException e) {
                                    log.info("order not found");
                                }

                                try {
                                    placeOrder(specificOrder);

                                    triggeredOrders.add(triggeredOrder);

                                    if (triggeredOrder.getParentFill() != null)
                                        triggeredOrder.getParentFill().setPositionType(PositionType.EXITING);

                                    log.info(triggeredOrder + " triggered as specificOrder " + specificOrder);
                                    context.publish(new PositionUpdate(null, specificOrder.getMarket(), PositionType.SHORT, PositionType.EXITING));
                                } catch (Throwable e) {
                                    log.error("Unable to place trigged order " + specificOrder);
                                }
                            } else {
                                log.info(triggeredOrder
                                        + " not triggered as zero volume"
                                        + (triggeredOrder.getParentFill() != null ? "fill open volume" + triggeredOrder.getParentFill().getOpenVolume()
                                                : "trigger order volume" + triggeredOrder.getVolume()));
                                updateOrderState(triggeredOrder, OrderState.EXPIRED, true);

                            }
                            //i = Math.min(0, i - 1);
                            //  triggerOrders.remove(triggerOrder);

                        } else if (triggeredOrder.getTrailingStopPrice() != null) {
                            //current price is less than the stop price so I will update the stop price
                            long stopPrice = Math.min((triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                                    .getCount(), (triggerPrice + (triggeredOrder.getTrailingStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(),
                                    Remainder.ROUND_EVEN)).getCount()));
                            DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggeredOrder.getMarket().getPriceBasis()));
                            triggeredOrder.setStopAmount(stopDiscrete);
                            triggeredOrder.merge();

                        }

                    }
                    if (triggeredOrder.isAsk()) {
                        Long triggerPrice = triggeredOrder.getExecutionInstruction() == (ExecutionInstruction.MAKER) ? ask.getPriceCount() : bid
                                .getPriceCount();

                        if ((triggeredOrder.getMarket() != null)
                                &&

                                ((triggeredOrder.getStopPrice() != null && (triggerPrice <= (triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket()
                                        .getPriceBasis(), Remainder.ROUND_EVEN)).getCount())) || (triggeredOrder.getTargetPrice() != null && (((triggeredOrder
                                        .getPositionEffect() == null || triggeredOrder.getPositionEffect() == (PositionEffect.CLOSE)) && triggerPrice >= (triggeredOrder
                                        .getTargetPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()) || (triggeredOrder
                                        .getPositionEffect() == (PositionEffect.OPEN) && triggerPrice <= (triggeredOrder.getTargetPrice().toBasis(
                                        triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount()))))) {
                            //Place order

                            log.info("triggered order:" + triggeredOrder);
                            if (triggeredOrder.getParentFill() != null)
                                log.info("triggered order:"
                                        + triggeredOrder
                                        + " parent fill unfilled volume:"
                                        + (triggeredOrder.getParentFill().getUnfilledVolume() != null ? (triggeredOrder.getParentFill().getUnfilledVolume() + " parent fill position type:")
                                                : "")

                                        //  triggeredOrder.getParentFill().getUnfilledVolume() + " parent fill position type:"
                                        + (triggeredOrder.getParentFill().getPositionType() != null ? (triggeredOrder.getParentFill().getPositionType() + " parent Fill child orders: ")
                                                : "")

                                        //   + triggeredOrder.getParentFill().getPositionType() + " parent Fill child orders: "
                                        + (triggeredOrder.getParentFill().getFillChildOrders() != null ? (triggeredOrder.getParentFill().getFillChildOrders())
                                                : ""));

                            // itTriggeredOrder.remove();
                            //  triggerOrders.get(parentKey).remove(triggeredOrder);

                            //   triggerOrders.remove(parentKey);

                            SpecificOrder specificOrder = convertGeneralOrderToSpecific((GeneralOrder) triggeredOrder, triggeredOrder.getMarket());

                            if (specificOrder == null) {
                                log.info("triggered order:" + triggeredOrder + " not convereted to specific order");
                                triggeredOrders.add(triggeredOrder);
                                updateOrderState(triggeredOrder, OrderState.REJECTED, true);
                                continue;
                            }
                            specificOrder.persit();
                            log.info("triggered order:" + triggeredOrder + " convereted to specific order " + specificOrder);
                            // ask order so volumes are -10 > -4 then t
                            //   DiscreteAmount volume = (triggeredOrder.getUnfilledVolume().compareTo(specificOrder.getVolume()) > 0) ? triggeredOrder
                            //         .getUnfilledVolume().toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD) : specificOrder.getVolume()
                            //       .toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);

                            // DiscreteAmount volume = (generalOrder.getParentFill() == null) ? generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD)
                            //       : generalOrder.getParentFill().getOpenVolume().negate().toBasis(market.getVolumeBasis(), Remainder.DISCARD);
                            // specificOrder.setVolumeCount(triggeredOrder.getUnfilledVolume()
                            //       .toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD).getCount());

                            if (!specificOrder.getVolume().equals(
                                    triggeredOrder.getUnfilledVolume().toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD)))
                                log.debug("Unfilled Volume: " + triggeredOrder + " not the same as trigger volume:" + specificOrder);

                            specificOrder.setVolumeCount(triggeredOrder.getUnfilledVolume()
                                    .toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD).getCount());

                            specificOrder.setExecutionInstruction(ExecutionInstruction.TAKER);
                            if (triggeredOrder.getPositionEffect() == (PositionEffect.OPEN)) {
                                specificOrder.setPositionEffect(PositionEffect.OPEN);
                                specificOrder.setFillType(FillType.LIMIT);
                                //specificOrder.setFillType(FillType.MARKET);
                                //specificOrder.setLimitPriceCount(0);
                            } else {
                                specificOrder.setPositionEffect(PositionEffect.CLOSE);
                                specificOrder.setFillType(FillType.MARKET);
                                specificOrder.setLimitPriceCount(0);
                            }

                            if (triggeredOrder.getStopPrice() != null
                                    && (triggerPrice <= (triggeredOrder.getStopPrice()
                                            .toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)).getCount())) {
                                specificOrder.setComment("Long Stop Order with Stop Price");

                            } else if (triggeredOrder.getPositionEffect() == (PositionEffect.CLOSE))
                                specificOrder.setComment("Long Stop Order with Target Price");
                            else if (triggeredOrder.getPositionEffect() == (PositionEffect.OPEN))
                                specificOrder.setComment("Short Entry Order with Target Price");

                            log.info("At " + context.getTime() + " Routing trigger order " + triggeredOrder + " to " + specificOrder + " to "
                                    + specificOrder.getMarket().getExchange().getSymbol());
                            // updateOrderState(triggeredOrder, OrderState.ROUTED);

                            if (specificOrder.getVolume() != null && !specificOrder.getVolume().isZero() && specificOrder.getVolume().isNegative()) {
                                log.info("Cancelling working orders for parent fill: " + triggeredOrder.getParentFill());
                                try {
                                    handleCancelSpecificOrderByParentFill(triggeredOrder.getParentFill());
                                } catch (OrderNotFoundException e) {
                                    log.info("order not found");
                                }

                                try {
                                    placeOrder(specificOrder);

                                    triggeredOrders.add(triggeredOrder);
                                    if (triggeredOrder.getParentFill() != null)
                                        triggeredOrder.getParentFill().setPositionType(PositionType.EXITING);

                                    log.info(triggeredOrder + " triggered as specificOrder " + specificOrder);
                                    context.publish(new PositionUpdate(null, specificOrder.getMarket(), PositionType.LONG, PositionType.EXITING));
                                } catch (Throwable e) {
                                    // TODO Auto-generated catch block
                                    log.error("Unable place triggered order" + specificOrder);
                                }
                            } else {
                                log.info(triggeredOrder
                                        + " not triggered as zero volume"
                                        + (triggeredOrder.getParentFill() != null ? "fill open volume" + triggeredOrder.getParentFill().getOpenVolume()
                                                : "trigger order volume" + triggeredOrder.getVolume()));
                                updateOrderState(triggeredOrder, OrderState.EXPIRED, true);

                            }

                            // portfolioService.publishPositionUpdate(new Position(triggeredOrder.getPortfolio(), triggeredOrder.getMarket().getExchange(), triggeredOrder.getMarket(), triggeredOrder.getMarket().getBase(),
                            //       DecimalAmount.ZERO, DecimalAmount.ZERO));

                            //i = Math.min(0, i - 1);
                            //triggeredOrders.add(triggeredOrder);

                        } else if (triggeredOrder.getTrailingStopPrice() != null) {
                            //&& ((bid.getPriceCount() + order.getTrailingStopPrice().getCount() > (order.getStopPrice().getCount())))) {
                            //current price is less than the stop price so I will update the stop price
                            long stopPrice = Math.max((triggeredOrder.getStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                                    .getCount(), (triggerPrice - (triggeredOrder.getTrailingStopPrice().toBasis(triggeredOrder.getMarket().getPriceBasis(),
                                    Remainder.ROUND_EVEN)).getCount()));
                            DecimalAmount stopDiscrete = DecimalAmount.of(new DiscreteAmount(stopPrice, triggeredOrder.getMarket().getPriceBasis()));
                            triggeredOrder.setStopAmount(stopDiscrete);
                            triggeredOrder.merge();

                        }

                    }

                }
            }
            triggerOrders.get(parentKey).removeAll(triggeredOrders);
            if (triggerOrders.get(parentKey).isEmpty())
                triggeredParents.add(parentKey);

        }
        for (Event parent : triggeredParents)
            triggerOrders.remove(parent);
        //triggerOrders.removeAll(triggeredOrders);
        //  }

    }

    private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, Market market) {
        if (generalOrder.getVolume().isZero())

            return null;
        DiscreteAmount volume = generalOrder.getUnfilledVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);

        DiscreteAmount discreteLimit;
        DiscreteAmount discreteMarket;
        DiscreteAmount discreteStop = null;
        RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
        DecimalAmount limitPrice;
        if (generalOrder.getTargetPrice() != null)
            limitPrice = generalOrder.getTargetPrice();
        else
            limitPrice = generalOrder.getLimitPrice();

        final DecimalAmount marketPrice = generalOrder.getMarketPrice();

        final DecimalAmount stopPrice = generalOrder.getStopPrice();
        final DecimalAmount trailingStopPrice = generalOrder.getTrailingStopPrice();
        DecimalAmount paretnFill = generalOrder.getTrailingStopPrice();

        // the volume will already be negative for a sell order

        SpecificOrder specificOrder = specificOrderFactory.create(context.getTime(), generalOrder.getPortfolio(), market, volume, generalOrder,
                generalOrder.getComment());

        // Market markettest = specificOrder.getMarket();
        specificOrder.withParentFill(generalOrder.getParentFill());
        specificOrder.withPositionEffect(generalOrder.getPositionEffect());
        specificOrder.withExecutionInstruction(generalOrder.getExecutionInstruction());

        switch (generalOrder.getFillType()) {
            case GOOD_TIL_CANCELLED:
                break;
            case GTC_OR_MARGIN_CAP:
                break;
            case CANCEL_REMAINDER:
                break;
            case LIMIT:
                discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                specificOrder.withLimitPrice(discreteLimit);
                break;
            case STOP_LIMIT:
                //we will put the stop order in at best bid or best ask
                SpecificOrder stopOrder = specificOrder;
                //  stopOrder.persit();
                if (stopOrder.isBid())
                    discreteStop = stopOrder.getExecutionInstruction() == (ExecutionInstruction.MAKER) ? quotes.getLastBidForMarket(stopOrder.getMarket())
                            .getPrice() : quotes.getLastAskForMarket(stopOrder.getMarket()).getPrice();
                if (stopOrder.isAsk())
                    discreteStop = stopOrder.getExecutionInstruction() == (ExecutionInstruction.MAKER) ? quotes.getLastBidForMarket(stopOrder.getMarket())
                            .getPrice() : quotes.getLastAskForMarket(stopOrder.getMarket()).getPrice();

                if (discreteStop == null)
                    break;
                // discreteStop = offer.getPrice();
                if (limitPrice != null) {
                    discreteLimit = volume.isNegative() ? discreteStop.decrement(4) : discreteStop.increment(4);
                    specificOrder.withLimitPrice(discreteLimit);
                } else {
                    discreteMarket = marketPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                    specificOrder.withMarketPrice(discreteMarket);
                }

                break;
            case TRAILING_STOP_LIMIT:
                break;
            case STOP_LOSS:
                if (limitPrice != null) {
                    discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                    specificOrder.withLimitPrice(discreteLimit);
                } else {
                    discreteMarket = marketPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                    specificOrder.withMarketPrice(discreteMarket);
                }
                break;
            case ONE_CANCELS_OTHER:
                //  builder.withFillType(FillType.ONE_CANCELS_OTHER);
                if (limitPrice != null) {
                    discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                    specificOrder.withLimitPrice(discreteLimit);
                } else {
                    discreteMarket = marketPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                    specificOrder.withMarketPrice(discreteMarket);
                }

                break;
            case COMPLETED_CANCELS_OTHER:
                //  builder.withFillType(FillType.COMPLETED_CANCELS_OTHER);

                if (limitPrice != null) {
                    discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                    specificOrder.withLimitPrice(discreteLimit);
                } else {
                    discreteMarket = marketPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
                    specificOrder.withMarketPrice(discreteMarket);
                }

                break;
        }

        specificOrder.copyCommonOrderProperties(generalOrder);
        //specificOrder.persit();
        return specificOrder;
    }

    protected void reject(Order order, String message) {
        log.warn("Order " + order + " rejected: " + message);
        updateOrderState(order, OrderState.REJECTED, false);
    }

    protected abstract void handleSpecificOrder(SpecificOrder specificOrder) throws Throwable;

    protected abstract boolean cancelSpecificOrder(SpecificOrder specificOrder);

    @Override
    public synchronized void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> ordersToCancel = new ArrayList<>();
        // synchronized (lock) {
        for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
            Order order = it.next();
            if (order instanceof SpecificOrder) {
                SpecificOrder specificOrder = (SpecificOrder) order;
                if (specificOrder.getMarket().equals(market)) {
                    ordersToCancel.add(specificOrder);
                    log.info("handleCancelAllSpecificOrders cancelling order : " + specificOrder);
                }
            }
        }
        Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(ordersToCancel);

        for (Order cancelledOrder : cancelledOrders) {
            log.info("handleCancelAllSpecificOrders cancelled order : " + cancelledOrder);
            updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
        }

    }

    //cancelledOrders.add(specificOrder);
    // updateOrderState(specificOrder, OrderState.CANCELLING);

    //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
    //              SpecificOrder specificOrder = it.next();
    //
    //              
    //          }
    //  }

    @Override
    public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;
                if (pendingOrder.getPortfolio().equals(portfolio)) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;

    }

    @Override
    public Collection<Fill> getFills(Market market, Portfolio portfolio) {
        List<Fill> portfolioFills = new ArrayList<Fill>();
        for (Order order : orderStateMap.keySet()) {
            if (order.getFills() != null && !order.getFills().isEmpty() && order.getPortfolio().equals(portfolio) && order instanceof SpecificOrder) {

                SpecificOrder pendingOrder = (SpecificOrder) order;

                if (pendingOrder.getMarket().equals(market))

                    portfolioFills.addAll(pendingOrder.getFills());

            }
        }
        Collections.sort(portfolioFills, timeOrderIdComparator);
        return portfolioFills;
        // return portfolioFills;

        //  return null;

    }

    @Override
    public Collection<SpecificOrder> getPendingLongOpenOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;

                if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect() == (PositionEffect.OPEN) && pendingOrder.isBid()) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;

                if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE) && pendingOrder.isAsk()) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingShortOpenOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;

                if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect() == (PositionEffect.OPEN) && pendingOrder.isAsk()) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;

                if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE) && pendingOrder.isBid()) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingCloseOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;

                if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;
    }

    @Override
    public synchronized void handleCancelAllShortClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst) {
        Collection<SpecificOrder> orderToCancel = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
            Order order = it.next();
            if (order instanceof SpecificOrder) {
                SpecificOrder specificOrder = (SpecificOrder) order;
                if (specificOrder.getMarket().equals(market)
                        && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
                        && specificOrder.isBid()
                        && (specificOrder.getExecutionInstruction() == null || (specificOrder.getExecutionInstruction() != null && specificOrder
                                .getExecutionInstruction().equals(execInst)))) {
                    orderToCancel.add(specificOrder);
                    log.info("handleCancelAllShortClosingSpecificOrders cancelling order : " + specificOrder);

                }
            }
        }

        Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

        for (Order cancelledOrder : cancelledOrders)
            updateOrderState(cancelledOrder, OrderState.CANCELLED, false);

    }

    protected void handleUpdateSpecificOrderWorkingQuantity(SpecificOrder specificOrder, DiscreteAmount quantity) {
        for (Order pendingOrder : getPendingOrders()) {
            if (pendingOrder.equals(specificOrder)) {
                // so we need to ensure that the unfilled wuanity is waulty to the qauntity.

                // 200 lots order, 100 lots fill, want to update to 10 lots, so 110.
                long updatedQuantity = (quantity.isNegative()) ? -1
                        * (Math.abs(specificOrder.getVolume().getCount()) - Math.abs(specificOrder.getUnfilledVolumeCount()) + Math.abs(quantity.getCount()))
                        : Math.abs(specificOrder.getVolume().getCount()) - Math.abs(specificOrder.getUnfilledVolumeCount()) + Math.abs(quantity.getCount());

                //&& pendingOrder.getMarket().equals(market)) {
                specificOrder.setVolumeCount(updatedQuantity);

            }

        }

    }

    @Override
    public synchronized void handleCancelSpecificOrderByParentFill(Fill parentFill) {
        if (parentFill == null)
            return;
        ArrayList<Order> allChildOrders = new ArrayList<Order>();
        parentFill.getAllSpecificOrdersByParentFill(parentFill, allChildOrders);

        for (Order childOrder : allChildOrders) {
            if (childOrder instanceof SpecificOrder) {
                try {
                    handleCancelSpecificOrder((SpecificOrder) childOrder);

                    //updateOrderState(childOrder, OrderState.CANCELLED, false);
                    log.info("handleCancelSpecificOrderByParentFill cancelled Specific Order:" + childOrder);
                } catch (OrderNotFoundException e) {
                    log.info("handleCancelSpecificOrderByParentFill ubale to cancel Specific Order:" + childOrder);
                    //throw e;
                }
            }
        }

        //        while (itPending.hasNext()) {
        //            // closing position
        //            SpecificOrder cancelledOrder = itPending.next();
        //
        //            if (cancelledOrder != null && cancelledOrder.getParentFill() != null && cancelledOrder.getParentFill().equals(parentFill)) {
        //               
        //
        //                }
        //
        //            }
        //        }
    }

    @Override
    public synchronized void handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> orderToCancel = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
            Order order = it.next();
            if (order instanceof SpecificOrder) {
                SpecificOrder specificOrder = (SpecificOrder) order;
                if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)) {
                    //cancelledOrders.add(specificOrder);
                    orderToCancel.add(specificOrder);
                    log.debug("handleCancelAllLongClosingSpecificOrders cancelling order : " + specificOrder);
                }
            }
        }
        Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

        for (Order cancelledOrder : cancelledOrders) {
            log.info("handleCancelAllLongClosingSpecificOrders cancelled order : " + cancelledOrder);
            updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
        }

    }

    @Override
    public synchronized void handleCancelAllLongOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> orderToCancel = new ArrayList<>();
        //  synchronized (lock) {
        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder specificOrder = (SpecificOrder) order;
                if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect() == (PositionEffect.OPEN) && specificOrder.isBid()) {
                    orderToCancel.add(specificOrder);
                    log.info("handleCancelAllLongOpeningSpecificOrders cancelling order : " + specificOrder);
                }

            }
        }
        Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);
        for (Order cancelledOrder : cancelledOrders) {
            log.info("handleCancelAllLongOpeningSpecificOrder cancelled order : " + cancelledOrder);
            updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
        }

    }

    @Override
    public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, ExecutionInstruction execInst) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;
                if (pendingOrder.getPortfolio().equals(portfolio)
                        && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
                        && pendingOrder.isAsk()
                        && (pendingOrder.getExecutionInstruction() == null || (pendingOrder.getExecutionInstruction() != null && pendingOrder
                                .getExecutionInstruction().equals(execInst)))) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, ExecutionInstruction execInst) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;
                if (pendingOrder.getPortfolio().equals(portfolio)
                        && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
                        && pendingOrder.isBid()
                        && (pendingOrder.getExecutionInstruction() == null || (pendingOrder.getExecutionInstruction() != null && pendingOrder
                                .getExecutionInstruction().equals(execInst)))) {

                    portfolioPendingOrders.add(pendingOrder);
                }

            }
        }
        return portfolioPendingOrders;
    }

    @Override
    public synchronized void handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst) {
        Collection<SpecificOrder> orderToCancel = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
            Order order = it.next();
            if (order instanceof SpecificOrder) {
                SpecificOrder specificOrder = (SpecificOrder) order;
                if (specificOrder.getMarket().equals(market)
                        && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
                        && specificOrder.isAsk()
                        && (specificOrder.getExecutionInstruction() == null || (specificOrder.getExecutionInstruction() != null && specificOrder
                                .getExecutionInstruction().equals(execInst)))) {
                    orderToCancel.add(specificOrder);
                    log.info("handleCancelAllLongClosingSpecificOrders cancelling order : " + specificOrder);
                }
            }
        }
        Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

        for (Order cancelledOrder : cancelledOrders) {
            log.info("handleCancelAllLongOpeningSpecificOrder cancelled order : " + cancelledOrder);
            updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
        }

    }

    @Override
    public synchronized void handleCancelSpecificOrder(SpecificOrder specificOrder) {
        if (cancelSpecificOrder(specificOrder)) {
            if (specificOrder.getParentFill() != null)
                specificOrder.getParentFill().setPositionType(specificOrder.getParentFill().getOpenVolumeCount() > 0 ? PositionType.LONG : PositionType.SHORT);
            // need to remove it from any parent
            //            if (specificOrder.getParentOrder() != null && specificOrder.getParentOrder().getOrderChildren() != null
            //                    && !specificOrder.getParentOrder().getOrderChildren().isEmpty())
            //
            //                specificOrder.getParentOrder().removeChildOrder(specificOrder);

            updateOrderState(specificOrder, OrderState.CANCELLED, false);
            log.info("handleCancelSpecificOrder cancelled Specific Order:" + specificOrder);

        }

        else
            log.info("handleCancelSpecificOrder unable to cancelled Specific Order:" + specificOrder);
        // throw new OrderNotFoundException("Unable to cancelled order");
        //cancelledOrders.add(cancelledOrder);
        // pendingOrders.removeAll(cancelledOrders);

    }

    @When("@Priority(7) select * from OrderUpdate where state.open=false and NOT (OrderUpdate.state = OrderState.CANCELLED)")
    private synchronized void completeOrder(OrderUpdate update) {
        OrderState orderState = update.getState();
        Order order = update.getOrder();
        SpecificOrder specificOrder;
        if (order instanceof SpecificOrder) {
            specificOrder = (SpecificOrder) order;
            switch (orderState) {
                case CANCELLING:
                    cancelSpecificOrder(specificOrder);
                    updateOrderState(order, OrderState.CANCELLED, false);
                    break;
                default:
                    cancelSpecificOrder(specificOrder);
                    break;
            }
        }
    }

    @Override
    public Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio) {

        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<>();

        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder pendingOrder = (SpecificOrder) order;
                if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getMarket().equals(market))
                    portfolioPendingOrders.add(pendingOrder);

            }
        }

        return portfolioPendingOrders;

    }

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
    public Collection<Order> getPendingLongStopOrders(Portfolio portfolio, Market market) {
        Collection<Order> portfolioPendingStopOrders = new ConcurrentLinkedQueue<>();
        Fill parentFill;
        Order parentOrder;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();
                if (triggerOrder.getPortfolio().equals(portfolio) && triggerOrder.getMarket().equals(market) && triggerOrder.isAsk()
                        && triggerOrder.getStopPrice() != null)

                    portfolioPendingStopOrders.add(triggerOrder);

            }

        }
        return portfolioPendingStopOrders;
    }

    @Override
    public Collection<Order> getPendingShortStopOrders(Portfolio portfolio, Market market) {
        Collection<Order> portfolioPendingStopOrders = new ConcurrentLinkedQueue<>();
        Fill parentFill;
        Order parentOrder;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();
                if (triggerOrder.getPortfolio().equals(portfolio) && triggerOrder.getMarket().equals(market) && triggerOrder.isBid()
                        && triggerOrder.getStopPrice() != null)

                    portfolioPendingStopOrders.add(triggerOrder);

            }

        }
        return portfolioPendingStopOrders;
    }

    @Override
    public synchronized void handleCancelAllLongOpeningGeneralOrders(Portfolio portfolio, Market market) {
        Collection<Order> cancelledOrders = new ConcurrentLinkedQueue<>();
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.getMarket().equals(market) && triggerOrder.isBid() && triggerOrder.getPositionEffect() == (PositionEffect.OPEN))

                    cancelledOrders.add(triggerOrder);

            }
            triggerOrders.get(parentKey).removeAll(cancelledOrders);
            for (Order cancelledOrder : cancelledOrders) {
                updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
                log.info("Cancelled Long Opening Trigger Order: " + cancelledOrder);

            }

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
    public synchronized void handleCancelAllShortOpeningGeneralOrders(Portfolio portfolio, Market market) {
        Collection<Order> cancelledOrders = new ConcurrentLinkedQueue<>();
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.getMarket().equals(market) && triggerOrder.isAsk() && triggerOrder.getPositionEffect() == (PositionEffect.OPEN))

                    cancelledOrders.add(triggerOrder);

                //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill
                //   if (triggerOrder.getParentFill() != null)
                //     triggerOrder.getParentFill().setStopPriceCount(0);
            }
            triggerOrders.get(parentKey).removeAll(cancelledOrders);
            for (Order cancelledOrder : cancelledOrders) {
                updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
                log.info("Cancelled Short Opening Trigger Order: " + cancelledOrder);
            }

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
    public Order getPendingTriggerOrder(Order order) {
        Collection<Order> portfolioPendingStopOrders = new ConcurrentLinkedQueue<>();
        Fill parentFill;
        Order parentOrder;
        //synchronized (lock) {
        for (Iterator<Event> ite = triggerOrders.keySet().iterator(); ite.hasNext();) {
            Event parentKey = ite.next();
            for (Iterator<Order> it = triggerOrders.get(parentKey).iterator(); it.hasNext();) {
                Order triggerOrder = it.next();

                if (triggerOrder.equals(order))
                    return triggerOrder;

                //TODO could be a condition here where we have not removed the trigger order but we have set the stop price to 0 on the fill

            }

        }
        return null;

    }

    @Override
    public synchronized void handleCancelAllShortOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> orderToCancel = new ArrayList<>();
        //  synchronized (lock) {
        for (Order order : getPendingOrders()) {
            if (order instanceof SpecificOrder) {
                SpecificOrder specificOrder = (SpecificOrder) order;
                if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect() == (PositionEffect.OPEN) && specificOrder.isAsk()) {
                    orderToCancel.add(specificOrder);
                    log.info("handleCancelAllShortOpeningSpecificOrders cancelling order : " + specificOrder);
                }

            }
        }

        Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

        for (Order cancelledOrder : cancelledOrders) {
            log.info("handleCancelAllShortOpeningSpecificOrders cancelled order : " + cancelledOrder);
            updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
        }

    }

    @Override
    public synchronized void handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> orderToCancel = new ArrayList<>();
        //  synchronized (lock) {
        for (Order order : getPendingOrders()) {
            SpecificOrder specificOrder;
            if (order instanceof SpecificOrder) {
                specificOrder = (SpecificOrder) order;

                if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect() == (PositionEffect.OPEN)) {
                    orderToCancel.add(specificOrder);
                    log.info("handleCancelAllOpeningSpecificOrders cancelling order : " + specificOrder);
                }

            }
        }

        Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);
        for (Order cancelledOrder : cancelledOrders) {
            log.info("handleCancelAllOpeningSpecificOrders cancelled order : " + cancelledOrders);
            updateOrderState(cancelledOrder, OrderState.CANCELLED, false);
        }

    }

    @Override
    public synchronized Collection<SpecificOrder> cancelSpecificOrder(Collection<SpecificOrder> orders) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<SpecificOrder>();
        for (SpecificOrder order : orders) {
            if (cancelSpecificOrder(order))
                cancelledOrders.add(order);
        }
        return cancelledOrders;

    }

    protected void updateOrderState(Order order, OrderState state, boolean route) {

        // need to add vaildation here on state and last state
        OrderState oldState = null;
        if (order != null)
            oldState = orderStateMap.get(order);

        if (oldState != null
                && (oldState.equals(state) || oldState == (OrderState.CANCELLED) || oldState == (OrderState.FILLED) || oldState.compareTo(state) > 0)) {
            log.info(this.getClass().getSimpleName() + ": updateOrderState. Later state " + oldState + " procssed before " + state + " for order " + order);
            return;
        }
        if (oldState == null)
            oldState = OrderState.NEW;
        else if (oldState != null && order != null)
            stateOrderMap.get(oldState).remove(order);
        if (order != null) {
            orderStateMap.put(order, state);
            if (stateOrderMap.get(state) == null) {
                Set<Order> orderSet = new HashSet<Order>();
                // orderSet.add(order);
                stateOrderMap.put(state, orderSet);

            }
            stateOrderMap.get(state).add(order);
            log.info(order + " added to order state cache");
        }

        // this.getClass()
        // context.route(new OrderUpdate(order, oldState, state));
        OrderUpdate orderUpdate = orderUpdateFactory.create(order, oldState, state);
        //if (route)
        context.setPublishTime(orderUpdate);

        orderUpdate.persit();
        context.route(orderUpdate);
        //else
        //  context.publish(orderUpdate);

        if (order.getParentOrder() != null)
            updateParentOrderState(order.getParentOrder(), order, state);
    }

    private void updateParentOrderState(Order order, Order childOrder, OrderState childOrderState) {
        //    if (order.getFillType() == FillType.ONE_CANCELS_OTHER)
        //       return;

        OrderState oldState = orderStateMap.get(order);
        switch (childOrderState) {
            case NEW:
                boolean fullyNew = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isNew()) {
                        fullyNew = false;
                        break;
                    }
                }
                if (fullyNew)
                    updateOrderState(order, OrderState.NEW, true);
                // }
                break;

            case TRIGGER:
                boolean fullyTrigger = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isTrigger()) {
                        fullyTrigger = false;
                        break;
                    }
                }
                if (fullyTrigger)
                    updateOrderState(order, OrderState.TRIGGER, true);
                // }
                break;

            case ROUTED:
                //TODO: update state once all children have same state
                boolean fullyRouted = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isRouted()) {
                        fullyRouted = false;
                        break;
                    }
                }
                if (fullyRouted)
                    updateOrderState(order, OrderState.ROUTED, true);
                // }
                break;
            case PLACED:
                //TODO: update state once all children have same state
                boolean fullyPlaced = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isPlaced()) {
                        fullyPlaced = false;
                        break;
                    }
                }
                if (fullyPlaced)
                    updateOrderState(order, OrderState.PLACED, true);
                // }
                break;
            case PARTFILLED:
                boolean fullyPartFilled = false;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child).isPartfilled()) {
                        fullyPartFilled = true;
                        break;
                    }
                }
                if (fullyPartFilled)
                    updateOrderState(order, OrderState.PARTFILLED, true);

                break;

            case FILLED:
                //if (oldState == OrderState.CANCELLING) {
                boolean fullyFilled = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && orderStateMap.get(child).isOpen()) {
                        fullyFilled = false;
                        updateOrderState(order, OrderState.PARTFILLED, true);
                        break;
                    }
                }
                if (fullyFilled)
                    updateOrderState(order, OrderState.FILLED, true);

                break;

            case CANCELLING:
                boolean fullyCancelling = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isCancelled()) {
                        fullyCancelling = false;
                        break;
                    }
                }
                if (fullyCancelling)
                    updateOrderState(order, OrderState.CANCELLING, true);
                // }
                break;
            case CANCELLED:
                //  if (oldState == OrderState.CANCELLING) {
                boolean fullyCancelled = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isCancelled()) {
                        fullyCancelled = false;
                        break;
                    }
                }
                if (fullyCancelled)
                    updateOrderState(order, OrderState.CANCELLED, true);
                // }
                break;
            case REJECTED:
                boolean fullyRejected = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isRejected()) {
                        fullyRejected = false;
                        break;
                    }
                }
                if (fullyRejected) {
                    updateOrderState(order, OrderState.REJECTED, true);
                    reject(order, "Child order was rejected");
                }
                // }
                break;

            case EXPIRED:
                if (!childOrder.getExpiration().isEqual(order.getExpiration()))
                    throw new Error("Child order expirations must match parent order expirations");
                boolean fullyExpired = true;
                for (Order child : order.getOrderChildren()) {
                    if (orderStateMap.get(child) != null && !orderStateMap.get(child).isExpired()) {
                        fullyExpired = false;
                        break;
                    }
                }
                if (fullyExpired)
                    updateOrderState(order, OrderState.EXPIRED, true);
                // }
                break;

            default:
                log.warn("Unknown order state: " + childOrderState);
                break;
        }
    }

    /*  protected static final void persitOrderFill(Event event) {

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

          } // else { // if not a Trade, persist unconditionally
            // try {
            //   PersistUtil.insert(event);
            //} catch (Throwable e) {
            //  throw new Error("Could not insert " + event, e);
            // }
            //}
      }
    */
    private synchronized void removeTriggerOrder(Order order) {

        for (Event parentKey : triggerOrders.keySet()) {
            List<Order> removedTriggerOrders = Collections.synchronizedList(new ArrayList<Order>());
            for (Order triggerOrder : triggerOrders.get(parentKey)) {

                if (triggerOrder.equals(order)) {
                    removedTriggerOrders.add(order);

                    //  triggerOrders.get(parentKey).remove(order);
                    // --removeOrder(order);
                }

                // pendingOrders.remove(order);
            }
            triggerOrders.get(parentKey).removeAll(removedTriggerOrders);

        }
    }

    private void addTriggerOrder(Order triggerOrder) {
        //If the trigger order is from a fill, we use the fill as the key for mutliple triggers, else we use the parent
        //any one of the multiple triggers can trigger first, but once one is triggered, all others are removed at for either the same fill or same parent
        Event eventKey = (triggerOrder.getParentFill() != null) ? triggerOrder.getParentFill() : triggerOrder.getParentOrder();
        if (eventKey == null)
            eventKey = triggerOrder;
        // synchronized (lock) {
        ArrayList<Order> triggerOrderQueue = new ArrayList<Order>();
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
                    transaction = transactionFactory.create(order, context.getTime());
                    context.setPublishTime(transaction);

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
                    transaction = transactionFactory.create(fill, context.getTime());
                    context.setPublishTime(transaction);

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

    @Override
    public Map<Order, OrderState> getOrderStateMap() {
        return orderStateMap;
    }

    protected void setPortfolioService(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    public BaseOrderService() {
    }

    private static ExecutorService service;

    @Inject
    protected Context context;

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.orderService");

    protected boolean enableTrading = false;
    protected final Map<Order, OrderState> orderStateMap = new ConcurrentHashMap<>();

    protected final Map<OrderState, Set<Order>> stateOrderMap = new ConcurrentHashMap<OrderState, Set<Order>>();
    @Inject
    protected transient QuoteService quotes;
    @Inject
    protected transient PortfolioService portfolioService;

    @Inject
    protected transient GeneralOrderFactory generalOrderFactory;

    @Inject
    protected transient SpecificOrderFactory specificOrderFactory;

    @Inject
    protected transient OrderUpdateFactory orderUpdateFactory;

    @Inject
    protected transient TransactionFactory transactionFactory;

    @Inject
    protected transient FillFactory fillFactory;

    protected final static Map<Event, ArrayList<Order>> triggerOrders = new ConcurrentHashMap<Event, ArrayList<Order>>();
    private static Object lock = new Object();

}
