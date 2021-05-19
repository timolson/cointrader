package org.cryptocoinpartners.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.ContingencyType;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.enumeration.TargetStrategy;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.exceptions.InvaildOrderStateException;
import org.cryptocoinpartners.exceptions.OrderNotFoundException;
import org.cryptocoinpartners.exceptions.TradingDisabledException;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.BookFactory;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.FillFactory;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.GeneralOrderFactory;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.OrderUpdateFactory;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.PositionUpdate;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.SpecificOrderFactory;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.Injector;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.RemainderHandler;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */

// TODO syncronized on order objects, not on this class to improve performance
@SuppressWarnings("UnusedDeclaration")
public abstract class BaseOrderService implements OrderService {
  static {
    service = Executors.newFixedThreadPool(1);
  }

  // ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>
  // listingPosition = new ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType,
  // ConcurrentLinkedQueue<Position>>>();
  // protected final Lock replacingOrderLock = new ReentrantLock();
  private final Lock triggerOrderLock = new ReentrantLock();
  private final Lock trailingTriggerOrdersLock = new ReentrantLock();

  private transient Map<
          Asset, Map<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>>>
      positionsMap;
  private final int updateOrderAfter = 5;
  protected static final HashMap<Exchange, ExecutorService> exchangeCancellationPool =
      new HashMap<Exchange, ExecutorService>();
  protected static final HashMap<Exchange, BlockingQueue<SpecificOrder>>
      exchangeCancellationQueues = new HashMap<Exchange, BlockingQueue<SpecificOrder>>();

  private static final Map<Tradeable, Map<Double, Map<TransactionType, Map<FillType, List<Order>>>>>
      triggerOrders =
          new ConcurrentHashMap<
              Tradeable, Map<Double, Map<TransactionType, Map<FillType, List<Order>>>>>();

  private static final Map<Tradeable, Map<Double, Map<TransactionType, Map<FillType, List<Order>>>>>
      targetOrders =
          new ConcurrentHashMap<
              Tradeable, Map<Double, Map<TransactionType, Map<FillType, List<Order>>>>>();

  private static final Map<Tradeable, Map<Double, Map<TransactionType, List<Order>>>>
      trailingTriggerOrders =
          new ConcurrentHashMap<Tradeable, Map<Double, Map<TransactionType, List<Order>>>>();

  protected static boolean cancelUnknownOrders =
      (ConfigUtil.combined() != null)
          ? ConfigUtil.combined().getBoolean("cancel.unknownorders", false)
          : false;
  protected static boolean ignoreUnknownOrders =
      (ConfigUtil.combined() != null)
          ? ConfigUtil.combined().getBoolean("ignore.unknownorders", true)
          : true;
  protected static int maxPlacementCount =
      (ConfigUtil.combined() != null)
          ? ConfigUtil.combined().getInt("xchange.maxplacementcount", new Integer(20))
          : new Integer(20);
  protected static int multiLegTicks =
      (ConfigUtil.combined() != null)
          ? ConfigUtil.combined().getInt("multileg.tick", new Integer(10))
          : new Integer(10);
  protected static boolean saveStopPriceUpdates =
      (ConfigUtil.combined() != null)
          ? ConfigUtil.combined().getBoolean("save.stopupdates", true)
          : true;

  @Override
  public void init() {
    for (Portfolio portfolio : portfolioService.getPortfolios()) {
      findTriggerOrders(portfolio);

      // Create an executor for each market
      for (Tradeable tradeable : portfolio.getMarkets())
        if (tradeable instanceof Market) {
          Market market = (Market) tradeable;
          exchangeCancellationPool.put(market.getExchange(), Executors.newFixedThreadPool(1));
          exchangeCancellationQueues.put(
              market.getExchange(), new LinkedBlockingQueue<SpecificOrder>());
        }
    }
  }

  // @Override
  // public CountDownLatch getFillProcessingLatch() {
  //     return fillProcessingLatch;

  // }

