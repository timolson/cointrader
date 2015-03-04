package org.cryptocoinpartners.service;

import java.util.Collection;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;

@Service
public interface OrderService {

    // send new Order events to the correct market
    public void placeOrder(Order order);

    public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio);

    public void handleCancelSpecificOrder(SpecificOrder specificOrder);

    public void handleCancelGeneralOrder(GeneralOrder generalOrder);

    public void handleCancelAllStopOrders(Portfolio portfolio, Market market);

    public void handleCancelAllSpecificOrders(Portfolio portfolio, Market market);

    public void adjustStopLoss(Amount price, Amount stopAdjustment);

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

    public OrderState getOrderState(Order o);

    void cancelOrder(Order order);

    Collection<SpecificOrder> getPendingOrders();

    void handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market);

    void handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market);

}
