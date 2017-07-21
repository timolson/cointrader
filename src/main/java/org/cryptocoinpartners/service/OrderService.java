package org.cryptocoinpartners.service;

import java.util.Collection;
import java.util.Map;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.exceptions.OrderNotFoundException;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Tradeable;

@Service
public interface OrderService {

  // send new Order events to the correct market
  public boolean placeOrder(Order order) throws Throwable;

  void init();

  public Collection<SpecificOrder> getPendingLongOpenOrders(Portfolio portfolio, Market market);

  public Collection<SpecificOrder> getPendingLongOpenOrders(Portfolio portfolio, Market market, double orderGroup);

  public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, Market market);

  public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, Market market, double orderGroup);

  public Collection<SpecificOrder> getPendingShortOpenOrders(Portfolio portfolio, Market market);

  public Collection<SpecificOrder> getPendingShortOpenOrders(Portfolio portfolio, Market market, double orderGroup);

  public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, Market market);

  public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, Market market, double orderGroup);

  public Collection<SpecificOrder> getPendingCloseOrders(Portfolio portfolio, Market market);

  public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, ExecutionInstruction execInst, Market market);

  public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, ExecutionInstruction execInst, Market market, double interval);

  public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, ExecutionInstruction execInst, Market market);

  public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, ExecutionInstruction execInst, Market market, double interval);

  public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio, Market market);

  public Collection<Order> getPendingStopOrders(Portfolio portfolio, Market market);

  public boolean handleCancelSpecificOrder(SpecificOrder specificOrder);

  public boolean handleCancelGeneralOrder(GeneralOrder generalOrder);

  public Collection<Order> handleCancelAllShortStopOrders(Portfolio portfolio, Market market);

  public Collection<Order> handleCancelAllLongStopOrders(Portfolio portfolio, Market market);

  public Collection<Order> handleCancelAllLongStopOrders(Portfolio portfolio, Market market, double orderGroup);

  public Collection<SpecificOrder> handleCancelAllSpecificOrders(Portfolio portfolio, Market market);

  public void adjustShortStopLoss(Amount price, Amount stopAdjustment, Boolean force, double orderGroup);

  public void adjustLongStopLoss(Amount price, Amount stopAdjustment, Boolean force, double orderGroup);

  public void adjustShortTargetPrices(Amount price, Amount targetAdjustment, double orderGroup);

  public void adjustLongTargetPrices(Amount price, Amount targetAdjustment, double orderGroup);

  // DO PLENTY OF LOGGING IN THIS METHOD
  // initialize data interface with Xchange
  // initialize order interface with Xchange
  // if paper trade... then replace order interface with paper simulator
  // if simulation... then replace both data and order interfaces with simulator
  //
  // if exchange is specified...
  //     send to exchange using order interface
  // otherwise...
  //     use data interface to look at current order book for each market that has the listing
  //     guess at how much might fill on each market at what price
  //     sort the list of fill assumptions by best price
  //     create and submit one or more sub-orders to each market in the fill list
  //         - don't over-subscribe
  //         - do add a short auto-cancellation time (or shorter if specified on order)
  //                      AKA reroute timeout
  //                      retry routing periodically as books change vvvvv
  //         - add a timeout handler for markets that failed to fill where the sub-order has been canceled
  //             - this handler should try the next best market
  //                 - this can be based on data from time of placement, or from the current data interface

  public OrderState getOrderState(Order o) throws IllegalStateException;

  Collection<Order> getPendingOrders();

  Collection<SpecificOrder> handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market);

  Collection<SpecificOrder> handleCancelAllLongOpeningSpecificOrders(Portfolio portfolio, Market market);

  Collection<SpecificOrder> handleCancelAllLongOpeningSpecificOrders(Portfolio portfolio, Market market, double interval);

  Collection<Order> handleCancelAllLongOpeningGeneralOrders(Portfolio portfolio, Market market);

  Collection<Order> handleCancelAllLongOpeningGeneralOrders(Portfolio portfolio, Market market, double interval);

  Collection<SpecificOrder> handleCancelAllShortOpeningSpecificOrders(Portfolio portfolio, Market market);

  Collection<Order> handleCancelAllShortOpeningGeneralOrders(Portfolio portfolio, Market market);

  Collection<SpecificOrder> handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market);

  Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst,
      double orderGroup);

  Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst);

  Collection<SpecificOrder> handleCancelAllShortClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst);

  Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio);

  void setTradingEnabled(Boolean enableTrading);

  boolean getTradingEnabled();

  Order getPendingTriggerOrder(Order order);

  boolean handleCancelSpecificOrderByParentFill(Fill parentFill) throws OrderNotFoundException;

  void updateWorkingOrderQuantity(Order order, Amount quantity);

  Collection<Order> getPendingShortStopOrders(Portfolio portfolio, Market market);

  Collection<Order> getPendingShortStopOrders(Portfolio portfolio, Market market, double interval);

  Collection<Order> getPendingShortTriggerOrders(Portfolio portfolio, Market market, double interval);

  Collection<Order> getPendingLongStopOrders(Portfolio portfolio, Market market);

  Collection<Order> getPendingLongStopOrders(Portfolio portfolio, Market market, double interval);

  Collection<Order> getPendingLongTriggerOrders(Portfolio portfolio, Market market, double interval);

  void handleCancelAllTriggerOrdersByParentFill(Fill parentFill);

  public Map<Order, OrderState> getOrderStateMap();

  Collection<SpecificOrder> cancelSpecificOrder(Collection<SpecificOrder> orders);

  public Collection<Fill> getFills(Market market, Portfolio portfolio);

  void handleFillProcessing(Fill fill);

  Collection<SpecificOrder> getPendingLongOrders();

  Collection<SpecificOrder> getPendingShortOrders();

  Collection<SpecificOrder> handleCancelAllShortClosingSpecificOrders(Portfolio portfolio, Market market);

  Collection<SpecificOrder> handleCancelAllShortClosingSpecificOrders(Portfolio portfolio, Market market, double orderGroup);

  Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market);

  Collection<SpecificOrder> handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market, double orderGroup);

  Collection<Order> getRoutedLongStopOrders(Portfolio portfolio, Market market);

  Collection<Order> getRoutedShortStopOrders(Portfolio portfolio, Market market);

  Collection<SpecificOrder> handleCancelAllShortOpeningSpecificOrders(Portfolio portfolio, Market market, double interval);

  Collection<Order> handleCancelAllShortOpeningGeneralOrders(Portfolio portfolio, Market market, double interval);

  Order triggerOrder(Order triggeredOrder, String comment, Collection<Order> triggeredOrders);

  void adjustLongStopLossByAmount(Amount price, Boolean force, double orderGroup, double scaleFactor);

  void adjustShortStopLossByAmount(Amount price, Boolean force, double orderGroup, double scaleFactor);

  void sortLongStopOrders(Tradeable market);

  void sortShortStopOrders(Tradeable market);

  boolean handleCancelOrders(Collection<Order> orders);

  boolean handleCancelOrder(Order order);

  void lockTriggerOrders();

  void unlockTriggerOrders();

  // CountDownLatch getFillProcessingLatch();

}