  // TODO Need to ensure we consider minum order size when exiiting as we might have too many order
  // closing the posionts!
  @Override
  public boolean placeOrder(Order order) throws Throwable {

    if (!enableTrading) {
      updateOrderState(order, OrderState.REJECTED, true);

      log.info("Trading Mode Disabled");
      throw new TradingDisabledException("Trading Mode Disabled");
      // return;
    }
    if (orderStateMap.get(order) != null && getOrderState(order).isWorking()) {

      log.info(
          this.getClass().getSimpleName()
              + ":placeOrder: Unable to place "
              + order.getClass().getSimpleName()
              + ": "
              + order
              + " as state "
              + getOrderState(order));

      throw new InvaildOrderStateException(order + " state is " + getOrderState(order));
    }
    order.persit();
    if (order.getVolume() == null || order.getVolume().isZero()) {
      log.info(
          this.getClass().getSimpleName()
              + ":placeOrder: Unable to place "
              + order.getClass().getSimpleName()
              + ": "
              + order
              + " as zero volume");
      updateOrderState(order, OrderState.REJECTED, true);
      return false;
    }
    if (order instanceof GeneralOrder) {
      GeneralOrder generalOrder = (GeneralOrder) order;
      log.info("new general order recieved " + generalOrder);
      updateOrderState(generalOrder, OrderState.NEW, true);
      if (!handleGeneralOrder(generalOrder)) return false;
    } else if (order instanceof SpecificOrder) {

      SpecificOrder specificOrder = (SpecificOrder) order;
      log.info("new specific order recieved " + specificOrder);
      updateOrderState(specificOrder, OrderState.NEW, true);
      if (specificOrder.getPlacementCount() > maxPlacementCount) {
        log.info(
            this.getClass().getSimpleName()
                + ":placeOrder - Unable to place specific order "
                + specificOrder
                + " the order has been placed "
                + specificOrder.getPlacementCount()
                + " with a maxPlacementCount of "
                + maxPlacementCount);

        updateOrderState(order, OrderState.REJECTED, true);
        return false;
      }

      specificOrder.setPlacementCount(specificOrder.getPlacementCount() + 1);
      double minOrderSize =
          specificOrder.getMarket().getMinimumOrderSize(specificOrder.getMarket());
      long minOrderSizeCount =
          (long) (minOrderSize * (1 / specificOrder.getMarket().getVolumeBasis()));

      if (Math.abs(specificOrder.getUnfilledVolume().getCount()) < minOrderSizeCount
          && specificOrder.getPositionEffect().equals(PositionEffect.OPEN)) {
        log.info(
            this.getClass().getSimpleName()
                + ":placeOrder - Unable to palce specific order "
                + specificOrder
                + " with state "
                + getOrderState(specificOrder)
                + " as unfilled volume is less than mininum order size "
                + minOrderSize);

        updateOrderState(order, OrderState.REJECTED, true);
        return false;
      }
      if (specificOrder.getExecutionInstruction() == null
          || !specificOrder.getExecutionInstruction().equals(ExecutionInstruction.MANUAL)) {

        try {
          if (specificOrder.getLimitPriceCount() == 0
              || specificOrder.getLimitPriceCount() == Long.MAX_VALUE) {
            log.info("new specific order recieved without limit price" + specificOrder);
            Collection<SpecificOrder> pendingOrders =
                (specificOrder.isBid()) ? getPendingLongOrders() : getPendingShortOrders();
            Amount workingVolume = specificOrder.getUnfilledVolume();
            for (SpecificOrder workingOrder : pendingOrders)
              workingVolume = workingVolume.plus(workingOrder.getUnfilledVolume());
            // if I am buying, then I can buy at current best ask and sell at current best bid
            Book lastBook = quotes.getLastBook(specificOrder.getMarket());
            // Lastbook might be null so we need to use prices!
            log.debug(
                "BasedOrderSerivce - PlaceOrder: Setting limit prices for market "
                    + specificOrder.getMarket()
                    + " using lastBook"
                    + lastBook);
            Offer bestOffer;
            if (specificOrder.getExecutionInstruction() == (ExecutionInstruction.TAKER)) {
              log.info("placeOrder: setting fill type to market for order " + specificOrder);
              specificOrder.withFillType(FillType.MARKET);

              bestOffer =
                  (specificOrder.isBid())
                      ? (lastBook == null
                          ? quotes.getImpliedBestAskForListing(
                              specificOrder.getMarket().getListing())
                          : lastBook.getBestAskByVolume(
                              new DiscreteAmount(
                                  DiscreteAmount.roundedCountForBasis(
                                      workingVolume.asBigDecimal(),
                                      specificOrder.getMarket().getVolumeBasis()),
                                  specificOrder.getMarket().getVolumeBasis())))
                      : (lastBook == null
                          ? quotes.getImpliedBestBidForListing(
                              specificOrder.getMarket().getListing())
                          : lastBook.getBestBidByVolume(
                              new DiscreteAmount(
                                  DiscreteAmount.roundedCountForBasis(
                                      workingVolume.asBigDecimal(),
                                      specificOrder.getMarket().getVolumeBasis()),
                                  specificOrder.getMarket().getVolumeBasis())));
            } else {
              log.info("placeOrder: setting fill type to limit for order " + specificOrder);
              specificOrder.withFillType(FillType.LIMIT);

              bestOffer =
                  (specificOrder.isBid())
                      ? (lastBook == null
                          ? quotes.getImpliedBestBidForListing(
                              specificOrder.getMarket().getListing())
                          : lastBook.getBestBidByVolume(
                              new DiscreteAmount(
                                  DiscreteAmount.roundedCountForBasis(
                                      workingVolume.asBigDecimal(),
                                      specificOrder.getMarket().getVolumeBasis()),
                                  specificOrder.getMarket().getVolumeBasis())))
                      : (lastBook == null
                          ? quotes.getImpliedBestAskForListing(
                              specificOrder.getMarket().getListing())
                          : lastBook.getBestAskByVolume(
                              new DiscreteAmount(
                                  DiscreteAmount.roundedCountForBasis(
                                      workingVolume.asBigDecimal(),
                                      specificOrder.getMarket().getVolumeBasis()),
                                  specificOrder.getMarket().getVolumeBasis())));
            }

            // this is short exit, so I am buy, so hitting the ask
            // loop down asks until the total quanity of the order is reached.
            DiscreteAmount limitPrice;
            if (bestOffer != null
                && bestOffer.getPrice() != null
                && (bestOffer.getPrice().getCount() != 0
                    || bestOffer.getPrice().getCount() != Long.MAX_VALUE)) {
              limitPrice =
                  new DiscreteAmount(
                      DiscreteAmount.roundedCountForBasis(
                          bestOffer.getPrice().asBigDecimal(),
                          specificOrder.getMarket().getPriceBasis()),
                      specificOrder.getMarket().getPriceBasis());

              log.debug(
                  "placeOrder: setting limit price to best offer by volume"
                      + limitPrice
                      + " for order "
                      + specificOrder);
            } else {
              limitPrice =
                  ((specificOrder.isAsk())
                      ? (quotes.getLastBidForMarket(specificOrder.getMarket()) == null
                              || quotes
                                      .getLastBidForMarket(specificOrder.getMarket())
                                      .getPriceCount()
                                  == 0L
                          ? quotes
                              .getImpliedBestAskForListing(specificOrder.getMarket().getListing())
                              .getPrice()
                          : quotes.getLastBidForMarket(specificOrder.getMarket()).getPrice())
                      : (quotes.getLastAskForMarket(specificOrder.getMarket()) == null
                              || quotes
                                      .getLastAskForMarket(specificOrder.getMarket())
                                      .getPriceCount()
                                  == Long.MAX_VALUE
                          ? quotes
                              .getImpliedBestAskForListing(specificOrder.getMarket().getListing())
                              .getPrice()
                          : quotes.getLastAskForMarket(specificOrder.getMarket()).getPrice()));

              log.debug(
                  "placeOrder: setting limit price to best offer  "
                      + limitPrice
                      + " for order "
                      + specificOrder);
            }
            limitPrice =
                (limitPrice == null)
                    ? (specificOrder.isBid())
                        ? (quotes.getLastBidForMarket(specificOrder.getMarket()) == null
                            ? quotes.getLastTrade(specificOrder.getMarket()).getPrice()
                            : quotes.getLastBidForMarket(specificOrder.getMarket()).getPrice())
                        : (quotes.getLastAskForMarket(specificOrder.getMarket()) == null
                            ? quotes.getLastTrade(specificOrder.getMarket()).getPrice()
                            : quotes.getLastAskForMarket(specificOrder.getMarket()).getPrice())
                    : limitPrice;

            if (limitPrice == null
                || (limitPrice != null
                    && (limitPrice.getCount() == 0L || limitPrice.getCount() == Long.MAX_VALUE)))
              limitPrice = quotes.getLastTrade(specificOrder.getMarket()).getPrice();

            if (limitPrice == null
                || (limitPrice != null
                    && (limitPrice.getCount() == 0L || limitPrice.getCount() == Long.MAX_VALUE))) {
              updateOrderState(specificOrder, OrderState.REJECTED, true);

              log.info(
                  "placeOrder: specific order "
                      + specificOrder
                      + " not placed as no prices on book "
                      + (specificOrder.isBid()
                          ? (quotes.getLastBidForMarket(specificOrder.getMarket()) == null
                              ? quotes.getLastTrade(specificOrder.getMarket())
                              : quotes.getLastBidForMarket(specificOrder.getMarket()))
                          : (quotes.getLastAskForMarket(specificOrder.getMarket()) == null
                              ? quotes.getLastTrade(specificOrder.getMarket())
                              : quotes.getLastAskForMarket(specificOrder.getMarket())))
                      + "and last trade "
                      + quotes.getLastTrade(specificOrder.getMarket())
                      + " for limit price");
              return false;
            }
            limitPrice =
                new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        limitPrice.asBigDecimal(), specificOrder.getMarket().getPriceBasis()),
                    specificOrder.getMarket().getPriceBasis());

            specificOrder.withLimitPrice(limitPrice);
          }
          handleSpecificOrder(specificOrder);
        } catch (Throwable ex) {
          if (orderStateMap.get(specificOrder) != null
              && orderStateMap.get(specificOrder).equals(OrderState.NEW))
            updateOrderState(specificOrder, OrderState.REJECTED, true);
          throw ex;
        }
      } else if (specificOrder.getExecutionInstruction().equals(ExecutionInstruction.MANUAL)) {
        updateOrderState(order, OrderState.FILLED, true);
      }
    }
    CreateTransaction(order, true);
    return true;
  }

  private void findTriggerOrders(Portfolio portfolio) {
    // TODO this loads a lot of workking orders why? THink the query is wrong and it returns all
    // orders.
    List<OrderState> openOrderStates = new ArrayList<OrderState>();
    openOrderStates.add(OrderState.TRIGGER);
    openOrderStates.add(OrderState.PARTFILLED);
    // openOrderStates.add(OrderState.NEW);
    openOrderStates.add(OrderState.PLACED);
    openOrderStates.add(OrderState.CANCELLING);
    openOrderStates.add(OrderState.ROUTED);
    Map<Order, OrderUpdate> workingOrderStates = new HashMap<Order, OrderUpdate>();

    List<OrderUpdate> workingOrderRecords =
        EM.namedQueryList(
            OrderUpdate.class, "orderUpdate.findOrdersByState", openOrderStates, portfolio);
    HashSet<OrderUpdate> workingOrderUpdates =
        workingOrderRecords == null
            ? new HashSet<OrderUpdate>()
            : Sets.newHashSet(workingOrderRecords);
    workingOrderUpdates.removeAll(Collections.singleton(null));

    Map<Order, Order> portfolioOrders = new HashMap<Order, Order>();
    // Something wrong in the getAllOrder not retruing all orders loaded in the orderTree. it is
    // only loadign childer orders of fills in positios
    for (Order order : portfolio.getAllOrders()) {
      order.setPersisted(true);
      log.debug(
          "adding order: "
              + order.getUuid()
              + "/"
              + System.identityHashCode(order)
              + " to portfolio orders");
      if (order.getDao() == null) Injector.root().getInjector().injectMembers(order);
      if (order.getPortfolio() != portfolio) {
        log.debug("not portfolio");
        order.setPortfolio(portfolio);
      }
      if (order.getMarket() != null) {
        Tradeable myMarket;
        //  context.getInjector().injectMembers(order);
        if (portfolio.addMarket(order.getMarket()) == null)
          myMarket = portfolio.addMarket(order.getMarket());
        else order.setMarket((Market) portfolio.addMarket(order.getMarket()));

      } else order.setMarket((Market) portfolio.addMarket(order.getMarket()));
      portfolioOrders.put(order, order);
    }
    Set<Order> workingOrders = new HashSet<Order>();

    for (OrderUpdate orderUpdate : workingOrderUpdates) {
      orderUpdate.setPersisted(true);
      orderUpdate.getOrder().setPersisted(true);
      log.debug(
          "adding order: "
              + orderUpdate.getOrder().getUuid()
              + "/"
              + System.identityHashCode(orderUpdate.getOrder())
              + " to working orders");
      workingOrderStates.put(orderUpdate.getOrder(), orderUpdate);
      if (orderUpdate.getOrder().getMarket() != null)
        if (orderUpdate.getOrder().getDao() == null)
          Injector.root().getInjector().injectMembers(orderUpdate.getOrder());

      orderUpdate
          .getOrder()
          .setMarket((Market) portfolio.addMarket(orderUpdate.getOrder().getMarket()));
      if (orderUpdate.getOrder().getPortfolio().equals(portfolio)
          && orderUpdate.getOrder().getPortfolio() != portfolio)
        orderUpdate.getOrder().setPortfolio(portfolio);
      workingOrders.add(orderUpdate.getOrder());
    }
    // System.gc();
    Map<Fill, Fill> portfolioFills = new HashMap<Fill, Fill>();
    for (Fill fill : portfolio.getAllFills()) {
      fill.setPersisted(true);
      if (fill.getMarket() != null) fill.setMarket((Market) portfolio.addMarket(fill.getMarket()));

      if (fill.getOrder().getMarket() != null)
        fill.getOrder().setMarket((Market) portfolio.addMarket(fill.getOrder().getMarket()));

      portfolioFills.put(fill, fill);
    }
    // all working orders not in porfolio
    SetView<Order> missingPortfolioOrders =
        Sets.difference(workingOrders, portfolioOrders.keySet());
    // for (Order missingOrder : missingPortfolioOrders)
    // log.debug("Missing Portfolio Order:" + missingOrder.getId() + "/" +
    // System.identityHashCode(missingOrder));
    // all portfoilo orders that are not in working orders
    SetView<Order> missingWorkingOrders = Sets.difference(portfolioOrders.keySet(), workingOrders);
    //  for (Order missingOrder : missingWorkingOrders)
    //  log.debug("Missing Working Order:" + missingOrder.getId() + "/" +
    // System.identityHashCode(missingOrder));

    // all order in both portoflio and working orders
    SetView<Order> portfolioWorkingOrders =
        Sets.intersection(portfolioOrders.keySet(), workingOrders);
    // log.debug("Missing Working Order:" + portfolioWorkingOrders);

    for (Order missingPortfolioOrder : missingPortfolioOrders) {
      if (portfolioOrders.get(missingPortfolioOrder) == null) {
        if (missingPortfolioOrder.getPortfolio().equals(portfolio)
            && missingPortfolioOrder.getPortfolio() != (portfolio))
          missingPortfolioOrder.setPortfolio(portfolio);
        if (missingPortfolioOrder.getParentOrder() != null
            && missingPortfolioOrder.getParentOrder().getPortfolio().equals(portfolio)
            && missingPortfolioOrder.getParentOrder().getPortfolio() != (portfolio))
          missingPortfolioOrder.getParentOrder().setPortfolio(portfolio);
        if (missingPortfolioOrder.getParentFill() != null
            && missingPortfolioOrder.getParentFill().getPortfolio().equals(portfolio)
            && missingPortfolioOrder.getParentFill().getPortfolio() != (portfolio))
          missingPortfolioOrder.getParentFill().setPortfolio(portfolio);
        if (missingPortfolioOrder.getParentFill() != null
            && missingPortfolioOrder.getParentFill().getOrder() != null
            && missingPortfolioOrder.getParentFill().getOrder().getPortfolio().equals(portfolio)
            && missingPortfolioOrder.getParentFill().getOrder().getPortfolio() != (portfolio))
          missingPortfolioOrder.getParentFill().getOrder().setPortfolio(portfolio);

        if (missingPortfolioOrder.getParentFill() != null
            && missingPortfolioOrder
                .getPortfolio()
                .getPositions()
                .contains(missingPortfolioOrder.getParentFill().getPosition())) {
          synchronized (missingPortfolioOrder.getPortfolio()) {
            for (Position fillPosition : missingPortfolioOrder.getPortfolio().getPositions())
              if (fillPosition.equals(missingPortfolioOrder.getParentFill().getPosition())) {
                missingPortfolioOrder.getParentFill().setPosition(fillPosition);
                break;
              }
          }
        }

        log.debug(
            "Loading all child order for missing portfolio "
                + missingPortfolioOrder.getClass().getSimpleName()
                + " :"
                + missingPortfolioOrder.getUuid()
                + "/"
                + System.identityHashCode(missingPortfolioOrder)
                + " with order state "
                + workingOrderStates.get(missingPortfolioOrder).getState());

        // loading all the child order for missing order
        // this consumes vast amounts of memory need to figure out why.

        missingPortfolioOrder.loadAllChildOrdersByParentOrder(
            missingPortfolioOrder, portfolioOrders, portfolioFills);

        if (!portfolioOrders.containsKey(missingPortfolioOrder))
          portfolioOrders.put(missingPortfolioOrder, missingPortfolioOrder);
        // load all the child order for the parent fill
        if (missingPortfolioOrder.getParentFill() != null) {
          log.debug(
              "Loading all child order for parent fill "
                  + missingPortfolioOrder.getParentFill().getUuid()
                  + "/"
                  + System.identityHashCode(missingPortfolioOrder.getParentFill())
                  + " for missing portfolio "
                  + missingPortfolioOrder.getClass().getSimpleName()
                  + " :"
                  + missingPortfolioOrder.getUuid()
                  + "/"
                  + System.identityHashCode(missingPortfolioOrder));

          //   "Portfolio: findOrCreate Loading all child order for fill order " +
          // fill.getOrder().getId());

          missingPortfolioOrder
              .getParentFill()
              .loadAllChildOrdersByFill(
                  missingPortfolioOrder.getParentFill(), portfolioOrders, portfolioFills);
          log.debug(
              "Loading all child order for parent fill order "
                  + missingPortfolioOrder.getParentFill().getOrder().getUuid()
                  + "/"
                  + System.identityHashCode(missingPortfolioOrder.getParentFill().getOrder())
                  + " for missing portfolio "
                  + missingPortfolioOrder.getClass().getSimpleName()
                  + " :"
                  + missingPortfolioOrder.getUuid()
                  + "/"
                  + System.identityHashCode(missingPortfolioOrder));

          missingPortfolioOrder
              .getParentFill()
              .getOrder()
              .loadAllChildOrdersByParentOrder(
                  missingPortfolioOrder.getParentFill().getOrder(),
                  portfolioOrders,
                  portfolioFills);
        }
      }
    }
    // System.gc();
    Set<OrderUpdate> missingOrderUpdate = new HashSet<OrderUpdate>();
    for (Order missingOrder : missingWorkingOrders) {
      List<OrderUpdate> orderUpdates =
          EM.namedQueryList(OrderUpdate.class, "orderUpdate.findStateByOrder", missingOrder);
      orderUpdates.removeAll(Collections.singleton(null));
      if (orderUpdates == null || orderUpdates.isEmpty()) {
        log.debug(
            "No order updates found for "
                + missingOrder.getClass().getSimpleName()
                + " id:"
                + missingOrder.getUuid()
                + ". Creating and persisting order update");

        OrderUpdate createdOrderUpdate =
            orderUpdateFactory.create(
                missingOrder.getTime(), missingOrder, OrderState.NEW, OrderState.NEW);
        createdOrderUpdate.publishedAt(missingOrder.getTime());
        createdOrderUpdate.persit();
        missingOrderUpdate.add(createdOrderUpdate);
      } else {
        for (OrderUpdate orderUpdate : orderUpdates) {
          orderUpdate.setPersisted(true);
          if (orderUpdate.getOrder().equals(missingOrder)) orderUpdate.setOrder(missingOrder);
        }
        missingOrderUpdate.addAll(orderUpdates);
      }
    }

    // System.gc();
    workingOrderUpdates.addAll(missingOrderUpdate);

    for (OrderUpdate orderUpdate : workingOrderUpdates) {
      boolean orderInPortfolio = false;
      for (Order portfolioOrder : portfolioOrders.keySet()) {
        if (orderUpdate.getOrder().equals(portfolioOrder)) {
          orderUpdate.setOrder(portfolioOrders.get(portfolioOrder));
          orderInPortfolio = true;
          break;
        }
      }
      if (orderUpdate.getOrder().getPortfolio().equals(portfolio))
        orderUpdate.getOrder().setPortfolio(portfolio);

      if (orderUpdate.getOrder() instanceof SpecificOrder
          && orderUpdate.getOrder().getUnfilledVolume().isZero()) {
        if (!orderUpdate.getState().equals(OrderState.FILLED))
          log.error(
              "Order  "
                  + orderUpdate.getOrder().getUuid()
                  + "/"
                  + System.identityHashCode(orderUpdate.getOrder())
                  + " with state "
                  + OrderState.FILLED
                  + " rather than state "
                  + orderUpdate.getState()
                  + " to orderStateMap as order "
                  + orderUpdate.getOrder()
                  + " is fully filled");
      }

      log.debug(
          "Adding "
              + orderUpdate.getOrder().getUuid()
              + "/"
              + System.identityHashCode(orderUpdate.getOrder())
              + " with state "
              + orderUpdate.getState()
              + " to orderStateMap");
      orderStateMap.put(orderUpdate.getOrder(), orderUpdate.getState());

      if (stateOrderMap.get(orderUpdate.getState()) == null) {
        Set<Order> orderSet = new HashSet<Order>();
        stateOrderMap.put(orderUpdate.getState(), orderSet);
      }
      stateOrderMap.get(orderUpdate.getState()).add(orderUpdate.getOrder());
      if (orderUpdate.getState() == (OrderState.TRIGGER)) {
        Market triggerMarket = null;
        if (orderUpdate.getOrder().getParentFill() != null) {
          if (orderUpdate.getOrder().getParentFill().getMarket() instanceof Market)
            triggerMarket = (Market) orderUpdate.getOrder().getParentFill().getMarket();
        }

        if (triggerMarket == null && (orderUpdate.getOrder().getMarket() instanceof Market))
          triggerMarket = (Market) orderUpdate.getOrder().getMarket();
        if (triggerMarket != null) addTriggerOrder(orderUpdate.getOrder(), triggerMarket);
      }
    }
    log.info("loaded state of order: " + workingOrderUpdates);
  }

  @Override
  public Collection<Order> getPendingOrders() {
    // PersitOrderFill(order);
    List<Order> cointraderOpenOrders = new ArrayList<Order>();
    //  for (Order order : orderStateMap.keySet())
    // if (orderStateMap.get(order).isOpen())
    //  cointraderOpenOrders.add(order);
    try {
      //        replacingOrderLock.lock();
      // this == OrderState.NEW || this == OrderState.TRIGGER || this == OrderState.ROUTED || this
      // == OrderState.PLACED || this == OrderState.PARTFILLED;

      if (stateOrderMap.get(OrderState.NEW) != null)
        cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.NEW));
      if (stateOrderMap.get(OrderState.TRIGGER) != null)
        cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.TRIGGER));
      if (stateOrderMap.get(OrderState.PLACED) != null)
        cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PLACED));
      if (stateOrderMap.get(OrderState.PARTFILLED) != null)
        cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PARTFILLED));
      if (stateOrderMap.get(OrderState.ROUTED) != null)
        cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.ROUTED));
      if (stateOrderMap.get(OrderState.CANCELLING) != null)
        cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.CANCELLING));
    } catch (Exception e) {
      log.error(
          this.getClass().getSimpleName() + ": getPendingOrders - Unable to retrive order state ",
          e);
    } finally {
      // replacingOrderLock.unlock();
    }
    // we need to sort these by time?
    // Collections.sort(cointraderOpenOrders, timeComparator);
    return cointraderOpenOrders;
  }

  public static final Comparator<Order> timeComparator =
      new Comparator<Order>() {
        // @Override
        //  public int compare(RemoteEvent event, RemoteEvent event2) {
        //    return event.getTime().compareTo(event2.getTime());
        // }
        @Override
        public int compare(Order order, Order order2) {
          // if (event.getRemoteKey() != null && event.getRemoteKey() != null)
          //   return event.getRemoteKey().compareTo(event.getRemoteKey());

          // else
          return (order.getTime().compareTo(order2.getTime()));
        }
      };

  /*
   * @Override public void cancelOrder(Order order) { //PersitOrderFill(order); //CreateTransaction(order); //updateOrderState(order, OrderState); if
   * (orderStateMap.get(order) != null && orderStateMap.get(order).isOpen()) { log.info("cancelOrder: Cancelling  order " + order);
   * updateOrderState(order, OrderState.CANCELLING, true); } else log.info("cancelOrder: Unable to cancel order with state " +
   * orderStateMap.get(order) + " order " + order); }
   * @Override public void updateWorkingOrderQuantity(Order order, Amount quantity) { if (quantity.isZero()) { cancelOrder(order); return; } if (order
   * instanceof GeneralOrder) { GeneralOrder generalOrder = (GeneralOrder) order; log.info("updateing quanity of general order: " + order + " from: "
   * + order.getVolume() + " to: " + quantity); generalOrder.setVolumeDecimal(quantity.asBigDecimal()); generalOrder.getVolume(); } else if (order
   * instanceof SpecificOrder) { SpecificOrder specifcOrder = (SpecificOrder) order; log.info("updateing quanity of specific order: " + order +
   * " from: " + order.getVolume() + " to: " + quantity); handleUpdateSpecificOrderWorkingQuantity(specifcOrder,
   * quantity.toBasis(specifcOrder.getMarket().getVolumeBasis(), Remainder.ROUND_EVEN)); specifcOrder.getVolume(); } //PersitOrderFill(order);
   * //CreateTransaction(order); //updateOrderState(order, OrderState); }
   */

  @Override
  public Collection<Order> handleCancelAllShortTimedOrders(Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    if (market == null || portfolio == null) return cancelledOrders;

    boolean found = false;
    Fill parentFill;
    Order parentOrder;

    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllShortTimedOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .get(FillType.TIMED)
              == null
          || triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.TIMED)
              .isEmpty()) continue;
      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.TIMED)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(FillType.TIMED)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();

          if (triggerOrder.getStopPrice() != null) cancelledOrders.add(triggerOrder);
          Collection<SpecificOrder> closingOrders = null;
          if (triggerOrder.getParentFill() != null) {
            triggerOrder
                .getParentFill()
                .setPositionType(
                    (triggerOrder.getParentFill().getVolumeCount() == 0
                        ? PositionType.FLAT
                        : (triggerOrder.getParentFill().getVolumeCount() > 0
                            ? PositionType.LONG
                            : PositionType.SHORT)));

            if (triggerOrder.isAsk())
              closingOrders =
                  getPendingLongCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());
            if (triggerOrder.isBid())
              closingOrders =
                  getPendingShortCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());

            for (Order closingOrder : closingOrders)
              if (closingOrder.getParentFill() != null
                  && triggerOrder.getParentFill() != null
                  && closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                found = true;
                break;
              }
          }
        }
      }

      removeTriggerOrders(market, cancelledOrders);
    }
    //   }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllShortTimedOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + " Cancelled Short Timed Trigger Order: "
              + cancelledOrder);
    }

    return cancelledOrders;
  }

  @Override
  public Collection<Order> handleCancelAllShortStopOrders(Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    if (market == null || portfolio == null) return cancelledOrders;

    boolean found = false;
    Fill parentFill;
    Order parentOrder;

    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllShortStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .get(FillType.STOP_LIMIT)
              == null
          || triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.STOP_LIMIT)
              .isEmpty()) continue;
      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();

          if (triggerOrder.getStopPrice() != null) cancelledOrders.add(triggerOrder);
          Collection<SpecificOrder> closingOrders = null;
          if (triggerOrder.getParentFill() != null) {
            triggerOrder
                .getParentFill()
                .setPositionType(
                    (triggerOrder.getParentFill().getVolumeCount() == 0
                        ? PositionType.FLAT
                        : (triggerOrder.getParentFill().getVolumeCount() > 0
                            ? PositionType.LONG
                            : PositionType.SHORT)));

            if (triggerOrder.isAsk())
              closingOrders =
                  getPendingLongCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());
            if (triggerOrder.isBid())
              closingOrders =
                  getPendingShortCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());

            for (Order closingOrder : closingOrders)
              if (closingOrder.getParentFill() != null
                  && triggerOrder.getParentFill() != null
                  && closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                found = true;
                break;
              }
          }
        }
      }

      removeTriggerOrders(market, cancelledOrders);
    }
    //   }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllShortStopOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + " Cancelled Short Stop Trigger Order: "
              + cancelledOrder);
    }

    return cancelledOrders;
  }

  @Override
  public void handleCancelAllTriggerOrdersByParentFill(Fill parentFill) {

    if (parentFill == null) return;
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllTriggerOrdersByParentFill to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (triggerOrders.get(parentFill.getMarket()) != null
        && !triggerOrders.get(parentFill.getMarket()).isEmpty()) {

      for (Iterator<Double> itd = triggerOrders.get(parentFill.getMarket()).keySet().iterator();
          itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        for (Iterator<TransactionType> ittt =
                triggerOrders.get(parentFill.getMarket()).get(triggerInterval).keySet().iterator();
            ittt.hasNext(); ) {
          TransactionType transactionType = ittt.next();
          for (Iterator<FillType> ift =
                  triggerOrders
                      .get(parentFill.getMarket())
                      .get(triggerInterval)
                      .get(transactionType)
                      .keySet()
                      .iterator();
              ift.hasNext(); ) {
            FillType fillType = ift.next();

            synchronized (
                triggerOrders
                    .get(parentFill.getMarket())
                    .get(triggerInterval)
                    .get(transactionType)
                    .get(fillType)) {
              for (Iterator<Order> itto =
                      triggerOrders
                          .get(parentFill.getMarket())
                          .get(triggerInterval)
                          .get(transactionType)
                          .get(fillType)
                          .iterator();
                  itto.hasNext(); ) {
                Order triggerOrder = itto.next();

                if (triggerOrder.getParentFill() != null
                    && triggerOrder.getParentFill().equals(parentFill)) {

                  itto.remove();
                  if (trailingTriggerOrders != null
                      && trailingTriggerOrders.get(parentFill.getMarket()) != null
                      && trailingTriggerOrders.get(parentFill.getMarket()).get(transactionType)
                          != null
                      && !trailingTriggerOrders
                          .get(parentFill.getMarket())
                          .get(transactionType)
                          .isEmpty()) {
                    synchronized (
                        trailingTriggerOrders.get(parentFill.getMarket()).get(transactionType)) {
                      trailingTriggerOrders
                          .get(parentFill.getMarket())
                          .get(transactionType)
                          .remove(triggerOrder);
                    }
                  }

                  log.debug(
                      "handleCancelAllTriggerOrdersByParentFill called from class "
                          + Thread.currentThread().getStackTrace()[2]
                          + " Cancelled Trigger Order "
                          + triggerOrder
                          + " for : "
                          + parentFill);
                }
              }
            }
          }
        }
      }
    }
  }

  @Override
  public Collection<Order> handleCancelAllLongTimedOrders(Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    if (market == null || portfolio == null) return cancelledOrders;
    boolean found = false;
    Fill parentFill;
    Order parentOrder;

    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllLongTimedOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .get(FillType.TIMED)
              == null
          || triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.TIMED)
              .isEmpty()) continue;

      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.TIMED)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.TIMED)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();

          if (triggerOrder.getStopPrice() != null) cancelledOrders.add(triggerOrder);

          Collection<SpecificOrder> closingOrders = null;
          if (triggerOrder.getParentFill() != null) {
            triggerOrder
                .getParentFill()
                .setPositionType(
                    (triggerOrder.getParentFill().getVolumeCount() == 0
                        ? PositionType.FLAT
                        : (triggerOrder.getParentFill().getVolumeCount() > 0
                            ? PositionType.LONG
                            : PositionType.SHORT)));

            if (triggerOrder.isAsk())
              closingOrders =
                  getPendingLongCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());
            if (triggerOrder.isBid())
              closingOrders =
                  getPendingShortCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());

            for (Order closingOrder : closingOrders)
              if (closingOrder.getParentFill() != null
                  && triggerOrder.getParentFill() != null
                  && closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                found = true;
                break;
              }
          }
        }
      }

      removeTriggerOrders(market, cancelledOrders);

      for (Order cancelledOrder : cancelledOrders) {
        updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
        log.debug(
            "handleCancelAllLongTimedOrders called from class "
                + Thread.currentThread().getStackTrace()[2]
                + " Cancelled Long Timed Trigger Order: "
                + cancelledOrder);
      }
    }

    return cancelledOrders;
  }

  @Override
  public Collection<Order> handleCancelAllLongStopOrders(Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    if (market == null || portfolio == null) return cancelledOrders;
    boolean found = false;
    Fill parentFill;
    Order parentOrder;

    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllLongStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .get(FillType.STOP_LIMIT)
              == null
          || triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT)
              .isEmpty()) continue;

      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();

          if (triggerOrder.getStopPrice() != null) cancelledOrders.add(triggerOrder);

          Collection<SpecificOrder> closingOrders = null;
          if (triggerOrder.getParentFill() != null) {
            triggerOrder
                .getParentFill()
                .setPositionType(
                    (triggerOrder.getParentFill().getVolumeCount() == 0
                        ? PositionType.FLAT
                        : (triggerOrder.getParentFill().getVolumeCount() > 0
                            ? PositionType.LONG
                            : PositionType.SHORT)));

            if (triggerOrder.isAsk())
              closingOrders =
                  getPendingLongCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());
            if (triggerOrder.isBid())
              closingOrders =
                  getPendingShortCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket());

            for (Order closingOrder : closingOrders)
              if (closingOrder.getParentFill() != null
                  && triggerOrder.getParentFill() != null
                  && closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                found = true;
                break;
              }
          }
        }
      }

      removeTriggerOrders(market, cancelledOrders);

      for (Order cancelledOrder : cancelledOrders) {
        updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
        log.debug(
            "handleCancelAllLongStopOrders called from class "
                + Thread.currentThread().getStackTrace()[2]
                + " Cancelled Long Stop Trigger Order: "
                + cancelledOrder);
      }
    }

    return cancelledOrders;
  }

  @Override
  public Collection<Order> handleCancelAllLongTimedOrders(
      Portfolio portfolio, Market market, double interval) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    if (market == null || portfolio == null) return cancelledOrders;

    boolean found = false;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllLongTimedOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .get(FillType.TIMED)
              == null
          || triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.TIMED)
              .isEmpty()) continue;
      //  .get(market).get(TransactionType
      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.TIMED)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.TIMED)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();

          if (triggerOrder.getStopPrice() != null && triggerOrder.getOrderGroup() == interval)
            cancelledOrders.add(triggerOrder);
          Collection<SpecificOrder> closingOrders = null;
          if (triggerOrder.getParentFill() != null) {
            triggerOrder
                .getParentFill()
                .setPositionType(
                    (triggerOrder.getParentFill().getVolumeCount() == 0
                        ? PositionType.FLAT
                        : (triggerOrder.getParentFill().getVolumeCount() > 0
                            ? PositionType.LONG
                            : PositionType.SHORT)));

            if (triggerOrder.isAsk())
              closingOrders =
                  getPendingLongCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket(),
                      interval);
            if (triggerOrder.isBid())
              closingOrders =
                  getPendingShortCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket(),
                      interval);

            for (Order closingOrder : closingOrders)
              if (closingOrder.getParentFill() != null
                  && triggerOrder.getParentFill() != null
                  && closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                found = true;
                break;
              }
            //   if (closingOrders == null || closingOrders.isEmpty() || !found)
            //     triggerOrder.getParentFill().setStopPriceCount(0);

          }
        }
      }

      removeTriggerOrders(market, cancelledOrders);
    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllLongTimedOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + " Cancelled Long Timed Trigger Order: "
              + cancelledOrder);
    }
    return cancelledOrders;
  }

  @Override
  public Collection<Order> handleCancelAllLongStopOrders(
      Portfolio portfolio, Market market, double interval) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    if (market == null || portfolio == null) return cancelledOrders;

    boolean found = false;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllLongStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .get(FillType.STOP_LIMIT)
              == null
          || triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT)
              .isEmpty()) continue;
      //  .get(market).get(TransactionType
      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();

          if (triggerOrder.getStopPrice() != null && triggerOrder.getOrderGroup() == interval)
            cancelledOrders.add(triggerOrder);
          Collection<SpecificOrder> closingOrders = null;
          if (triggerOrder.getParentFill() != null) {
            triggerOrder
                .getParentFill()
                .setPositionType(
                    (triggerOrder.getParentFill().getVolumeCount() == 0
                        ? PositionType.FLAT
                        : (triggerOrder.getParentFill().getVolumeCount() > 0
                            ? PositionType.LONG
                            : PositionType.SHORT)));

            if (triggerOrder.isAsk())
              closingOrders =
                  getPendingLongCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket(),
                      interval);
            if (triggerOrder.isBid())
              closingOrders =
                  getPendingShortCloseOrders(
                      triggerOrder.getPortfolio(),
                      triggerOrder.getExecutionInstruction(),
                      (Market) triggerOrder.getMarket(),
                      interval);

            for (Order closingOrder : closingOrders)
              if (closingOrder.getParentFill() != null
                  && triggerOrder.getParentFill() != null
                  && closingOrder.getParentFill().equals(triggerOrder.getParentFill())) {
                found = true;
                break;
              }
            //   if (closingOrders == null || closingOrders.isEmpty() || !found)
            //     triggerOrder.getParentFill().setStopPriceCount(0);

          }
        }
      }

      removeTriggerOrders(market, cancelledOrders);
    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllLongStopOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + " Cancelled Long Stop Trigger Order: "
              + cancelledOrder);
    }
    return cancelledOrders;
  }

  // need to adde null checks in here.
  @Override
  public boolean handleCancelGeneralOrder(GeneralOrder order) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    boolean found = false;
    boolean isCancelled = false;
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelGeneralOrder to called from stack "
            + Thread.currentThread().getStackTrace()[2]);

    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        for (Iterator<TransactionType> ittt =
                triggerOrders.get(market).get(triggerInterval).keySet().iterator();
            ittt.hasNext(); ) {
          TransactionType transactionType = ittt.next();
          // Need to add null check here!
          for (Iterator<FillType> itf =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(transactionType)
                      .keySet()
                      .iterator();
              itf.hasNext(); ) {
            FillType fillType = itf.next();
            if (triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(transactionType)
                .get(fillType)
                .contains(order)) {
              cancelledOrders.add(order);
            }
            for (Order cancelledOrder : cancelledOrders) {
              updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
              log.debug(
                  "handleCancelGeneralOrder called from class "
                      + Thread.currentThread().getStackTrace()[2]
                      + " Cancelled General  Trigger Order: "
                      + cancelledOrder);
              // if (cancelledOrder.)
              Collection<SpecificOrder> closingOrders = null;
              if (cancelledOrder.getParentFill() != null) {
                cancelledOrder
                    .getParentFill()
                    .setPositionType(
                        (cancelledOrder.getParentFill().getVolumeCount() == 0
                            ? PositionType.FLAT
                            : (cancelledOrder.getParentFill().getVolumeCount() > 0
                                ? PositionType.LONG
                                : PositionType.SHORT)));

                if (cancelledOrder.isAsk())
                  closingOrders =
                      getPendingLongCloseOrders(
                          cancelledOrder.getPortfolio(),
                          cancelledOrder.getExecutionInstruction(),
                          (Market) cancelledOrder.getMarket());
                if (cancelledOrder.isBid())
                  closingOrders =
                      getPendingShortCloseOrders(
                          cancelledOrder.getPortfolio(),
                          cancelledOrder.getExecutionInstruction(),
                          (Market) cancelledOrder.getMarket());

                for (Order closingOrder : closingOrders)
                  if (closingOrder.getParentFill() != null
                      && cancelledOrder.getParentFill() != null
                      && closingOrder.getParentFill().equals(cancelledOrder.getParentFill())) {
                    found = true;
                    break;
                  }
              }
              isCancelled = true;
            } // might not be a trigger or
          }
        }
      }
      removeTriggerOrders(market, cancelledOrders);
    }
    return isCancelled;
  }

  @Override
  public void adjustShortStopLoss(Amount price, Amount amount, Boolean force, double orderGroup) {
    log.debug(
        this.getClass().getSimpleName()
            + " : adjustShortStopLoss to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
            || triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(FillType.STOP_LIMIT)
                == null
            || triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .get(FillType.STOP_LIMIT)
                .isEmpty()) continue;

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .get(FillType.STOP_LIMIT)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(FillType.STOP_LIMIT)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();
            if (triggerOrder.isBid()
                && triggerOrder.getStopPrice() != null
                && (orderGroup == 0
                    || triggerOrder.getOrderGroup() == 0
                    || orderGroup == triggerOrder.getOrderGroup())) {
              log.trace(
                  "Determining to adjust stops from trigger order stop price "
                      + triggerOrder.getStopPrice()
                      + " to stop price: "
                      + price.plus(amount.abs()));

              long stopPrice =
                  (force)
                      ? (price
                              .plus(amount.abs())
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount()
                      : Math.min(
                          (triggerOrder
                                  .getStopPrice()
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount(),
                          (price
                                  .plus(amount.abs())
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount());
              DecimalAmount stopDiscrete =
                  DecimalAmount.of(
                      new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
              if (stopDiscrete.compareTo(triggerOrder.getStopPrice()) == 0) return;
              log.debug(
                  this.getClass().getSimpleName()
                      + ":adjustShortStopLoss Updsting stop price from "
                      + triggerOrder.getStopPrice()
                      + " to "
                      + stopDiscrete
                      + " for "
                      + triggerOrder);

              if ((amount == null || amount.isZero()) && triggerOrder.getStopAmount() != null
                  || !triggerOrder.getStopAmount().isZero()) {

                DecimalAmount impliedStopAmount =
                    DecimalAmount.of(
                        triggerOrder
                            .getStopAmount()
                            .minus(triggerOrder.getStopPrice().minus(stopDiscrete).abs())
                            .abs());
                triggerOrder.setStopAmount(impliedStopAmount);
              }
              triggerOrder.setStopPrice(stopDiscrete);
              triggerOrder.setStopAdjustmentCount(triggerOrder.getStopAdjustmentCount() + 1);

              if (triggerOrder.getParentFill() != null) {
                triggerOrder.getParentFill().setStopPriceCount(stopPrice);
                //   triggerOrder.getParentFill().merge();
              }
              triggerOrder.merge();
            }
          }
        }
      }
      sortShortStopOrders(market);
    }
  }

  @Override
  public void adjustShortStopLossByAmount(
      Amount price, Boolean force, double orderGroup, double scaleFactor) {
    log.debug(
        this.getClass().getSimpleName()
            + " : adjustShortStopLossByAmount to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();
        if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
            || triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(FillType.STOP_LIMIT)
                == null
            || triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .get(FillType.STOP_LIMIT)
                .isEmpty()) continue;

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .get(FillType.STOP_LIMIT)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(FillType.STOP_LIMIT)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();
            if (triggerOrder.isBid()
                && triggerOrder.getStopPrice() != null
                && (orderGroup == 0
                    || triggerOrder.getOrderGroup() == 0
                    || orderGroup == triggerOrder.getOrderGroup())) {
              // curent stop is 160, new stop price is 100, so we reducethe stop of 160 by scale
              // factor * (triggerOrder.getStopPrice()-price)
              log.trace(
                  "Determining to adjust stops from trigger order stop price "
                      + triggerOrder.getStopPrice()
                      + " to stop price: "
                      + triggerOrder
                          .getStopPrice()
                          .minus(
                              ((triggerOrder.getStopPrice().minus(price)))
                                  .times(scaleFactor, Remainder.ROUND_EVEN)));

              long stopPrice =
                  (force)
                      ? (triggerOrder
                              .getStopPrice()
                              .minus(
                                  ((triggerOrder.getStopPrice().minus(price)))
                                      .times(scaleFactor, Remainder.ROUND_EVEN))
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount()
                      : Math.min(
                          (triggerOrder
                                  .getStopPrice()
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount(),
                          (triggerOrder
                                  .getStopPrice()
                                  .minus(
                                      ((triggerOrder.getStopPrice().minus(price)))
                                          .times(scaleFactor, Remainder.ROUND_EVEN))
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount());
              DecimalAmount stopDiscrete =
                  DecimalAmount.of(
                      new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
              if (stopDiscrete.compareTo(triggerOrder.getStopPrice()) == 0) return;
              log.debug(
                  this.getClass().getSimpleName()
                      + ":adjustShortStopLossByAmount Updsting stop price from "
                      + triggerOrder.getStopPrice()
                      + " to "
                      + stopDiscrete
                      + " for "
                      + triggerOrder);

              DecimalAmount impliedStopAmount =
                  DecimalAmount.of(
                      triggerOrder
                          .getStopAmount()
                          .minus(triggerOrder.getStopPrice().minus(stopDiscrete).abs())
                          .abs());
              triggerOrder.setStopAmount(impliedStopAmount);

              triggerOrder.setStopPrice(stopDiscrete);
              triggerOrder.setStopAdjustmentCount(triggerOrder.getStopAdjustmentCount() + 1);

              if (triggerOrder.getParentFill() != null) {
                triggerOrder.getParentFill().setStopPriceCount(stopPrice);
                //   triggerOrder.getParentFill().merge();
              }
              triggerOrder.merge();
            }
          }
        }
      }
      sortShortStopOrders(market);
    }
  }

  @Override
  public void adjustLongStopLossByAmount(
      Amount price, Boolean force, double orderGroup, double scaleFactor) {
    log.debug(
        this.getClass().getSimpleName()
            + " : adjustLongStopLossByAmount to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
            || triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.STOP_LIMIT)
                == null
            || triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(FillType.STOP_LIMIT)
                .isEmpty()) continue;
        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(FillType.STOP_LIMIT)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(FillType.STOP_LIMIT)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();
            if (triggerOrder.isAsk()
                && triggerOrder.getStopPrice() != null
                && (orderGroup == 0
                    || triggerOrder.getOrderGroup() == 0
                    || orderGroup == triggerOrder.getOrderGroup())) {

              // curent stop is 160, new stop price is 220, so we raise the 160 by scale factor *
              // (triggerOrder.getStopPrice()-price)
              log.trace(
                  "Determining to adjust stops from trigger order stop price "
                      + triggerOrder.getStopPrice()
                      + " to stop price: "
                      + triggerOrder
                          .getStopPrice()
                          .plus(
                              (price.minus(triggerOrder.getStopPrice()))
                                  .times(scaleFactor, Remainder.ROUND_EVEN)));
              long stopPrice =
                  (force)
                      ? (triggerOrder
                              .getStopPrice()
                              .plus(
                                  (price.minus(triggerOrder.getStopPrice()))
                                      .times(scaleFactor, Remainder.ROUND_EVEN))
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount()
                      : Math.max(
                          (triggerOrder
                                  .getStopPrice()
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount(),
                          (triggerOrder
                                  .getStopPrice()
                                  .plus(
                                      (price.minus(triggerOrder.getStopPrice()))
                                          .times(scaleFactor, Remainder.ROUND_EVEN))
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount());
              DecimalAmount stopDiscrete =
                  DecimalAmount.of(
                      new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
              if (stopDiscrete.compareTo(triggerOrder.getStopPrice()) == 0) return;
              log.debug(
                  this.getClass().getSimpleName()
                      + ":adjustLongStopLossByAmount Updsting stop price from "
                      + triggerOrder.getStopPrice()
                      + " to "
                      + stopDiscrete
                      + " for "
                      + triggerOrder);

              DecimalAmount impliedStopAmount =
                  DecimalAmount.of(
                      triggerOrder
                          .getStopAmount()
                          .minus(triggerOrder.getStopPrice().minus(stopDiscrete).abs())
                          .abs());
              triggerOrder.setStopAmount(impliedStopAmount);

              triggerOrder.setStopPrice(stopDiscrete);
              triggerOrder.setStopAdjustmentCount(triggerOrder.getStopAdjustmentCount() + 1);

              if (triggerOrder.getParentFill() != null) {
                triggerOrder.getParentFill().setStopPriceCount(stopPrice);
                //  triggerOrder.getParentFill().merge();
              }
              triggerOrder.merge();
            }
          }
        }
      }
      sortLongStopOrders(market);
    }
  }

  @Override
  public void adjustLongStopLoss(Amount price, Amount amount, Boolean force, double orderGroup) {
    log.debug(
        this.getClass().getSimpleName()
            + " : adjustLongStopLoss to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
            || triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.STOP_LIMIT)
                == null
            || triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(FillType.STOP_LIMIT)
                .isEmpty()) continue;

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(FillType.STOP_LIMIT)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(FillType.STOP_LIMIT)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();
            if (triggerOrder.isAsk()
                && triggerOrder.getStopPrice() != null
                && (orderGroup == 0
                    || triggerOrder.getOrderGroup() == 0
                    || orderGroup == triggerOrder.getOrderGroup())) {
              log.trace(
                  "Determining to adjust stops from trigger order stop price "
                      + triggerOrder.getStopPrice()
                      + " to stop price: "
                      + price.minus(amount.abs()));
              long stopPrice =
                  (force)
                      ? (price
                              .minus(amount.abs())
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount()
                      : Math.max(
                          (triggerOrder
                                  .getStopPrice()
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount(),
                          (price
                                  .minus(amount.abs())
                                  .toBasis(
                                      triggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN))
                              .getCount());
              DecimalAmount stopDiscrete =
                  DecimalAmount.of(
                      new DiscreteAmount(stopPrice, triggerOrder.getMarket().getPriceBasis()));
              if (stopDiscrete.compareTo(triggerOrder.getStopPrice()) == 0) return;
              log.debug(
                  this.getClass().getSimpleName()
                      + ":adjustLongStopLoss Updsting stop price from "
                      + triggerOrder.getStopPrice()
                      + " to "
                      + stopDiscrete
                      + " for "
                      + triggerOrder);
              if ((amount == null || amount.isZero()) && triggerOrder.getStopAmount() != null
                  || !triggerOrder.getStopAmount().isZero()) {

                DecimalAmount impliedStopAmount =
                    DecimalAmount.of(
                        triggerOrder
                            .getStopAmount()
                            .minus(triggerOrder.getStopPrice().minus(stopDiscrete).abs())
                            .abs());
                triggerOrder.setStopAmount(impliedStopAmount);
              }
              triggerOrder.setStopPrice(stopDiscrete);
              triggerOrder.setStopAdjustmentCount(triggerOrder.getStopAdjustmentCount() + 1);

              if (triggerOrder.getParentFill() != null) {
                triggerOrder.getParentFill().setStopPriceCount(stopPrice);
                //  triggerOrder.getParentFill().merge();
              }
              triggerOrder.merge();
            }
          }
        }
      }
      sortLongStopOrders(market);
    }
  }

  @Override
  public void adjustLongTargetPrices(Amount price, Amount amount, double orderGroup) {
    log.debug(
        this.getClass().getSimpleName()
            + " : adjustLongTargetPrices to called from stack "
            + Thread.currentThread().getStackTrace()[2]);

    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
            || triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.TARGET_LIMIT)
                == null
            || triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(FillType.TARGET_LIMIT)
                .isEmpty()) continue;
        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(FillType.TARGET_LIMIT)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(FillType.TARGET_LIMIT)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            // TODO: should this be the max for bid and the min for shorts
            if (triggerOrder.isAsk()
                && (orderGroup == 0
                    || triggerOrder.getOrderGroup() == 0
                    || orderGroup == triggerOrder.getOrderGroup())) {
              long targetPrice =
                  Math.max(
                      (triggerOrder
                              .getTargetPrice()
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount(),
                      (price
                              .plus(amount.abs())
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount());
              DecimalAmount targetDiscrete =
                  DecimalAmount.of(
                      new DiscreteAmount(targetPrice, triggerOrder.getMarket().getPriceBasis()));
              triggerOrder.setTargetPrice(targetDiscrete);
              if (triggerOrder.getParentFill() != null)
                triggerOrder.getParentFill().setTargetPriceCount(targetPrice);
            }
          }
        }
      }
      sortLongTargetOrders(market);
    }
  }

  @Override
  public void adjustShortTargetPrices(Amount price, Amount amount, double orderGroup) {
    log.debug(
        this.getClass().getSimpleName()
            + " : adjustShortTargetPrices to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
            || triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(FillType.TARGET_LIMIT)
                == null
            || triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .get(FillType.TARGET_LIMIT)
                .isEmpty()) continue;

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .get(FillType.TARGET_LIMIT)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(FillType.TARGET_LIMIT)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            // TODO: should this be the max for bid and the min for shorts
            if (triggerOrder.isBid()
                && (orderGroup == 0
                    || triggerOrder.getOrderGroup() == 0
                    || orderGroup == triggerOrder.getOrderGroup())) {
              long targetPrice =
                  Math.min(
                      (triggerOrder
                              .getTargetPrice()
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount(),
                      (price
                              .minus(amount.abs())
                              .toBasis(
                                  triggerOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN))
                          .getCount());
              DecimalAmount TargetDiscrete =
                  DecimalAmount.of(
                      new DiscreteAmount(targetPrice, triggerOrder.getMarket().getPriceBasis()));
              triggerOrder.setTargetPrice(TargetDiscrete);
              if (triggerOrder.getParentFill() != null)
                triggerOrder.getParentFill().setTargetPriceCount(targetPrice);
            }
          }
        }
      }
      sortShortTargetOrders(market);
    }
  }

  // loop over our  tigger orders

  @Override
  public OrderState getOrderState(Order o) throws IllegalStateException {
    OrderState state = orderStateMap.get(o);
    if (state == null) {
      try {
        //  log.debug(this.getClass().getSimpleName() + ":getOrderState - Loading order " + o + "
        // from database")
        // let's check if we can get state from exchange or DB
        OrderState orderState = getOrderStateFromOrderService(o);
        if (orderState != null && orderState.isOpen()) {
          updateOrderState(o, orderState, true);

        } else if (orderState != null && !orderState.isOpen()) {
          return orderState;
        } else throw new IllegalStateException("Untracked order " + o);

      } catch (Throwable e) {
        throw new IllegalStateException("Untracked order " + o);
      }
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

  public SpecificOrder getSpecifcOrderFromStateMap(UUID uuid) {
    boolean found = false;
    for (Order order : orderStateMap.keySet()) {
      if (order.getUuid().equals(uuid)) {
        found = true;
        if (order instanceof SpecificOrder) return (SpecificOrder) order;
      }
    }
    if (!found) throw new IllegalStateException("Untracked order " + uuid);
    return null;
  }

  @Override
  public boolean getTradingEnabled() {

    return enableTrading;
  }

  @Override
  public void setTradingEnabled(Boolean enableTrading) {

    log.debug(
        "{} : setTradingEnabled to {} called from stack  {}",
        this.getClass().getSimpleName(),
        enableTrading,
        Thread.currentThread().getStackTrace()[2]);

    this.enableTrading = enableTrading;
  }

  // @When("@Priority(8) select * from OrderUpdate")
  public synchronized void handleOrderUpdate(OrderUpdate orderUpdate) {
    log.debug(
        "{} : handleOrderUpdate to called from stack {} ",
        this.getClass().getSimpleName(),
        Thread.currentThread().getStackTrace()[2]);

    // TOOD somethig is up in here causing the states of stop orders to be changed when they are
    // still resting
    OrderState orderState = orderUpdate.getState();
    Order order = orderUpdate.getOrder();
    //        if (stateOrderMap.get(orderState) == null) {
    //            Set<Order> orderSet = new HashSet<Order>();
    //            // orderSet.add(order);
    //            if (oldState != null)
    //                stateOrderMap.get(oldState).remove(order);
    //            stateOrderMap.put(orderState, orderSet);
    //
    //        }
    switch (orderState) {
      case NEW:
        // stateOrderMap.get(orderState).add(order);

        // orderStateMap.put(order, orderState);

        if (order.getParentOrder() != null
            && (order.getContingencyType() != ContingencyType.ONE_CANCELS_OTHER))
          updateParentOrderState(order.getParentOrder(), order, orderState);

        // TODO Order persitantce, keep getting TransientPropertyValueException  errors
        // PersitOrderFill(orderUpdate.getOrder());
        break;
      case TRIGGER:
        //  orderStateMap.put(order, orderState);
        // stateOrderMap.get(orderState).add(order);
        // if all children have same state, set parent state.
        if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null)
          updateParentOrderState(order.getParentOrder(), order, orderState);

        break;
      case ROUTED:
        //  orderStateMap.put(order, orderState);
        // stateOrderMap.get(orderState).add(order);
        if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null)
          updateParentOrderState(order.getParentOrder(), order, orderState);
        break;

      case PLACED:
        //   orderStateMap.put(order, orderState);
        //  stateOrderMap.get(orderState).add(order);
        if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null)
          updateParentOrderState(order.getParentOrder(), order, orderState);
        break;
      case PARTFILLED:
        //    orderStateMap.put(order, orderState);
        //   stateOrderMap.get(orderState).add(order);
        if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null)
          updateParentOrderState(order.getParentOrder(), order, orderState);
        break;

      case FILLED:
        // orderStateMap.put(order, orderState);
        // stateOrderMap.get(orderState).add(order);
        if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null)
          updateParentOrderState(order.getParentOrder(), order, orderState);
        break;

      case CANCELLING:
        if (order instanceof GeneralOrder) {
          GeneralOrder generalOrder = (GeneralOrder) order;
          handleCancelGeneralOrder(generalOrder);
        }
        break;
      case CANCELLED:
        if (order.getParentFill() != null) {
          order
              .getParentFill()
              .setPositionType(
                  (order.getParentFill().getVolumeCount() == 0
                      ? PositionType.FLAT
                      : (order.getParentFill().getVolumeCount() > 0
                          ? PositionType.LONG
                          : PositionType.SHORT)));
        }
        // So if we are canclleing and order, and the parent order is of type trigger, let's put it
        // back in trigger state if all the children care cancelled.
        if (order.getParentOrder() != null
            && order.getParentOrder().getOrderChildren() != null
            && order.getParentOrder().getOrderChildren().isEmpty()) {
          if (order.getParentOrder().getFillType().isTrigger())
            updateParentOrderState(order.getParentOrder(), order, OrderState.TRIGGER);
          else updateParentOrderState(order.getParentOrder(), order, orderState);

          break;
        }
        if (order.getParentOrder() != null
            && order.getParentOrder().getOrderChildren() != null
            && !order.getParentOrder().getOrderChildren().isEmpty()) {
          boolean fullyCancelled = true;
          for (Order child : order.getParentOrder().getOrderChildren()) {
            if (orderStateMap.get(child) != null
                && (!orderStateMap.get(child).isCancelled() && orderStateMap.get(child).isOpen())) {
              fullyCancelled = false;
              break;
            }
          }
          if (fullyCancelled)
            if (order.getParentOrder().getFillType().isTrigger())
              updateParentOrderState(order.getParentOrder(), order, OrderState.TRIGGER);
            else updateParentOrderState(order.getParentOrder(), order, orderState);
        }
        break;

      case EXPIRED:
        // orderStateMap.put(order, orderState);
        // stateOrderMap.get(orderState).add(order);
        // ;
        removeTriggerOrders(order.getMarket(), new ArrayList<Order>(Arrays.asList(order)));
        if (order.getParentFill() != null) {
          order
              .getParentFill()
              .setPositionType(
                  (order.getParentFill().getVolumeCount() == 0
                      ? PositionType.FLAT
                      : (order.getParentFill().getVolumeCount() > 0
                          ? PositionType.LONG
                          : PositionType.SHORT)));
        }
        if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null)
          updateParentOrderState(order.getParentOrder(), order, orderState);

        break;
      case REJECTED:
        //   orderStateMap.put(order, orderState);
        //  stateOrderMap.get(orderState).add(order);
        // ;
        removeTriggerOrders(order.getMarket(), new ArrayList<Order>(Arrays.asList(order)));
        if (order.getParentFill() != null) {
          order
              .getParentFill()
              .setPositionType(
                  (order.getParentFill().getVolumeCount() == 0
                      ? PositionType.FLAT
                      : (order.getParentFill().getVolumeCount() > 0
                          ? PositionType.LONG
                          : PositionType.SHORT)));
        }
        if (order.getParentOrder() != null && order.getParentOrder().getOrderChildren() != null)
          updateParentOrderState(order.getParentOrder(), order, orderState);

        break;
    }
  }

  // @When("@Priority(9) select * from Fill")
  @Override
  public void handleFillProcessing(Fill fill) {
    // fillProcessingLatch = new CountDownLatch(1);
    log.info("BaseOrderService:handleFillProcessing Fill Recieved: {}", fill);
    handleFill(fill);
    //    fillProcessingLatch.countDown();
    //  fillProcessingLatch = null;
    // service.submit(new handleFillRunnable(fill));
  }

  public void handleFill(Fill fill) {

    SpecificOrder order = fill.getOrder();
    log.debug("handleFill: Updating position for fill{}", fill);
    try {
      fill.persit();
      fill.getPortfolio().merge(fill);
    } catch (Throwable t) {
      // fill.persit();
      log.error("handleFill: Unable to update position for fill {} due to stack trace: ", fill, t);
    }

    if (fill.getPositionEffect() == PositionEffect.CLOSE) {
      log.debug("Received Fill{}", fill);
    }
    OrderState state =
        orderStateMap.get(order) == null ? order.getOrderState() : orderStateMap.get(order);
    log.debug("handleFill: update fill for order with state {}", state);
    if (state == null) {
      log.warn("Untracked order " + order);
      state = OrderState.PLACED;
    }
    if (state == (OrderState.NEW))
      log.warn("Fill received for Order in NEW state: skipping PLACED state");
    //   if (state.isOpen()) {
    log.debug("handleFill: Order is open with state {} for {}", state, order);

    OrderState newState;
    if (order.isFilled()) {
      log.debug("handleFill: Order is filled for {}", order);
      PositionType newFillState =
          ((fill.getVolumeCount() == 0
              ? PositionType.FLAT
              : (fill.getVolumeCount() > 0 ? PositionType.LONG : PositionType.SHORT)));
      // TODO create a new child order for a fraction of the quanity

      fill.setPositionType(newFillState);
      if (order.getParentFill() != null
          && (order.getVolume().compareTo(order.getParentFill().getVolume().negate()) == 0)) {
        order.getParentFill().setPositionType(PositionType.FLAT);
      }
      newState = OrderState.FILLED;
      if (order.getTargetStrategy() != null
          && order.getTargetStrategy().equals(TargetStrategy.PERCENTAGEOFVOLUME)
          && order.getPercentageOfVolume() != null
          && order.getPercentageOfVolumeInterval() != null) {
        SpecificOrder percentageOfVolumeOrder = buildPercentageOfVolumeOrder(fill);
        if (percentageOfVolumeOrder != null) {
          try {
            percentageOfVolumeOrder.persit();
            placeOrder(percentageOfVolumeOrder);
            log.info("Placed percentageOfVolumeOrder order {}", percentageOfVolumeOrder);

            // Now we can link them up
          } catch (Throwable e) {
            log.info(
                "Unable to place percentageOfVolumeOrder order {}. Full stack trace {}",
                percentageOfVolumeOrder,
                e);
            // throw e;
          }
        } else {
          log.error("Unable to build percentageOfVolumeOrder order from fill {}", fill);
        }
      }
    } else {
      log.debug("handleFill: Order is partfilled for{} ", order);

      newState = OrderState.PARTFILLED;
    }
    updateOrderState(order, newState, true);
    // }

    // TODO For some reason when this method is called it is locked, so removed syncronisation lock

    log.debug(
        "handleFill: Updated position for fill {} for order {}  with state {}  new state {} ",
        fill,
        order,
        state,
        newState);

    FillType fillType = null;
    ContingencyType contingencyType = null;
    List<Order> allChildOrders = new ArrayList<Order>();

    if (order.getParentOrder() != null
        || (order.getOrderChildren() != null && !order.getOrderChildren().isEmpty())) {
      fillType = order.getFillType();
      if (fillType == null)
        fillType =
            (order.getParentOrder() != null)
                ? order.getParentOrder().getFillType()
                : order.getFillType();

      switch (fillType) {
        case GOOD_TIL_CANCELLED:
          break;
        case GTC_OR_MARGIN_CAP:
          break;
        case CANCEL_REMAINDER:
          break;
        case LIMIT:
          break;
        case TRAILING_STOP_LIMIT:
        case TRAILING_UNREALISED_STOP_LIMIT:
        case STOP_LIMIT:
        case TIMED:
          break;

        case STOP_LOSS:
        case TIMED_EXIT:
          // Place a stop order at the stop price

          break;
        case TRAILING_STOP_LOSS:
          // Place a stop order at the stop price

          break;
        default:
          break;
      }
      contingencyType = order.getContingencyType();
      if (contingencyType == null)
        contingencyType =
            (order.getParentOrder() != null)
                ? order.getParentOrder().getContingencyType()
                : order.getContingencyType();
      if (order.getLinkedOrder() != null
          && order.getLinkedOrder() instanceof SpecificOrder
          && !order.getFillType().equals(FillType.MARKET)) {

        SpecificOrder pendingOrder = (SpecificOrder) order.getLinkedOrder();
        DecimalAmount linkedLimitPrice =
            DecimalAmount.of(
                pendingOrder.getLegNumber() == 2
                    ? order.getAverageFillPrice().minus(pendingOrder.getDifferentialPrice())
                    : order.getAverageFillPrice().plus(pendingOrder.getDifferentialPrice()));
        DiscreteAmount linkedLimitPriceDiscrete =
            linkedLimitPrice.toBasis(pendingOrder.getMarket().getPriceBasis(), Remainder.DISCARD);
        // linkedLimitPriceDiscrete =
        //   pendingOrder.getVolume().isPositive()
        //     ? linkedLimitPriceDiscrete.decrement(multiLegTicks)
        //   : linkedLimitPriceDiscrete.increment(multiLegTicks);

        Amount linkedVolume =
            (pendingOrder.getVolume().divide(fill.getOrder().getVolume(), Remainder.ROUND_EVEN))
                .times(fill.getVolume(), Remainder.ROUND_EVEN);

        DiscreteAmount linkedVolumeDiscrete =
            linkedVolume.toBasis(pendingOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);

        Order replaceOrder = null;
        try {
          replaceOrder =
              replaceOrder(
                  pendingOrder,
                  linkedLimitPriceDiscrete,
                  linkedVolumeDiscrete,
                  pendingOrder.getFillType());
          if (replaceOrder != null) {

            replaceOrder.withPeggedPrice(fill.getPrice());
            replaceOrder.setTimeToLive(pendingOrder.getTimeToLive());
            replaceOrder.setExecutionInstruction(pendingOrder.getExecutionInstruction());
            replaceOrder.merge();

            log.debug(
                "handelFill - At {} replaced order :{}  with order{} ",
                context.getTime(),
                replaceOrder,
                pendingOrder);
          }

          /// pendingOrder.merge();
          // pendingOrder.withComment(replaceOrder.getLinkedOrder().getId() + "/" +
          // pendingOrder.getDifferentialPrice());
        } catch (Throwable e) {

          try {
            updateOrderState(pendingOrder, getOrderStateFromOrderService(pendingOrder), true);

            log.error(
                "updateRestingOrders: Unable to place replacment old order  {} with state {} with replacement order {} {} "
                    + pendingOrder,
                getOrderState(pendingOrder),
                (replaceOrder != null
                    ? replaceOrder + " with state  " + getOrderState(replaceOrder)
                    : " null replacment order"),
                e);

          } catch (Throwable e1) {
            // TODO Auto-generated catch block
            log.error(
                "updateRestingOrders: Unable to place replacment old order  "
                    + pendingOrder
                    + " with state "
                    + getOrderState(pendingOrder)
                    + (replaceOrder != null
                        ? " with replacement order "
                            + replaceOrder
                            + " with state "
                            + getOrderState(replaceOrder)
                        : " null replacment order"),
                e1);
            //    throw e1;

          }
        }
      }
      switch (contingencyType) {
        case ONE_CANCELS_OTHER:
          SpecificOrder otherOrder;
          GeneralOrder otherGeneralOrder;
          if (order.getParentOrder() != null)
            for (Order pairOrder : order.getParentOrder().getOrderChildren()) {
              // for (Order pairSpecifcOrder : pairOrder.getChildren()) {

              if (pairOrder instanceof SpecificOrder && !pairOrder.equals(order)) {
                otherOrder = (SpecificOrder) pairOrder;
                if (getOrderState(otherOrder).isOpen())
                  try {
                    if (handleCancelSpecificOrder(otherOrder))
                      log.info(
                          "handleFill cancelled Specific otherOrder:"
                              + otherOrder
                              + " parent order: "
                              + order.getParentOrder());
                    else
                      log.info(
                          "handleFill ubale to cancel Specific otherOrder:"
                              + otherOrder
                              + " parent order: "
                              + order.getParentOrder());
                  } catch (OrderNotFoundException onfe) {
                    log.info("Order Not Found:" + otherOrder);
                  } catch (Exception | Error e) {
                    log.info("cancel specifc order " + otherOrder + ". Full stack trace", e);
                    throw e;
                  }
              }

              if (pairOrder instanceof GeneralOrder && !pairOrder.equals(order)) {
                otherGeneralOrder = (GeneralOrder) pairOrder;
                if (getOrderState(otherGeneralOrder).isOpen())
                  try {

                    if (handleCancelGeneralOrder(otherGeneralOrder))
                      log.info(
                          "handleFill cancelled otherGeneralOrder: "
                              + otherGeneralOrder
                              + " parent order: "
                              + order.getParentOrder());
                    else
                      log.info(
                          "handleFill ubale to otherGeneralOrder: "
                              + otherGeneralOrder
                              + " parent order: "
                              + order.getParentOrder());

                  } catch (OrderNotFoundException onfe) {
                    log.info("Order Not Found:" + otherGeneralOrder);
                  } catch (IllegalStateException onfe) {
                    log.info("Order Not Placed:" + otherGeneralOrder);

                  } catch (Exception | Error e) {
                    log.info("cancel general order " + otherGeneralOrder + ". Full stack trace", e);
                    throw e;
                  }
              }
              // }
              if (order.getParentOrder().getParentOrder() != null)
                for (Order childPairOrder :
                    order.getParentOrder().getParentOrder().getOrderChildren()) {
                  // for (Order pairSpecifcOrder : pairOrder.getChildren()) {

                  if (childPairOrder instanceof SpecificOrder && childPairOrder != order) {
                    otherOrder = (SpecificOrder) childPairOrder;
                    if (getOrderState(otherOrder).isOpen())
                      try {

                        if (handleCancelSpecificOrder(otherOrder))
                          log.info(
                              "handleFill cancelled specific otherOrder Order: "
                                  + otherOrder
                                  + " parent order: "
                                  + order.getParentOrder().getParentOrder());
                        else
                          log.info(
                              "handleFill ubale to cancel specific otherOrder Order: "
                                  + otherOrder
                                  + " parent order: "
                                  + order.getParentOrder().getParentOrder());

                        // handleCancelSpecificOrder(otherOrder);
                      } catch (OrderNotFoundException onfe) {
                        log.info("Order Not Found:" + otherOrder);
                      } catch (Exception | Error e) {
                        log.info("cancel spceific order " + otherOrder + ". Full stack trace", e);
                        throw e;
                      }
                  }
                  if (childPairOrder instanceof GeneralOrder
                      && childPairOrder != order.getParentOrder()) {
                    otherGeneralOrder = (GeneralOrder) childPairOrder;
                    if (getOrderState(childPairOrder).isOpen())
                      try {

                        if (handleCancelGeneralOrder(otherGeneralOrder))
                          log.info(
                              "handleFill cancelled general otherGeneralOrder Order: "
                                  + otherGeneralOrder
                                  + ".");
                        else
                          log.info(
                              "handleFill ubale to cancel general otherGeneralOrder Order: "
                                  + otherGeneralOrder
                                  + ".");

                      } catch (OrderNotFoundException onfe) {
                        log.info("Order Not Found:" + otherGeneralOrder);
                      } catch (Exception | Error e) {
                        log.info(
                            "cancel general order " + otherGeneralOrder + ". Full stack trace", e);
                        throw e;
                      }
                  }
                  // }
                }
              break;
            }
          break;
        default:
          break;
      }
    }
    // always create teh stops
    //    if (fill.getOrder().getParentOrder().getComment().contains("Reentry"))
    //      log.debug("test1");

    // lets check if the float leg (leg 2) of the pair order is fully filled, if it is we can
    // removed it from a triggered order.

    GeneralOrder stopOrder = buildStopLimitOrder(fill);
    SpecificOrder pairOrder = buildPairOrder(fill);
    GeneralOrder targetOrder = buildReentrantLimitOrder(fill);
    GeneralOrder timedOrder = buildTimedExitOrder(fill);
    //    stopOrder.persit();
    if (timedOrder != null && order.getPositionEffect() == (PositionEffect.OPEN)) {
      try {
        timedOrder.persit();
        placeOrder(timedOrder);
        log.info("Placed timed order " + stopOrder);

        // Now we can link them up
      } catch (Throwable e) {
        log.info("Unable to place timed order " + stopOrder + ". Full stack trace", e);
        // throw e;
      }
    }
    if (stopOrder != null && order.getPositionEffect() == (PositionEffect.OPEN)) {
      try {
        stopOrder.persit();
        placeOrder(stopOrder);
        log.info("Placed Stop order " + stopOrder);

        // Now we can link them up
      } catch (Throwable e) {
        log.info("Unable to place Stop order " + stopOrder + ". Full stack trace", e);
        // throw e;
      }
    }

    if (pairOrder != null) {
      try {
        pairOrder.persit();
        placeOrder(pairOrder);
        log.info("Placed pair order " + pairOrder);

        // Now we can link them up
      } catch (Throwable e) {
        log.info("Unable to place pair order " + pairOrder + ". Full stack trace", e);
        // throw e;
      }
    }

    if (targetOrder != null && order.getPositionEffect() == (PositionEffect.CLOSE)) {

      try {
        targetOrder.persit();
        placeOrder(targetOrder);
        log.info("Placed Target order " + targetOrder);

        // Now we can link them up
      } catch (Throwable e) {
        log.info("Unable to place Target order " + targetOrder + ". Full stack trace", e);
        // throw e;
      }
    }
    /*
     * TransactionType oppositeSide = (order.getParentFill() != null && order.getParentFill().isLong()) ? TransactionType.SELL : TransactionType.BUY;
     * if ((order.getPositionEffect() == null && order.getTransactionType() == oppositeSide) || order.getPositionEffect() == PositionEffect.CLOSE) {
     * // we know this is a closing trade // need to update any stop orders with new quanity ArrayList<Order> allChildOrders = new ArrayList<Order>();
     * getAllOrdersByParentFill(order.getParentFill(), allChildOrders); for (Order childOrder : allChildOrders) if (childOrder != order) { if
     * ((childOrder.getPositionEffect() == null && childOrder.getTransactionType() == oppositeSide) || childOrder.getPositionEffect() ==
     * PositionEffect.CLOSE) { log.info("updating quanity to : " + order.getUnfilledVolume() + " for  order: " + childOrder);
     * updateOrderQuantity(childOrder, order.getUnfilledVolume()); } } }
     */
    //

    // fill.merge();
    context.route(fill);
    CreateTransaction(fill, true);
  }

  private static final Comparator<Fill> timeOrderIdComparator =
      new Comparator<Fill>() {
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

  private static final Comparator<Order> timedOrderIdComparator =
      new Comparator<Order>() {
        @Override
        public int compare(Order event, Order event2) {
          int sComp = event.getTriggerTime().compareTo(event2.getTriggerTime());
          if (sComp != 0) {
            return sComp;
          } else {
            return (event.getUuid().compareTo(event2.getUuid()));
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

  private GeneralOrder buildReentrantLimitOrder(Fill fill) {

    //      GeneralOrder order = generalOrderFactory.create(fill.getOrder().getPortfolio(), this));

    //        OrderBuilder stoporder = new OrderBuilder(fill.getOrder().getPortfolio(), this);

    if ((fill.getPositionEffect() != null && fill.getPositionEffect() != PositionEffect.OPEN)
        && ((!fill.getUnfilledVolume().isZero()))) {
      Amount targetPrice = null;
      DiscreteAmount targetPriceDiscrete;
      BigDecimal bdTargetPrice;
      BigDecimal bdLimitPrice;
      GeneralOrder targetOrder;
      BigDecimal bdVolume = fill.getVolume().asBigDecimal();
      //   targetPrice = fill.getOrder().getParentOrder().getParentFill().getPrice();

      ArrayList<Fill> parentFills = new ArrayList<Fill>();
      Fill parent = null;
      fill.getAllParentFillsByFill(fill, parentFills);
      Collections.reverse(parentFills);
      Amount targetAmount = null;

      for (Fill parentFill : parentFills) {
        if (parentFill.getPositionEffect().equals(PositionEffect.OPEN)) {
          targetPrice = parentFill.getPrice();
          parent = parentFill;
          break;
        }
      }

      if (targetPrice == null) return null;
      targetAmount = targetAmount == null ? targetPrice.minus(fill.getPrice()).abs() : targetAmount;

      targetPriceDiscrete =
          new DiscreteAmount(
              DiscreteAmount.roundedCountForBasis(
                  targetPrice.asBigDecimal(), fill.getMarket().getPriceBasis()),
              fill.getMarket().getPriceBasis());
      bdTargetPrice = targetPriceDiscrete.asBigDecimal();
      // String comment = (fill.isLong()? "")

      bdLimitPrice =
          (fill.getVolume().isNegative())
              ? targetPriceDiscrete.increment(2).asBigDecimal()
              : targetPriceDiscrete.decrement(2).asBigDecimal();

      FillType fillType = null;
      if (parent.getFillChildOrders().get(0).getFillType() != null)
        switch (parent.getFillChildOrders().get(0).getFillType()) {
          case TRAILING_STOP_LOSS:
            break;
          case REENTRANT_STOP_LOSS:
          case REENTRANT_STOP_LIMIT:
            fillType = FillType.REENTRANT_STOP_LIMIT;
            break;
          case REENTRANT_TRAILING_STOP_LOSS:
          case REENTRANT_TRAILING_STOP_LIMIT:
            fillType = FillType.REENTRANT_TRAILING_STOP_LIMIT;
            break;
          case TRAILING_UNREALISED_STOP_LOSS:
            break;
        }
      if (fillType == null) return null;
      targetOrder =
          generalOrderFactory.create(context.getTime(), fill, fill.getMarket(), bdVolume, fillType);
      String comment =
          (bdVolume.compareTo(BigDecimal.ZERO) > 0) ? "Long Reentry Order" : "Short Reentry Order";
      //   if (stopOrder.getFillType().equals(FillType.TRAILING_UNREALISED_STOP_LIMIT))
      targetOrder.withLastBestPrice(fill.getPrice().asBigDecimal());

      targetOrder
          .withComment(comment)
          .withTargetPrice(bdTargetPrice)
          .withLimitPrice(bdLimitPrice)
          .withPositionEffect(PositionEffect.OPEN)
          .withExecutionInstruction(fill.getOrder().getExecutionInstruction())
          .withTargetAmount(targetAmount.asBigDecimal())
          .withTargetPercentage(parent.getFillChildOrders().get(0).getTargetPercentage())
          .withTriggerInterval(parent.getFillChildOrders().get(0).getTriggerInterval())
          .withUsePosition(false)
          .withOrderGroup(fill.getOrder().getOrderGroup());

      targetOrder.copyCommonFillProperties(fill);
      fill.setTargetPriceCount(targetPriceDiscrete.getCount());
      // this might cause the order to trigger as stop loss exit order!
      // if (fillType.equals(FillType.REENTRANT_STOP_LIMIT) ||
      // fillType.equals(FillType.REENTRANT_TRAILING_STOP_LIMIT)) {
      //   targetOrder.withStopPrice(bdStopPrice).withStopAmount(stopAmount.asBigDecimal())
      //         .withStopPercentage(fill.getOrder().getParentOrder().getStopPercentage());
      // fill.setStopPriceCount(stopPriceDiscrete.getCount());
      // }

      Set<Order> parentFillOrders = new HashSet<Order>();
      fill.getAllSpecificOrdersByParentFill(fill, parentFillOrders);
      for (Order childOrder : parentFillOrders) {
        if (childOrder instanceof SpecificOrder) {
          targetOrder.addChildOrder(childOrder);
          childOrder.setParentOrder(targetOrder);
        }
      }
      return targetOrder;
    }
    return null;
  }

  private GeneralOrder buildTimedExitOrder(Fill fill) {

    //      GeneralOrder order = generalOrderFactory.create(fill.getOrder().getPortfolio(), this));

    //        OrderBuilder stoporder = new OrderBuilder(fill.getOrder().getPortfolio(), this);

    //   fill.getAllOrdersByParentFill(allChildren)
    //    if (fill.getOrder().getParentOrder().getComment().contains("Reentry"))
    //      log.debug("test");
    if ((fill.getPositionEffect() != null && fill.getPositionEffect() != PositionEffect.CLOSE)
        && (!fill.getUnfilledVolume().isZero())) {
      BigDecimal bdVolume = fill.getVolume().asBigDecimal();
      ArrayList<Order> parentOrders = new ArrayList<Order>();
      fill.getAllParentOrdersByFill(fill, parentOrders);
      Collections.reverse(parentOrders);
      Order parent = null;
      Instant triggerTime = null;
      for (Order parentOrder : parentOrders) {

        if (parentOrder.getTriggerTime() != null) {
          triggerTime = parentOrder.getTriggerTime();
          break;
        }
      }

      if (triggerTime == null) return null;
      FillType fillType = FillType.TIMED;

      GeneralOrder timedOrder =
          generalOrderFactory.create(
              context.getTime(), fill, fill.getMarket(), bdVolume.negate(), fillType);
      String comment =
          (bdVolume.compareTo(BigDecimal.ZERO) > 0) ? "Long Timed Order" : "Short Timed Order";
      //   if (stopOrder.getFillType().equals(FillType.TRAILING_UNREALISED_STOP_LIMIT))
      timedOrder.withLastBestPrice(fill.getPrice().asBigDecimal());
      timedOrder
          .withComment(comment)
          .withTriggerTime(triggerTime)
          .withPositionEffect(PositionEffect.CLOSE)
          .withExecutionInstruction(ExecutionInstruction.TAKER)
          .withOrderGroup(fill.getOrder().getOrderGroup());
      timedOrder.copyCommonFillProperties(fill);

      Set<Order> parentFillOrders = new HashSet<Order>();
      fill.getAllSpecificOrdersByParentFill(fill, parentFillOrders);
      for (Order childOrder : parentFillOrders) {
        if (childOrder instanceof SpecificOrder) {
          timedOrder.addChildOrder(childOrder);
          childOrder.setParentOrder(timedOrder);
        }
      }
      return timedOrder;
    }
    return null;
  }

  private GeneralOrder buildStopLimitOrder(Fill fill) {

    //      GeneralOrder order = generalOrderFactory.create(fill.getOrder().getPortfolio(), this));

    //        OrderBuilder stoporder = new OrderBuilder(fill.getOrder().getPortfolio(), this);

    //   fill.getAllOrdersByParentFill(allChildren)
    //    if (fill.getOrder().getParentOrder().getComment().contains("Reentry"))
    //      log.debug("test");
    if ((fill.getPositionEffect() != null && fill.getPositionEffect() != PositionEffect.CLOSE)
        && (!fill.getUnfilledVolume().isZero())) {
      Amount stopPrice = null;
      Amount targetPrice = null;

      DiscreteAmount stopPriceDiscrete;
      DiscreteAmount targetPriceDiscrete;

      BigDecimal bdStopPrice;
      BigDecimal bdTargetPrice = null;
      BigDecimal bdLimitPrice;
      GeneralOrder stopOrder;
      BigDecimal bdVolume = fill.getVolume().asBigDecimal();
      ArrayList<Order> parentOrders = new ArrayList<Order>();
      fill.getAllParentOrdersByFill(fill, parentOrders);
      Collections.reverse(parentOrders);
      Order parent = null;
      Amount stopAmount = null;
      Amount targetAmount = null;
      for (Order parentOrder : parentOrders) {
        if (parentOrder.getStopAmount() != null) {
          stopAmount =
              (parentOrder.getStopPercentage() != 0)
                  ? fill.getPrice().times(parentOrder.getStopPercentage(), Remainder.ROUND_EVEN)
                  : parentOrder.getStopAmount();
          stopPrice =
              (fill.getVolume().isPositive())
                  ? fill.getPrice().minus(stopAmount)
                  : fill.getPrice().plus(stopAmount);
          if (parentOrder.getTargetAmount() != null) {
            targetAmount =
                (parentOrder.getTargetPercentage() != 0)
                    ? fill.getPrice().times(parentOrder.getTargetPercentage(), Remainder.ROUND_EVEN)
                    : parentOrder.getTargetAmount();
            targetPrice =
                (fill.getVolume().isPositive())
                    ? fill.getPrice().plus(targetAmount)
                    : fill.getPrice().minus(targetAmount);
          }
          parent = parentOrder;
          break;
        } else if (parentOrder.getStopPrice() != null) {
          stopPrice = parentOrder.getStopPrice();
          targetPrice = parentOrder.getTargetPrice();
          parent = parentOrder;
          break;
        }
      }

      if (stopPrice == null) return null;
      stopPriceDiscrete =
          new DiscreteAmount(
              DiscreteAmount.roundedCountForBasis(
                  stopPrice.asBigDecimal(), fill.getMarket().getPriceBasis()),
              fill.getMarket().getPriceBasis());
      if (targetPrice != null) {
        targetPriceDiscrete =
            new DiscreteAmount(
                DiscreteAmount.roundedCountForBasis(
                    targetPrice.asBigDecimal(), fill.getMarket().getPriceBasis()),
                fill.getMarket().getPriceBasis());
        bdTargetPrice = targetPriceDiscrete.asBigDecimal();
      }
      bdStopPrice = stopPriceDiscrete.asBigDecimal();
      // String comment = (fill.isLong()? "")

      bdLimitPrice =
          (fill.getVolume().isNegative())
              ? stopPriceDiscrete.increment(2).asBigDecimal()
              : stopPriceDiscrete.decrement(2).asBigDecimal();

      FillType fillType = FillType.STOP_LIMIT;
      if (parent.getFillType() != null)
        switch (parent.getFillType()) {
          case TRAILING_STOP_LOSS:
            fillType = FillType.TRAILING_STOP_LIMIT;
            break;
          case REENTRANT_STOP_LOSS:
          case REENTRANT_STOP_LIMIT:
            fillType = FillType.REENTRANT_STOP_LIMIT;
            break;
          case REENTRANT_TRAILING_STOP_LOSS:
          case REENTRANT_TRAILING_STOP_LIMIT:
            fillType = FillType.REENTRANT_TRAILING_STOP_LIMIT;
            break;
          case TRAILING_UNREALISED_STOP_LOSS:
            fillType = FillType.TRAILING_UNREALISED_STOP_LIMIT;
            break;
          default:
            fillType = FillType.STOP_LIMIT;
            break;
        }

      stopOrder =
          generalOrderFactory.create(
              context.getTime(), fill, fill.getMarket(), bdVolume.negate(), fillType);
      String comment =
          (bdVolume.compareTo(BigDecimal.ZERO) > 0) ? "Long Stop Order" : "Short Stop Order";
      //   if (stopOrder.getFillType().equals(FillType.TRAILING_UNREALISED_STOP_LIMIT))
      stopOrder.withLastBestPrice(fill.getPrice().asBigDecimal());
      stopOrder
          .withComment(comment)
          .withStopPrice(bdStopPrice)
          .withLimitPrice(bdLimitPrice)
          .withPositionEffect(PositionEffect.CLOSE)
          .withExecutionInstruction(ExecutionInstruction.TAKER)
          .withStopPercentage(parent.getStopPercentage())
          .withTriggerInterval(parent.getTriggerInterval())
          .withUsePosition(true)
          .withOrderGroup(fill.getOrder().getOrderGroup());
      if (bdTargetPrice != null && bdTargetPrice.compareTo(BigDecimal.ZERO) != 0)
        stopOrder.withTargetPrice(bdTargetPrice).withTargetPercentage(parent.getTargetPercentage());

      if (stopAmount != null) stopOrder.withStopAmount(stopAmount.asBigDecimal());
      if (targetAmount != null) stopOrder.withTargetAmount(targetAmount.asBigDecimal());
      //       if (fillType.equals(FillType.REENTRANT_STOP_LIMIT) ||
      // fillType.equals(FillType.REENTRANT_TRAILING_STOP_LIMIT))
      //         stopOrder.withTargetPrice(fill.getPrice().asBigDecimal());

      stopOrder.copyCommonFillProperties(fill);
      fill.setStopPriceCount(stopPriceDiscrete.getCount());

      Set<Order> parentFillOrders = new HashSet<Order>();
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

  private SpecificOrder buildPercentageOfVolumeOrder(Fill fill) {

    BigDecimal bdLimitPrice;
    Tradeable market = null;
    Amount outstandingFills = DecimalAmount.ZERO;

    ArrayList<Order> parentOrders = new ArrayList<Order>();
    fill.getAllParentOrdersByFill(fill, parentOrders);
    Collections.reverse(parentOrders);
    Order parent = null;
    Amount limitPrice = null;
    for (Order parentOrder : parentOrders) {
      if (parentOrder.getTargetStrategy() != null
          && parentOrder.getTargetStrategy().equals(TargetStrategy.PERCENTAGEOFVOLUME)
          && parentOrder.getPercentageOfVolume() != null
          && parentOrder.getPercentageOfVolumeInterval() != null
          && parentOrder instanceof GeneralOrder) {
        parent = parentOrder;
        limitPrice = parentOrder.getLimitPrice();
        market = parentOrder.getMarket();
        break;
      }
    }

    if (parent == null || limitPrice == null) {
      log.warn(
          "BasedOrderSerivce - buildPercentageOfVolumeOrder: Unable to create POV order "
              + " from "
              + parent
              + " limitPrice="
              + limitPrice);
      return null;
    }
    Set<Order> allChildOrders = new HashSet<Order>();
    parent.getAllSpecificOrderByParentOrder(parent, allChildOrders);
    Amount filledVolume = Order.getFilledVolume(allChildOrders, (Market) parent.getMarket());

    Set<Order> allWorkingLinkedChildOrders = new HashSet<Order>();

    for (Order childOrder : allChildOrders) {
      if (childOrder instanceof SpecificOrder && getOrderState(childOrder).isWorking()) {
        allWorkingLinkedChildOrders.add(childOrder);
      }
    }

    Amount workngVolume = parent.getWorkingVolume(allWorkingLinkedChildOrders, (Market) market);
    Amount totalVolume = filledVolume.plus(workngVolume);

    DiscreteAmount outstandingVolume =
        (parent.getVolume().minus(workngVolume).minus(filledVolume))
            .toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN);
    if (outstandingVolume.isZero()) return null;

    Bar POVBar = quotes.getLastBar(market, parent.getPercentageOfVolumeInterval());
    DiscreteAmount percentageOfVolume = getPercentageOfVolume(parent, (Market) market, POVBar);
    DiscreteAmount orderVolume;
    if (parent.isBid()) {
      if (outstandingVolume.compareTo(percentageOfVolume) > 0) orderVolume = percentageOfVolume;
      else orderVolume = outstandingVolume;
    } else {
      if (outstandingVolume.compareTo(percentageOfVolume) < 0) orderVolume = percentageOfVolume;
      else orderVolume = outstandingVolume;
    }
    if (orderVolume == null || orderVolume.isZero()) {
      log.warn(
          "BasedOrderSerivce - buildPercentageOfVolumeOrder: Unable to create POV order "
              + " from "
              + parent
              + " filledVolume="
              + filledVolume
              + ",workngVolume="
              + workngVolume
              + ",outstandingVolume="
              + outstandingVolume
              + ",percentageOfVolume="
              + percentageOfVolume);
      return null;
    }

    DiscreteAmount priceDiscrete =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(limitPrice.asBigDecimal(), market.getPriceBasis()),
            market.getPriceBasis());

    bdLimitPrice = priceDiscrete.asBigDecimal();
    // String comment = (fill.isLong()? "")

    //  int payUpTicks = linkedOrder.getPayUpTicks() == 0 ? 2 : linkedOrder.getPayUpTicks();

    String comment = "";
    if (parent.getPositionEffect() != PositionEffect.CLOSE) {
      comment = (outstandingVolume.isPositive()) ? "Long Entry POV Order" : "Short Entry POV Order";
    } else {
      comment = (outstandingVolume.isPositive()) ? "Short Exit POV Order" : "Long Exit POV Order";
    }

    SpecificOrder povOrder =
        specificOrderFactory.create(
            context.getTime(),
            parent.getPortfolio(),
            (Market) market,
            orderVolume,
            parent,
            comment);
    parent.persit();
    povOrder.persit();

    // Market markettest = specificOrder.getMarket();
    povOrder.withOrderGroup(fill.getOrder().getOrderGroup());
    povOrder.withUsePosition(fill.getOrder().getUsePosition());
    povOrder.withPositionEffect(fill.getOrder().getPositionEffect());
    povOrder.withExecutionInstruction(fill.getOrder().getExecutionInstruction());
    povOrder.withTimeToLive(fill.getOrder().getTimeToLive());
    povOrder.withMinimumPricePercentage(fill.getOrder().getMinimumPricePercentage());

    povOrder.withPayUpTicks(fill.getOrder().getPayUpTicks());
    povOrder.withLegNumber(fill.getOrder().getLegNumber());
    if (fill.getOrder().getDifferentialPrice() != null)
      povOrder.withDifferentialPrice(fill.getOrder().getDifferentialPrice());

    povOrder.withRatioQuantity(fill.getOrder().getRatioQuantity());
    povOrder.withPercentageOfVolume(fill.getOrder().getPercentageOfVolume());
    povOrder.withPercentageOfVolumeInterval(fill.getOrder().getPercentageOfVolumeInterval());
    povOrder.withTargetStrategy(fill.getOrder().getTargetStrategy());
    povOrder.withLimitPrice(bdLimitPrice).withFillType(fill.getOrder().getFillType());

    log.debug(
        "BasedOrderSerivce - buildPercentageOfVolumeOrder: Creating POV order "
            + povOrder
            + " from "
            + parent
            + " filledVolume="
            + filledVolume
            + ",workngVolume="
            + workngVolume
            + ",outstandingVolume="
            + outstandingVolume);

    return povOrder;
  }

  private SpecificOrder buildPairOrder(Fill fill) {

    Amount pairPrice = null;

    DiscreteAmount pairPriceDiscrete;

    BigDecimal bdPairPrice;
    BigDecimal bdLimitPrice;
    SpecificOrder pairOrder;
    Order linkedOrder = null;
    Market pairMarket = null;
    Amount outstandingFills = DecimalAmount.ZERO;

    ArrayList<Order> parentOrders = new ArrayList<Order>();
    fill.getAllParentOrdersByFill(fill, parentOrders);
    Collections.reverse(parentOrders);
    Order parent = null;
    Amount priceDifferntial = null;
    Integer legNumber = null;
    double ratioQuantity = 0;
    for (Order parentOrder : parentOrders) {
      if (parentOrder.getDifferentialPrice() != null && parentOrder.getLinkedOrder() != null) {
        parent = parentOrder;
        priceDifferntial = parentOrder.getDifferentialPrice();
        pairPrice =
            (fill.getOrder().getLegNumber() == 1)
                ? fill.getPrice().minus(priceDifferntial)
                : fill.getPrice().plus(priceDifferntial);
        linkedOrder = parentOrder.getLinkedOrder();
        pairMarket = (Market) linkedOrder.getMarket();
        legNumber = parentOrder.getLegNumber();
        ratioQuantity = parentOrder.getRatioQuantity();
        break;
      }
    }

    if (parent == null
        || linkedOrder == null
        || pairPrice == null
        || ratioQuantity == 0
        || (pairPrice != null && legNumber == null)) {

      log.debug(
          "BasedOrderSerivce - buildPairOrder: Unable to build pair order for fill={} ,parent={},linkedOrder={},pairPrice={},ratioQuantity={},pairPrice={},legNumber={} ",
          fill,
          parent,
          linkedOrder,
          pairPrice,
          ratioQuantity,
          pairPrice,
          legNumber);

      return null;
    }

    if (legNumber == 1) {
      outstandingFills =
          Order.getFilledVolume(parent.getOrderChildren(), (Market) parent.getMarket());

      Set<Order> allChildOrders = new HashSet<Order>();
      parent.getAllSpecificOrderByParentOrder(parent, allChildOrders);

      Amount filledVolume = Order.getFilledVolume(allChildOrders, (Market) parent.getMarket());

      Set<Order> allLinkedChildOrders = new HashSet<Order>();
      linkedOrder.getAllSpecificOrderByParentOrder(linkedOrder, allLinkedChildOrders);

      Set<Order> allWorkingLinkedChildOrders = new HashSet<Order>();

      for (Order linkedChild : allLinkedChildOrders) {
        if (linkedChild instanceof SpecificOrder && getOrderState(linkedChild).isWorking()) {
          allWorkingLinkedChildOrders.add(linkedChild);
        }
      }

      Amount workngLinkedVolume =
          linkedOrder.getWorkingVolume(
              allWorkingLinkedChildOrders, (Market) linkedOrder.getMarket());
      Amount filledLinkedVolume =
          linkedOrder.getFilledVolume(allLinkedChildOrders, (Market) linkedOrder.getMarket());

      RemainderHandler roundingMode =
          filledVolume.isNegative() ? Remainder.ROUND_FLOOR : Remainder.ROUND_CEILING;

      DiscreteAmount outstandingVolume =
          ((filledVolume.times(BigDecimal.valueOf(ratioQuantity), roundingMode))
                  .minus(workngLinkedVolume)
                  .minus(filledLinkedVolume))
              .toBasis(pairMarket.getVolumeBasis(), roundingMode);

      if (parent.getUnfilledVolume().isZero()) {
        if (linkedOrder.getVolume().isNegative()) {
          if ((linkedOrder.getUnfilledVolume().minus(workngLinkedVolume))
                  .compareTo(outstandingVolume)
              < 0) {
            // -85< --84
            log.debug(
                "BasedOrderSerivce - buildPairOrder: adjusing outstandingVolume to "
                    + linkedOrder.getUnfilledVolume()
                    + " from "
                    + outstandingVolume);

            outstandingVolume =
                linkedOrder
                    .getUnfilledVolume()
                    .minus(workngLinkedVolume)
                    .toBasis(linkedOrder.getMarket().getVolumeBasis(), roundingMode);
            ;
          }
        }
        if (linkedOrder.getVolume().isPositive()) {
          if ((linkedOrder.getUnfilledVolume().minus(workngLinkedVolume))
                  .compareTo(outstandingVolume)
              > 0) {
            // 85 > 84
            log.debug(
                "BasedOrderSerivce - buildPairOrder: adjusing outstandingVolume to "
                    + linkedOrder.getUnfilledVolume()
                    + " from "
                    + outstandingVolume);
            outstandingVolume =
                linkedOrder
                    .getUnfilledVolume()
                    .minus(workngLinkedVolume)
                    .toBasis(linkedOrder.getMarket().getVolumeBasis(), roundingMode);
            ;
          }
        }
      }

      // TODO do we need to create a fill if there is a lot of outstanding volume when the order if
      // fully filled.
      if (outstandingVolume.isZero()) {
        log.debug(
            "BasedOrderSerivce - buildPairOrder: Unable to build pair order for fill="
                + fill
                + ",outstandingVolume="
                + outstandingVolume
                + ",filledVolume="
                + filledVolume
                + ",workngLinkedVolume="
                + workngLinkedVolume
                + ",filledLinkedVolume="
                + filledLinkedVolume
                + ",outstandingFills="
                + outstandingFills);

        return null;
      }

      if (outstandingVolume.times(linkedOrder.getVolume(), roundingMode).isNegative()) {
        log.debug("over filled");
        filledVolume = Order.getFilledVolume(allChildOrders, (Market) parent.getMarket());

        workngLinkedVolume =
            linkedOrder.getWorkingVolume(allLinkedChildOrders, (Market) linkedOrder.getMarket());
        filledLinkedVolume =
            linkedOrder.getFilledVolume(allLinkedChildOrders, (Market) linkedOrder.getMarket());
      }

      if (fill.getOrder().getPositionEffect().equals(PositionEffect.CLOSE)) {

        Asset tradedCCY = pairMarket.getListing().getBase();

        Position netPosition =
            outstandingVolume.isPositive()
                ? fill.getPortfolio()
                    .getShortPosition(tradedCCY, pairMarket, linkedOrder.getOrderGroup())
                : fill.getPortfolio()
                    .getLongPosition(tradedCCY, pairMarket, linkedOrder.getOrderGroup());

        if ((outstandingVolume.isPositive() && !netPosition.getOpenVolume().isNegative())
            || (outstandingVolume.isNegative() && !netPosition.getOpenVolume().isPositive())) {

          log.debug(
              "BasedOrderSerivce - buildPairOrder: Unable to build pair order as current position is too small for fill="
                  + fill
                  + ",outstandingVolume="
                  + outstandingVolume
                  + ",filledVolume="
                  + filledVolume
                  + ",workngLinkedVolume="
                  + workngLinkedVolume
                  + ",filledLinkedVolume="
                  + filledLinkedVolume
                  + ",outstandingFills="
                  + outstandingFills
                  + ",netPosition="
                  + netPosition);

          return null;
        }
      }

      /*      if (parent.getUnfilledVolume().isZero()) {
       //parent order if fully filled
       DiscreteAmount targetVolume =


             outstandingVolume =
               ((filledVolume.times(BigDecimal.valueOf(ratioQuantity), roundingMode))
                       .minus(workngLinkedVolume)
                       .minus(filledLinkedVolume))
                   .toBasis(pairMarket.getVolumeBasis(), roundingMode);


      }*/

      pairPriceDiscrete =
          new DiscreteAmount(
              DiscreteAmount.roundedCountForBasis(
                  pairPrice.asBigDecimal(), pairMarket.getPriceBasis()),
              pairMarket.getPriceBasis());

      bdPairPrice = pairPriceDiscrete.asBigDecimal();
      // String comment = (fill.isLong()? "")

      //  int payUpTicks = linkedOrder.getPayUpTicks() == 0 ? 2 : linkedOrder.getPayUpTicks();

      bdLimitPrice =
          (fill.getVolume().isNegative())
              ? pairPriceDiscrete.increment(linkedOrder.getPayUpTicks()).asBigDecimal()
              : pairPriceDiscrete.decrement(linkedOrder.getPayUpTicks()).asBigDecimal();
      // this is too simple

      String comment = "";
      if (linkedOrder.getPositionEffect() != PositionEffect.CLOSE) {
        comment =
            (outstandingVolume.isPositive())
                ? "Long Entry Passive Order"
                : "Short Entry Passive Order";
      } else {
        comment =
            (outstandingVolume.isPositive())
                ? "Short Exit Passive Order"
                : "Long Exit Passive Order";
      }

      pairOrder =
          specificOrderFactory.create(
              context.getTime(),
              linkedOrder.getPortfolio(),
              (Market) pairMarket,
              outstandingVolume,
              linkedOrder,
              comment);
      linkedOrder.persit();
      pairOrder.persit();
      //   if (stopOrder.getFillType().equals(FillType.TRAILING_UNREALISED_STOP_LIMIT))
      pairOrder
          .withLimitPrice(bdLimitPrice)
          .withPositionEffect(fill.getOrder().getPositionEffect())
          .withFillType(fill.getOrder().getFillType())
          .withExecutionInstruction(ExecutionInstruction.MAKERTOTAKER)
          .withUsePosition(true)
          .withOrderGroup(linkedOrder.getOrderGroup())
          .withTimeToLive(linkedOrder.getTimeToLive())
          .withParentFill(fill);

      DiscreteAmount bestLimitPrice = getBestLimitPriceForOrder(pairOrder);
      if (pairOrder.isBid() && (pairOrder.getLimitPrice().compareTo(bestLimitPrice) < 0)) {
        log.debug(
            "BasedOrderSerivce - buildPairOrder: adjusing buy order limit price to "
                + bestLimitPrice
                + " from limit price"
                + pairOrder.getLimitPrice());
        pairOrder.withLimitPrice(bestLimitPrice);

      } else if (pairOrder.isAsk() && (pairOrder.getLimitPrice().compareTo(bestLimitPrice) > 0)) {
        log.debug(
            "BasedOrderSerivce - buildPairOrder: adjusing sell order limit price to "
                + bestLimitPrice
                + " from limit price"
                + pairOrder.getLimitPrice());

        pairOrder.withLimitPrice(bestLimitPrice);
      }

      log.debug(
          "BasedOrderSerivce - buildPairOrder: Creating pair order "
              + pairOrder
              + " from "
              + parent
              + " filledVolume="
              + filledVolume
              + ",workngLinkedVolume="
              + workngLinkedVolume
              + ",filledLinkedVolume="
              + filledLinkedVolume
              + ",outstandingVolume="
              + outstandingVolume
              + ",outstandingFills="
              + outstandingFills
              + ",linkedUnfilledVolume="
              + linkedOrder.getUnfilledVolume()
              + ",linkedVolume="
              + linkedOrder.getVolume());

      Set<Order> parentFillOrders = new HashSet<Order>();
      fill.getAllSpecificOrdersByParentFill(fill, parentFillOrders);
      for (Order childOrder : parentFillOrders) {
        if (childOrder instanceof SpecificOrder) {
          pairOrder.addChildOrder(childOrder);
          childOrder.setParentOrder(pairOrder);
        }
      }

      return pairOrder;
    } else if (legNumber == 2) {
      // so we need to check if the trigger order of type MULT_LEG if fully filled and if it is
      // removed it as a trigger order
      HashSet<Order> triggerOrdersToCancel = new HashSet<Order>();
      if (parent.getFillType().equals(FillType.MULTI_LEG)) {
        // linkedworkingorders

        Set<Order> allLinkedChildOrders = new HashSet<Order>();
        linkedOrder.getAllSpecificOrderByParentOrder(linkedOrder, allLinkedChildOrders);

        Set<Order> allWorkingLinkedChildOrders = new HashSet<Order>();

        for (Order linkedChild : allLinkedChildOrders) {
          if (linkedChild instanceof SpecificOrder && getOrderState(linkedChild).isWorking()) {
            allWorkingLinkedChildOrders.add(linkedChild);
          }
        }
        // this working orders

        Set<Order> allChildOrders = new HashSet<Order>();
        parent.getAllSpecificOrderByParentOrder(parent, allChildOrders);

        Set<Order> allWorkingChildOrders = new HashSet<Order>();

        for (Order child : allChildOrders) {
          if (child instanceof SpecificOrder && getOrderState(child).isWorking()) {
            allWorkingChildOrders.add(child);
          }
        }

        if (allWorkingChildOrders.isEmpty() && allWorkingLinkedChildOrders.isEmpty()) {

          log.debug(
              "BaseOrderService: buildPairOrder - Removing multi-leg trigger orders"
                  + parent
                  + " as  allWorkingChildOrders="
                  + allWorkingChildOrders
                  + ",allWorkingLinkedChildOrders="
                  + allWorkingLinkedChildOrders);
          triggerOrdersToCancel.add(parent);
          if (parent.getParentFill() != null)
            handleCancelAllTriggerOrdersByParentFill(parent.getParentFill());
        }
      }
      if (!triggerOrdersToCancel.isEmpty()) {
        removeTriggerOrders(parent.getMarket(), triggerOrdersToCancel);
      }
      return null;
    }
    return null;
  }

  protected boolean handleGeneralOrder(GeneralOrder generalOrder) {
    Market market;
    SpecificOrder specificOrder = null;
    // generalOrder.persit();
    boolean returnStatus = false;
    if (generalOrder.getMarket() == null) {
      Offer offer =
          generalOrder.isBid()
              ? quotes.getBestBidForListing(generalOrder.getListing())
              : quotes.getBestAskForListing(generalOrder.getListing());
      if (offer == null)
        offer =
            generalOrder.isBid()
                ? quotes.getImpliedBestBidForListing(generalOrder.getListing())
                : quotes.getImpliedBestAskForListing(generalOrder.getListing());

      if (offer == null) {

        log.warn("No offers on the book for " + generalOrder.getListing());
        reject(
            generalOrder,
            "No recent book data for "
                + generalOrder.getListing()
                + " so GeneralOrder routing is disabled");
        return returnStatus;
      }
      generalOrder.setMarket((Market) offer.getMarket());
    }
    try {
      Market triggerMarket = null;
      switch (generalOrder.getFillType()) {
        case GOOD_TIL_CANCELLED:
          throw new NotImplementedException();
        case GTC_OR_MARGIN_CAP:
          throw new NotImplementedException();
        case CANCEL_REMAINDER:
          throw new NotImplementedException();

        case LIMIT:
          if (triggerMarket == null && (generalOrder.getMarket() instanceof Market))
            triggerMarket = (Market) generalOrder.getMarket();
          if (triggerMarket == null
              || (generalOrder.getLegNumber() != 1 && generalOrder.getLinkedOrder() != null)) break;
          specificOrder = convertGeneralOrderToSpecific(generalOrder, triggerMarket);
          if (specificOrder == null) break;
          specificOrder.withParentOrder(generalOrder);
          specificOrder.withParentFill(generalOrder.getParentFill());

          //  specificOrder.withParentFill(generalOrder.getParentFill());
          specificOrder.persit();
          updateOrderState(specificOrder, OrderState.NEW, true);
          updateOrderState(generalOrder, OrderState.ROUTED, true);
          log.info("Routing Limit order " + generalOrder + " to " + generalOrder.getMarket());
          returnStatus = placeOrder(specificOrder);

          break;
        case MARKET:
          // TODO If market order, we set the limit price to the price in the order book that will
          // absorb the quantity, then resubmit the order above best ask and below best bid every
          // two polling cycles
          // get the total open quanity of all buy or sell orders for this market and add it up
          // then set the limit price to the vurren value in the order book.
          if (triggerMarket == null && (generalOrder.getMarket() instanceof Market))
            triggerMarket = (Market) generalOrder.getMarket();
          if (triggerMarket == null) break;
          specificOrder = convertGeneralOrderToSpecific(generalOrder, triggerMarket);
          if (specificOrder == null) break;

          Collection<SpecificOrder> pendingOrders =
              (specificOrder.isBid()) ? getPendingLongOrders() : getPendingShortOrders();
          Amount workingVolume = specificOrder.getVolume();
          for (SpecificOrder workingOrder : pendingOrders)
            workingVolume = workingVolume.plus(workingOrder.getVolume());
          // if I am buying, then I can buy at current best ask and sell at current best bid
          Book lastBook = quotes.getLastBook(specificOrder.getMarket());
          log.debug(
              "BasedOrderSerivce - handleGeneralOrder: Setting limit prices for market "
                  + specificOrder.getMarket()
                  + " using lastBook"
                  + lastBook);

          Offer bestOffer =
              (specificOrder.isBid())
                  ? (lastBook == null
                      ? quotes.getImpliedBestAskForListing(specificOrder.getMarket().getListing())
                      : lastBook.getBestAskByVolume(
                          new DiscreteAmount(
                              DiscreteAmount.roundedCountForBasis(
                                  workingVolume.asBigDecimal(),
                                  specificOrder.getMarket().getVolumeBasis()),
                              specificOrder.getMarket().getVolumeBasis())))
                  : (lastBook == null
                      ? quotes.getImpliedBestBidForListing(specificOrder.getMarket().getListing())
                      : lastBook.getBestBidByVolume(
                          new DiscreteAmount(
                              DiscreteAmount.roundedCountForBasis(
                                  workingVolume.asBigDecimal(),
                                  specificOrder.getMarket().getVolumeBasis()),
                              specificOrder.getMarket().getVolumeBasis())));

          // this is short exit, so I am buy, so hitting the ask
          // loop down asks until the total quanity of the order is reached.
          DiscreteAmount limitPrice =
              (bestOffer.getPrice() == null)
                  ? (specificOrder.isBid()) ? lastBook.getAskPrice() : lastBook.getBidPrice()
                  : bestOffer.getPrice();
          limitPrice =
              new DiscreteAmount(
                  DiscreteAmount.roundedCountForBasis(
                      limitPrice.asBigDecimal(), specificOrder.getMarket().getPriceBasis()),
                  specificOrder.getMarket().getPriceBasis());

          specificOrder.withLimitPrice(limitPrice);
          specificOrder.withFillType(FillType.MARKET);
          specificOrder.withParentFill(generalOrder.getParentFill());

          specificOrder.withParentOrder(generalOrder);
          specificOrder.persit();
          updateOrderState(specificOrder, OrderState.NEW, true);
          updateOrderState(generalOrder, OrderState.ROUTED, true);

          log.info(
              "Routing market order " + generalOrder + " to " + triggerMarket != null
                  ? triggerMarket.getExchange().getSymbol()
                  : "null");
          returnStatus = placeOrder(specificOrder);
          break;
        case STOP_LIMIT:
        case REENTRANT_STOP_LIMIT:
        case TIMED:
        case MULTI_LEG:
          if (generalOrder.getParentFill() != null) {
            if (generalOrder.getParentFill().getMarket() instanceof Market)
              triggerMarket = (Market) generalOrder.getParentFill().getMarket();
          }

          if (triggerMarket == null && (generalOrder.getMarket() instanceof Market))
            triggerMarket = (Market) generalOrder.getMarket();
          if (triggerMarket == null) break;
          addTriggerOrder(generalOrder, triggerMarket);
          updateOrderState(generalOrder, OrderState.TRIGGER, true);
          if (generalOrder.getStopPrice() != null)
            log.info(generalOrder + " Stop trade Entered at " + generalOrder.getStopPrice());
          if (generalOrder.getTargetPrice() != null)
            log.info("Target trade Entered at " + generalOrder.getTargetPrice());
          if (generalOrder.getTriggerTime() != null)
            log.info("Timed trade Entered at " + generalOrder.getTriggerTime());
          if (generalOrder.getLinkedOrder() != null)
            log.info("Multi-leg trade Entered at " + generalOrder.getTriggerTime());
          returnStatus = true;
          break;
        case TRAILING_STOP_LIMIT:
        case REENTRANT_TRAILING_STOP_LIMIT:
        case TRAILING_UNREALISED_STOP_LIMIT:
          if (generalOrder.getParentFill() != null) {
            if (generalOrder.getParentFill().getMarket() instanceof Market)
              triggerMarket = (Market) generalOrder.getParentFill().getMarket();
          }

          if (triggerMarket == null && (generalOrder.getMarket() instanceof Market))
            triggerMarket = (Market) generalOrder.getMarket();
          if (triggerMarket == null) break;
          addTriggerOrder(generalOrder, triggerMarket);
          updateOrderState(generalOrder, OrderState.TRIGGER, true);
          log.info("Trailing Stop trade Entered at " + generalOrder.getStopPrice());
          returnStatus = true;
          break;
        case TRAILING_STOP_LOSS:
        case TRAILING_UNREALISED_STOP_LOSS:
        case REENTRANT_TRAILING_STOP_LOSS:
        case STOP_LOSS:
        case REENTRANT_STOP_LOSS:
        case TIMED_EXIT:
          if (generalOrder.getTargetPrice() != null) {
            // so we are adding an orde that might or might not have a stop price/ammount.
            if (generalOrder.getParentFill() != null) {
              if (generalOrder.getParentFill().getMarket() instanceof Market)
                triggerMarket = (Market) generalOrder.getParentFill().getMarket();
            }

            if (triggerMarket == null && (generalOrder.getMarket() instanceof Market))
              triggerMarket = (Market) generalOrder.getMarket();
            if (triggerMarket == null) break;
            addTriggerOrder(generalOrder, triggerMarket);
            updateOrderState(generalOrder, OrderState.TRIGGER, true);
            log.info("Trigger Order Entered at " + generalOrder.getTargetPrice());
            returnStatus = true;
            break;
          } else {
            if (triggerMarket == null && (generalOrder.getMarket() instanceof Market))
              triggerMarket = (Market) generalOrder.getMarket();
            if (triggerMarket == null) break;
            specificOrder = convertGeneralOrderToSpecific(generalOrder, triggerMarket);
            if (specificOrder == null) break;
            FillType fillType =
                generalOrder.getExecutionInstruction().equals(ExecutionInstruction.TAKER)
                    ? FillType.MARKET
                    : FillType.LIMIT;
            specificOrder
                .withFillType(fillType)
                .withParentOrder(generalOrder)
                .withParentOrder(generalOrder)
                .withParentFill(generalOrder.getParentFill());
            specificOrder.persit();
            updateOrderState(specificOrder, OrderState.NEW, true);
            updateOrderState(generalOrder, OrderState.ROUTED, true);
            log.info(
                "Routing Stop Loss order " + generalOrder + " to " + triggerMarket != null
                    ? triggerMarket.getExchange().getSymbol()
                    : "null");
            if (placeOrder(specificOrder)) {
              generalOrder.setLastBestPriceDecimal(specificOrder.getLimitPrice().asBigDecimal());
            }
            returnStatus = true;
            break;
          }
      }
      switch (generalOrder.getContingencyType()) {
        case ONE_CANCELS_OTHER:
          if (triggerMarket == null && (generalOrder.getMarket() instanceof Market))
            triggerMarket = (Market) generalOrder.getMarket();
          if (triggerMarket == null) break;
          specificOrder = convertGeneralOrderToSpecific(generalOrder, triggerMarket);
          specificOrder.withParentOrder(generalOrder);
          specificOrder.withParentFill(generalOrder.getParentFill());

          if (specificOrder == null) break;
          //  specificOrder.withParentFill(generalOrder.getParentFill());
          specificOrder.persit();
          updateOrderState(specificOrder, OrderState.NEW, true);
          updateOrderState(generalOrder, OrderState.ROUTED, true);
          log.info(
              "Routing OCO order " + generalOrder + " to " + triggerMarket != null
                  ? triggerMarket.getExchange().getSymbol()
                  : "null");
          returnStatus = placeOrder(specificOrder);

          break;
        default:
          break;
      }

      return returnStatus;
    } catch (Throwable e) {
      updateOrderState(generalOrder, OrderState.REJECTED, true);

      log.info("Unable to place general order  " + generalOrder + ". Full stack trace", e);
      return returnStatus;
    }
  }

  //    @SuppressWarnings("ConstantConditions")
  //    @When("@Priority(9) select * from Trade")
  //    private void handleTrade(Trade t) {
  //
  //    }

  // @When("@Priority(6) select * from Book(Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
  //  @When("@Priority(6) select * from LastBookWindow")
  private void handleBook(Book b) {
    log.trace("BasedOrderSerivce: handleBook: Book Recieved:{} ", b);
    updateRestingOrders(b, 0.0);

    //  service.submit(new handleBookRunnable(b));
  }

  // @When("@Priority(8) select * from LastTradeWindow(market.synthetic=false)")
  @SuppressWarnings("ConstantConditions")
  @When("@Priority(1)  @Audit select * from LastTradeWindow(market in (BaseStrategy.getMarkets()))")
  private void handleTrade(Trade t) {
    log.trace("BasedOrderSerivce: handleTrade: Trade Recieved:{} ", t);
    updateRestingOrders(t, 0.0);

    // mockOrderService.submit(new updateBookRunnable(t));
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public Order triggerOrder(Market market, Order triggeredOrder, String comment) {
    ArrayList<Order> triggeredOrders = new ArrayList<Order>();
    triggerOrder(triggeredOrder, comment, triggeredOrders);
    if (!triggeredOrders.isEmpty()) {
      log.debug(
          "BaseOrderService: UpdateRestingOrders - Removing "
              + market
              + " triggered "
              + (triggeredOrder.isBid() ? TransactionType.BUY : TransactionType.SELL)
              + " orders"
              + triggeredOrders
              + " buy trigger order map with fill type "
              + triggeredOrder.getFillType());
      removeTriggerOrders(market, triggeredOrders);
    }
    return (triggeredOrders.contains(triggeredOrder) ? triggeredOrder : null);
  }

  @SuppressWarnings("ConstantConditions")
  protected DiscreteAmount getOpenVolume(Order triggeredOrder) {
    DiscreteAmount unfilledVolumeDiscrete;
    if (triggeredOrder.getParentFill() != null) {
      if (!triggeredOrder.getUsePosition()
          && triggeredOrder.getParentFill().getPositionType().equals(PositionType.EXITING)) {
        log.info(
            "UpdateRestingOrders: unable to trigger order "
                + triggeredOrder.getUuid()
                + " as already eixting parent fill");
        // triggerOrderLock.unlock();
        return null;
      }

      unfilledVolumeDiscrete =
          (triggeredOrder.getUsePosition())
              ? triggeredOrder
                  .getOpenVolume()
                  .toBasis(triggeredOrder.getMarket().getVolumeBasis(), Remainder.DISCARD)
                  .negate()
              : triggeredOrder
                  .getUnfilledVolume()
                  .toBasis(triggeredOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);
    } else {
      unfilledVolumeDiscrete =
          triggeredOrder
              .getVolume()
              .toBasis(triggeredOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);
    }

    return unfilledVolumeDiscrete;
  }

  @SuppressWarnings("ConstantConditions")
  protected Order triggerOrder(
      Order triggeredOrder, String comment, Collection<Order> triggeredOrders) {
    Boolean orderFound = false;
    // convert order to specfic order
    double interval = 0;
    for (Iterator<Double> itd = triggerOrders.get(triggeredOrder.getMarket()).keySet().iterator();
        itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(triggeredOrder.getMarket())
                  .get(triggerInterval)
                  .get(triggeredOrder.getTransactionType())
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();
        if (triggerOrders
            .get(triggeredOrder.getMarket())
            .get(triggerInterval)
            .get(triggeredOrder.getTransactionType())
            .get(fillType)
            .contains(triggeredOrder)) {
          interval = triggerInterval;
          orderFound = true;
          break;
        }
        //		}

        //	}
        // }
      }
    }
    // synchronized ((triggeredOrder.getMarket() != null &&
    // triggerOrders.get(triggeredOrder.getMarket()) != null
    //   && triggerOrders.get(triggeredOrder.getMarket()).get(interval) != null &&
    // triggerOrders.get(triggeredOrder.getMarket()).get(interval)
    // .get(triggeredOrder.getTransactionType()) != null) ?
    // triggerOrders.get(triggeredOrder.getMarket()).get(interval)
    // ./get(triggeredOrder.getTransactionType()) : new Object()) {

    OrderState triggerOrderState =
        orderStateMap.get(triggeredOrder) == null
            ? triggeredOrder.getOrderState()
            : orderStateMap.get(triggeredOrder);
    //	if (!triggerOrderState.isOpen())
    //	return triggeredOrder;
    if (!orderFound
        || (triggerOrderState == null)
        || (triggerOrderState != null
            && !triggeredOrder.getUsePosition()
            && !triggerOrderState.equals(OrderState.TRIGGER))) {
      log.debug(
          this.getClass().getSimpleName()
              + " : triggerOrder - unable to trigger order "
              + triggeredOrder.getUuid()
              + " with state "
              + triggerOrderState
              + " found state: "
              + orderFound
              + " as not triggered state.");
      // TODO what should we do here? as it just loops as the order has been triggered and is part
      // filled!
      return null;
    }

    //  + triggeredOrder.getParentFill().getFillChildOrders());
    //  itTriggeredOrder.remove();
    //   triggerOrders.get(parentKey).remove(triggeredOrder);
    // if (triggerOrders.get(parentKey).isEmpty())
    //   triggeredParents.add(parentKey);

    synchronized (triggeredOrder) {
      Amount totalWorkingVolume = DecimalAmount.ZERO;
      Amount totalTriggeredVolume = DecimalAmount.ZERO;
      DiscreteAmount unfilledVolumeDiscrete = getOpenVolume(triggeredOrder);
      Collection<SpecificOrder> closeOrders = null;
      synchronized (
          triggeredOrder.getParentFill() != null ? triggeredOrder.getParentFill() : new Object()) {
        if (unfilledVolumeDiscrete == null) return null;

        if (triggeredOrder.getUsePosition()) {
          Amount currentPosition =
              (triggeredOrder.getParentFill() != null
                      && triggeredOrder.getParentFill().getPosition() != null)
                  ? triggeredOrder.getParentFill().getPosition().getOpenVolume()
                  : DecimalAmount.ZERO;

          closeOrders =
              triggeredOrder.isBid()
                      && (triggeredOrder.getPositionEffect() != null
                          && triggeredOrder.getPositionEffect().equals(PositionEffect.CLOSE))
                  ? getPendingShortCloseOrders(
                      triggeredOrder.getPortfolio(), (Market) triggeredOrder.getMarket())
                  : triggeredOrder.isAsk()
                          && (triggeredOrder.getPositionEffect() != null
                              && triggeredOrder.getPositionEffect().equals(PositionEffect.CLOSE))
                      ? getPendingLongCloseOrders(
                          triggeredOrder.getPortfolio(), (Market) triggeredOrder.getMarket())
                      : triggeredOrder.isBid()
                          ? getPendingLongOpenOrders(
                              triggeredOrder.getPortfolio(), (Market) triggeredOrder.getMarket())
                          : getPendingShortOpenOrders(
                              triggeredOrder.getPortfolio(), (Market) triggeredOrder.getMarket());
          // so if we are using positons, we should not exit if the current position + working
          // orders + unfilledAmount

          for (Order workingOrder : closeOrders)
            totalWorkingVolume = totalWorkingVolume.plus(workingOrder.getUnfilledVolume());
          for (Order previousTriggeredOrder : triggeredOrders)
            totalTriggeredVolume = totalTriggeredVolume.plus(previousTriggeredOrder.getVolume());
          if (totalTriggeredVolume.compareTo(totalWorkingVolume) != 0) log.debug("test");
          totalWorkingVolume =
              totalWorkingVolume.isNegative()
                  ? (totalTriggeredVolume.compareTo(totalWorkingVolume) > 0
                      ? totalWorkingVolume
                      : totalTriggeredVolume)
                  : (totalTriggeredVolume.compareTo(totalWorkingVolume) > 0
                      ? totalTriggeredVolume
                      : totalWorkingVolume);

          // -100 + 80 +30) + unfilledVolume() +20
          if ((currentPosition.isNegative()
                  && (currentPosition.plus(totalWorkingVolume).plus(unfilledVolumeDiscrete))
                      .isPositive())
              || (currentPosition.isPositive()
                  && (currentPosition.plus(totalWorkingVolume).plus(unfilledVolumeDiscrete))
                      .isNegative())) {
            log.info(
                this.getClass().getSimpleName()
                    + " : triggerOrder - unable to trigger order "
                    + triggeredOrder.getUuid()
                    + "as working volume "
                    + totalWorkingVolume
                    + " previoiusly triggered orders "
                    + totalTriggeredVolume
                    + " unfilledVolumeDiscrete "
                    + unfilledVolumeDiscrete
                    + " is greater than open volume "
                    + currentPosition
                    + " with working order "
                    + closeOrders);
            // triggerOrderLock.unlock();
            return null;
          }
        }
      }
      // : triggeredOrder.getParentFill().getOpenVolume()
      // .negate().toBasis(triggeredOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);
      // Something not right here.
      // 281650 2018-02-18 09:53:54 [pool-16-thread-10] INFO  org.cryptocoinpartners.orderService -
      // MockOrderService : triggerOrder  - working volume 0 unfilledVolumeDiscrete 3 with open
      // order volume -3 position volume -98 and unfilled volum       e -3 for f
      // ac3e7e8-7980-4e0a-af85-4fe1d7599a20 with child orders -634897372
      log.info(
          this.getClass().getSimpleName()
              + " : triggerOrder  - working volume "
              + totalWorkingVolume
              + " previoiusly triggered orders "
              + totalTriggeredVolume
              + " unfilledVolumeDiscrete "
              + unfilledVolumeDiscrete
              + " with open order volume "
              + (triggeredOrder.getParentFill() == null
                  ? triggeredOrder.getVolume()
                  : triggeredOrder.getParentFill().getOpenVolume())
              + " position volume "
              + (triggeredOrder.getParentFill().getPosition() != null
                  ? triggeredOrder.getParentFill().getPosition().getOpenVolume()
                  : "0")
              + " and unfilled volume "
              + triggeredOrder.getUnfilledVolume()
              + " for "
              + triggeredOrder.getUuid()
              + " with child orders "
              + triggeredOrder.getOrderChildren().hashCode()
              + " and working order "
              + closeOrders);
      for (Order childOrder : triggeredOrder.getOrderChildren()) {
        if (orderStateMap.get(childOrder) != null && orderStateMap.get(childOrder).isOpen())
          if (!handleCancelOrder(childOrder)) {
            log.info(
                this.getClass().getSimpleName()
                    + " : triggerOrder - unable to cancell all child order "
                    + childOrder.getUuid()
                    + " from orders: "
                    + triggeredOrder.getOrderChildren()
                    + "for order:"
                    + triggeredOrder.getUuid());
            // triggerOrderLock.unlock();
            return null;
          }
      }
      if (unfilledVolumeDiscrete != null && !unfilledVolumeDiscrete.isZero()) {

        SpecificOrder specificOrder =
            convertGeneralOrderToSpecific(
                (GeneralOrder) triggeredOrder, (Market) triggeredOrder.getMarket());

        if (specificOrder == null) {
          log.info(
              this.getClass().getSimpleName()
                  + " : triggerOrder -"
                  + triggeredOrder.getUuid()
                  + " not convereted to specific order");
          //  triggerOrders.remove(triggeredOrder);
          //	triggeredOrders.add(triggeredOrder);
          triggeredOrders.add(triggeredOrder);

          updateOrderState(triggeredOrder, OrderState.ERROR, true);

          return triggeredOrder;
        }
        //   specificOrder.persit();
        //   DiscreteAmount volume =
        // (triggeredOrder.getUnfilledVolume().compareTo(specificOrder.getVolume()) < 0) ? :
        // specificOrder.getVolume()
        //         .toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD);

        // DiscreteAmount volume = (generalOrder.getParentFill() == null) ?
        // generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD)
        //       :
        // generalOrder.getParentFill().getOpenVolume().negate().toBasis(market.getVolumeBasis(),
        // Remainder.DISCARD);
        //
        if (!specificOrder
            .getVolume()
            .equals(
                triggeredOrder
                    .getUnfilledVolume()
                    .toBasis(specificOrder.getMarket().getVolumeBasis(), Remainder.DISCARD)))
          log.debug(
              this.getClass().getSimpleName()
                  + " : triggerOrder - Unfilled Volume: "
                  + triggeredOrder.getUuid()
                  + " not the same as trigger volume:"
                  + specificOrder.getUuid());

        specificOrder.setVolumeCount(unfilledVolumeDiscrete.getCount());
        specificOrder.setUnfilledVolumeCount(unfilledVolumeDiscrete.getCount());
        // GeneralOrder triggeredOrderGeneralOrder;
        if (triggeredOrder instanceof GeneralOrder) {
          GeneralOrder triggeredOrderGeneralOrder = (GeneralOrder) triggeredOrder;
          triggeredOrderGeneralOrder.setVolumeDecimal(unfilledVolumeDiscrete.asBigDecimal());
        }
        if (triggeredOrder instanceof SpecificOrder) {
          SpecificOrder triggeredOrderSpecifcOrder = (SpecificOrder) triggeredOrder;
          triggeredOrderSpecifcOrder.setVolumeCount(unfilledVolumeDiscrete.getCount());
          triggeredOrderSpecifcOrder.setUnfilledVolumeCount(unfilledVolumeDiscrete.getCount());
        }

        if (specificOrder.getExecutionInstruction().equals(ExecutionInstruction.TAKER)) {

          specificOrder.setFillType(FillType.MARKET);
          specificOrder.setLimitPriceCount(0);
        } else {
          specificOrder.setFillType(FillType.LIMIT);
          specificOrder.setLimitPriceCount(
              triggeredOrder
                  .getTargetPrice()
                  .toBasis(specificOrder.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)
                  .getCount());
        }
        specificOrder.setPositionEffect(triggeredOrder.getPositionEffect());
        specificOrder.setComment(comment);
        specificOrder.persit();
        triggeredOrder.merge();
        log.info(
            this.getClass().getSimpleName()
                + " : triggerOrder -"
                + triggeredOrder.getUuid()
                + " convereted to specific order "
                + specificOrder);

        // TODO: loop over open order by parent fill and link them so we know to cancel them is this
        // get's filled first.
        log.info(
            this.getClass().getSimpleName()
                + " : triggerOrder -Cancelling working orders for parent fill: "
                + (triggeredOrder.getParentFill() != null
                    ? triggeredOrder.getParentFill().getUuid()
                    : " "));
        try {
          if (!getPendingOrders().isEmpty())
            if (triggeredOrder.getParentFill() != null
                && !handleCancelSpecificOrderByParentFill(triggeredOrder.getParentFill())) {
              log.info(
                  this.getClass().getSimpleName()
                      + " : triggerOrder - unable to cancell all orders by parent fill:"
                      + triggeredOrder.getParentFill().getUuid());
              updateOrderState(specificOrder, OrderState.ERROR, true);
              updateOrderState(triggeredOrder, OrderState.ERROR, true);

              // triggerOrderLock.unlock();
              return triggeredOrder;
            }
          if (specificOrder.getVolume() == null || specificOrder.getVolume().isZero()) {
            log.info(
                this.getClass().getSimpleName()
                    + " : triggerOrder - "
                    + triggeredOrder
                    + " not triggered as zero volume"
                    + (triggeredOrder.getParentFill() != null
                        ? "fill open volume" + triggeredOrder.getParentFill().getOpenVolume()
                        : "trigger order volume" + triggeredOrder.getVolume()));
            // triggerOrders.remove(triggeredOrder);
            triggeredOrders.add(triggeredOrder);

            updateOrderState(triggeredOrder, OrderState.EXPIRED, true);
            //		triggeredOrders.add(triggeredOrder);
            //     itto.remove();
            return triggeredOrder;
          }

          // specificOrder.persit();
          log.info(
              this.getClass().getSimpleName()
                  + " : triggerOrder - At "
                  + context.getTime()
                  + " Routing trigger order "
                  + triggeredOrder.getUuid()
                  + " to "
                  + triggeredOrder.getMarket());
          // If trigger order is not in state map we don't place triggreed Order

          updateOrderState(specificOrder, OrderState.NEW, true);
          //	updateOrderState(triggeredOrder, OrderState.ROUTED, true);

          if (placeOrder(specificOrder)) {

            //   specificOrder.persit();
            log.info(
                this.getClass().getSimpleName()
                    + " : triggerOrder -"
                    + triggeredOrder.getUuid()
                    + " triggered as specificOrder "
                    + specificOrder);
            removeTriggerOrders(
                triggeredOrder.getMarket(), new ArrayList<Order>(Arrays.asList(triggeredOrder)));
            context.route(
                new PositionUpdate(
                    null,
                    specificOrder.getMarket(),
                    specificOrder.getOrderGroup(),
                    PositionType.SHORT,
                    PositionType.EXITING));

            triggeredOrders.add(triggeredOrder);
            specificOrder.withParentOrder(triggeredOrder);
            specificOrder.withParentFill(triggeredOrder.getParentFill());

            if (triggeredOrder.getParentFill() != null)
              triggeredOrder.getParentFill().setPositionType(PositionType.EXITING);
          }
          //   triggerOrders.remove(triggeredOrder);

          //     itto.remove();

          return triggeredOrder;
        } catch (OrderNotFoundException e) {
          log.info("order not found");
          triggeredOrders.add(triggeredOrder);
          updateOrderState(triggeredOrder, OrderState.REJECTED, true);
          updateOrderState(specificOrder, OrderState.REJECTED, true);
        } catch (Throwable e) {
          log.error(
              this.getClass().getSimpleName()
                  + " : triggerOrder - Unable to place trigged order "
                  + specificOrder.getUuid()
                  + " ",
              e);
          triggeredOrders.add(triggeredOrder);

          updateOrderState(specificOrder, OrderState.REJECTED, true);
          updateOrderState(triggeredOrder, OrderState.REJECTED, true);
        }
      } else {
        //  specificOrder.persit();

        log.info(
            this.getClass().getSimpleName()
                + " : triggerOrder -"
                + triggeredOrder
                + " not triggered as zero volume"
                + (triggeredOrder.getParentFill() != null
                    ? "fill open volume" + triggeredOrder.getParentFill().getOpenVolume()
                    : "trigger order volume" + triggeredOrder.getVolume()));
        triggeredOrders.add(triggeredOrder);
        updateOrderState(triggeredOrder, OrderState.EXPIRED, true);

        //   triggerOrders.remove(triggeredOrder);
        //	triggeredOrders.add(triggeredOrder);

        //  itto.remove();
        //   updateOrderState(triggeredOrder, OrderState.EXPIRED, true);

      }
      //  triggerOrders.remove(triggeredOrder);
      return triggeredOrder;
      // }
      // i = Math.min(0, i - 1);
      //  triggerOrders.remove(triggerOrder);
    }
  }

  @SuppressWarnings("ConstantConditions")
  private synchronized void updateRestingOrders(Event event, Double triggerInterval) {
    // TODO If we trigger an order and it get's rejected by the exchange, the trigger order also
    // get's rejeccted, should we place it back into a triggered state so it can trigger again?

    // TODO if there is a miminimum order size, we might need to cancel all stops then place a
    // bigger order above the imunum size.
    // example want to exit 1.631747 ETC/USD position with 3 stop orders of 0.54391566666667 but
    // minumum order size if 1, so we loop over, cancel all stops adding them up until the reach the
    // mimum order size,
    // then place them as one orders.

    // For trigger orders we need to move then if the event time is after the order time even when
    // the trade service is disabled.

    Tradeable market = null;
    List<Offer> asks = new ArrayList<>();
    Book b = null;
    Trade t = null;
    List<Offer> bids = new ArrayList<>();
    if (event instanceof Book) {
      b = (Book) event;
      //  market = b.getMarket();

    }
    /*
     * if (event.getTimestamp() >= 1390101388000L) { log.debug("here we go"); log.debug("orderstatmap: " + orderStateMap); log.debug("pendingOrders: "
     * + getPendingOrders()); log.debug("TriggerOrders: " + triggerOrders); log.debug("TrailingTriggerOrders: " + trailingTriggerOrders); }
     */

    if (event instanceof Trade) {
      t = (Trade) event;

      //  market = t.getMarket();
      b = bookFactory.create(new Instant(t.getTimestamp()), t.getMarket());

      // if Trade is a sell then it must have big the ask
      if (!t.getMarket().isSynthetic()) {
        if (t.getVolume().isNegative()) {
          Offer bestBid =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().getCount(),
                  Math.abs(t.getVolume().getCount()) * -1);
          b.getAsks().add(bestBid);
          Offer bestAsk =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().getCount(),
                  Math.abs(t.getVolume().getCount()));
          b.getBids().add(bestAsk);

        } else {
          Offer bestAsk =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().getCount(),
                  Math.abs(t.getVolume().getCount()) * -1);
          b.getAsks().add(bestAsk);
          Offer bestBid =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().getCount(),
                  Math.abs(t.getVolume().getCount()));
          b.getBids().add(bestBid);
        }
      } else {
        if (t.getVolume().isNegative()) {
          Offer bestBid =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().asBigDecimal(),
                  t.getVolume().asBigDecimal().abs().negate());
          b.getAsks().add(bestBid);
          Offer bestAsk =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().asBigDecimal(),
                  t.getVolume().asBigDecimal().abs());
          b.getBids().add(bestAsk);

        } else {
          Offer bestAsk =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().asBigDecimal(),
                  t.getVolume().asBigDecimal().abs().negate());
          b.getAsks().add(bestAsk);
          Offer bestBid =
              new Offer(
                  t.getMarket(),
                  t.getTime(),
                  t.getTimeReceived(),
                  t.getPrice().asBigDecimal(),
                  t.getVolume().asBigDecimal().abs());
          b.getBids().add(bestBid);
        }
      }

      //     b.build();

    }
    market = b.getMarket();
    asks = b.getAsks();
    bids = b.getBids();
    Offer offer;
    //  synchronized (lock) {
    Offer ask = b.getBestAsk();

    Offer bid = b.getBestBid();

    if (bid == null || bid.getPrice().isZero() || ask == null || ask.getPrice().isZero()) return;

    log.trace(
        "Bid price for trigger:{} . Ask price for trigger: {} ", bid.getPrice(), ask.getPrice());
    if (getTradingEnabled() && event != null) {

      for (Order order : getPendingOrders()) {
        if (order instanceof SpecificOrder && getOrderState(order) != OrderState.NEW) {

          SpecificOrder pendingOrder = (SpecificOrder) order;
          if (!pendingOrder.getMarket().equals(b.getMarket())) continue;
          if (pendingOrder.getExpiryTime() != null
              && context.getTime().isAfter(pendingOrder.getExpiryTime())
              && !(pendingOrder.getExecutionInstruction().equals(ExecutionInstruction.MAKERTOTAKER)
                  || pendingOrder
                      .getExecutionInstruction()
                      .equals(ExecutionInstruction.PEGTOBOOK))) {
            //   synchronized (pendingOrder) {
            try {
              if (getOrderState(pendingOrder) != null
                  && getOrderState(pendingOrder).isOpen()
                  && !pendingOrder.getFillType().equals(FillType.MARKET)) {

                log.info(
                    "Order Expired at {} with state {} . Cancelling Specifc Order:{} ",
                    context.getTime(),
                    getOrderState(pendingOrder),
                    pendingOrder);

                if (handleCancelSpecificOrder(pendingOrder))
                  log.info("udpateRestingOrders cancelled Specific Order:{} ", pendingOrder);
                else
                  log.info("udpateRestingOrders ubale to cancel Specific Order:{}", pendingOrder);
              }
            } catch (Exception e) {
              log.error("attmept to cancel an order that was not pending : {} {}", pendingOrder, e);
            }

          } else if ((pendingOrder.getExpiryTime() != null
                  && context.getTime().isAfter(pendingOrder.getExpiryTime())
                  && (pendingOrder
                          .getExecutionInstruction()
                          .equals(ExecutionInstruction.MAKERTOTAKER)
                      || pendingOrder
                          .getExecutionInstruction()
                          .equals(ExecutionInstruction.PEGTOBOOK)))
              || (pendingOrder.getExpiryTime() == null
                  && (pendingOrder
                          .getExecutionInstruction()
                          .equals(ExecutionInstruction.MAKERTOTAKER)
                      || pendingOrder
                          .getExecutionInstruction()
                          .equals(ExecutionInstruction.PEGTOBOOK)))
              || (getOrderState(pendingOrder) != null
                  && (pendingOrder.getFillType() != null
                      && pendingOrder.getFillType().equals(FillType.MARKET)))) {

            pendingOrder.setPlacementCount(pendingOrder.getPlacementCount() + 1);

            if ((pendingOrder.getPlacementCount() % updateOrderAfter) != 0) {
              continue;
            }

            DiscreteAmount limitPriceDiscrete = getBestLimitPriceForOrder(pendingOrder);

            // so if I am buying and the new price is higher I update
            // buying last price is 0.999 my limtie price is 10

            if (limitPriceDiscrete != null) {
              if (pendingOrder.isBid()) {
                if (limitPriceDiscrete.isPositive()
                    && limitPriceDiscrete.compareTo(pendingOrder.getLimitPrice()) <= 0) continue;

              } else {
                if (limitPriceDiscrete.isPositive()
                    && limitPriceDiscrete.compareTo(pendingOrder.getLimitPrice()) >= 0) continue;
              }
            }

            log.debug(
                "{}:updatingRestingOrder - At {}  cancelling and replacing :{}",
                this.getClass().getSimpleName(),
                context.getTime(),
                pendingOrder);

            if (!cancelAndReplaceOrder(
                pendingOrder,
                (pendingOrder.getExecutionInstruction().equals(ExecutionInstruction.PEGTOBOOK)
                    ? limitPriceDiscrete
                    : null))) {
              continue;
            }
          }
        }
      }
      lockTriggerOrders();
      // Set<Order> triggeredOrders = new HashSet<Order>();

      if (triggerOrders != null
          && !triggerOrders.isEmpty()
          && triggerOrders.get(market) != null
          && !triggerOrders.get(market).isEmpty()
          && triggerOrders.get(market).get(triggerInterval) != null
          && !triggerOrders.get(market).get(triggerInterval).isEmpty()
          && triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) != null
          && !triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty()) {

        // `Something is up here are we triggering orders whilst we are in teh loop, need to trigger
        // them all once we have exited lopp
        for (Iterator<FillType> itf =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .keySet()
                    .iterator();
            itf.hasNext(); ) {
          FillType fillType = itf.next();

          synchronized (
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .get(fillType)) {
            HashMap<Order, String> triggeredBuyOrders = new HashMap<Order, String>();
            Set<Order> expiredBuyOrders = new HashSet<Order>();
            Set<Order> cancelledBuyOrders = new HashSet<Order>();

            Iterator<Order> itto =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(fillType)
                    .iterator();
            //  int size =
            // triggerOrders.get(tr).get(triggerInterval).get(TransactionType.BUY).size();
            //	log.debug("trigger orders" +
            // triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType));
            while (itto.hasNext()) {
              Order triggeredOrder = itto.next();

              log.trace(
                  "determining to trigger resting buy  order: {} {}  for interval  {} at Bid price for trigger: {} Ask price for trigger: {} ",
                  triggeredOrder.getUuid(),
                  (triggeredOrder.getStopPrice() != null
                      ? " with stop price " + (triggeredOrder.getStopPrice())
                      : ""),
                  triggerInterval,
                  bid.getPrice(),
                  ask.getPrice());

              if (triggeredOrder.getExpiryTime() != null
                  && context.getTime().isAfter(triggeredOrder.getExpiryTime())
                  && !triggeredOrder
                      .getExecutionInstruction()
                      .equals(ExecutionInstruction.MAKERTOTAKER)) {
                log.info(
                    "At {} Trigger buy order Expired, cancelling general Order: {}",
                    context.getTime(),
                    triggeredOrder);

                expiredBuyOrders.add(triggeredOrder);
                itto.remove();
                continue;
              }
              if (b.getMarket() != null) {
                try {
                  // if the order is a buy order, then I want to trigger at best price I can sell at
                  // (best bid)
                  if (triggeredOrder.isBid()
                      && triggeredOrder.getTimestamp() < b.getBestAsk().getTimestamp()) {
                    DiscreteAmount triggerPrice = b.getBestAsk().getPrice();
                    if (triggeredOrder != null) {
                      if (fillType == FillType.MULTI_LEG
                          && triggeredOrder.getLinkedOrder() != null) {
                        // selling on passive leg infers expecting spread to widen
                        if (triggeredOrder.getLegNumber() == 2) {
                          if (triggeredOrder.getDifferentialPrice() != null) {
                            triggeredOrder.setPlacementCount(
                                triggeredOrder.getPlacementCount() + 1);
                            if ((triggeredOrder.getPlacementCount() % updateOrderAfter) != 0)
                              continue;
                            DiscreteAmount bestLimitPrice =
                                getBestLimitPriceForOrder(triggeredOrder);
                            DiscreteAmount bestLinkedLimitPrice =
                                getBestLimitPriceForOrder(triggeredOrder.getLinkedOrder());

                            Amount linkedLimitPrice =
                                bestLimitPrice.plus(triggeredOrder.getDifferentialPrice());
                            if (triggeredOrder.getLinkedOrder().isBid()) {
                              //  we are buying so either get to top of bid or
                              if (triggeredOrder
                                      .getLinkedOrder()
                                      .getPositionEffect()
                                      .equals(PositionEffect.CLOSE)
                                  && linkedLimitPrice.compareTo(bestLinkedLimitPrice) > 0) {
                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), bestLinkedLimitPrice, false);
                              } else if (linkedLimitPrice.compareTo(bestLinkedLimitPrice) > 0) {

                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), linkedLimitPrice, false);
                              }
                            } else {
                              if (triggeredOrder
                                      .getLinkedOrder()
                                      .getPositionEffect()
                                      .equals(PositionEffect.CLOSE)
                                  && linkedLimitPrice.compareTo(bestLinkedLimitPrice) < 0) {
                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), bestLinkedLimitPrice, false);
                              } else if (linkedLimitPrice.compareTo(bestLinkedLimitPrice) < 0) {

                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), linkedLimitPrice, false);
                              }
                            }

                            updateAllWorkingOrdersByParent(triggeredOrder, bestLimitPrice, false);
                          }
                        }
                      }

                      if ((fillType == FillType.TIMED
                              && triggeredOrder.getTriggerTime() != null
                              && context.getTime().isAfter(triggeredOrder.getTriggerTime()))
                          || (fillType == FillType.STOP_LIMIT
                              && triggeredOrder.getStopPrice() != null
                              && (triggerPrice.compareTo(triggeredOrder.getStopPrice()) >= 0))
                          || (fillType == FillType.TARGET_LIMIT
                              && triggeredOrder.getTargetPrice() != null
                              && (triggerPrice.compareTo(triggeredOrder.getTargetPrice()) <= 0))) {

                        log.info(
                            "{}:updatingRestingOrder - At {} at price  {}  triggered order with fill type {} : buy {}  ",
                            this.getClass().getSimpleName(),
                            context.getTime(),
                            triggerPrice,
                            event,
                            fillType,
                            triggeredOrder.getUuid());

                        if (triggeredOrder.getParentFill() != null)
                          log.info(
                              "triggered order: {} parent fill unfilled volume: {} {} {} ",
                              triggeredOrder.getUuid(),
                              (triggeredOrder.getParentFill().getUnfilledVolume() != null
                                  ? (triggeredOrder.getParentFill().getUnfilledVolume()
                                      + " parent fill position type:")
                                  : ""),
                              (triggeredOrder.getParentFill().getPositionType() != null
                                  ? (triggeredOrder.getParentFill().getPositionType()
                                      + " parent Fill child orders: ")
                                  : ""),
                              (triggeredOrder.getParentFill().getFillChildOrders() != null
                                  ? (triggeredOrder.getParentFill().getFillChildOrders().hashCode())
                                  : ""));

                        String comment = null;
                        if (fillType == FillType.STOP_LIMIT
                            && triggeredOrder.getStopPrice() != null
                            && (triggerPrice.compareTo(triggeredOrder.getStopPrice()) >= 0)) {
                          comment = "Short Stop Order with Stop Price";
                        } else if (fillType == FillType.TIMED
                            && triggeredOrder.getTriggerTime() != null) {
                          comment = "Short Timed Order with Trigger Time";
                        } else comment = "Long Target Order with Target Price";
                        triggeredOrder.setFillType(fillType);
                        triggeredBuyOrders.put(triggeredOrder, comment);
                      }
                    } else break;
                  }
                } catch (NullPointerException npe) {
                  // most likey thrown cos the trigger order have been removed by a cancel action
                  log.info(
                      "BaseOrderService: UpdateRestingOrders - Trigger order removed for triggerOrders hashmap whilst process a market data event, continuing to process remaining trigger orders {} ",
                      npe);
                  continue;
                } catch (Throwable ex) {
                  log.info(
                      "BaseOrderService: UpdateRestingOrders - Unable to process trigger orders {}",
                      ex);
                  unlockTriggerOrders();
                  throw ex;
                }
              }
            }

            ArrayList<Order> triggeredOrders = new ArrayList<Order>();
            for (Order orderToTrigger : triggeredBuyOrders.keySet()) {
              triggerOrder(orderToTrigger, triggeredBuyOrders.get(orderToTrigger), triggeredOrders);
            }
            if (!triggeredOrders.isEmpty()) {
              log.debug(
                  "BaseOrderService: UpdateRestingOrders - Removing triggered buy orders {}  buy trigger order map with fill type {} ",
                  triggeredOrders,
                  fillType);
              removeTriggerOrders(market, triggeredOrders);
            }
            for (Order expiredOrder : expiredBuyOrders) {
              updateOrderState(expiredOrder, OrderState.EXPIRED, true);
              log.info(
                  "{}:Expired buy Trigger Order:{} ",
                  this.getClass().getSimpleName(),
                  expiredOrder);
              if (expiredOrder.getParentFill() != null)
                handleCancelAllTriggerOrdersByParentFill(expiredOrder.getParentFill());
            }
          }
        }
      }

      if (triggerOrders != null
          && !triggerOrders.isEmpty()
          && triggerOrders.get(market) != null
          && !triggerOrders.get(market).isEmpty()
          && triggerOrders.get(market).get(triggerInterval) != null
          && !triggerOrders.get(market).get(triggerInterval).isEmpty()
          && triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) != null
          && !triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty()) {

        //  for (Event parentKey : triggerOrders.keySet()) {
        //  Iterator<Event> itEvent = triggerOrders.keySet().iterator();
        // while (itEvent.hasNext()) {
        // closing position
        //  Event parentKey = itEvent.next();
        for (Iterator<FillType> itf =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .keySet()
                    .iterator();
            itf.hasNext(); ) {
          FillType fillType = itf.next();

          synchronized (
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .get(fillType)) {
            Iterator<Order> itto =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(fillType)
                    .iterator();
            HashMap<Order, String> triggeredSellOrders = new HashMap<Order, String>();
            Set<Order> expiredSellOrders = new HashSet<Order>();

            while (itto.hasNext()) {
              Order triggeredOrder = itto.next();
              //  synchronized (triggeredOrder) {
              //  Event parentKey = itps.next();
              // for (Order triggeredOrder :
              // triggerOrders.get(market).get(TransactionType.SELL).get(parentKey)) {
              /*
               * Iterator<Order> it = triggerOrders.get(parentKey).iterator(); while (it.hasNext()) { Order triggeredOrder = it.next();
               */

              // for (Order triggeredOrder : triggerOrders.get(parentKey)) {

              log.trace(
                  "determining to trigger resting sell  order:{} {} for interval {} at Bid price for trigger: {}  . Ask price for trigger: {}",
                  triggeredOrder.getUuid(),
                  (triggeredOrder.getStopPrice() != null
                      ? " with stop price " + (triggeredOrder.getStopPrice())
                      : ""),
                  (triggeredOrder.getTargetPrice() != null
                      ? " target price " + (triggeredOrder.getTargetPrice())
                      : ""),
                  triggerInterval,
                  bid.getPrice(),
                  ask.getPrice());

              if (triggeredOrder.getExpiryTime() != null
                  && context.getTime().isAfter(triggeredOrder.getExpiryTime())
                  && !triggeredOrder
                      .getExecutionInstruction()
                      .equals(ExecutionInstruction.MAKERTOTAKER)) {

                log.info(
                    "At  {} Trigger sell order Expired, cancelling general Order: {}",
                    context.getTime(),
                    triggeredOrder);
                // addTriggerOrder(triggeredOrder)
                //  triggerOrders.remove(triggeredOrder);
                expiredSellOrders.add(triggeredOrder);
                itto.remove();
                //  itto.remove();
                //  itto.remove();
                //    triggeredOrders.add(triggeredOrder);
                continue;
              }
              if (b.getMarket() != null) {
                try {

                  if (triggeredOrder.getTimestamp() < b.getBestBid().getTimestamp()) {
                    // triggerOrderLock.lock();
                    //  I want to sell at market bid price, as long, so when the
                    // buy entry to can take ask or make bid + increment
                    // bids={10.0@606.81;2.0@606.57;12.0@606.45;1.0@605.67;87.0@605.66}
                    // asks={-96.0@607.22;-64.0@607.49;-121.0@607.51;-4.0@607.59;-121.0@607.79}
                    // so sell order, I need to sell at the current bid
                    DiscreteAmount triggerPrice = b.getBestBid().getPrice();

                    /*
                     * && if ((triggeredOrder != null) && ((triggeredOrder.getStopPrice() != null &&
                     * (!(triggerPrice.compareTo(triggeredOrder.getStopPrice()) > 0))) || (triggeredOrder .getTargetPrice() != null &&
                     * (((triggeredOrder.getPositionEffect() == null || triggeredOrder .getPositionEffect() == (PositionEffect.CLOSE)) &&
                     * (!(triggerPrice.compareTo(triggeredOrder.getTargetPrice()) < 0))) || (triggeredOrder .getPositionEffect() ==
                     * (PositionEffect.OPEN) && (!(triggerPrice.compareTo(triggeredOrder.getTargetPrice()) > 0))))))) {
                     */

                    if (triggeredOrder != null) {
                      if (fillType == FillType.MULTI_LEG
                          && triggeredOrder.getLinkedOrder() != null) {
                        // selling on passive leg infers expecting spread to widen
                        if (triggeredOrder.getLegNumber() == 2) {
                          if (triggeredOrder.getDifferentialPrice() != null) {
                            triggeredOrder.setPlacementCount(
                                triggeredOrder.getPlacementCount() + 1);
                            if ((triggeredOrder.getPlacementCount() % updateOrderAfter) != 0)
                              continue;
                            DiscreteAmount bestLimitPrice =
                                getBestLimitPriceForOrder(triggeredOrder);

                            DiscreteAmount bestLinkedLimitPrice =
                                getBestLimitPriceForOrder(triggeredOrder.getLinkedOrder());

                            Amount linkedLimitPrice =
                                bestLimitPrice.plus(triggeredOrder.getDifferentialPrice());

                            if (triggeredOrder.getLinkedOrder().isBid()) {
                              //  we are buying order pirce is 100 and we are buying, so  we will
                              // pay up to 100, if
                              // linked order price is 10, dfiferntail is 5, and the new trade price
                              // is 12.
                              // limit price is 14
                              if (linkedLimitPrice.compareTo(bestLinkedLimitPrice) > 0) {

                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), linkedLimitPrice, false);
                              } else if (triggeredOrder
                                      .getLinkedOrder()
                                      .getPositionEffect()
                                      .equals(PositionEffect.CLOSE)
                                  && linkedLimitPrice.compareTo(bestLinkedLimitPrice) > 0) {
                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), bestLinkedLimitPrice, false);
                              }
                            } else {
                              if (linkedLimitPrice.compareTo(bestLinkedLimitPrice) < 0) {

                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), linkedLimitPrice, false);
                              } else if (triggeredOrder
                                      .getLinkedOrder()
                                      .getPositionEffect()
                                      .equals(PositionEffect.CLOSE)
                                  && linkedLimitPrice.compareTo(bestLinkedLimitPrice) < 0) {
                                updateAllWorkingOrdersByParent(
                                    triggeredOrder.getLinkedOrder(), bestLinkedLimitPrice, false);
                              }
                            }

                            updateAllWorkingOrdersByParent(triggeredOrder, bestLimitPrice, false);
                          }
                        }
                      }

                      if ((fillType == FillType.TIMED
                              && triggeredOrder.getTriggerTime() != null
                              && context.getTime().isAfter(triggeredOrder.getTriggerTime()))
                          || (fillType == FillType.STOP_LIMIT
                              && triggeredOrder.getStopPrice() != null
                              && (triggerPrice.compareTo(triggeredOrder.getStopPrice()) <= 0))
                          || (fillType == FillType.TARGET_LIMIT
                              && triggeredOrder.getTargetPrice() != null
                              && (triggerPrice.compareTo(triggeredOrder.getTargetPrice()) >= 0))) {

                        log.info(
                            "{} :updatingRestingOrder - At {}  at price {}  {} triggered sell order with fill type {} : {} ",
                            this.getClass().getSimpleName(),
                            context.getTime(),
                            triggerPrice,
                            event,
                            fillType,
                            triggeredOrder);

                        if (triggeredOrder.getParentFill() != null)
                          log.info(
                              "triggered order:{} parent fill unfilled volume:{} {} {}",
                              triggeredOrder.getUuid(),
                              (triggeredOrder.getParentFill().getUnfilledVolume() != null
                                  ? (triggeredOrder.getParentFill().getUnfilledVolume()
                                      + " parent fill position type:")
                                  : ""),
                              (triggeredOrder.getParentFill().getPositionType() != null
                                  ? (triggeredOrder.getParentFill().getPositionType()
                                      + " parent Fill child orders: ")
                                  : ""),
                              (triggeredOrder.getParentFill().getFillChildOrders() != null
                                  ? (triggeredOrder.getParentFill().getFillChildOrders().hashCode())
                                  : ""));

                        String comment = null;
                        if (fillType == FillType.STOP_LIMIT
                            && triggeredOrder.getStopPrice() != null
                            && (triggerPrice.compareTo(triggeredOrder.getStopPrice()) <= 0)) {
                          //  if (triggeredOrder.getPositionEffect() == null ||
                          // triggeredOrder.getPositionEffect().equals(PositionEffect.CLOSE))
                          comment = "Long Stop Order with Stop Price";
                          //      else
                          //        comment = "Short Entry Order with Target Price";

                        } else if (fillType == FillType.TIMED
                            && triggeredOrder.getTriggerTime() != null) {
                          comment = "Long Timed Order with Trigger Time";
                        } else comment = ("Short Target Order with Target Price");
                        //   if (triggeredOrder.getPositionEffect() == (PositionEffect.CLOSE))
                        // comment = ("Long Stop Order with Stop Price");
                        // else if (triggeredOrder.getPositionEffect() == (PositionEffect.OPEN))
                        //  comment = ("Short Entry Order with Target Price");
                        triggeredOrder.setFillType(fillType);
                        triggeredSellOrders.put(triggeredOrder, comment);

                        // itTriggeredOrder.remove();
                        //  triggerOrders.get(parentKey).remove(triggeredOrder);

                        //   triggerOrders.remove(parentKey);

                        // portfolioService.publishPositionUpdate(new
                        // Position(triggeredOrder.getPortfolio(),
                        // triggeredOrder.getMarket().getExchange(), triggeredOrder.getMarket(),
                        // triggeredOrder.getMarket().getBase(),
                        //       DecimalAmount.ZERO, DecimalAmount.ZERO));

                        // i = Math.min(0, i - 1);
                        // triggeredOrders.add(triggeredOrder);

                      }
                    } else break;
                  }
                } catch (NullPointerException npe) {
                  // most likey thrown cos the trigger order have been removed by a cancel action
                  log.info(
                      "BaseOrderService: UpdateRestingOrders - Trigger order removed for triggerOrders hashmap whilst process a market data event, continuing to process remaining trigger orders");
                  continue;

                } catch (Throwable ex) {
                  log.info(
                      "BaseOrderService: UpdateRestingOrders - Unable to process trigger orders {}",
                      ex);

                  unlockTriggerOrders();
                  throw ex;
                }
              }
            }
            ArrayList<Order> triggeredOrders = new ArrayList<Order>();
            for (Order orderToTrigger : triggeredSellOrders.keySet()) {
              triggerOrder(
                  orderToTrigger, triggeredSellOrders.get(orderToTrigger), triggeredOrders);
            }
            if (!triggeredOrders.isEmpty()) {
              log.debug(
                  "BaseOrderService: UpdateRestingOrders - Removing triggered sell orders {} sell trigger order map with fill type {}",
                  triggeredOrders,
                  fillType);
              removeTriggerOrders(market, triggeredOrders);
            }
            for (Order expiredOrder : expiredSellOrders) {
              updateOrderState(expiredOrder, OrderState.EXPIRED, true);
              log.info(
                  "{} :Expired sell Trigger Order: {}",
                  this.getClass().getSimpleName(),
                  expiredOrder);
              if (expiredOrder.getParentFill() != null)
                handleCancelAllTriggerOrdersByParentFill(expiredOrder.getParentFill());
            }
          }
        }
      }
    }
    //
    // lets' move all the trailing stops

    if (trailingTriggerOrders != null
        && !trailingTriggerOrders.isEmpty()
        && trailingTriggerOrders.get(market) != null
        && !trailingTriggerOrders.get(market).isEmpty()
        && trailingTriggerOrders.get(market).get(triggerInterval) != null
        && !trailingTriggerOrders.get(market).get(triggerInterval).isEmpty()
        && trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) != null
        && !trailingTriggerOrders
            .get(market)
            .get(triggerInterval)
            .get(TransactionType.BUY)
            .isEmpty()) {

      boolean updatedOrders = false;
      synchronized (
          trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY)) {
        Iterator<Order> ittto =
            trailingTriggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .iterator();
        Set<Order> trailingTriggeredBuyOrders = new HashSet<Order>();
        while (ittto.hasNext()) {
          Order trailingTriggerOrder = ittto.next();

          if (b.getTime().isBefore(trailingTriggerOrder.getTime())) continue;
          //    synchronized (trailingTriggerOrder) {
          //  Iterator<Order> ittto =
          // triggerOrdersTable.get(market).get(TransactionType.BUY).columnKeySet().iterator();
          // while (ittto.hasNext()) {
          //  Order trailingTriggerOrder = ittto.next();

          log.trace(
              "determining to to update trailing trigger order:{}  at Bid price for trigger: {}  . Ask price for trigger: {}",
              trailingTriggerOrder.getUuid(),
              bid.getPrice(),
              ask.getPrice());

          if (b.getMarket() != null) {
            try {
              // if the order is a buy order, then I want to trigger at best price I can sell at
              // (best bid)
              if (trailingTriggerOrder.getTimestamp() < b.getBestAsk().getTimestamp()) {
                DiscreteAmount triggerPrice = b.getBestAsk().getPrice();

                if (trailingTriggerOrder.getFillType() != null
                    && (trailingTriggerOrder.getFillType().equals(FillType.TRAILING_STOP_LIMIT)
                        || trailingTriggerOrder
                            .getFillType()
                            .equals(FillType.REENTRANT_TRAILING_STOP_LIMIT))) {

                  log.trace(
                      "{} - updateRestingOrders: Determing if any buy trailing stops to update for order id {}",
                      this.getClass().getSimpleName(),
                      trailingTriggerOrder.getUuid());
                  DiscreteAmount unfilledVolumeDiscrete = getOpenVolume(trailingTriggerOrder);

                  if (unfilledVolumeDiscrete != null && unfilledVolumeDiscrete.isZero()) {
                    log.debug(
                        "{}- updateRestingOrders: Removed  buy trailing stop with zero unfilled {}",
                        this.getClass().getSimpleName(),
                        trailingTriggerOrder);

                    trailingTriggeredBuyOrders.add(trailingTriggerOrder);
                    // ittto.remove();

                  }
                  Amount stopAmount =
                      (trailingTriggerOrder.getStopPercentage() != 0)
                          ? triggerPrice.times(
                              trailingTriggerOrder.getStopPercentage(), Remainder.ROUND_EVEN)
                          : trailingTriggerOrder.getStopAmount();
                  // these are buy orders to exit a short, so if price is failing we want to move
                  // stop down.
                  // price =100, amount =10, stop price =120, as price + stop amount < current stop
                  // price
                  // 654.42 + 99.64 > 750.52
                  if (trailingTriggerOrder.getStopPrice() != null
                      && ((triggerPrice.getCount()
                              + (stopAmount
                                  .toBasis(
                                      trailingTriggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN)
                                  .getCount()))
                          < (trailingTriggerOrder
                              .getStopPrice()
                              .toBasis(
                                  trailingTriggerOrder.getMarket().getPriceBasis(),
                                  Remainder.ROUND_EVEN)
                              .getCount()))) {

                    long stopPrice =
                        Math.min(
                            (trailingTriggerOrder
                                    .getStopPrice()
                                    .toBasis(
                                        trailingTriggerOrder.getMarket().getPriceBasis(),
                                        Remainder.ROUND_EVEN))
                                .getCount(),
                            (triggerPrice.getCount()
                                + (stopAmount.toBasis(
                                        trailingTriggerOrder.getMarket().getPriceBasis(),
                                        Remainder.ROUND_EVEN))
                                    .getCount()));
                    DiscreteAmount stopDiscrete =
                        (new DiscreteAmount(
                            stopPrice, trailingTriggerOrder.getMarket().getPriceBasis()));

                    log.debug(
                        "{}- updateRestingOrders: At {} updating buy trailing stop from  {} to {} for order id  {} ",
                        this.getClass().getSimpleName(),
                        context.getTime(),
                        trailingTriggerOrder.getStopPrice(),
                        stopDiscrete,
                        trailingTriggerOrder.getUuid());

                    trailingTriggerOrder.setStopPrice(DecimalAmount.of(stopDiscrete));
                    //	trailingTriggerOrder.setStopAdjustmentCount(trailingTriggerOrder.getStopAdjustmentCount() + 1);

                    updatedOrders = true;
                    if (saveStopPriceUpdates) trailingTriggerOrder.merge();

                    if (trailingTriggerOrder.getParentFill() != null) {
                      trailingTriggerOrder
                          .getParentFill()
                          .setStopPriceCount(stopDiscrete.getCount());
                      if (saveStopPriceUpdates) trailingTriggerOrder.getParentFill().merge();
                    }

                  } else break;
                }
              }
            } catch (NullPointerException npe) {
              // most likey thrown cos the trigger order have been removed by a cancel action
              log.info(
                  "BaseOrderService: UpdateRestingOrders - Trigger order removed for triggerOrders hashmap whilst process a market data event, continuing to process remaining trigger orders");
              continue;

            } catch (Throwable ex) {
              log.info(
                  "BaseOrderService: UpdateRestingOrders - Unable to process trigger orders {}",
                  ex);
              removeTriggerOrders(market, trailingTriggeredBuyOrders);
              for (Order cancelledOrder : trailingTriggeredBuyOrders) {
                updateOrderState(cancelledOrder, OrderState.ERROR, true);

                log.debug(
                    "handleCancelAllLongOpeningGeneralOrders called from class {} Cancelled Long Opening Trigger Order:  {}",
                    Thread.currentThread().getStackTrace()[2],
                    cancelledOrder);

                if (cancelledOrder.getParentFill() != null)
                  handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
              }
              unlockTriggerOrders();
              throw ex;
            }
          }
        }
        removeTriggerOrders(market, trailingTriggeredBuyOrders);
        for (Order cancelledOrder : trailingTriggeredBuyOrders) {
          updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
          log.debug(
              "handleCancelAllLongOpeningGeneralOrders called from class {} Cancelled Long Opening Trigger Order:  {}",
              Thread.currentThread().getStackTrace()[2],
              cancelledOrder);

          if (cancelledOrder.getParentFill() != null)
            handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
        }
      }
      if (updatedOrders) {
        sortOrders(
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.BUY)
                .get(FillType.STOP_LIMIT),
            TransactionType.BUY,
            market,
            ascendingStopPriceComparator);
        sortOrders(
            trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY),
            TransactionType.BUY,
            market,
            descendingTrailingStopPriceComparator);
      }
    }

    // we should remove the order for all tigger intervals as might be added to many.

    if (trailingTriggerOrders != null
        && !trailingTriggerOrders.isEmpty()
        && trailingTriggerOrders.get(market) != null
        && !trailingTriggerOrders.get(market).isEmpty()
        && trailingTriggerOrders.get(market).get(triggerInterval) != null
        && !trailingTriggerOrders.get(market).get(triggerInterval).isEmpty()
        && trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) != null
        && !trailingTriggerOrders
            .get(market)
            .get(triggerInterval)
            .get(TransactionType.SELL)
            .isEmpty()) {

      boolean updatedOrders = false;
      synchronized (
          trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL)) {
        Iterator<Order> ittto =
            trailingTriggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .iterator();
        Set<Order> trailingTriggeredSellOrders = new HashSet<Order>();

        while (ittto.hasNext()) {
          Order trailingTriggerOrder = ittto.next();
          if (b.getTime().isBefore(trailingTriggerOrder.getTime())) continue;
          //  synchronized (trailingTriggerOrder) {
          //  for (Order trailingTriggerOrder :
          // trailingTriggerOrders.get(market).get(TransactionType.SELL)) {

          //    Iterator<Order> ittto =
          // triggerOrdersTable.get(market).get(TransactionType.SELL).columnKeySet().iterator();
          //  while (ittto.hasNext()) {
          //    Order trailingTriggerOrder = ittto.next();

          log.trace(
              "determining to to update trailing trigger order:{}  at Bid price for trigger: {}  . Ask price for trigger: {}",
              trailingTriggerOrder.getUuid(),
              bid.getPrice(),
              ask.getPrice());

          if (b.getMarket() != null) {
            try {
              // if the order is a buy order, then I want to trigger at best price I can sell at
              // (best bid)
              if (trailingTriggerOrder.getTimestamp() < b.getBestAsk().getTimestamp()) {
                DiscreteAmount triggerPrice = b.getBestAsk().getPrice();

                if (trailingTriggerOrder.getFillType() != null
                    && (trailingTriggerOrder.getFillType().equals(FillType.TRAILING_STOP_LIMIT)
                        || trailingTriggerOrder
                            .getFillType()
                            .equals(FillType.REENTRANT_TRAILING_STOP_LIMIT))) {
                  log.trace(
                      "{} - updateRestingOrders: Determining if sell trailing stops to update for order id {}",
                      this.getClass().getSimpleName(),
                      trailingTriggerOrder.getUuid());
                  DiscreteAmount unfilledVolumeDiscrete = getOpenVolume(trailingTriggerOrder);

                  if (unfilledVolumeDiscrete != null && unfilledVolumeDiscrete.isZero()) {

                    //	if (trailingTriggerOrder.getUnfilledVolume().isZero()) {
                    log.debug(
                        "{}- updateRestingOrders: Removed  buy trailing stop with zero unfilled {} ",
                        this.getClass().getSimpleName(),
                        trailingTriggerOrder);

                    trailingTriggeredSellOrders.add(trailingTriggerOrder);
                    //  ittto.remove();
                  }
                  Amount stopAmount =
                      (trailingTriggerOrder.getStopPercentage() != 0)
                          ? triggerPrice.times(
                              trailingTriggerOrder.getStopPercentage(), Remainder.ROUND_EVEN)
                          : trailingTriggerOrder.getStopAmount();

                  //////

                  if (trailingTriggerOrder.getStopPrice() != null
                      && ((triggerPrice.getCount()
                              - (stopAmount
                                  .toBasis(
                                      trailingTriggerOrder.getMarket().getPriceBasis(),
                                      Remainder.ROUND_EVEN)
                                  .getCount()))
                          > (trailingTriggerOrder
                              .getStopPrice()
                              .toBasis(
                                  trailingTriggerOrder.getMarket().getPriceBasis(),
                                  Remainder.ROUND_EVEN)
                              .getCount()))) {

                    long stopPrice =
                        Math.max(
                            (trailingTriggerOrder
                                    .getStopPrice()
                                    .toBasis(
                                        trailingTriggerOrder.getMarket().getPriceBasis(),
                                        Remainder.ROUND_EVEN))
                                .getCount(),
                            (triggerPrice.getCount()
                                - (stopAmount.toBasis(
                                        trailingTriggerOrder.getMarket().getPriceBasis(),
                                        Remainder.ROUND_EVEN))
                                    .getCount()));
                    DiscreteAmount stopDiscrete =
                        (new DiscreteAmount(
                            stopPrice, trailingTriggerOrder.getMarket().getPriceBasis()));

                    log.debug(
                        "{}- updateRestingOrders: At {}  updating sell trailing stop from  {} to {} for order id  {} ",
                        this.getClass().getSimpleName(),
                        context.getTime(),
                        trailingTriggerOrder.getStopPrice(),
                        stopDiscrete,
                        trailingTriggerOrder.getUuid());

                    trailingTriggerOrder.setStopPrice(DecimalAmount.of(stopDiscrete));
                    //	trailingTriggerOrder.setStopAdjustmentCount(trailingTriggerOrder.getStopAdjustmentCount() + 1);
                    updatedOrders = true;
                    if (saveStopPriceUpdates) trailingTriggerOrder.merge();
                    if (trailingTriggerOrder.getParentFill() != null) {
                      trailingTriggerOrder
                          .getParentFill()
                          .setStopPriceCount(stopDiscrete.getCount());
                      if (saveStopPriceUpdates) trailingTriggerOrder.getParentFill().merge();
                    }

                  } else break;
                }
              }
            } catch (NullPointerException npe) {
              // most likey thrown cos the trigger order have been removed by a cancel action
              log.info(
                  "BaseOrderService: UpdateRestingOrders - Trigger order removed for triggerOrders hashmap whilst process a market data event, continuing to process remaining trigger orders");
              continue;

            } catch (Throwable ex) {
              log.info(
                  "BaseOrderService: UpdateRestingOrders - Unable to process trigger orders {}",
                  ex);
              removeTriggerOrders(market, trailingTriggeredSellOrders);
              for (Order cancelledOrder : trailingTriggeredSellOrders) {
                updateOrderState(cancelledOrder, OrderState.ERROR, true);

                log.debug(
                    "handleCancelAllLongOpeningGeneralOrders called from class {} Cancelled Long Opening Trigger Order {} ",
                    Thread.currentThread().getStackTrace()[2],
                    cancelledOrder);

                if (cancelledOrder.getParentFill() != null)
                  handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
              }
              unlockTriggerOrders();
              throw ex;
            }
          }
        }
        removeTriggerOrders(market, trailingTriggeredSellOrders);
        for (Order cancelledOrder : trailingTriggeredSellOrders) {
          updateOrderState(cancelledOrder, OrderState.CANCELLED, true);

          log.debug(
              "handleCancelAllLongOpeningGeneralOrders called from class {} Cancelled Long Opening Trigger Order: {}",
              Thread.currentThread().getStackTrace()[2],
              cancelledOrder);
          ;
          if (cancelledOrder.getParentFill() != null)
            handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
        }
      }
      if (updatedOrders) {
        sortOrders(
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(FillType.STOP_LIMIT),
            TransactionType.SELL,
            market,
            descendingStopPriceComparator);
        sortOrders(
            trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL),
            TransactionType.SELL,
            market,
            ascendingTrailingStopPriceComparator);
      }
    }

    /*
     * if ((triggerOrders.get(market) != null && triggerOrders.get(market).get(TransactionType.SELL) != null && triggerOrders.get(market)
     * .get(TransactionType.SELL).get(parentKey) != null) && triggerOrders.get(market).get(TransactionType.SELL).get(parentKey).isEmpty())
     * triggeredParents.add(parentKey);
     */

    // triggerOrderLock.lock();
    unlockTriggerOrders();
  }

  private DiscreteAmount getBestLimitPriceForOrder(Order pendingOrder) {
    DiscreteAmount bestLastPrice;
    Market market = (Market) pendingOrder.getMarket();
    if (pendingOrder.getExecutionInstruction() == null
        && (pendingOrder.getExecutionInstruction() != null
            && pendingOrder.getExecutionInstruction() == (ExecutionInstruction.TAKER))) {

      bestLastPrice =
          (pendingOrder.isAsk())
              ? (quotes.getLastBidForMarket(pendingOrder.getMarket()) == null
                      || quotes.getLastBidForMarket(pendingOrder.getMarket()).getPriceCount() == 0L
                  ? quotes.getImpliedBestBidForListing(market.getListing()).getPrice()
                  : quotes.getLastBidForMarket(pendingOrder.getMarket()).getPrice())
              : (quotes.getLastAskForMarket(pendingOrder.getMarket()) == null
                      || quotes.getLastAskForMarket(pendingOrder.getMarket()).getPriceCount() == 0L
                  ? quotes.getImpliedBestAskForListing(market.getListing()).getPrice()
                  : quotes.getLastAskForMarket(pendingOrder.getMarket()).getPrice());

      // (direction.isLong()) ? quotes.getLastBidForMarket(pairMarket) :
      // quotes.getLastAskForMarket(pairMarket);
    } else {
      bestLastPrice =
          (pendingOrder.isBid())
              ? (quotes.getLastBidForMarket(pendingOrder.getMarket()) == null
                      || quotes.getLastBidForMarket(pendingOrder.getMarket()).getPriceCount() == 0L
                  ? quotes.getImpliedBestBidForListing(market.getListing()).getPrice()
                  : quotes.getLastAskForMarket(pendingOrder.getMarket()).getPrice().decrement())
              : (quotes.getLastAskForMarket(pendingOrder.getMarket()) == null
                      || quotes.getLastAskForMarket(pendingOrder.getMarket()).getPriceCount() == 0L
                  ? quotes.getImpliedBestAskForListing(market.getListing()).getPrice()
                  : quotes.getLastBidForMarket(pendingOrder.getMarket()).getPrice().increment());
    }

    if (bestLastPrice.getCount() == 0 || bestLastPrice.getCount() == Long.MAX_VALUE) {
      log.info(
          "BasedOrderSerivce - UpdateRestingOrdsers: Unable to set limit prices for market "
              + pendingOrder.getMarket()
              + " using best offer"
              + bestLastPrice
              + " for order "
              + pendingOrder
              + " with state"
              + (orderStateMap.get(pendingOrder) == null
                  ? pendingOrder.getOrderState()
                  : orderStateMap.get(pendingOrder)));

      return null;
    }
    // only update on every 2nd tick.

    DiscreteAmount limitPriceDiscrete =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(
                bestLastPrice.asBigDecimal(), pendingOrder.getMarket().getPriceBasis()),
            pendingOrder.getMarket().getPriceBasis());

    return limitPriceDiscrete;
  }

  private SpecificOrder replaceOrder(
      SpecificOrder pendingOrder, DiscreteAmount limitPrice, FillType fillType)
      throws IllegalStateException, Throwable {

    return replaceOrder(pendingOrder, limitPrice, pendingOrder.getUnfilledVolume(), fillType);
  }

  private boolean cancelAndReplaceOrder(
      SpecificOrder pendingOrder, DiscreteAmount limitPriceDiscrete) {

    log.debug(
        "cancelAndReplaceOrder called from class "
            + Thread.currentThread().getStackTrace()[2]
            + "limitPriceDiscrete="
            + limitPriceDiscrete
            + ", cancelling order : "
            + pendingOrder);

    boolean cancelledAndReplaced = false;

    // if I am buying, then I can buy at current best ask and sell at current best bid

    log.debug(
        "BasedOrderSerivce - UpdateRestingOrdsers: Setting limit prices for market "
            + pendingOrder.getMarket()
            + " using limit price"
            + limitPriceDiscrete
            + " for order "
            + pendingOrder
            + " with state"
            + (orderStateMap.get(pendingOrder) == null
                ? pendingOrder.getOrderState()
                : orderStateMap.get(pendingOrder)));

    try {
      if (getOrderState(pendingOrder).isOpen()) {
        Collection<SpecificOrder> pendingOrders =
            (pendingOrder.isBid())
                ? getPendingLongOrders(pendingOrder.getMarket())
                : getPendingShortOrders(pendingOrder.getMarket());
        Amount workingVolume = pendingOrder.getUnfilledVolume();
        for (SpecificOrder workingOrder : pendingOrders)
          workingVolume = workingVolume.plus(workingOrder.getUnfilledVolume());

        log.debug(
            this.getClass().getSimpleName()
                        + ":updateRestingOrders - At "
                        + context.getTime()
                        + " replacing existing market order with fill type "
                        + pendingOrder.getFillType()
                        + " expirty time "
                        + pendingOrder.getExpiryTime()
                    == null
                ? "null"
                : pendingOrder.getExpiryTime() + ":" + pendingOrder);
        SpecificOrder replaceOrder = specificOrderFactory.create(pendingOrder);
        //   replacingOrderLock.lock();
        // let's place the new order first!

        replaceOrder.setPlacementCount(0);
        if (limitPriceDiscrete == null) {
          replaceOrder.withFillType(FillType.MARKET);
          replaceOrder.withExecutionInstruction(ExecutionInstruction.TAKER);

        } else {
          replaceOrder.withFillType(FillType.LIMIT);
          replaceOrder.withLimitPrice(limitPriceDiscrete);
        }
        replaceOrder.persit();
        updateOrderState(replaceOrder, OrderState.NEW, true);
        // }
        log.debug(
            "At"
                + context.getTime()
                + " placing replacment market order :"
                + replaceOrder
                + " for order"
                + pendingOrder);
        // So order is placed,

        // chicken and egg situation.
        // We need to cancel and replace the order
        // but the order might be filled and we might place too much quantity.
        // only once the order is cancelled will we really know how much to place.
        // but then we might not be able to place a new order, so we will place the old one
        // back.

        if (handleCancelSpecificOrder(pendingOrder)) {
          log.info(
              this.getClass().getSimpleName()
                  + ":updateRestingOrders - Cancelled  Orders "
                  + pendingOrder
                  + " with state "
                  + getOrderState(pendingOrder));
          // order the order counf is less thna miniumum size
          double minOrderSize =
              pendingOrder.getMarket().getMinimumOrderSize(pendingOrder.getMarket());
          long minOrderSizeCount =
              (long) (minOrderSize * (1 / pendingOrder.getMarket().getVolumeBasis()));

          if (pendingOrder.getUnfilledVolume().getCount() == 0
              || (Math.abs(pendingOrder.getUnfilledVolume().getCount()) < minOrderSizeCount
                  && pendingOrder.getPositionEffect().equals(PositionEffect.OPEN))) {
            log.info(
                this.getClass().getSimpleName()
                    + ":updateRestingOrders - Skipping replacing pending order "
                    + pendingOrder
                    + " with state "
                    + getOrderState(pendingOrder)
                    + " as fully filled or working volume is less than mininum order size "
                    + minOrderSize
                    + ". Cancelled replaceOrder "
                    + replaceOrder);
            updateOrderState(replaceOrder, OrderState.CANCELLED, true);
            return cancelledAndReplaced;
          }
          // TODO Once we get the fill quantity of the order, the unfilled voume might be
          // less then our minimum order size!
          replaceOrder.setVolumeCount(pendingOrder.getUnfilledVolume().getCount());
          replaceOrder.setUnfilledVolumeCount(pendingOrder.getUnfilledVolume().getCount());
          replaceOrder.merge();
          if (placeOrder(replaceOrder))
            log.debug(
                this.getClass().getSimpleName()
                    + ":updateRestingOrders - Replaced market order with state : "
                    + getOrderState(pendingOrder)
                    + " "
                    + pendingOrder
                    + " with "
                    + replaceOrder);
          else {
            log.debug(
                this.getClass().getSimpleName()
                    + ":updateRestingOrders - Unable to replace market order with state : "
                    + getOrderState(pendingOrder)
                    + " "
                    + pendingOrder
                    + " with "
                    + replaceOrder
                    + " setting limit price to pending order limit price "
                    + pendingOrder.getLimitPrice());
            replaceOrder.setLimitPriceCount(pendingOrder.getLimitPriceCount());
            if (placeOrder(replaceOrder)) {
              log.debug(
                  this.getClass().getSimpleName()
                      + ":updateRestingOrders - Replaced market order with state : "
                      + getOrderState(pendingOrder)
                      + " "
                      + pendingOrder
                      + " with "
                      + replaceOrder);
              cancelledAndReplaced = true;
            } else
              log.error(
                  this.getClass().getSimpleName()
                      + ":updateRestingOrders - Unable to place replacement order: "
                      + replaceOrder
                      + " with state "
                      + getOrderState(replaceOrder));
          }
        } else {
          log.error(
              this.getClass().getSimpleName()
                  + ":updateRestingOrders - Unable to cancel pending order: "
                  + pendingOrder
                  + " with state "
                  + getOrderState(pendingOrder)
                  + ". Cancelled replaceOrder "
                  + replaceOrder);
          updateOrderState(replaceOrder, OrderState.CANCELLED, true);
        }
      }

    } catch (Throwable e) {

      try {
        updateOrderState(pendingOrder, getOrderStateFromOrderService(pendingOrder), true);
        log.error(
            "updateRestingOrders: Unable to place replacment old order  "
                + pendingOrder
                + " with state "
                + getOrderState(pendingOrder),
            e);

      } catch (Throwable e1) {
        // TODO Auto-generated catch block
        log.error(
            "updateRestingOrders: Unable to place replacment old order  "
                + pendingOrder
                + " with state "
                + getOrderState(pendingOrder)
                + " with replacement order ",
            e1);
        //    throw e1;

      }
      // so we hae rejected replaceOrder but don't know state of pendingOrder.

    }
    return cancelledAndReplaced;
  }

  private SpecificOrder replaceOrder(
      SpecificOrder pendingOrder,
      DiscreteAmount limitPrice,
      DiscreteAmount volume,
      FillType fillType)
      throws IllegalStateException, Throwable {
    SpecificOrder replaceOrder = null;
    if (getOrderState(pendingOrder).isOpen()) {
      replaceOrder = specificOrderFactory.create(pendingOrder);
      replaceOrder.setTime(context.getTime());
      //   replacingOrderLock.lock();
      // let's place the new order first!
      replaceOrder.setTimeToLive(
          Math.max(
              replaceOrder.getTimeToLive()
                  - (pendingOrder.getTime().getMillis() - replaceOrder.getTime().getMillis()),
              0));
      replaceOrder.setPlacementCount(0);
      replaceOrder.withFillType(fillType);
      DiscreteAmount limitPriceDescrete =
          new DiscreteAmount(
              DiscreteAmount.roundedCountForBasis(
                  limitPrice.asBigDecimal(), replaceOrder.getMarket().getPriceBasis()),
              replaceOrder.getMarket().getPriceBasis());
      replaceOrder.withLimitPrice(limitPriceDescrete);
      DiscreteAmount volumeDiscrete =
          new DiscreteAmount(
              DiscreteAmount.roundedCountForBasis(
                  volume.asBigDecimal(), replaceOrder.getMarket().getVolumeBasis()),
              replaceOrder.getMarket().getVolumeBasis());
      replaceOrder.withVolume(volumeDiscrete);
      if (pendingOrder.getLimitPrice().equals(replaceOrder.getLimitPrice())
          && pendingOrder.getVolume().equals(replaceOrder.getVolume())
          && pendingOrder.getFillType().equals(replaceOrder.getFillType())) {
        log.debug(
            "At"
                + context.getTime()
                + " replacment  order :"
                + replaceOrder
                + " and order"
                + pendingOrder
                + " have same volume, filltype and price so not replacing");
        return null;
      }
      replaceOrder.persit();
      updateOrderState(replaceOrder, OrderState.NEW, true);
      // }
      log.debug(
          "At"
              + context.getTime()
              + " placing replacment  order :"
              + replaceOrder
              + " for order"
              + pendingOrder);
      // So order is placed,

      // chicken and egg situation.
      // We need to cancel and replace the order
      // but the order might be filled and we might place too much quantity.
      // only once the order is cancelled will we really know how much to place.
      // but then we might not be able to place a new order, so we will place the old one back.
      DiscreteAmount unfilledVolume = pendingOrder.getUnfilledVolume();
      if (handleCancelSpecificOrder(pendingOrder)) {
        log.info(
            this.getClass().getSimpleName()
                + ":replaceOrder - Cancelled  Orders "
                + pendingOrder
                + " with state "
                + getOrderState(pendingOrder));
        // order the order counf is less thna miniumum size
        double minOrderSize =
            pendingOrder.getMarket().getMinimumOrderSize(pendingOrder.getMarket());
        long minOrderSizeCount =
            (long) (minOrderSize * (1 / pendingOrder.getMarket().getVolumeBasis()));
        DiscreteAmount unfilledVolumeChange =
            (DiscreteAmount) unfilledVolume.minus(pendingOrder.getUnfilledVolume());
        DiscreteAmount updatedVolume = (DiscreteAmount) volumeDiscrete.minus(unfilledVolumeChange);
        if (updatedVolume.times(volumeDiscrete, Remainder.ROUND_EVEN).compareTo(DecimalAmount.ZERO)
                <= 0
            || (Math.abs(pendingOrder.getUnfilledVolume().getCount()) < minOrderSizeCount
                && pendingOrder.getPositionEffect().equals(PositionEffect.OPEN))) {
          log.info(
              this.getClass().getSimpleName()
                  + ":replaceOrder - Skipping replacing pending order "
                  + pendingOrder
                  + " with state "
                  + getOrderState(pendingOrder)
                  + " as fully filled or working volume is less than mininum order size "
                  + minOrderSize
                  + ". Cancelled replaceOrder "
                  + replaceOrder);
          updateOrderState(replaceOrder, OrderState.CANCELLED, true);
          return null;
        }
        // TODO Once we get the fill quantity of the order, the unfilled voume might be less then
        // our minimum order size!
        replaceOrder.setVolumeCount(updatedVolume.getCount());
        //  replaceOrder.setUnfilledVolumeCount(pendingOrder.getUnfilledVolume().getCount());
        replaceOrder.merge();
        if (placeOrder(replaceOrder))
          log.debug(
              this.getClass().getSimpleName()
                  + ":replaceOrder - Replaced market order with state : "
                  + getOrderState(pendingOrder)
                  + " "
                  + pendingOrder
                  + " with "
                  + replaceOrder);
        else {
          log.debug(
              this.getClass().getSimpleName()
                  + ":replaceOrder - Unable to replace market order with state : "
                  + getOrderState(pendingOrder)
                  + " "
                  + pendingOrder
                  + " with "
                  + replaceOrder
                  + " setting limit price to pending order limit price "
                  + pendingOrder.getLimitPrice());
          replaceOrder.setLimitPriceCount(pendingOrder.getLimitPriceCount());
          if (placeOrder(replaceOrder))
            log.debug(
                this.getClass().getSimpleName()
                    + ":replaceOrder - Replaced market order with state : "
                    + getOrderState(pendingOrder)
                    + " "
                    + pendingOrder
                    + " with "
                    + replaceOrder);
          else
            log.error(
                this.getClass().getSimpleName()
                    + ":replaceOrder - Unable to place replacement order: "
                    + replaceOrder
                    + " with state "
                    + getOrderState(replaceOrder));
        }
      } else {
        log.error(
            this.getClass().getSimpleName()
                + ":updateRestingOrders - Unable to cancel pending order: "
                + pendingOrder
                + " with state "
                + getOrderState(pendingOrder)
                + ". Cancelled replaceOrder "
                + replaceOrder);
        updateOrderState(replaceOrder, OrderState.CANCELLED, true);
      }
    }
    if (replaceOrder != null && pendingOrder.getLinkedOrder() != null) {
      replaceOrder.setLinkedOrder(pendingOrder.getLinkedOrder());
      // replaceOrder.persit();

      // replaceOrder.withComment(pendingOrder.getLinkedOrder().getId() + "/" +
      // replaceOrder.getDifferentialPrice());
      pendingOrder.getLinkedOrder().setLinkedOrder(replaceOrder);
      pendingOrder.getLinkedOrder().merge();
    }
    return replaceOrder;
  }

  // TODO We need a way to remove any one cancels order parent orders. May be this should be done
  // when the order is triggered and we just add it to the cancelled list.

  // unlockTriggerOrders();

  // when running against mock order serice, we get an npe, so ether trigger orders is empty ot the
  // parnet key is empty

  // triggerOrderLock.unlock();

  // triggerOrders.removeAll(triggeredOrders);
  //  }

  private void removeTriggerOrders(Tradeable market, Collection<Order> triggeredOrdersToRemove) {
    log.debug(
        this.getClass().getSimpleName()
            + ":removeTriggerOrders - called from class "
            + Thread.currentThread().getStackTrace()[2]
            + " with triggeredOrdersToRemove="
            + triggeredOrdersToRemove
            + ",market="
            + market);
    if (triggerOrders != null
        && triggerOrders.get(market) != null
        && triggeredOrdersToRemove != null
        && !triggeredOrdersToRemove.isEmpty()) {
      for (Iterator<Double> itti = triggerOrders.get(market).keySet().iterator();
          itti.hasNext(); ) {
        Double interval = itti.next();
        for (Iterator<TransactionType> ittt =
                triggerOrders.get(market).get(interval).keySet().iterator();
            ittt.hasNext(); ) {
          TransactionType transactionType = ittt.next();
          // Loop over each fill type and remove the orders from it
          for (Iterator<FillType> itf =
                  triggerOrders.get(market).get(interval).get(transactionType).keySet().iterator();
              itf.hasNext(); ) {
            FillType fillType = itf.next();
            if (triggerOrders.get(market).get(interval).get(transactionType).get(fillType) != null
                && !triggerOrders
                    .get(market)
                    .get(interval)
                    .get(transactionType)
                    .get(fillType)
                    .isEmpty()) {
              log.debug(
                  this.getClass().getSimpleName()
                      + ":removeTriggerOrders - Attepting to remove "
                      + triggeredOrdersToRemove
                      + " from "
                      + market
                      + " "
                      + interval
                      + "  "
                      + transactionType
                      + " "
                      + fillType
                      + " trigger orders");
              synchronized (
                  triggerOrders.get(market).get(interval).get(transactionType).get(fillType)) {
                triggerOrders
                    .get(market)
                    .get(interval)
                    .get(transactionType)
                    .get(fillType)
                    .removeAll(triggeredOrdersToRemove);
              }
            }
          }
        }
      }
    }

    if (trailingTriggerOrders != null
        && trailingTriggerOrders.get(market) != null
        && triggeredOrdersToRemove != null
        && !triggeredOrdersToRemove.isEmpty()) {
      for (Iterator<Double> itti = trailingTriggerOrders.get(market).keySet().iterator();
          itti.hasNext(); ) {
        Double interval = itti.next();
        for (Iterator<TransactionType> ittt =
                trailingTriggerOrders.get(market).get(interval).keySet().iterator();
            ittt.hasNext(); ) {
          TransactionType transactionType = ittt.next();

          // Loop over each fill type and remove the orders from it
          if (trailingTriggerOrders.get(market).get(interval).get(transactionType) != null
              && !trailingTriggerOrders.get(market).get(interval).get(transactionType).isEmpty()) {
            log.debug(
                this.getClass().getSimpleName()
                    + ":removeTriggerOrders - Attepting to remove "
                    + triggeredOrdersToRemove
                    + " from "
                    + market
                    + " "
                    + interval
                    + "  "
                    + transactionType
                    + " trailingTriggerOrders orders");

            synchronized (trailingTriggerOrders.get(market).get(interval).get(transactionType)) {
              trailingTriggerOrders
                  .get(market)
                  .get(interval)
                  .get(transactionType)
                  .removeAll(triggeredOrdersToRemove);
            }
          }
        }
      }
    }
  }

  public static DiscreteAmount getPercentageOfVolume(Order order, Market market, Bar bar) {
    DiscreteAmount marketVolume =
        new DiscreteAmount(
            (order.isBid()
                ? (bar.getBuyVolume().longValue() == 0
                    ? bar.getSellVolume().longValue()
                    : bar.getBuyVolume().longValue())
                : (bar.getSellVolume().longValue() == 0
                    ? bar.getBuyVolume().longValue()
                    : bar.getSellVolume().longValue())),
            market.getVolumeBasis());

    DiscreteAmount targetVolume =
        marketVolume
            .abs()
            .times(order.getPercentageOfVolume(), Remainder.ROUND_EVEN)
            .toBasis(market.getVolumeBasis(), Remainder.ROUND_CEILING);

    DiscreteAmount outstandingOrderVolume =
        order.getUnfilledVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);

    DiscreteAmount volume =
        targetVolume
            .times(0.5, Remainder.ROUND_CEILING)
            .plus(targetVolume.times((1 - 0.5) * Math.random(), Remainder.ROUND_CEILING))
            .toBasis(market.getVolumeBasis(), Remainder.DISCARD);
    if (order.isAsk()) {
      volume = (DiscreteAmount) volume.abs().negate();
      volume = volume.compareTo(outstandingOrderVolume) < 0 ? outstandingOrderVolume : volume;
    } else {
      volume = volume.compareTo(outstandingOrderVolume) > 0 ? outstandingOrderVolume : volume;
    }

    log.debug(
        // this.getClass().getSimpleName() +
        ":getPercentageOfVolume - calculated volume:"
            + volume
            + " from percentageOfVolume="
            + order.getPercentageOfVolume()
            + ",  volume="
            + volume
            + ",  marketVolume="
            + marketVolume
            + ",  outstandingOrderVolume="
            + outstandingOrderVolume
            + ",volumeBasis="
            + market.getVolumeBasis());
    return volume;
  }

  private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, Market market) {
    if (generalOrder.getVolume().isZero()) return null;
    DiscreteAmount volume;
    if (generalOrder.getTargetStrategy() != null
        && generalOrder.getTargetStrategy().equals(TargetStrategy.PERCENTAGEOFVOLUME)
        && generalOrder.getPercentageOfVolume() != null
        && generalOrder.getPercentageOfVolumeInterval() != null) {
      Bar POVBar = quotes.getLastBar(market, generalOrder.getPercentageOfVolumeInterval());
      volume = getPercentageOfVolume(generalOrder, market, POVBar);
    } else {
      volume = generalOrder.getUnfilledVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);
    }
    if (volume == null || volume.isZero()) {
      log.warn(
          "convertGeneralOrderToSpecific - convertGeneralOrderToSpecific: Unable to create specific order from general order "
              + generalOrder);
      return null;
    }
    DiscreteAmount discreteLimit;
    DiscreteAmount discreteMarket;
    DiscreteAmount discreteStop = null;
    RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
    DecimalAmount limitPrice;
    if (generalOrder.getTargetPrice() != null) limitPrice = generalOrder.getTargetPrice();
    else limitPrice = generalOrder.getLimitPrice();

    final DecimalAmount marketPrice = generalOrder.getMarketPrice();

    final DecimalAmount stopPrice = generalOrder.getStopPrice();
    final DecimalAmount trailingStopPrice = generalOrder.getTrailingStopPrice();
    // DecimalAmount paretnFill = generalOrder.getTrailingStopPrice();

    // the volume will already be negative for a sell order

    SpecificOrder specificOrder =
        specificOrderFactory.create(
            context.getTime(),
            generalOrder.getPortfolio(),
            market,
            volume,
            generalOrder,
            generalOrder.getComment());

    // Market markettest = specificOrder.getMarket();
    specificOrder.withOrderGroup(generalOrder.getOrderGroup());
    specificOrder.withUsePosition(generalOrder.getUsePosition());
    specificOrder.withPositionEffect(generalOrder.getPositionEffect());
    specificOrder.withExecutionInstruction(generalOrder.getExecutionInstruction());
    specificOrder.withTimeToLive(generalOrder.getTimeToLive());
    specificOrder.withMinimumPricePercentage(generalOrder.getMinimumPricePercentage());

    specificOrder.withPayUpTicks(generalOrder.getPayUpTicks());
    specificOrder.withLegNumber(generalOrder.getLegNumber());
    if (generalOrder.getDifferentialPriceDecimal() != null)
      specificOrder.withDifferentialPrice(generalOrder.getDifferentialPriceDecimal());

    specificOrder.withRatioQuantity(generalOrder.getRatioQuantity());
    specificOrder.withPercentageOfVolume(generalOrder.getPercentageOfVolume());
    specificOrder.withPercentageOfVolumeInterval(generalOrder.getPercentageOfVolumeInterval());
    specificOrder.withTargetStrategy(generalOrder.getTargetStrategy());

    if (generalOrder.getLinkedOrder() != null)
      for (Order linkedOrder : generalOrder.getLinkedOrder().getOrderChildren()) {
        if (linkedOrder.getPositionEffect().equals(specificOrder.getPositionEffect())) {
          if (linkedOrder instanceof SpecificOrder) {
            specificOrder.persit();
            linkedOrder.setLinkedOrder(specificOrder);
            linkedOrder.persit();
            specificOrder.setLinkedOrder(linkedOrder);
            specificOrder.merge();

            if (linkedOrder.hasChildren()) {
              DecimalAmount peggedPrice = linkedOrder.getAverageFillPrice();
              limitPrice =
                  DecimalAmount.of(
                      specificOrder.getLegNumber() == 1
                          ? peggedPrice.plus(specificOrder.getDifferentialPrice())
                          : peggedPrice.minus(specificOrder.getDifferentialPrice()));

              DiscreteAmount discretePeggedPrice =
                  peggedPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
              specificOrder.withPeggedPrice(discretePeggedPrice);

            } else
              limitPrice =
                  DecimalAmount.of(
                      specificOrder.getLegNumber() == 1
                          ? linkedOrder.getLimitPrice().plus(specificOrder.getDifferentialPrice())
                          : linkedOrder
                              .getLimitPrice()
                              .minus(specificOrder.getDifferentialPrice()));
            // specificOrder.withComment(linkedOrder.getLinkedOrder().getId() + "/" +
            // specificOrder.getDifferentialPrice());
            // linkedOrder.withComment(specificOrder.getLinkedOrder().getId() + "/" +
            // linkedOrder.getDifferentialPrice());
          }
        }
      }
    if (limitPrice != null) specificOrder.withFillType(FillType.LIMIT);
    else specificOrder.withFillType(FillType.MARKET);

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
        generalOrder.setLastBestPriceDecimal(discreteLimit.asBigDecimal());

        break;
      case TRAILING_UNREALISED_STOP_LIMIT:
      case TRAILING_STOP_LIMIT:
      case REENTRANT_TRAILING_STOP_LIMIT:
      case STOP_LIMIT:
        // we will put the stop order in at best bid or best ask
        SpecificOrder stopOrder = specificOrder;
        //  stopOrder.persit();
        if (stopOrder.isBid())
          discreteStop =
              stopOrder.getExecutionInstruction() == (ExecutionInstruction.TAKER)
                  ? (quotes.getLastAskForMarket(stopOrder.getMarket()) == null
                      ? quotes.getLastTrade(stopOrder.getMarket()).getPrice()
                      : quotes.getLastAskForMarket(stopOrder.getMarket()).getPrice())
                  : (quotes.getLastBidForMarket(stopOrder.getMarket()) == null
                      ? quotes.getLastTrade(stopOrder.getMarket()).getPrice()
                      : quotes.getLastBidForMarket(stopOrder.getMarket()).getPrice());
        if (stopOrder.isAsk())
          discreteStop =
              stopOrder.getExecutionInstruction() == (ExecutionInstruction.TAKER)
                  ? (quotes.getLastAskForMarket(stopOrder.getMarket()) == null
                      ? quotes.getLastTrade(stopOrder.getMarket()).getPrice()
                      : quotes.getLastAskForMarket(stopOrder.getMarket()).getPrice())
                  : (quotes.getLastBidForMarket(stopOrder.getMarket()) == null
                      ? quotes.getLastTrade(stopOrder.getMarket()).getPrice()
                      : quotes.getLastBidForMarket(stopOrder.getMarket()).getPrice());
        if (discreteStop.isMax() || discreteStop.isMin() || discreteStop.isZero())
          discreteStop = limitPrice.toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN);
        if (discreteStop == null) break;
        // discreteStop = offer.getPrice();
        if (limitPrice != null) {
          discreteLimit =
              volume.isNegative() ? discreteStop.decrement(4) : discreteStop.increment(4);
          specificOrder.withLimitPrice(discreteLimit);
          generalOrder.setLastBestPriceDecimal(discreteLimit.asBigDecimal());

        } else {
          discreteMarket = marketPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
          specificOrder.withMarketPrice(discreteMarket);
          generalOrder.setLastBestPriceDecimal(discreteMarket.asBigDecimal());
        }

        break;
      case TRAILING_STOP_LOSS:
      case REENTRANT_TRAILING_STOP_LOSS:
      case TRAILING_UNREALISED_STOP_LOSS:
      case STOP_LOSS:
      case TIMED_EXIT:
      case REENTRANT_STOP_LOSS:
        if (limitPrice != null) {
          discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
          specificOrder.withLimitPrice(discreteLimit);
          generalOrder.setLastBestPriceDecimal(discreteLimit.asBigDecimal());

        } else {
          discreteMarket = marketPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
          specificOrder.withMarketPrice(discreteMarket);
          generalOrder.setLastBestPriceDecimal(discreteMarket.asBigDecimal());
        }
        break;
    }
    switch (generalOrder.getContingencyType()) {
      case ONE_CANCELS_OTHER:
        //  builder.withFillType(FillType.ONE_CANCELS_OTHER);
        if (limitPrice != null) {
          discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
          specificOrder.withLimitPrice(discreteLimit);
          generalOrder.setLastBestPriceDecimal(discreteLimit.asBigDecimal());
        } else {
          discreteMarket = marketPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
          specificOrder.withMarketPrice(discreteMarket);
          generalOrder.setLastBestPriceDecimal(discreteMarket.asBigDecimal());
        }

        break;
      default:
        break;
    }

    specificOrder.copyCommonOrderProperties(generalOrder);
    // specificOrder.persit();

    log.debug(
        this.getClass().getSimpleName()
            + ":convertGeneralOrderToSpecific - coverneted generalOrder:"
            + generalOrder
            + " to specificOrder"
            + specificOrder);
    return specificOrder;
  }

  protected void reject(Order order, String message) {
    log.warn("Order " + order + " rejected: " + message);
    updateOrderState(order, OrderState.REJECTED, true);
  }

  protected void error(Order order, String message) {
    log.error("Order " + order + " errored: " + message);
    updateOrderState(order, OrderState.ERROR, true);
  }

  protected abstract void handleSpecificOrder(SpecificOrder specificOrder) throws Throwable;

  protected abstract OrderState getOrderStateFromOrderService(Order order) throws Throwable;

  protected abstract boolean specificOrderToCancel(SpecificOrder specificOrder) throws Throwable;

  protected boolean cancelSpecificOrder(SpecificOrder order) throws Throwable {
    // get the executor service

    if (order.getMarket().isSynthetic()) return specificOrderToCancel(order);
    else {
      if (!specificOrderToCancel(order)) {

        // String configPrefix = "xchange." +
        // order.getMarket().getExchange().toString().toLowerCase();

        // int dealyPeriod = ConfigUtil.combined().getInt(configPrefix + ".cancel.period", 5);
        // int attempts = ConfigUtil.combined().getInt(configPrefix + ".cancel.attempts", 5);

        //	log.error(this.getClass().getSimpleName() + "- cancelSpecificOrder: Unable to cancel" +
        // order
        //			+ " on first attempt. Handing off to background thread to attempt " + attempts + "
        // times at " + dealyPeriod + " second intervals.");
        log.error(
            this.getClass().getSimpleName()
                + "- cancelSpecificOrder: Unable to cancel"
                + order
                + " with state "
                + (orderStateMap.get(order) == null
                    ? order.getOrderState()
                    : orderStateMap.get(order)));
        // exchangeCancellationPool.get(order.getMarket().getExchange()).submit(new
        // CancelOrderRunnable(order, dealyPeriod, attempts));

        return false;
      } else return true;
    }
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllSpecificOrders(
      Portfolio portfolio, Market market) {
    Collection<SpecificOrder> ordersToCancel = new ArrayList<>();
    if (market == null || portfolio == null) return ordersToCancel;
    // synchronized (lock) {
    // for (Order order : getPendingOrders()) {
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      //  for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
      //    Order order = it.next();
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && orderStateMap.get(specificOrder) != null
            && orderStateMap.get(specificOrder).isOpen()) {
          ordersToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(ordersToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  // cancelledOrders.add(specificOrder);
  // updateOrderState(specificOrder, OrderState.CANCELLING);

  //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
  //              SpecificOrder specificOrder = it.next();
  //
  //
  //          }
  //  }

  @Override
  public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio, Market market) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;
        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingOrders(
      Portfolio portfolio, Market market, double interval) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;
        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getOrderGroup() == (interval)) {

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
      if (order.getFills() != null
          && !order.getFills().isEmpty()
          && order.getPortfolio().equals(portfolio)
          && order instanceof SpecificOrder) {

        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getMarket().equals(market)) portfolioFills.addAll(pendingOrder.getFills());
      }
    }
    Collections.sort(portfolioFills, timeOrderIdComparator);
    return portfolioFills;
    // return portfolioFills;

    //  return null;

  }

  @Override
  public Collection<SpecificOrder> getPendingLongOpenOrders(Portfolio portfolio, Market market) {
    List<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;

    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getPositionEffect() == (PositionEffect.OPEN)
            && pendingOrder.isBid()) {

          if (!portfolioPendingOrders.contains(pendingOrder))
            portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //     log.trace("getPendingLongOpenOrders pending long open orders : " +
    // portfolioPendingOrders);
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingLongOpenOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

    if (market == null || portfolio == null) return portfolioPendingOrders;

    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getOrderGroup() == (orderGroup)
            && pendingOrder.getPositionEffect() == (PositionEffect.OPEN)
            && pendingOrder.isBid()) {

          if (!portfolioPendingOrders.contains(pendingOrder))
            portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //     log.trace("getPendingLongOpenOrders pending long open orders : " +
    // portfolioPendingOrders);
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingLongOrders() {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.isBid()) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //    log.trace("getPendingLongOrders pending long open orders : " + portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingLongOrders(Tradeable tradeable) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (tradeable == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.isBid() && pendingOrder.equals(tradeable)) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //    log.trace("getPendingLongOrders pending long open orders : " + portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortOrders(Tradeable tradeable) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (tradeable == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.isAsk() && pendingOrder.equals(tradeable)) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //    log.trace("getPendingLongOrders pending long open orders : " + portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, Market market) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;

    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isAsk()) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //    log.trace("getPendingLongCloseOrders pending long close orders : " +
    // portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingLongCloseOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;

    for (Order order : getPendingOrders()) {

      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getOrderGroup() == (orderGroup)
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isAsk()) {

          portfolioPendingOrders.add(pendingOrder);
        } else {
          // need to get all child orders thfill
        }
      }
    }
    //    log.trace("getPendingLongCloseOrders pending long close orders : " +
    // portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortOpenOrders(Portfolio portfolio, Market market) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getPositionEffect() == (PositionEffect.OPEN)
            && pendingOrder.isAsk()) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //      log.trace("getPendingShortOpenOrders pending short open orders : " +
    // portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortOpenOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getOrderGroup() == orderGroup
            && pendingOrder.getPositionEffect() == (PositionEffect.OPEN)
            && pendingOrder.isAsk()) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //      log.trace("getPendingShortOpenOrders pending short open orders : " +
    // portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortOrders() {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.isAsk()) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }

    // log.debug("trace" + log.isTraceEnabled());
    // log.debug("trace" + log.isTraceEnabled());
    // log.trace("getPendingShortOrders pending short open orders : " + portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, Market market) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isBid()) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //    log.trace("getPendingShortCloseOrders pending short close orders : " +
    // portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortCloseOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getOrderGroup() == orderGroup
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isBid()) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    //    log.trace("getPendingShortCloseOrders pending short close orders : " +
    // portfolioPendingOrders);

    return portfolioPendingOrders;
  }

  @Override
  public Collection<GeneralOrder> getPendingLongOpenTriggerOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<GeneralOrder> portfolioPendingTriggerOrders = new ArrayList<GeneralOrder>();
    if (market == null || portfolio == null) return portfolioPendingTriggerOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {

    log.debug(
        "{}: getPendingLongOpenTriggerOrders to called from stack {} with portfolio={}/{} ,market={}/{} ,orderGroup={}/{} ",
        this.getClass().getSimpleName(),
        Thread.currentThread().getStackTrace()[2],
        portfolio,
        System.identityHashCode(portfolio),
        market,
        System.identityHashCode(market),
        orderGroup,
        System.identityHashCode(orderGroup));

    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingTriggerOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return portfolioPendingTriggerOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder instanceof GeneralOrder
                && triggerOrder.getPositionEffect().equals(PositionEffect.OPEN))
              if (!portfolioPendingTriggerOrders.contains(triggerOrder))
                portfolioPendingTriggerOrders.add((GeneralOrder) triggerOrder);
          }
        }
      }
    }
    Collections.sort(portfolioPendingTriggerOrders, parentFillTimeComparator);
    return portfolioPendingTriggerOrders;
  }

  @Override
  public Collection<GeneralOrder> getPendingLongCloseTriggerOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<GeneralOrder> portfolioPendingTriggerOrders = new ArrayList<GeneralOrder>();
    if (market == null || portfolio == null) return portfolioPendingTriggerOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {

    log.debug(
        "{}: getPendingLongCloseTriggerOrders to called from stack {} with portfolio={}/{} ,market={}/{} ,orderGroup={}/{} ",
        this.getClass().getSimpleName(),
        Thread.currentThread().getStackTrace()[2],
        portfolio,
        System.identityHashCode(portfolio),
        market,
        System.identityHashCode(market),
        orderGroup,
        System.identityHashCode(orderGroup));

    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingTriggerOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return portfolioPendingTriggerOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder instanceof GeneralOrder
                && triggerOrder.getPositionEffect().equals(PositionEffect.CLOSE))
              if (!portfolioPendingTriggerOrders.contains(triggerOrder))
                portfolioPendingTriggerOrders.add((GeneralOrder) triggerOrder);
          }
        }
      }
    }
    Collections.sort(portfolioPendingTriggerOrders, parentFillTimeComparator);
    return portfolioPendingTriggerOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingCloseOrders(Portfolio portfolio, Market market) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;

        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllShortClosingSpecificOrders(
      Portfolio portfolio, Market market) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    if (market == null || portfolio == null) return orderToCancel;
    //   synchronized (lock) {
    //  for (Order order:getPendingOrders()) {
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      // for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
      //   Order order = it.next();
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && specificOrder.isBid()
            && (specificOrder.getExecutionInstruction() == null
                || (specificOrder.getExecutionInstruction() != null))) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllShortClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }

    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllShortClosingSpecificOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    if (market == null || portfolio == null) return orderToCancel;
    //   synchronized (lock) {
    //  for (Order order:getPendingOrders()) {
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      // for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
      //   Order order = it.next();
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && specificOrder.getOrderGroup() == orderGroup
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && specificOrder.isBid()
            && (specificOrder.getExecutionInstruction() == null
                || (specificOrder.getExecutionInstruction() != null))) {
          orderToCancel.add(specificOrder);
          log.info(
              "handleCancelAllShortClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }

    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllShortClosingSpecificOrders(
      Portfolio portfolio, Market market, ExecutionInstruction execInst) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    if (market == null || portfolio == null) return orderToCancel;
    //   synchronized (lock) {
    //  for (Order order:getPendingOrders()) {
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      // for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
      //   Order order = it.next();
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && specificOrder.isBid()
            && (specificOrder.getExecutionInstruction() == null
                || (specificOrder.getExecutionInstruction() != null
                    && specificOrder.getExecutionInstruction().equals(execInst)))) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllShortClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }

    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  protected void handleUpdateSpecificOrderWorkingQuantity(
      SpecificOrder specificOrder, DiscreteAmount quantity) {
    for (Order pendingOrder : getPendingOrders()) {
      if (pendingOrder.equals(specificOrder)) {
        // so we need to ensure that the unfilled wuanity is waulty to the qauntity.

        // 200 lots order, 100 lots fill, want to update to 10 lots, so 110.
        long updatedQuantity =
            (quantity.isNegative())
                ? -1
                    * (Math.abs(specificOrder.getVolume().getCount())
                        - Math.abs(specificOrder.getUnfilledVolumeCount())
                        + Math.abs(quantity.getCount()))
                : Math.abs(specificOrder.getVolume().getCount())
                    - Math.abs(specificOrder.getUnfilledVolumeCount())
                    + Math.abs(quantity.getCount());

        // && pendingOrder.getMarket().equals(market)) {
        specificOrder.setVolumeCount(updatedQuantity);
        specificOrder.setUnfilledVolumeCount(updatedQuantity);
      }
    }
  }

  @Override
  public boolean handleCancelSpecificOrderByParentFill(Fill parentFill) {
    if (parentFill == null) return false;
    Set<Order> allChildOrders = new HashSet<Order>();
    parentFill.getAllSpecificOrdersByParentFill(parentFill, allChildOrders);
    Collection<SpecificOrder> ordersToCancel = new HashSet<SpecificOrder>();
    Collection<SpecificOrder> cancelledOrders;

    for (Order childOrder : allChildOrders) {
      if (childOrder instanceof SpecificOrder
          && orderStateMap.get(childOrder) != null
          && orderStateMap.get(childOrder).isOpen()) ordersToCancel.add((SpecificOrder) childOrder);
    }
    boolean foundOrderToBeCancelled = false;
    if (ordersToCancel == null || ordersToCancel.isEmpty()) {
      return true;
    }

    cancelledOrders = cancelSpecificOrder(ordersToCancel);
    // if all the orders to cancelled have been canclled then return true;
    ORDERSTOCANCELLOOP:
    for (SpecificOrder orderToCancel : ordersToCancel) {
      // if all the orders to are in the cancelledOrders return ture
      foundOrderToBeCancelled = false;

      CANCELLEDORDERSLOOP:
      for (SpecificOrder cancelledOrder : cancelledOrders) {

        if (cancelledOrder.equals(orderToCancel)) {
          log.debug(
              "handleCancelSpecificOrderByParentFill called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " canclled order : "
                  + orderToCancel);

          foundOrderToBeCancelled = true;
          break CANCELLEDORDERSLOOP;
        }
      }
      if (!foundOrderToBeCancelled) {
        log.debug(
            "handleCancelSpecificOrderByParentFill called from class "
                + Thread.currentThread().getStackTrace()[2]
                + " unable to cancel order : "
                + orderToCancel);

        break ORDERSTOCANCELLOOP;
      }
    }
    return foundOrderToBeCancelled;

    //        while (itPending.hasNext()) {
    //            // closing position
    //            SpecificOrder cancelledOrder = itPending.next();
    //
    //            if (cancelledOrder != null && cancelledOrder.getParentFill() != null &&
    // cancelledOrder.getParentFill().equals(parentFill)) {
    //
    //
    //                }
    //
    //            }
    //        }
  }

  @Override
  public boolean handleCancelOrders(Collection<Order> orders) {
    boolean ordersCancelled = true;
    if (orders == null) return ordersCancelled;
    for (Order order : orders) {
      if (order instanceof GeneralOrder) {
        if (!handleCancelGeneralOrder((GeneralOrder) order)) ;
        ordersCancelled = false;
      } else if (order instanceof SpecificOrder) {
        if (!handleCancelSpecificOrder((SpecificOrder) order)) ordersCancelled = false;
      }
    }
    return ordersCancelled;
  }

  @Override
  public boolean handleCancelOrder(Order order) {
    boolean ordersCancelled = true;
    if (order == null) return ordersCancelled;
    if (order instanceof GeneralOrder) {
      if (!handleCancelGeneralOrder((GeneralOrder) order)) ordersCancelled = false;

    } else if (order instanceof SpecificOrder) {
      if (!handleCancelSpecificOrder((SpecificOrder) order)) ordersCancelled = false;
    }

    return ordersCancelled;
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllClosingSpecificOrders(
      Portfolio portfolio, Market market) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    //   synchronized (lock) {
    //  for ( Order order: )

    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      //   for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
      //     Order order = it.next();
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())) {
          // cancelledOrders.add(specificOrder);
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  public static final Comparator<Order> ascendingStopPriceComparator =
      new Comparator<Order>() {
        // user when order is buy (so either a long trigger order or a short exit ord)
        // the trigger orders are highest in the list sorted from hifest to  lowest (
        // sort order from lowest stop price to highest, used when I want to trigger the lowest
        // price frist.
        @Override
        public int compare(Order order, Order order2) {

          if (order2.getStopPrice() == null && order.getStopPrice() == null) {
            return 0;
          }
          // this sort from lowest to highest
          // Null values go to bottom of list.
          if (order.getStopPrice() == null) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return 1;
          }

          // Null values go to bottom of list.
          if (order2.getStopPrice() == null) {
            // || order2.getTargetPrice() == null) {
            // order < order2.
            return -1;
          }
          int pComp;
          // so order one has either a traget price or a stop price as so does order 2
          // let's compare the target pricess

          if (order2.getStopPrice() != null && order.getStopPrice() != null) {
            pComp = order.getStopPrice().compareTo(order2.getStopPrice());
            if (pComp != 0) return pComp;

            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;
            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;

            return System.identityHashCode(order) - System.identityHashCode(order2);
          }

          return 0;

          // we could have order 1 as traget and order 2 as stop

        }
      };

  public static final Comparator<Order> ascendingTargetPriceComparator =
      new Comparator<Order>() {
        // user when order is buy (so either a long trigger order or a short exit ord)
        // the trigger orders are highest in the list sorted from hifest to  lowest (
        // sort order from lowest stop price to highest, used when I want to trigger the lowest
        // price frist.
        @Override
        public int compare(Order order, Order order2) {

          if (order2.getTargetPrice() == null && order.getTargetPrice() == null) {
            return 0;
          }
          // this sort from lowest to highest
          // Null values go to bottom of list.
          if (order.getTargetPrice() == null) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return 1;
          }

          // Null values go to bottom of list.
          if (order2.getTargetPrice() == null) {
            // || order2.getTargetPrice() == null) {
            // order < order2.
            return -1;
          }
          int pComp;
          // so order one has either a traget price or a stop price as so does order 2
          // let's compare the target pricess

          if (order2.getTargetPrice() != null && order.getTargetPrice() != null) {
            pComp = order.getTargetPrice().compareTo(order2.getTargetPrice());
            if (pComp != 0) return pComp;
            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;
            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;
            return System.identityHashCode(order) - System.identityHashCode(order2);
          }

          return 0;

          // we could have order 1 as traget and order 2 as stop

        }
      };
  // sorts the highest to lowest price, so if we are buying, our higest price will get filled first
  public static final Comparator<Order> descendingStopPriceComparator =
      new Comparator<Order>() {
        // sort order from highest stop price to lowest,used when I want to trigger the highest
        // price frist.
        @Override
        public int compare(Order order, Order order2) {
          if (order2.getStopPrice() == null && order.getStopPrice() == null) {
            return 0;
          }
          // this sort from lowest to highest
          // Null values go to bottom of list.
          if (order.getStopPrice() == null) {
            return 1;
          }

          // Null values go to bottom of list.
          if (order2.getStopPrice() == null) {
            return -1;
          }
          int pComp;
          if (order2.getStopPrice() != null && order.getStopPrice() != null) {
            pComp = order2.getStopPrice().compareTo(order.getStopPrice());
            if (pComp != 0) return pComp;

            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;
            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;

            return System.identityHashCode(order) - System.identityHashCode(order2);
          }

          return 0;
        }
      };

  public static final Comparator<Order> descendingTargetPriceComparator =
      new Comparator<Order>() {
        // sort order from highest stop price to lowest,used when I want to trigger the highest
        // price frist.
        @Override
        public int compare(Order order, Order order2) {
          if (order2.getTargetPrice() == null && order.getTargetPrice() == null) {
            return 0;
          }
          // this sort from lowest to highest
          // Null values go to bottom of list.
          if (order.getTargetPrice() == null) {
            return 1;
          }

          // Null values go to bottom of list.
          if (order2.getTargetPrice() == null) {
            return -1;
          }
          int pComp;
          if (order2.getTargetPrice() != null && order.getTargetPrice() != null) {
            pComp = order2.getTargetPrice().compareTo(order.getTargetPrice());
            if (pComp != 0) return pComp;

            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;
            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;
            return System.identityHashCode(order) - System.identityHashCode(order2);
          }

          return 0;
        }
      };

  public static final Comparator<Order> ascendingTrailingStopPriceComparator =
      new Comparator<Order>() {
        // sort order from lowest stop price to highest, used when I want to trigger the lowest
        // price frist.
        @Override
        public int compare(Order order, Order order2) {
          if (order2.getStopPrice() == null && order.getStopPrice() == null) {
            return 0;
          }
          // if the stop price of order is null put to bottom of list.
          if (order.getStopPrice() == null) {
            return -1;
          }
          if (order2.getStopPrice() == null) {
            return 1;
          }
          int pComp = 0;
          if (order.getStopAmount() == null && order2.getStopAmount() == null) {
            // sort as ascending stop prices
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order.getStopPrice()).compareTo(order2.getStopPrice())
                    : (order.getStopPrice()).compareTo(order2.getStopPrice());

          } else if (order.getStopAmount() == null) {
            // sort as one asecnding sided stop
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order.getStopPrice())
                        .compareTo(order2.getStopPrice().minus(order2.getStopAmount()))
                    : (order.getStopPrice())
                        .compareTo(order2.getStopPrice().plus(order2.getStopAmount()));

          } else if (order2.getStopAmount() == null) {
            // sort as acseinding one sided stop
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order.getStopPrice().minus(order.getStopAmount()))
                        .compareTo(order2.getStopPrice())
                    : (order.getStopPrice().plus(order.getStopAmount()))
                        .compareTo(order2.getStopPrice());

          } else
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order.getStopPrice().minus(order.getStopAmount()))
                        .compareTo(order2.getStopPrice().minus(order2.getStopAmount()))
                    : (order.getStopPrice().plus(order.getStopAmount()))
                        .compareTo(order2.getStopPrice().plus(order2.getStopAmount()));
          if (pComp != 0) return pComp;
          int oComp =
              Double.compare(
                  order.getOrderGroup(), order2.getOrderGroup()); // smallet to largest order group
          if (oComp != 0) return oComp;
          int vComp = order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
          if (vComp != 0) return vComp;
          int tComp = order.getTime().compareTo(order2.getTime());
          if (tComp != 0) return tComp;

          return System.identityHashCode(order) - System.identityHashCode(order2);
        }
      };
  // sorts the highest to lowest price, so if we are buying, our higest price will get filled first
  public static final Comparator<Order> descendingTrailingStopPriceComparator =
      new Comparator<Order>() {
        // sort order from highest stop price to lowest,used when I want to trigger the highest
        // price frist.
        @Override
        public int compare(Order order, Order order2) {

          if (order2.getStopPrice() == null && order.getStopPrice() == null) {
            return 0;
          }
          // if the stop price of order is null put to bottom of list.
          if (order.getStopPrice() == null) {
            return -1;
          }
          if (order2.getStopPrice() == null) {
            return 1;
          }
          int pComp = 0;
          if (order.getStopAmount() == null && order2.getStopAmount() == null) {
            // sort as ascending stop prices
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order2.getStopPrice()).compareTo(order.getStopPrice())
                    : (order2.getStopPrice()).compareTo(order.getStopPrice());

          } else if (order.getStopAmount() == null) {
            // sort as one asecnding sided stop
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order2.getStopPrice().minus(order2.getStopAmount()))
                        .compareTo(order.getStopPrice())
                    : (order2.getStopPrice().plus(order2.getStopAmount()))
                        .compareTo(order.getStopPrice());

          } else if (order2.getStopAmount() == null) {
            // sort as acseinding one sided stop
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order2.getStopPrice())
                        .compareTo(order.getStopPrice().minus(order.getStopAmount()))
                    : (order2.getStopPrice())
                        .compareTo(order.getStopPrice().plus(order.getStopAmount()));

          } else
            pComp =
                (order.isBid() && order2.isBid())
                    ? (order2.getStopPrice().minus(order2.getStopAmount()))
                        .compareTo(order.getStopPrice().minus(order.getStopAmount()))
                    : (order2.getStopPrice().plus(order2.getStopAmount()))
                        .compareTo(order.getStopPrice().plus(order.getStopAmount()));
          if (pComp != 0) return pComp;
          int oComp =
              Double.compare(
                  order.getOrderGroup(), order2.getOrderGroup()); // smallet to largest order group
          if (oComp != 0) return oComp;
          int vComp = order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
          if (vComp != 0) return vComp;
          int tComp = order.getTime().compareTo(order2.getTime());
          if (tComp != 0) return tComp;
          return System.identityHashCode(order2) - System.identityHashCode(order);
        }
      };

  public static final Comparator<Amount> ascendingAmountComparator =
      new Comparator<Amount>() {
        // sorts smallest to largest volume
        @Override
        public int compare(Amount amount, Amount amount2) {
          return (amount.compareTo(amount2));
        }
      };
  public static final Comparator<Amount> descendingAmountComparator =
      new Comparator<Amount>() {
        // sorts largest to smallest volume
        @Override
        public int compare(Amount amount, Amount amount2) {

          return (amount2.compareTo(amount));
        }
      };
  // sorts the lowest to highest price so if we are selling, the lowest price will get filled first.
  public static final Comparator<Order> ascendingPriceComparator =
      new Comparator<Order>() {
        // Order fills oldest first (lower time), then have the biggest quanity first to close out.
        // Market orders need to be at top of book order by time, volume, order group.
        @Override
        public int compare(Order order, Order order2) {
          if ((order2.getFillType() == FillType.MARKET && order.getFillType() == FillType.MARKET)) {
            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;
            if (order.getLimitPrice() == null) {
              return -1;
            }
            if (order2.getLimitPrice() == null) {
              return 1;
            }
            int pComp = order.getLimitPrice().compareTo(order2.getLimitPrice());
            if (pComp != 0) return pComp;
            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;
          }
          // this sort from lowest to highest
          // Null values go to bottom of list.
          if (order.getFillType() == FillType.MARKET) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return -1;
          }

          if (order2.getFillType() == FillType.MARKET) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return 1;
          }

          if ((order2.getLimitPrice() == null && order.getLimitPrice() == null)) {
            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;

            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;
          }

          if (order.getLimitPrice() == null) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return -1;
          }

          // Null values go to bottom of list.
          if (order2.getLimitPrice() == null) {
            // || order2.getTargetPrice() == null) {
            // order < order2.
            return 1;
          }
          int pComp = order.getLimitPrice().compareTo(order2.getLimitPrice());
          if (pComp != 0) return pComp;

          int oComp =
              Double.compare(
                  order.getOrderGroup(), order2.getOrderGroup()); // smallet to largest order group
          if (oComp != 0) return oComp;
          int vComp = order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
          if (vComp != 0) return vComp;

          int tComp = order.getTime().compareTo(order2.getTime());
          if (tComp != 0) return tComp;

          return System.identityHashCode(order) - System.identityHashCode(order2);
        }
      };
  // sorts the highest to lowest price, so if we are buying, our higest price will get filled first
  public static final Comparator<Order> descendingPriceComparator =
      new Comparator<Order>() {
        // Order fills oldest first (lower time), then have the biggest quanity first to close out.
        @Override
        public int compare(Order order, Order order2) {
          if ((order2.getFillType() == FillType.MARKET && order.getFillType() == FillType.MARKET)) {
            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;
            if (order.getLimitPrice() == null) {
              return -1;
            }
            if (order2.getLimitPrice() == null) {
              return 1;
            }
            int pComp = order.getLimitPrice().compareTo(order2.getLimitPrice());
            if (pComp != 0) return pComp;
            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;
          }
          // this sort from lowest to highest
          // Null values go to bottom of list.
          if (order.getFillType() == FillType.MARKET) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return -1;
          }

          if (order2.getFillType() == FillType.MARKET) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return 1;
          }

          if ((order2.getLimitPrice() == null && order.getLimitPrice() == null)) {
            int oComp =
                Double.compare(
                    order.getOrderGroup(),
                    order2.getOrderGroup()); // smallet to largest order group
            if (oComp != 0) return oComp;
            int vComp =
                order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
            if (vComp != 0) return vComp;

            int tComp = order.getTime().compareTo(order2.getTime());
            if (tComp != 0) return tComp;
          }

          if (order.getLimitPrice() == null) {
            // || order.getTargetPrice() == null) {
            // order > order2
            return -1;
          }

          // Null values go to bottom of list.
          if (order2.getLimitPrice() == null) {
            // || order2.getTargetPrice() == null) {
            // order < order2.
            return 1;
          }

          int pComp =
              order2.getLimitPrice().compareTo(order.getLimitPrice()); // highest to lowest price
          if (pComp != 0) return pComp;

          int oComp =
              Double.compare(
                  order.getOrderGroup(), order2.getOrderGroup()); // smallet to largest order group
          if (oComp != 0) return oComp;
          int vComp = order2.getVolume().compareTo(order.getVolume()); // largest to smallest volume
          if (vComp != 0) return vComp;

          int tComp = order.getTime().compareTo(order2.getTime());
          if (tComp != 0) return tComp;
          return System.identityHashCode(order) - System.identityHashCode(order2);
          //   order.hashCode() - order2.hashCode();

        }
      };

  @Override
  public Collection<SpecificOrder> handleCancelAllLongOpeningSpecificOrders(
      Portfolio portfolio, Market market) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    //  synchronized (lock) {
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && specificOrder.getPositionEffect() == (PositionEffect.OPEN)
            && specificOrder.isBid()
            && orderStateMap.get(specificOrder) != null
            && orderStateMap.get(specificOrder).isOpen()) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllLongOpeningSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);
    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllLongOpeningSpecificOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    //  synchronized (lock) {
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && specificOrder.getOrderGroup() == orderGroup
            && specificOrder.getPositionEffect() == (PositionEffect.OPEN)
            && specificOrder.isBid()
            && orderStateMap.get(specificOrder) != null
            && orderStateMap.get(specificOrder).isOpen()) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllLongOpeningSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);
    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> getPendingLongCloseOrders(
      Portfolio portfolio, ExecutionInstruction execInst, Market market) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;

    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;
        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isAsk()
            && (pendingOrder.getExecutionInstruction() == null
                || (pendingOrder.getExecutionInstruction() != null
                    && pendingOrder.getExecutionInstruction().equals(execInst)))) {
          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    // orderStateMap.remove(pendingOrder);
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingLongCloseOrders(
      Portfolio portfolio, ExecutionInstruction execInst, Market market, double orderGroup) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;

    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;
        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getOrderGroup() == orderGroup
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isAsk()
            && (pendingOrder.getExecutionInstruction() == null
                || (pendingOrder.getExecutionInstruction() != null
                    && pendingOrder.getExecutionInstruction().equals(execInst)))) {
          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    // orderStateMap.remove(pendingOrder);
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortCloseOrders(
      Portfolio portfolio, ExecutionInstruction execInst, Market market) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;
        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isBid()
            && (pendingOrder.getExecutionInstruction() == null
                || (pendingOrder.getExecutionInstruction() != null
                    && pendingOrder.getExecutionInstruction().equals(execInst)))) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> getPendingShortCloseOrders(
      Portfolio portfolio, ExecutionInstruction execInst, Market market, double orderGroup) {
    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
    if (market == null || portfolio == null) return portfolioPendingOrders;
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;
        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)
            && pendingOrder.getOrderGroup() == orderGroup
            && pendingOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && pendingOrder.isBid()
            && (pendingOrder.getExecutionInstruction() == null
                || (pendingOrder.getExecutionInstruction() != null
                    && pendingOrder.getExecutionInstruction().equals(execInst)))) {

          portfolioPendingOrders.add(pendingOrder);
        }
      }
    }
    return portfolioPendingOrders;
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(
      Portfolio portfolio, Market market, ExecutionInstruction execInst) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && specificOrder.isAsk()
            && (specificOrder.getExecutionInstruction() == null
                || (specificOrder.getExecutionInstruction() != null
                    && specificOrder.getExecutionInstruction().equals(execInst)))) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllLongClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(
      Portfolio portfolio, Market market, ExecutionInstruction execInst, double orderGroup) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getOrderGroup() == orderGroup
            && specificOrder.getMarket().equals(market)
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && specificOrder.isAsk()
            && (specificOrder.getExecutionInstruction() == null
                || (specificOrder.getExecutionInstruction() != null
                    && specificOrder.getExecutionInstruction().equals(execInst)))) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllLongClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(
      Portfolio portfolio, Market market) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    //   synchronized (lock) {
    // for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
    //   Order order = it.next();
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && specificOrder.isAsk()
            && (specificOrder.getExecutionInstruction() == null
                || (specificOrder.getExecutionInstruction() != null))) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllLongClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    //   synchronized (lock) {
    // for (Iterator<Order> it = getPendingOrders().iterator(); it.hasNext();) {
    //   Order order = it.next();
    Iterator<Order> it = getPendingOrders().iterator();
    while (it.hasNext()) {
      Order order = it.next();

      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (specificOrder.getMarket().equals(market)
            && specificOrder.getOrderGroup() == orderGroup
            && (orderStateMap.get(specificOrder) != null
                && orderStateMap.get(specificOrder).isOpen())
            && specificOrder.getPositionEffect() == (PositionEffect.CLOSE)
            && specificOrder.isAsk()
            && (specificOrder.getExecutionInstruction() == null
                || (specificOrder.getExecutionInstruction() != null))) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllLongClosingSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }
    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  private class CancelOrderRunnable implements Callable<Boolean> {

    DateFormat dateFormat = new SimpleDateFormat("ddMMyy");
    private final SpecificOrder specificOrder;
    private final int delayPeriod;
    private final int maxAttempts;

    public CancelOrderRunnable(SpecificOrder specificOrder, int delayPeriod, int maxAttempts) {

      this.specificOrder = specificOrder;
      this.delayPeriod = delayPeriod;
      //   this.tradeService = tradeService;
      this.maxAttempts = maxAttempts;
    }

    @Override
    public Boolean call() {
      boolean cancelled = false;
      try {

        int attempt = 1;
        while (attempt <= maxAttempts) {
          log.error(
              this.getClass().getSimpleName()
                  + ":call. Attempting to cancel order "
                  + specificOrder
                  + " after "
                  + attempt
                  + " attempts. Waiting "
                  + delayPeriod * attempt
                  + " seconds before retrying.");

          Thread.sleep(delayPeriod * 1000 * attempt);
          if (specificOrderToCancel(specificOrder)) {
            cancelled = true;
            break;
          }

          attempt++;
        }
        if (!cancelled) {
          log.error(
              this.getClass().getSimpleName()
                  + ":call. Unable to cancel order "
                  + specificOrder
                  + " after "
                  + attempt
                  + " attempts.");
          updateOrderState(specificOrder, OrderState.PLACED, true);
          return cancelled;
        }
        return cancelled;

      } catch (Throwable e) {
        log.error(
            this.getClass().getSimpleName() + ":call. Unable to cancel order" + specificOrder, e);
        return cancelled;
      }
    }
  }

  @Override
  public boolean handleCancelSpecificOrder(SpecificOrder specificOrder) {
    try {
      if (orderStateMap.get(specificOrder) != null
          && orderStateMap.get(specificOrder).isOpen()
          && cancelSpecificOrder(specificOrder)) {
        if (specificOrder.getParentFill() != null)
          specificOrder
              .getParentFill()
              .setPositionType(
                  (specificOrder.getParentFill().getOpenVolumeCount() == 0
                      ? PositionType.FLAT
                      : (specificOrder.getParentFill().getOpenVolumeCount() > 0
                          ? PositionType.LONG
                          : PositionType.SHORT)));
        // need to remove it from any parent
        //            if (specificOrder.getParentOrder() != null &&
        // specificOrder.getParentOrder().getOrderChildren() != null
        //                    && !specificOrder.getParentOrder().getOrderChildren().isEmpty())
        //
        //                specificOrder.getParentOrder().removeChildOrder(specificOrder);

        log.debug(
            "handleCancelSpecificOrder cancelled called from class "
                + Thread.currentThread().getStackTrace()[2]
                + " Specific Order:"
                + specificOrder);
        return true;
      }

      //   } else if (getOrderState(specificOrder).isNew()) {
      //     log.info("handleCancelSpecificOrder unable to cancelled " + specificOrder + " with
      // order state " + getOrderState(specificOrder).toString()
      //             + " so force cancelling");
      //     return true;
      // }

      else {
        log.debug(
            "handleCancelSpecificOrder called from class "
                + Thread.currentThread().getStackTrace()[2]
                + " unable to cancelled Specific Order:"
                + specificOrder
                + " with order state "
                + (orderStateMap.get(specificOrder) == null
                    ? specificOrder.getOrderState()
                    : orderStateMap.get(specificOrder)));

        return false;
      }
    } catch (IllegalArgumentException ex) {
      try {
        // if (getOrderState(specificOrder) != null && getOrderState(specificOrder).isNew()) {
        if (orderStateMap.get(specificOrder) != null && orderStateMap.get(specificOrder).isOpen()) {
          log.info(
              "handleCancelSpecificOrder unable to cancelled "
                  + specificOrder
                  + " with order state "
                  + getOrderState(specificOrder).toString()
                  + " so force rejecting. Exception: "
                  + ex);
          if (specificOrder.getParentFill() != null)
            specificOrder
                .getParentFill()
                .setPositionType(
                    (specificOrder.getParentFill().getOpenVolumeCount() == 0
                        ? PositionType.FLAT
                        : (specificOrder.getParentFill().getOpenVolumeCount() > 0
                            ? PositionType.LONG
                            : PositionType.SHORT)));

          updateOrderState(specificOrder, OrderState.REJECTED, true);
        }
        return true;

        /*
         * else log.info("handleCancelSpecificOrder called from class " + sun.reflect.Reflection.getCallerClass(2) + " unable to cancel state " +
         * getOrderState(specificOrder).toString() + " for " + specificOrder.getClass().getSimpleName() + ": " + specificOrder); return false; } catch
         * (IllegalArgumentException | IllegalStateException ise) { log.info("handleCancelSpecificOrder called from class " +
         * sun.reflect.Reflection.getCallerClass(2) + " unable to cancel " + specificOrder + " as unknown order so force rejecting. Exception: " +
         * ex); if (specificOrder.getParentFill() != null) specificOrder.getParentFill().setPositionType(
         * (specificOrder.getParentFill().getOpenVolumeCount() == 0 ? PositionType.FLAT : (specificOrder.getParentFill().getOpenVolumeCount() > 0 ?
         * PositionType.LONG : PositionType.SHORT))); updateOrderState(specificOrder, OrderState.REJECTED, true); return true;
         */
      } catch (Throwable t) {
        if (orderStateMap.get(specificOrder) != null && orderStateMap.get(specificOrder).isOpen()) {
          log.debug(
              "handleCancelSpecificOrder called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " unable to cancel state "
                  + getOrderState(specificOrder).toString()
                  + " for "
                  + specificOrder.getClass().getSimpleName()
                  + ": "
                  + specificOrder);
          return false;
        } else return true;
      }
    } catch (Throwable t) {
      if (orderStateMap.get(specificOrder) != null && orderStateMap.get(specificOrder).isOpen()) {
        log.debug(
            "handleCancelSpecificOrder called from class "
                + Thread.currentThread().getStackTrace()[2]
                + " unable to cancel state "
                + getOrderState(specificOrder).toString()
                + " for "
                + specificOrder.getClass().getSimpleName()
                + ": "
                + specificOrder);
        return false;
      } else return true;
    }
    // throw new OrderNotFoundException("Unable to cancelled order");
    // cancelledOrders.add(cancelledOrder);
    // pendingOrders.removeAll(cancelledOrders);

  }

  // @When("@Priority(7) select * from OrderUpdate where state.open=false and NOT (OrderUpdate.state
  // = OrderState.CANCELLED)")
  private void completeOrder(OrderUpdate update) {
    OrderState orderState = update.getState();
    Order order = update.getOrder();
    SpecificOrder specificOrder;
    if (order instanceof SpecificOrder) {
      specificOrder = (SpecificOrder) order;
      try {
        switch (orderState) {
          case CANCELLING:
            if (handleCancelSpecificOrder(specificOrder))
              updateOrderState(order, OrderState.CANCELLED, true);
            break;
          default:
            handleCancelSpecificOrder(specificOrder);
            break;
        }
      } catch (Throwable ex) {
        log.debug(
            "completeOrder called from class "
                + Thread.currentThread().getStackTrace()[2]
                + " unable to cancelled Specific Order:"
                + specificOrder);
      }
    }
  }

  @Override
  public Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio) {

    Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<>();
    if (market == null || portfolio == null) return portfolioPendingOrders;

    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder pendingOrder = (SpecificOrder) order;
        if (pendingOrder.getPortfolio().equals(portfolio)
            && pendingOrder.getMarket().equals(market)) portfolioPendingOrders.add(pendingOrder);
      }
    }

    return portfolioPendingOrders;
  }

  @Override
  public Collection<Order> getPendingStopOrders(Portfolio portfolio, Market market) {
    List<Order> portfolioPendingStopOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingStopOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingStopOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();

      for (Iterator<TransactionType> ittt =
              triggerOrders.get(market).get(triggerInterval).keySet().iterator();
          ittt.hasNext(); ) {
        TransactionType transactionType = ittt.next();
        if (triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(transactionType)
                .get(FillType.STOP_LIMIT)
            != null) {
          synchronized (
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(transactionType)
                  .get(FillType.STOP_LIMIT)) {
            for (Iterator<Order> it =
                    triggerOrders
                        .get(market)
                        .get(triggerInterval)
                        .get(transactionType)
                        .get(FillType.STOP_LIMIT)
                        .iterator();
                it.hasNext(); ) {

              Order triggerOrder = it.next();

              if (triggerOrder.getPortfolio().equals(portfolio)
                  && triggerOrder.getMarket().equals(market))
                if (!portfolioPendingStopOrders.contains(triggerOrder))
                  portfolioPendingStopOrders.add(triggerOrder);
              // TODO could be a condition here where we have not removed the trigger order but we
              // have set the stop price to 0 on the fill

            }
          }
        }
      }
    }
    Collections.sort(portfolioPendingStopOrders, parentFillTimeComparator);

    return portfolioPendingStopOrders;
  }

  @Override
  public Collection<Order> getPendingLongStopOrders(Portfolio portfolio, Market market) {
    List<Order> portfolioPendingStopOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingStopOrders;

    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingLongStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingStopOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return portfolioPendingStopOrders;

      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();
          if (triggerOrder.getPortfolio().equals(portfolio)
              && triggerOrder.getMarket().equals(market)
              && triggerOrder.isAsk()
              && triggerOrder.getStopPrice() != null)
            if (!portfolioPendingStopOrders.contains(triggerOrder))
              portfolioPendingStopOrders.add(triggerOrder);
        }
      }
    }
    Collections.sort(portfolioPendingStopOrders, parentFillTimeComparator);
    return portfolioPendingStopOrders;
  }

  @Override
  public Collection<Order> getPendingLongStopOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<Order> portfolioPendingStopOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingStopOrders;

    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingLongStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingStopOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return portfolioPendingStopOrders;

      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {
          Order triggerOrder = it.next();
          if (triggerOrder.getPortfolio().equals(portfolio)
              && triggerOrder.getMarket().equals(market)
              && triggerOrder.getOrderGroup() == orderGroup
              && triggerOrder.isAsk()
              && triggerOrder.getStopPrice() != null)
            if (!portfolioPendingStopOrders.contains(triggerOrder))
              portfolioPendingStopOrders.add(triggerOrder);
        }
      }
    }
    Collections.sort(portfolioPendingStopOrders, parentFillTimeComparator);
    return portfolioPendingStopOrders;
  }

  @Override
  public Collection<Order> getPendingLongTriggerOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<Order> portfolioPendingTriggerOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingTriggerOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {

    log.debug(
        "{} : getPendingLongTriggerOrders to called from stack {}",
        this.getClass().getSimpleName(),
        Thread.currentThread().getStackTrace()[2]);

    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingTriggerOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return portfolioPendingTriggerOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder.isAsk()
                && triggerOrder.getTargetPrice() != null)
              if (!portfolioPendingTriggerOrders.contains(triggerOrder))
                portfolioPendingTriggerOrders.add(triggerOrder);
          }
        }
      }
    }
    Collections.sort(portfolioPendingTriggerOrders, parentFillTimeComparator);
    return portfolioPendingTriggerOrders;
  }

  @Override
  public Collection<Order> getRoutedLongStopOrders(Portfolio portfolio, Market market) {
    List<Order> portfolioPendingStopOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingStopOrders;
    Fill parentFill;
    Order parentOrder;

    // stateOrderMap.get(OrderState.)
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getRoutedShortStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingStopOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return portfolioPendingStopOrders;

      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.SELL)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {

          Order triggerOrder = it.next();
          if (triggerOrder.getPortfolio().equals(portfolio)
              && triggerOrder.getMarket().equals(market)
              && triggerOrder.isAsk()
              && triggerOrder.getStopPrice() != null
              && orderStateMap.get(triggerOrder).equals(OrderState.ROUTED))
            if (!portfolioPendingStopOrders.contains(triggerOrder))
              portfolioPendingStopOrders.add(triggerOrder);
        }
      }
    }
    Collections.sort(portfolioPendingStopOrders, parentFillTimeComparator);

    return portfolioPendingStopOrders;
  }

  @Override
  public Collection<Order> getRoutedShortStopOrders(Portfolio portfolio, Market market) {
    List<Order> portfolioPendingStopOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingStopOrders;
    Fill parentFill;
    Order parentOrder;

    // stateOrderMap.get(OrderState.)
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getRoutedShortStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingStopOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return portfolioPendingStopOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.isBid()
                && triggerOrder.getStopPrice() != null
                && orderStateMap.get(triggerOrder).equals(OrderState.ROUTED))
              if (!portfolioPendingStopOrders.contains(triggerOrder))
                portfolioPendingStopOrders.add(triggerOrder);
          }
        }
      }
    }
    Collections.sort(portfolioPendingStopOrders, parentFillTimeComparator);
    return portfolioPendingStopOrders;
  }

  @Override
  public Collection<Order> getPendingShortStopOrders(Portfolio portfolio, Market market) {
    List<Order> portfolioPendingStopOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingStopOrders;

    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingShortStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingStopOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return portfolioPendingStopOrders;

      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {

          Order triggerOrder = it.next();
          if (triggerOrder.getPortfolio().equals(portfolio)
              && triggerOrder.getMarket().equals(market)
              && triggerOrder.isBid()
              && triggerOrder.getStopPrice() != null)
            if (!portfolioPendingStopOrders.contains(triggerOrder))
              portfolioPendingStopOrders.add(triggerOrder);
        }
      }
    }
    Collections.sort(portfolioPendingStopOrders, parentFillTimeComparator);
    return portfolioPendingStopOrders;
  }

  @Override
  public List<Order> getPendingShortStopOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<Order> portfolioPendingStopOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingStopOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingShortStopOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingStopOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return portfolioPendingStopOrders;
      synchronized (
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.STOP_LIMIT)) {
        for (Iterator<Order> it =
                triggerOrders
                    .get(market)
                    .get(triggerInterval)
                    .get(TransactionType.BUY)
                    .get(FillType.STOP_LIMIT)
                    .iterator();
            it.hasNext(); ) {

          Order triggerOrder = it.next();
          if (triggerOrder.getPortfolio().equals(portfolio)
              && triggerOrder.getMarket().equals(market)
              && triggerOrder.getOrderGroup() == orderGroup
              && triggerOrder.isBid()
              && triggerOrder.getStopPrice() != null)
            if (!portfolioPendingStopOrders.contains(triggerOrder))
              portfolioPendingStopOrders.add(triggerOrder);
        }
      }
    }
    Collections.sort(portfolioPendingStopOrders, parentFillTimeComparator);
    return portfolioPendingStopOrders;
  }

  private static final Comparator<Order> parentFillTimeComparator =
      new Comparator<Order>() {
        // Order orders by oldest parent fill first, then by the order time.

        @Override
        public int compare(Order order, Order order2) {
          int pComp;
          if (order2.getParentFill() == null && order.getParentFill() == null) {
            pComp = order.getTime().compareTo(order2.getTime());
            if (pComp != 0) {
              return pComp;
            } else {
              return 0;
            }
          }
          // this sort from lowest to highest
          // Null values go to bottom of list.
          if (order2.getParentFill() == null) {
            return 1;
          }

          // Null values go to bottom of list.
          if (order.getParentFill() == null) {
            return -1;
          }

          if (order2.getParentFill() != null && order.getParentFill() != null) {
            pComp = order.getParentFill().getTime().compareTo(order2.getParentFill().getTime());
            if (pComp != 0) return pComp;
            pComp =
                order2
                    .getParentFill()
                    .getVolume()
                    .abs()
                    .compareTo(order.getParentFill().getVolume().abs());
            if (pComp != 0) return pComp;

            return System.identityHashCode(order) - System.identityHashCode(order2);
          }

          return 0;
        }
      };

  @Override
  public Collection<Order> getPendingShortTriggerOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<Order> portfolioPendingTriggerOrders = new ArrayList<Order>();
    if (market == null || portfolio == null) return portfolioPendingTriggerOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingShortTriggerOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingTriggerOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return portfolioPendingTriggerOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder.isBid()
                && triggerOrder.getTargetPrice() != null)
              if (!portfolioPendingTriggerOrders.contains(triggerOrder))
                portfolioPendingTriggerOrders.add(triggerOrder);
          }
        }
      }
    }
    Collections.sort(portfolioPendingTriggerOrders, parentFillTimeComparator);
    return portfolioPendingTriggerOrders;
  }

  @Override
  public Collection<GeneralOrder> getPendingShortOpenTriggerOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<GeneralOrder> portfolioPendingTriggerOrders = new ArrayList<GeneralOrder>();
    if (market == null || portfolio == null) return portfolioPendingTriggerOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingShortOpenTriggerOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]
            + " with portfolio="
            + portfolio
            + "/"
            + System.identityHashCode(portfolio)
            + ",market="
            + market
            + "/"
            + System.identityHashCode(market)
            + ",orderGroup="
            + orderGroup
            + "/"
            + System.identityHashCode(orderGroup));

    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingTriggerOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return portfolioPendingTriggerOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder instanceof GeneralOrder
                && triggerOrder.getPositionEffect().equals(PositionEffect.OPEN))
              if (!portfolioPendingTriggerOrders.contains(triggerOrder))
                portfolioPendingTriggerOrders.add((GeneralOrder) triggerOrder);
          }
        }
      }
    }
    Collections.sort(portfolioPendingTriggerOrders, parentFillTimeComparator);
    return portfolioPendingTriggerOrders;
  }

  @Override
  public Collection<GeneralOrder> getPendingShortCloseTriggerOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    List<GeneralOrder> portfolioPendingTriggerOrders = new ArrayList<GeneralOrder>();
    if (market == null || portfolio == null) return portfolioPendingTriggerOrders;
    Fill parentFill;
    Order parentOrder;
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingShortCloseTriggerOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]
            + " with portfolio="
            + portfolio
            + "/"
            + System.identityHashCode(portfolio)
            + ",market="
            + market
            + "/"
            + System.identityHashCode(market)
            + ",orderGroup="
            + orderGroup
            + "/"
            + System.identityHashCode(orderGroup));

    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return portfolioPendingTriggerOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return portfolioPendingTriggerOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder instanceof GeneralOrder
                && triggerOrder.getPositionEffect().equals(PositionEffect.CLOSE))
              if (!portfolioPendingTriggerOrders.contains(triggerOrder))
                portfolioPendingTriggerOrders.add((GeneralOrder) triggerOrder);
          }
        }
      }
    }
    Collections.sort(portfolioPendingTriggerOrders, parentFillTimeComparator);
    return portfolioPendingTriggerOrders;
  }

  @Override
  public Collection<Order> handleCancelAllShortClosingGeneralOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllShortClosingGeneralOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]
            + "with portfolio="
            + portfolio
            + "/"
            + System.identityHashCode(portfolio)
            + ",market="
            + market
            + "/"
            + System.identityHashCode(market)
            + ",orderGroup="
            + orderGroup
            + "/"
            + System.identityHashCode(orderGroup));
    Collection<GeneralOrder> ordersToCancel =
        getPendingShortCloseTriggerOrders(portfolio, market, orderGroup);
    cancelledOrders.addAll(ordersToCancel);

    removeTriggerOrders(market, cancelledOrders);
    //    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllShortClosingGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + "Cancelled Long Opening Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Collection<Order> handleCancelAllLongClosingGeneralOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllLongClosingGeneralOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]
            + " with portfolio="
            + portfolio
            + "/"
            + System.identityHashCode(portfolio)
            + ",market="
            + market
            + "/"
            + System.identityHashCode(market)
            + ",orderGroup="
            + orderGroup
            + "/"
            + System.identityHashCode(orderGroup));

    Collection<GeneralOrder> orderToCancel =
        getPendingLongCloseTriggerOrders(portfolio, market, orderGroup);
    cancelledOrders.addAll(orderToCancel);
    removeTriggerOrders(market, cancelledOrders);
    //    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllLongClosingGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + "Cancelled Long Opening Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Collection<Order> handleCancelAllLongOpeningGeneralOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    // synchronized (lock) {
    // triggerOrderLock.lock();

    log.debug(
        "{} : handleCancelAllLongOpeningGeneralOrders to called from stack {}",
        this.getClass().getSimpleName(),
        Thread.currentThread().getStackTrace()[2]);

    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return cancelledOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            // npe jerere
            if (triggerOrder.getMarket() == null) log.debug("test");
            if (triggerOrder != null
                && triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.isBid()
                && triggerOrder.getPositionEffect() == (PositionEffect.OPEN))
              cancelledOrders.add(triggerOrder);
          }
        }
      }
    }
    removeTriggerOrders(market, cancelledOrders);
    //    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllLongOpeningGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + "Cancelled Long Opening Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Collection<Order> handleCancelAllShortClosingGeneralOrders(
      Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllShortClosingGeneralOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return cancelledOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            // npe jerere
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.isBid()
                && triggerOrder instanceof GeneralOrder
                && triggerOrder.getPositionEffect().equals(PositionEffect.CLOSE)) {
              cancelledOrders.add(triggerOrder);
            }
          }
        }
      }
    }
    removeTriggerOrders(market, cancelledOrders);

    //    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllShortClosingGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + "Cancelled Short Closing Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Collection<Order> handleCancelAllLongClosingGeneralOrders(
      Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllLongClosingGeneralOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return cancelledOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            // npe jerere
            if (triggerOrder.getPortfolio().equals(portfolio)
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.isAsk()
                && triggerOrder instanceof GeneralOrder
                && triggerOrder.getPositionEffect().equals(PositionEffect.CLOSE))
              cancelledOrders.add(triggerOrder);
          }
        }
      }
    }
    removeTriggerOrders(market, cancelledOrders);

    //    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllLongClosingGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + "Cancelled Long Closing Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Collection<Order> handleCancelAllLongOpeningGeneralOrders(
      Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<Order>();
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllLongOpeningGeneralOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).isEmpty())
        return cancelledOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.BUY)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY).get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.BUY)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {

            Order triggerOrder = it.next();
            // npe jerere
            if (triggerOrder != null
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.isBid()
                && triggerOrder.getPositionEffect() == (PositionEffect.OPEN))
              cancelledOrders.add(triggerOrder);
          }
        }
      }
    }
    removeTriggerOrders(market, cancelledOrders);

    //    }
    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllLongOpeningGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + "Cancelled Long Opening Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Collection<Order> handleCancelAllShortOpeningGeneralOrders(
      Portfolio portfolio, Market market) {
    Collection<Order> cancelledOrders = new HashSet<>();
    if (market == null || portfolio == null) return cancelledOrders;
    // synchronized (lock) {
    // triggerOrderLock.lock();
    log.debug(
        this.getClass().getSimpleName()
            + " : handleCancelAllShortOpeningGeneralOrders to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return cancelledOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();

            if (triggerOrder.getMarket().equals(market)
                && triggerOrder.isAsk()
                && triggerOrder.getPositionEffect() == (PositionEffect.OPEN))
              if (!cancelledOrders.contains(triggerOrder)) cancelledOrders.add(triggerOrder);

            // TODO could be a condition here where we have not removed the trigger order but we
            // have set the stop price to 0 on the fill
            //   if (triggerOrder.getParentFill() != null)
            //     triggerOrder.getParentFill().setStopPriceCount(0);
          }
        }
      }
    }
    removeTriggerOrders(market, cancelledOrders);

    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllShortOpeningGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + ": Cancelled Short Opening Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Collection<Order> handleCancelAllShortOpeningGeneralOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<Order> cancelledOrders = new HashSet<>();
    if (market == null || portfolio == null) return cancelledOrders;

    // synchronized (lock) {
    // triggerOrderLock.lock();

    log.debug(
        " {}: handleCancelAllShortOpeningGeneralOrders to called from stack {}",
        this.getClass().getSimpleName(),
        Thread.currentThread().getStackTrace()[2]);

    if (portfolio == null
        || market == null
        || triggerOrders == null
        || triggerOrders.isEmpty()
        || triggerOrders.get(market) == null
        || triggerOrders.get(market).isEmpty()) return cancelledOrders;

    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();
      if (triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL) == null
          || triggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL).isEmpty())
        return cancelledOrders;
      for (Iterator<FillType> itf =
              triggerOrders
                  .get(market)
                  .get(triggerInterval)
                  .get(TransactionType.SELL)
                  .keySet()
                  .iterator();
          itf.hasNext(); ) {
        FillType fillType = itf.next();

        synchronized (
            triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(TransactionType.SELL)
                .get(fillType)) {
          for (Iterator<Order> it =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(TransactionType.SELL)
                      .get(fillType)
                      .iterator();
              it.hasNext(); ) {
            Order triggerOrder = it.next();

            if (triggerOrder.getOrderGroup() == orderGroup
                && triggerOrder.getMarket().equals(market)
                && triggerOrder.isAsk()
                && triggerOrder.getPositionEffect() == (PositionEffect.OPEN))
              if (!cancelledOrders.contains(triggerOrder)) cancelledOrders.add(triggerOrder);

            // TODO could be a condition here where we have not removed the trigger order but we
            // have set the stop price to 0 on the fill
            //   if (triggerOrder.getParentFill() != null)
            //     triggerOrder.getParentFill().setStopPriceCount(0);
          }
        }
      }
    }
    removeTriggerOrders(market, cancelledOrders);

    for (Order cancelledOrder : cancelledOrders) {
      updateOrderState(cancelledOrder, OrderState.CANCELLED, true);
      log.debug(
          "handleCancelAllShortOpeningGeneralOrders called from class "
              + Thread.currentThread().getStackTrace()[2]
              + ": Cancelled Short Opening Trigger Order: "
              + cancelledOrder);
      if (cancelledOrder.getParentFill() != null)
        handleCancelAllTriggerOrdersByParentFill(cancelledOrder.getParentFill());
    }

    //                if(parentKey instanceof Fill)
    //                      parentFill = (Fill) parentKey;
    //                if(parentKey instanceof Order)
    //                     parentOrder = (Order) parentKey;
    //                if ((parentFill!=null && parentFill.getMarket().equals(market)) ||
    // (parentOrder!=null && parentOrder.getMarket().equals(market)))
    //                    triggerOrders.remove(parentKey);

    // TODO could be a condition here where we have not removed the trigger order but we have set
    // the stop price to 0 on the fill

    return cancelledOrders;
    // triggerOrderLock.unlock();
    // }

  }

  @Override
  public Order getPendingTriggerOrder(Order order) {
    // synchronized (lock) {
    log.debug(
        this.getClass().getSimpleName()
            + " : getPendingTriggerOrder to called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();

        for (Iterator<TransactionType> ittt =
                triggerOrders.get(market).get(triggerInterval).keySet().iterator();
            ittt.hasNext(); ) {
          TransactionType transactionType = ittt.next();
          for (Iterator<FillType> itf =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(transactionType)
                      .keySet()
                      .iterator();
              itf.hasNext(); ) {
            FillType fillType = itf.next();
            if (triggerOrders
                .get(market)
                .get(triggerInterval)
                .get(transactionType)
                .get(fillType)
                .contains(order)) return order;
          }
        }
      }
    }

    return null;
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllShortOpeningSpecificOrders(
      Portfolio portfolio, Market market) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    if (market == null || portfolio == null) return orderToCancel;

    //  synchronized (lock) {
    for (Order order : getPendingOrders()) {
      if (order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (orderStateMap.get(specificOrder) != null
            && orderStateMap.get(specificOrder).isOpen()
            && specificOrder.getMarket().equals(market)
            && specificOrder.getPositionEffect() == (PositionEffect.OPEN)
            && specificOrder.isAsk()) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllShortOpeningSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }

    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllShortOpeningSpecificOrders(
      Portfolio portfolio, Market market, double orderGroup) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    //  synchronized (lock) {
    for (Order order : getPendingOrders()) {
      if (order.getOrderGroup() == orderGroup && order instanceof SpecificOrder) {
        SpecificOrder specificOrder = (SpecificOrder) order;
        if (orderStateMap.get(specificOrder) != null
            && orderStateMap.get(specificOrder).isOpen()
            && specificOrder.getMarket().equals(market)
            && specificOrder.getPositionEffect() == (PositionEffect.OPEN)
            && specificOrder.isAsk()) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllShortOpeningSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }

    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);

    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> handleCancelAllOpeningSpecificOrders(
      Portfolio portfolio, Market market) {
    Collection<SpecificOrder> orderToCancel = new ArrayList<>();
    if (market == null || portfolio == null) return orderToCancel;

    //  synchronized (lock) {
    for (Order order : getPendingOrders()) {
      SpecificOrder specificOrder;
      if (order instanceof SpecificOrder) {
        specificOrder = (SpecificOrder) order;

        if (orderStateMap.get(specificOrder) != null
            && orderStateMap.get(specificOrder).isOpen()
            && specificOrder.getMarket().equals(market)
            && specificOrder.getPositionEffect() == (PositionEffect.OPEN)) {
          orderToCancel.add(specificOrder);
          log.debug(
              "handleCancelAllOpeningSpecificOrders called from class "
                  + Thread.currentThread().getStackTrace()[2]
                  + " cancelling order : "
                  + specificOrder);
        }
      }
    }

    Collection<SpecificOrder> cancelledOrders = cancelSpecificOrder(orderToCancel);
    return cancelSpecificOrder(cancelledOrders);
  }

  @Override
  public Collection<SpecificOrder> cancelSpecificOrder(Collection<SpecificOrder> orders) {

    log.trace(
        "cancelSpecificOrder: called from class {}", Thread.currentThread().getStackTrace()[2]);

    Collection<SpecificOrder> cancelledOrders = new ArrayList<SpecificOrder>();
    for (SpecificOrder order : orders) {
      try {
        if (orderStateMap.get(order) != null
            && orderStateMap.get(order).isOpen()
            && handleCancelSpecificOrder(order)) cancelledOrders.add(order);
      } catch (Throwable e) {
        // TODO Auto-generated catch block

        log.error(
            "cancelSpecificOrder: called from class {} unable to cancel order: {} {}",
            Thread.currentThread().getStackTrace()[2],
            order,
            e);
      }
    }
    return cancelledOrders;
  }

  public synchronized void updateOrderState(Order order, OrderState state, boolean route) {

    log.debug(
        "{} - updateOrderState to {} for {}/{}  called from class{}",
        this.getClass().getSimpleName(),
        state,
        order.getUuid(),
        System.identityHashCode(order),
        Thread.currentThread().getStackTrace()[2]);

    // need to add vaildation here on state and last state
    //   synchronized (order) {

    OrderState oldState = null;
    if (order != null) {
      oldState =
          (orderStateMap.get(order) != null) ? orderStateMap.get(order) : order.getOrderState();
      // order.merge();
    }

    if (oldState != null && (oldState.equals(state) || oldState.compareTo(state) > 0)) {
      log.trace(
          this.getClass().getSimpleName()
              + ": updateOrderState. Ealier state "
              + state
              + " procssed after "
              + oldState
              + " for order "
              + order.getUuid());
      return;
    }
    if (oldState == null) {
      oldState = OrderState.NEW;

    } else if (oldState != null
        && order != null
        && stateOrderMap.get(oldState) != null
        && !stateOrderMap.get(oldState).isEmpty()) {
      if (stateOrderMap.get(oldState).remove(order))
        log.debug(
            this.getClass().getSimpleName()
                + ":UpdateOrderState - Removed order "
                + order.getUuid()
                + "with state "
                + oldState
                + " from stateOrderMap");
    }
    if (order != null) {
      if (!state.isOpen()) {
        order.setOrderState(state);
        orderStateMap.remove(order);
      } else {

        orderStateMap.put(order, state);
        log.trace(
            order.getUuid()
                + "/"
                + System.identityHashCode(order)
                + " with state "
                + state
                + " added to orderStateMap as "
                + orderStateMap.get(order).toString());

        if (stateOrderMap.get(state) == null) {
          Set<Order> orderSet = new HashSet<Order>();
          // orderSet.add(order);
          stateOrderMap.put(state, orderSet);
        }
        if (stateOrderMap.get(state).add(order))
          log.trace(
              order.getUuid()
                  + " with state "
                  + state
                  + " added to stateOrderMap as "
                  + orderStateMap.get(order).toString());
      }
    }

    // this.getClass()
    // context.route(new OrderUpdate(order, oldState, state));
    OrderUpdate orderUpdate = orderUpdateFactory.create(context.getTime(), order, oldState, state);
    // if (route)
    context.setPublishTime(orderUpdate);

    orderUpdate.persit();
    handleOrderUpdate(orderUpdate);
    log.debug(
        this.getClass().getSimpleName()
            + " - updateOrderState: published orderupdate "
            + orderUpdate.getUuid()
            + " for order "
            + order.getUuid()
            + " with state "
            + state
            + " after added to stateOrderMap with "
            + (orderStateMap.get(order) == null
                ? order.getOrderState()
                : orderStateMap.get(order).toString()));

    context.route(orderUpdate);
    // else
    //  context.publish(orderUpdate);
    //  fter added to stateOrderMap

    // so we place a trigger order, then we create a child order, which then get's cancelled
    // and is replaced by a new child order,
    // we triggered it it got routed, then we cancelled the child
    // if we cancell the parent, it will not longer be routed, but it won't any way.

    if (order != null && order.getParentOrder() != null && oldState.compareTo(state) > 0)

      //  && (!state.isCancelled() || !state.isCancelling() || !state.isExpired()))
      updateParentOrderState(order.getParentOrder(), order, state);
    // }
  }

  private void replaceAllWorkingOrdersAsMarketByParent(Order parent, Amount limitPrice) {
    Set<Order> allLinkedOrderChildOrders = new HashSet<Order>();
    parent.getAllSpecificOrderByParentOrder(parent, allLinkedOrderChildOrders);
    for (Order linkedChildOrder : allLinkedOrderChildOrders) {
      if (linkedChildOrder instanceof SpecificOrder) {
        if (getOrderState(linkedChildOrder).isWorking()) {
          // Cancel and replace
          SpecificOrder linkedOrderToReplace = (SpecificOrder) linkedChildOrder;
          DiscreteAmount linkedLimitPriceDiscrete =
              new DiscreteAmount(
                  DiscreteAmount.roundedCountForBasis(
                      limitPrice.asBigDecimal(), linkedOrderToReplace.getMarket().getPriceBasis()),
                  linkedOrderToReplace.getMarket().getPriceBasis());

          if (linkedOrderToReplace.isAsk()) {
            if (linkedLimitPriceDiscrete.isPositive()
                && linkedOrderToReplace.getLimitPrice().compareTo(linkedLimitPriceDiscrete) <= 0)
              continue;
          } else {
            if (linkedLimitPriceDiscrete.isPositive()
                && linkedOrderToReplace.getLimitPrice().compareTo(linkedLimitPriceDiscrete) >= 0)
              continue;
          }

          cancelAndReplaceOrder(linkedOrderToReplace, null);
        }
      }
    }
  }

  // TODO this uses up a lot of cpu, many be call it less frequenetly
  private void updateAllWorkingOrdersByParent(
      Order parent, Amount limitPrice, boolean withPayUpTicks) {
    Set<Order> allLinkedOrderChildOrders = new HashSet<Order>();
    parent.getAllSpecificOrderByParentOrder(parent, allLinkedOrderChildOrders);
    for (Order linkedChildOrder : allLinkedOrderChildOrders) {
      if (linkedChildOrder instanceof SpecificOrder) {
        if (getOrderState(linkedChildOrder).isWorking()) {
          // Cancel and replace
          SpecificOrder linkedOrderToReplace = (SpecificOrder) linkedChildOrder;
          DiscreteAmount linkedLimitPriceDiscrete =
              new DiscreteAmount(
                  DiscreteAmount.roundedCountForBasis(
                      limitPrice.asBigDecimal(), linkedOrderToReplace.getMarket().getPriceBasis()),
                  linkedOrderToReplace.getMarket().getPriceBasis());

          // limit price of 100 is less than nwe price of 110
          // if I am buying order of 110 >=new price of 100, we can drop our price
          if ((linkedChildOrder.isBid()
                  && (linkedOrderToReplace
                          .getLimitPrice()
                          .compareTo(
                              linkedLimitPriceDiscrete.decrement(
                                  linkedOrderToReplace.getPayUpTicks()))
                      >= 0))
              || (linkedChildOrder.isAsk()
                  && (linkedOrderToReplace
                          .getLimitPrice()
                          .compareTo(
                              linkedLimitPriceDiscrete.increment(
                                  linkedOrderToReplace.getPayUpTicks()))
                      <= 0))) {

            DiscreteAmount updatedLimit = linkedLimitPriceDiscrete;

            if (withPayUpTicks) {
              updatedLimit =
                  linkedOrderToReplace.isAsk()
                      ? linkedLimitPriceDiscrete.decrement(linkedOrderToReplace.getPayUpTicks())
                      : linkedLimitPriceDiscrete.increment(linkedOrderToReplace.getPayUpTicks());
            }

            // cancelAndReplaceOrder(linkedOrderToReplace, updatedLimit);
          }
        }
      }
    }
  }

  private void updateParentOrderState(Order order, Order childOrder, OrderState childOrderState) {
    //    if (order.getFillType() == FillType.ONE_CANCELS_OTHER)
    //       return;
    OrderState oldState = orderStateMap.get(order);
    // certain states we don't want ot properage to parent orders
    if (childOrderState.isCancelling()
        || childOrderState.isRejected()
        || childOrderState.isError()) {
      log.debug(
          this.getClass().getSimpleName()
              + " : updateParentOrderState. Updateing parent order "
              + order.getUuid()
              + " from oldState "
              + oldState
              + "  to  childOrderState "
              + "for "
              + childOrderState
              + " Not permitted. Called from stack "
              + Thread.currentThread().getStackTrace()[2]);
      return;
    }

    switch (childOrderState) {
      case NEW:
        boolean fullyNew = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isNew() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyNew = false;
            break;
          }
        }
        if (fullyNew) updateOrderState(order, OrderState.NEW, true);
        // }
        break;

      case TRIGGER:
        boolean fullyTrigger = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isTrigger() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);

            fullyTrigger = false;
            break;
          }
        }
        if (fullyTrigger && order.getFillType().isTrigger()) {

          updateOrderState(order, OrderState.TRIGGER, true);
        }
        // }
        break;

      case ROUTED:
        // TODO: update state once all children have same state
        boolean fullyRouted = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isRouted() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyRouted = false;
            break;
          }
        }

        if (fullyRouted) updateOrderState(order, OrderState.ROUTED, true);
        break;
      case PLACED:
        // TODO: update state once all children have same state
        boolean fullyPlaced = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isPlaced() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyPlaced = false;
            break;
          }
        }
        if (fullyPlaced) updateOrderState(order, OrderState.PLACED, true);
        // }
        break;
      case PARTFILLED:
        boolean fullyPartFilled = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isPartfilled() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyPartFilled = false;
            break;
          }
        }
        if (fullyPartFilled) updateOrderState(order, OrderState.PARTFILLED, true);

        break;

      case FILLED:
        // if (oldState == OrderState.CANCELLING) {
        boolean fullyFilled = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null && orderStateMap.get(child).isOpen()) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyFilled = false;
            updateOrderState(order, OrderState.PARTFILLED, true);
            break;
          }
        }
        if (fullyFilled)
          if (order.getFillType().isTrigger()
              && order.getUsePosition()
              && !order.getOpenVolume().isZero()) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Unable to update order state to "
                    + childOrderState
                    + " for parent order "
                    + order
                    + " with state "
                    + orderStateMap.get(order));

            break;
          } else updateOrderState(order, OrderState.FILLED, true);

        break;

      case CANCELLING:
        boolean fullyCancelling = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isCancelling() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyCancelling = false;
            break;
          }
        }
        if (fullyCancelling)
          if (order.getFillType().isTrigger()
              && order.getUsePosition()
              && !order.getOpenVolume().isZero()) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Unable to update order state to "
                    + childOrderState
                    + " for parent order "
                    + order
                    + " with state "
                    + orderStateMap.get(order));

            break;
          } else updateOrderState(order, OrderState.CANCELLING, true);
        break;
        // Could be cancelled after being placed, without any fills.
      case CANCELLED:
        //  if (oldState == OrderState.CANCELLING) {
        boolean fullyCancelled = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isCancelled() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyCancelled = false;
            break;
          }
        }
        if (fullyCancelled)
          if (order.getFillType().isTrigger()
              && order.getUsePosition()
              && !order.getOpenVolume().isZero()) {

            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Unable to update order state to "
                    + childOrderState
                    + " for parent order "
                    + order
                    + " with state "
                    + orderStateMap.get(order));

            break;
          } else updateOrderState(order, OrderState.CANCELLED, true);
        // }
        break;
      case REJECTED:
        boolean fullyRejected = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isRejected() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyRejected = false;
            break;
          }
        }
        if (fullyRejected) {
          if (order.getFillType().isTrigger()
              && order.getUsePosition()
              && !order.getOpenVolume().isZero()) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Unable to update order state to "
                    + childOrderState
                    + " for parent order "
                    + order
                    + " with state "
                    + (orderStateMap.get(order) == null
                        ? order.getOrderState()
                        : orderStateMap.get(order)));

            break;
          } else {
            updateOrderState(order, OrderState.REJECTED, true);
            reject(order, "Child order was rejected");
          }
        }
        // }
        break;
      case ERROR:
        boolean fullyErrored = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isError() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyRejected = false;
            break;
          }
        }
        if (fullyErrored) {
          if (order.getFillType().isTrigger()
              && order.getUsePosition()
              && !order.getOpenVolume().isZero()) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Unable to update order state to "
                    + childOrderState
                    + " for parent order "
                    + order
                    + " with state "
                    + (orderStateMap.get(order) == null
                        ? order.getOrderState()
                        : orderStateMap.get(order)));

            break;
          } else {
            updateOrderState(order, OrderState.ERROR, true);
          }
        }
        // }
        break;
      case EXPIRED:
        if (childOrder != null
            && childOrder.getExpiration() != null
            && !childOrder.getExpiration().isEqual(order.getExpiration()))
          throw new Error("Child order expirations must match parent order expirations");
        boolean fullyExpired = true;
        for (Order child : order.getOrderChildren()) {
          if (orderStateMap.get(child) != null
              && (!orderStateMap.get(child).isExpired() && orderStateMap.get(child).isOpen())) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Child order "
                    + child.getUuid()
                    + " has state "
                    + orderStateMap.get(child)
                    + " which is different from requested state of "
                    + childOrderState);
            fullyExpired = false;
            break;
          }
        }
        if (fullyExpired)
          if (order.getFillType().isTrigger()
              && order.getUsePosition()
              && !order.getOpenVolume().isZero()) {
            log.debug(
                this.getClass().getSimpleName()
                    + " : updateParentOrderState. Unable to update order state to "
                    + childOrderState
                    + " for parent order "
                    + order
                    + " with state "
                    + (orderStateMap.get(order) == null
                        ? order.getOrderState()
                        : orderStateMap.get(order)));

            break;
          } else updateOrderState(order, OrderState.EXPIRED, true);
        // }
        break;

      default:
        log.warn("Unknown order state: " + childOrderState);
        break;
    }
  }

  /*
   * protected static final void persitOrderFill(Event event) { if (event instanceof Order) { Order order = (Order) event; order.persit(); // Order
   * duplicate = PersistUtil.queryZeroOne(Order.class, "select o from Order o where o=?1", order); // if (duplicate == null) //
   * PersistUtil.insert(order); // else // PersistUtil.merge(order); } else if (event instanceof Fill) { Fill fill = (Fill) event; // //
   * fill.persit(); // Fill duplicate = PersistUtil.queryZeroOne(Fill.class, "select f from Fill f where f=?1", fill); // if (duplicate == null) // fi
   * // PersistUtil.insert(fill); // else // PersistUtil.merge(fill); } // else { // if not a Trade, persist unconditionally // try { //
   * PersistUtil.insert(event); //} catch (Throwable e) { // throw new Error("Could not insert " + event, e); // } //} }
   */
  /*	private void removeTriggerOrder(Order order) {
  	log.debug(this.getClass().getSimpleName() + " : removeTriggerOrder to called from stack " + sun.reflect.Reflection.getCallerClass(2));
  	List<Order> triggerOrderToRemove = new ArrayList<Order>();
  	triggerOrderToRemove.add(order);
  	for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext();) {
  		Tradeable market = itm.next();
  		removeTriggerOrders(market, triggerOrderToRemove);
  	}
  }*/

  // (triggerOrder.isBid() ? ascendingStopPriceComparator
  //   : descendingStopPriceComparator);
  @Override
  public void sortLongStopOrders(Tradeable market) {
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();

      sortOrders(
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.STOP_LIMIT),
          TransactionType.SELL,
          market,
          descendingStopPriceComparator);
      if (trailingTriggerOrders != null
          && trailingTriggerOrders.get(market) != null
          && trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL)
              != null)
        sortOrders(
            trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.SELL),
            TransactionType.SELL,
            market,
            ascendingTrailingStopPriceComparator);
    }
  }

  @Override
  public void sortShortStopOrders(Tradeable market) {
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();

      sortOrders(
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.STOP_LIMIT),
          TransactionType.BUY,
          market,
          ascendingStopPriceComparator);
      if (trailingTriggerOrders != null
          && trailingTriggerOrders.get(market) != null
          && trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY)
              != null)
        sortOrders(
            trailingTriggerOrders.get(market).get(triggerInterval).get(TransactionType.BUY),
            TransactionType.BUY,
            market,
            descendingTrailingStopPriceComparator);
    }
  }

  @Override
  public void sortLongTargetOrders(Tradeable market) {
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();

      sortOrders(
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.SELL)
              .get(FillType.TARGET_LIMIT),
          TransactionType.SELL,
          market,
          ascendingTargetPriceComparator);
    }
  }

  @Override
  public void sortShortTargetOrders(Tradeable market) {
    for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
      Double triggerInterval = itd.next();

      sortOrders(
          triggerOrders
              .get(market)
              .get(triggerInterval)
              .get(TransactionType.BUY)
              .get(FillType.TARGET_LIMIT),
          TransactionType.BUY,
          market,
          descendingTargetPriceComparator);
    }
  }

  public void sortOrders(
      List<Order> orders,
      TransactionType transactionType,
      Tradeable market,
      Comparator<Order> comparator) {

    //      Comparator<Order> comparator = (transactionType == TransactionType.BUY) ?
    // ascendingStopPriceComparator : descendingStopPriceComparator;
    synchronized (orders) {
      Collections.sort(orders, comparator);
    }

    // triggerOrdersTable.get(market).put(transactionType,triggerTable);

    //      (triggerOrder.isBid() ? ascendingStopPriceComparator
    //   : descendingStopPriceComparator);

  }

  private void addTriggerOrder(Order triggerOrder, Market market) {
    // If the trigger order is from a fill, we use the fill as the key for mutliple triggers, else
    // we use the parent
    // any one of the multiple triggers can trigger first, but once one is triggered, all others are
    // removed at for either the same fill or same parent

    log.debug(
        this.getClass().getSimpleName()
            + " : addTriggerOrder "
            + triggerOrder.getUuid()
            + "/"
            + System.identityHashCode(triggerOrder)
            + ",portfolio="
            + triggerOrder.getPortfolio()
            + "/"
            + System.identityHashCode(triggerOrder.getPortfolio())
            + ",TransactionType="
            + triggerOrder.getTransactionType()
            + "/"
            + System.identityHashCode(triggerOrder.getTransactionType())
            + ",Market="
            + triggerOrder.getMarket()
            + "/"
            + System.identityHashCode(triggerOrder.getMarket())
            + ",TriggerInterval="
            + triggerOrder.getTriggerInterval()
            + "/"
            + System.identityHashCode(triggerOrder.getTriggerInterval())
            + ",OrderGroup="
            + triggerOrder.getOrderGroup()
            + "/"
            + System.identityHashCode(triggerOrder.getOrderGroup())
            + "  called from stack "
            + Thread.currentThread().getStackTrace()[2]);
    Event eventKey =
        (triggerOrder.getParentFill() != null)
            ? triggerOrder.getParentFill()
            : triggerOrder.getParentOrder();

    if (eventKey == null) eventKey = triggerOrder;
    // synchronized (lock) {
    // buy orders sorted lowest to highest (ascending)
    // sell order sorted highest to lowest (descending)
    // bid order are to exit a short posiont so we want the list that is highest to loser
    // (descnding)
    // Comparator<Order> comparator = (transactionType == TransactionType.BUY) ?
    // ascendingStopPriceComparator : descendingStopPriceComparator;

    List<Order> stopTriggerOrderQueue = new ArrayList<Order>();
    List<Order> targetTriggerOrderQueue = new ArrayList<Order>();
    List<Order> timedTriggerOrderQueue = new ArrayList<Order>();
    List<Order> multiLegTriggerOrderQueue = new ArrayList<Order>();
    List<Order> trailingTriggerOrderQueue = new ArrayList<Order>();
    // if (triggerOrder.isBid())
    //   triggerTable = TreeBasedTable.create(ascendingStopPriceComparator,
    // descendingTrailingStopPriceComparator);

    //  Table<Order, Order, Order> triggerTable;

    // Comparator<? super String> rowComparator;

    // (triggerOrder.isBid() ? ascendingStopPriceComparator
    //   : descendingStopPriceComparator);

    // buy orders have lowest price first, so if the price < first by order we break

    //   ConcurrentLinkedQueue<Order> triggerOrderQueue = new ConcurrentLinkedQueue<Order>();
    // We want buy orders sorted  loweset to highest (ascending) and sell orders sorted to highest
    // to lowest (decending)
    TransactionType transactionType =
        (triggerOrder.isBid()) ? TransactionType.BUY : TransactionType.SELL;
    //  TreeBasedTable<String, Integer, Character> table =
    //        TreeBasedTable.create(rowComparator, columnComparator);
    if (triggerOrder.getFillType().isTrailing()
        && (triggerOrder.getStopPrice() != null && triggerOrder.getStopAmount() != null)) {
      if (trailingTriggerOrders.get(market) == null
          || trailingTriggerOrders.get(market).isEmpty()) {
        trailingTriggerOrderQueue.add(triggerOrder);

        Map<TransactionType, List<Order>> transactionTypeTriggerOrders =
            new ConcurrentHashMap<TransactionType, List<Order>>();
        transactionTypeTriggerOrders.put(transactionType, trailingTriggerOrderQueue);
        Map<Double, Map<TransactionType, List<Order>>> triggerIntervalTriggerOrders =
            new ConcurrentHashMap<Double, Map<TransactionType, List<Order>>>();
        triggerIntervalTriggerOrders.put(
            triggerOrder.getTriggerInterval(), transactionTypeTriggerOrders);

        trailingTriggerOrders.put(market, triggerIntervalTriggerOrders);
        sortOrders(
            trailingTriggerOrderQueue,
            transactionType,
            market,
            (transactionType == TransactionType.BUY)
                ? descendingTrailingStopPriceComparator
                : ascendingTrailingStopPriceComparator);
        //               printTable(triggerOrdersTable.get(market).get(transactionType));

      } else if (trailingTriggerOrders.get(market).get(triggerOrder.getTriggerInterval()) == null
          || trailingTriggerOrders.get(market).get(triggerOrder.getTriggerInterval()).isEmpty()) {

        trailingTriggerOrderQueue.add(triggerOrder);

        Map<TransactionType, List<Order>> transactionTypeTriggerOrders =
            new ConcurrentHashMap<TransactionType, List<Order>>();
        transactionTypeTriggerOrders.put(transactionType, trailingTriggerOrderQueue);
        // so if the hash map is there, we just add our interval to it.
        trailingTriggerOrders
            .get(market)
            .put(triggerOrder.getTriggerInterval(), transactionTypeTriggerOrders);

        sortOrders(
            trailingTriggerOrderQueue,
            transactionType,
            market,
            (transactionType == TransactionType.BUY)
                ? descendingTrailingStopPriceComparator
                : ascendingTrailingStopPriceComparator);

      } else if (trailingTriggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
              == null
          || trailingTriggerOrders
              .get(market)
              .get(triggerOrder.getTriggerInterval())
              .get(transactionType)
              .isEmpty()) {
        trailingTriggerOrderQueue.add(triggerOrder);
        trailingTriggerOrders
            .get(market)
            .get(triggerOrder.getTriggerInterval())
            .put(transactionType, trailingTriggerOrderQueue);
        sortOrders(
            trailingTriggerOrderQueue,
            transactionType,
            market,
            (transactionType == TransactionType.BUY)
                ? descendingTrailingStopPriceComparator
                : ascendingTrailingStopPriceComparator);

      } else {
        synchronized (
            trailingTriggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)) {
          trailingTriggerOrders
              .get(market)
              .get(triggerOrder.getTriggerInterval())
              .get(transactionType)
              .add(triggerOrder);

          sortOrders(
              trailingTriggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType),
              transactionType,
              market,
              (transactionType == TransactionType.BUY)
                  ? descendingTrailingStopPriceComparator
                  : ascendingTrailingStopPriceComparator);
        }
        log.trace(
            "Row Stop Price all tailing trigger orders"
                + trailingTriggerOrders.get(market).get(transactionType));
      }
    }
    if (triggerOrders.get(market) == null || triggerOrders.get(market).isEmpty()) {

      Map<FillType, List<Order>> fillTypeTriggerOrders =
          new ConcurrentHashMap<FillType, List<Order>>();
      if ((triggerOrder.getStopPrice() != null && !triggerOrder.getStopPrice().isZero())
          || (triggerOrder.getStopAmount() != null && !triggerOrder.getStopAmount().isZero())) {
        stopTriggerOrderQueue.add(triggerOrder);

        fillTypeTriggerOrders.put(FillType.STOP_LIMIT, stopTriggerOrderQueue);
      }

      if (triggerOrder.getTriggerTime() != null
          && triggerOrder.getTriggerTime().isAfter(context.getTime())) {
        timedTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.TIMED, timedTriggerOrderQueue);
      }

      if (triggerOrder.getLinkedOrder() != null) {
        multiLegTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.MULTI_LEG, multiLegTriggerOrderQueue);
      }

      if ((triggerOrder.getTargetPrice() != null && !triggerOrder.getTargetPrice().isZero())
          || (triggerOrder.getTargetAmount() != null && !triggerOrder.getTargetAmount().isZero())) {
        targetTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.TARGET_LIMIT, targetTriggerOrderQueue);
      }
      Map<TransactionType, Map<FillType, List<Order>>> transactionTypeTriggerOrders =
          new ConcurrentHashMap<TransactionType, Map<FillType, List<Order>>>();
      transactionTypeTriggerOrders.put(transactionType, fillTypeTriggerOrders);
      Map<Double, Map<TransactionType, Map<FillType, List<Order>>>> triggerIntervalTriggerOrders =
          new ConcurrentHashMap<Double, Map<TransactionType, Map<FillType, List<Order>>>>();
      triggerIntervalTriggerOrders.put(
          triggerOrder.getTriggerInterval(), transactionTypeTriggerOrders);
      triggerOrders.put(market, triggerIntervalTriggerOrders);
      return;
    } else if (triggerOrders.get(market).get(triggerOrder.getTriggerInterval()) == null
        || triggerOrders.get(market).get(triggerOrder.getTriggerInterval()).isEmpty()) {
      Map<FillType, List<Order>> fillTypeTriggerOrders =
          new ConcurrentHashMap<FillType, List<Order>>();

      if ((triggerOrder.getStopPrice() != null && !triggerOrder.getStopPrice().isZero())
          || (triggerOrder.getStopAmount() != null && !triggerOrder.getStopAmount().isZero())) {
        stopTriggerOrderQueue.add(triggerOrder);

        fillTypeTriggerOrders.put(FillType.STOP_LIMIT, stopTriggerOrderQueue);
      }

      if (triggerOrder.getTriggerTime() != null
          && triggerOrder.getTriggerTime().isAfter(context.getTime())) {
        timedTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.TIMED, timedTriggerOrderQueue);
      }

      if (triggerOrder.getLinkedOrder() != null) {
        multiLegTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.MULTI_LEG, multiLegTriggerOrderQueue);
      }

      if ((triggerOrder.getTargetPrice() != null && !triggerOrder.getTargetPrice().isZero())
          || (triggerOrder.getTargetAmount() != null && !triggerOrder.getTargetAmount().isZero())) {
        targetTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.TARGET_LIMIT, targetTriggerOrderQueue);
      }
      Map<TransactionType, Map<FillType, List<Order>>> transactionTypeTriggerOrders =
          new ConcurrentHashMap<TransactionType, Map<FillType, List<Order>>>();
      transactionTypeTriggerOrders.put(transactionType, fillTypeTriggerOrders);

      // so if the hash map is there, we just add our interval to it.
      triggerOrders
          .get(market)
          .put(triggerOrder.getTriggerInterval(), transactionTypeTriggerOrders);

    } else if (triggerOrders.get(market).get(triggerOrder.getTriggerInterval()).get(transactionType)
            == null
        || triggerOrders
            .get(market)
            .get(triggerOrder.getTriggerInterval())
            .get(transactionType)
            .isEmpty()) {

      Map<FillType, List<Order>> fillTypeTriggerOrders =
          new ConcurrentHashMap<FillType, List<Order>>();
      if ((triggerOrder.getStopPrice() != null && !triggerOrder.getStopPrice().isZero())
          || (triggerOrder.getStopAmount() != null && !triggerOrder.getStopAmount().isZero())) {
        stopTriggerOrderQueue.add(triggerOrder);

        fillTypeTriggerOrders.put(FillType.STOP_LIMIT, stopTriggerOrderQueue);
      }
      if (triggerOrder.getTriggerTime() != null
          && triggerOrder.getTriggerTime().isAfter(context.getTime())) {
        timedTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.TIMED, timedTriggerOrderQueue);
      }

      if (triggerOrder.getLinkedOrder() != null) {
        multiLegTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.MULTI_LEG, multiLegTriggerOrderQueue);
      }
      if ((triggerOrder.getTargetPrice() != null && !triggerOrder.getTargetPrice().isZero())
          || (triggerOrder.getTargetAmount() != null && !triggerOrder.getTargetAmount().isZero())) {
        targetTriggerOrderQueue.add(triggerOrder);
        fillTypeTriggerOrders.put(FillType.TARGET_LIMIT, targetTriggerOrderQueue);
      }

      triggerOrders
          .get(market)
          .get(triggerOrder.getTriggerInterval())
          .put(transactionType, fillTypeTriggerOrders);

      return;

    } else {
      if ((triggerOrder.getStopPrice() != null && !triggerOrder.getStopPrice().isZero())
          || (triggerOrder.getStopAmount() != null && !triggerOrder.getStopAmount().isZero())) {
        if (triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.STOP_LIMIT)
            == null) {
          stopTriggerOrderQueue.add(triggerOrder);
          triggerOrders
              .get(market)
              .get(triggerOrder.getTriggerInterval())
              .get(transactionType)
              .put(FillType.STOP_LIMIT, stopTriggerOrderQueue);
        } else {
          synchronized (
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.STOP_LIMIT)) {
            if (!triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.STOP_LIMIT)
                .contains(triggerOrder)) {
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.STOP_LIMIT)
                  .add(triggerOrder);
              sortOrders(
                  triggerOrders
                      .get(market)
                      .get(triggerOrder.getTriggerInterval())
                      .get(transactionType)
                      .get(FillType.STOP_LIMIT),
                  transactionType,
                  market,
                  (transactionType == TransactionType.BUY)
                      ? ascendingStopPriceComparator
                      : descendingStopPriceComparator);
              log.trace(
                  "Row Stop Price all trigger orders"
                      + triggerOrders
                          .get(market)
                          .get(triggerOrder.getTriggerInterval())
                          .get(transactionType)
                          .get(FillType.STOP_LIMIT));
            }
          }
        }
      }

      if (triggerOrder.getTriggerTime() != null
          && triggerOrder.getTriggerTime().isAfter(context.getTime())) {
        if (triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.TIMED)
            == null) {

          timedTriggerOrderQueue.add(triggerOrder);
          triggerOrders
              .get(market)
              .get(triggerOrder.getTriggerInterval())
              .get(transactionType)
              .put(FillType.TIMED, timedTriggerOrderQueue);
        } else {
          synchronized (
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.TIMED)) {
            if (!triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.TIMED)
                .contains(triggerOrder)) {
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.TIMED)
                  .add(triggerOrder);
              sortOrders(
                  triggerOrders
                      .get(market)
                      .get(triggerOrder.getTriggerInterval())
                      .get(transactionType)
                      .get(FillType.TIMED),
                  transactionType,
                  market,
                  timedOrderIdComparator);
              log.trace(
                  "Row timed all trigger orders"
                      + triggerOrders
                          .get(market)
                          .get(triggerOrder.getTriggerInterval())
                          .get(transactionType)
                          .get(FillType.TIMED));
            }
          }
        }
      }

      if (triggerOrder.getLinkedOrder() != null) {
        if (triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.MULTI_LEG)
            == null) {
          multiLegTriggerOrderQueue.add(triggerOrder);
          triggerOrders
              .get(market)
              .get(triggerOrder.getTriggerInterval())
              .get(transactionType)
              .put(FillType.MULTI_LEG, multiLegTriggerOrderQueue);
        } else {
          synchronized (
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.MULTI_LEG)) {
            if (!triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.MULTI_LEG)
                .contains(triggerOrder)) {
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.MULTI_LEG)
                  .add(triggerOrder);
              sortOrders(
                  triggerOrders
                      .get(market)
                      .get(triggerOrder.getTriggerInterval())
                      .get(transactionType)
                      .get(FillType.MULTI_LEG),
                  transactionType,
                  market,
                  (transactionType == TransactionType.BUY)
                      ? ascendingPriceComparator
                      : descendingPriceComparator);
              log.trace(
                  "Row multi leg all trigger orders"
                      + triggerOrders
                          .get(market)
                          .get(triggerOrder.getTriggerInterval())
                          .get(transactionType)
                          .get(FillType.MULTI_LEG));
            }
          }
        }
      }

      if ((triggerOrder.getTargetPrice() != null && !triggerOrder.getTargetPrice().isZero())
          || (triggerOrder.getTargetAmount() != null && !triggerOrder.getTargetAmount().isZero())) {
        if (triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.TARGET_LIMIT)
            == null) {

          targetTriggerOrderQueue.add(triggerOrder);
          triggerOrders
              .get(market)
              .get(triggerOrder.getTriggerInterval())
              .get(transactionType)
              .put(FillType.TARGET_LIMIT, targetTriggerOrderQueue);
        } else {
          synchronized (
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.TARGET_LIMIT)) {
            if (!triggerOrders
                .get(market)
                .get(triggerOrder.getTriggerInterval())
                .get(transactionType)
                .get(FillType.TARGET_LIMIT)
                .contains(triggerOrder)) {
              triggerOrders
                  .get(market)
                  .get(triggerOrder.getTriggerInterval())
                  .get(transactionType)
                  .get(FillType.TARGET_LIMIT)
                  .add(triggerOrder);
              sortOrders(
                  triggerOrders
                      .get(market)
                      .get(triggerOrder.getTriggerInterval())
                      .get(transactionType)
                      .get(FillType.TARGET_LIMIT),
                  transactionType,
                  market,
                  (transactionType == TransactionType.BUY)
                      ? descendingTargetPriceComparator
                      : ascendingTargetPriceComparator);
              log.trace(
                  "Row target Price all trigger orders"
                      + triggerOrders
                          .get(market)
                          .get(triggerOrder.getTriggerInterval())
                          .get(transactionType)
                          .get(FillType.TARGET_LIMIT));
            }
          }
        }
      }

      return;
    }
  }

  protected void printTable(Table<Order, Order, Order> table) {
    //  Map<Order, Map<Order, Order>> row;

    // sell rows are ascending
    // sell cols are dcesnding

    for (Order row : table.rowKeySet()) {
      log.trace(
          "At "
              + context.getTime()
              + (row.isBid() ? " Buy" : " Sell")
              + " Row Stop Price:"
              + row.getStopPrice());
    }

    for (Order col : table.columnKeySet()) {
      log.trace(
          "At "
              + context.getTime()
              + (col.isBid() ? " Buy" : " Sell")
              + " Column Stop Amount:"
              + (col.isBid()
                  ? (col.getStopPrice().minus(col.getStopAmount()))
                  : (col.getStopPrice().plus(col.getStopAmount()))));
    }
    /*
     * for (Entry<Order, Order> entry : table.row(row).entrySet()) { log.debug("At " + context.getTime() + " Row Stop Price:" + row.getStopPrice();
     * for (Order row : table.rowKeySet()) { for (Entry<Order, Order> entry : table.row(row).entrySet()) { log.debug("At " + context.getTime() +
     * " Row Stop Price:" + row.getStopPrice() + " Column Stop Amount:" + (entry.getValue().isBid() ?
     * (entry.getKey().getStopPrice().minus(entry.getKey().getStopAmount())) : (entry.getKey().getStopPrice() .plus(entry.getKey().getStopAmount())))
     * + (entry.getValue().isBid() ? " Buy " : " Sell ") + "OrderID:" + entry.getValue().getId() + " Stop Price " + entry.getValue().getStopPrice() +
     * " Stop Amount " + entry.getValue().getStopAmount()); } }
     */
    // lets itterate over teh columns in the row.
    //       for (Order col : table.row) {
    // table.remove(rowKey, columnKey)
    for (Cell<Order, Order, Order> cell : table.cellSet()) {
      //  log.debug(
      log.trace(
          "At "
              + context.getTime()
              + " Row Stop Price:"
              + cell.getRowKey().getStopPrice()
              + " Column Stop Amount:"
              + (cell.getValue().isBid()
                  ? (cell.getColumnKey().getStopPrice().minus(cell.getColumnKey().getStopAmount()))
                  : (cell.getColumnKey().getStopPrice().plus(cell.getColumnKey().getStopAmount())))
              + (cell.getValue().isBid() ? " Buy " : " Sell ")
              + "OrderID:"
              + cell.getValue().getUuid()
              + " Stop Price "
              + cell.getValue().getStopPrice()
              + " Stop Amount "
              + cell.getValue().getStopAmount());
    }
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
          log.debug(
              "Created new transaction from order: "
                  + order.getUuid()
                  + " transaction: "
                  + transaction);
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
          log.debug(
              "Created new transaction from fill: "
                  + fill.getUuid()
                  + " transaction: "
                  + transaction);
          // if (route)
          context.route(transaction);
          // else
          // context.publish(transaction);

        } catch (Exception e) {
          // TODO Auto-generated catch block
          log.error("Threw a Execption, full stack trace follows:", e);

          e.printStackTrace();
        }
        break;
    }
  }

  // rounding depends on whether it is a buy or sell order.  we round to the "best" price
  private static final RemainderHandler sellHandler =
      new RemainderHandler() {
        @Override
        public RoundingMode getRoundingMode() {
          return RoundingMode.CEILING;
        }
      };

  private static final RemainderHandler buyHandler =
      new RemainderHandler() {
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

  @Transient
  public Set<Order> getTriggerOrders() {
    //   ConcurrentHashMap<Event, ConcurrentLinkedQueue<Order>> triggerOrderMap = new
    // ConcurrentHashMap<Event, ConcurrentLinkedQueue<Order>>();
    Set<Order> triggerOrderQueue = new HashSet<Order>();
    for (Iterator<Tradeable> itm = triggerOrders.keySet().iterator(); itm.hasNext(); ) {
      Tradeable market = itm.next();
      for (Iterator<Double> itd = triggerOrders.get(market).keySet().iterator(); itd.hasNext(); ) {
        Double triggerInterval = itd.next();
        // synchronized()
        for (Iterator<TransactionType> ittt =
                triggerOrders.get(market).get(triggerInterval).keySet().iterator();
            ittt.hasNext(); ) {
          TransactionType transactionType = ittt.next();
          for (Iterator<FillType> itft =
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(transactionType)
                      .keySet()
                      .iterator();
              itft.hasNext(); ) {
            FillType fillType = itft.next();

            synchronized (
                triggerOrders.get(market).get(triggerInterval).get(transactionType).get(fillType)) {
              triggerOrderQueue.addAll(
                  triggerOrders
                      .get(market)
                      .get(triggerInterval)
                      .get(transactionType)
                      .get(fillType));
            }
          }
        }
      }
    }

    //
    return triggerOrderQueue;
  }

  @Override
  public Map<Order, OrderState> getOrderStateMap() {
    return orderStateMap;
  }

  protected void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  public BaseOrderService() {}

  private static ExecutorService service;

  private static CountDownLatch fillProcessingLatch = null;

  @Inject protected Context context;

  protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.orderService");

  protected boolean enableTrading = false;
  protected final Map<Order, OrderState> orderStateMap = new HashMap<Order, OrderState>();
  protected static int cancellationCount = 0;

  protected final Map<OrderState, Set<Order>> stateOrderMap = new HashMap<OrderState, Set<Order>>();
  @Inject protected transient QuoteService quotes;
  @Inject protected transient PortfolioService portfolioService;

  @Inject protected transient GeneralOrderFactory generalOrderFactory;

  @Inject protected transient SpecificOrderFactory specificOrderFactory;

  @Inject protected transient OrderUpdateFactory orderUpdateFactory;

  @Inject protected transient TransactionFactory transactionFactory;

  @Inject protected transient BookFactory bookFactory;

  @Inject protected transient FillFactory fillFactory;
}
