package org.cryptocoinpartners.service;

import org.cryptocoinpartners.schema.*;

/**
 * Created with IntelliJ IDEA.
 * User: mike_d_olson
 * Date: 6/8/14
 * Time: 2:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class OrderService {
    // TODO: tie to esper
    // send new Order events to the correct market
    public void routeOrder(Order order)
    {
        // DO PLENTY OF LOGGING IN THIS METHOD
        // initialize data interface with Xchange
        // initialize order interface with Xchange
        // if paper trade... then replace order interface with paper simulator
        // if simulation... then replace both data and order interfaces with simulator
        //
        // if market is specified...
        //     send to market using order interface
        // otherwise...
        //     use data interface to look at current order book for each market that has the listing
        //     guess at how much might fill on each market at what price
        //     sort the list of fill assumptions by best price
        //     create and submit one or more sub-orders to each market in the fill list
        //         - don't over-subscribe
        //         - do add a short auto-cancellation time (or shorter if specified on order)
        //         - add a timeout handler for markets that failed to fill where the sub-order has been canceled
        //             - this handler should try the next best market
        //                 - this can be based on data from time of placement, or from the current data interface
    }
}
