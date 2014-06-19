package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.service.Strategy;
import org.slf4j.Logger;

import javax.inject.Inject;


/**
 * A Strategy represents a configurable approach to trading, but not a specific trading algorithm.  StrategyFundManager
 * instantiates a Strategy by loading to a module which contains a Strategy service using a specific configuration set
 * by the StrategyFundManager.  The Strategy may then place Orders against Positions in the StrategyFundManager's Fund.
 * BaseStrategy helps implement Strategies by providing injected fields for a QuoteService and OrderBuilder.
 *
 * @author Tim Olson
 */
public class BaseStrategy implements Strategy {


    public void setStrategyFundManager(StrategyFundManager strategyFundManager) {
        this.manager = strategyFundManager;
        order = new OrderBuilder(manager.getFund(),orderService);
    }


    /** This is what you use to place orders:
     * <pre>
     * order.create(Listing.BTC_USD,1.00).withLimit(651.538).place();
     * </pre>
     */
    @Inject
    protected OrderBuilder order;

    /** You may use this service to query the most recent Trades and Books for all Listings and Markets. */
    @Inject
    protected QuoteService quotes;

    @Inject
    protected StrategyFundManager manager;

    @Inject
    protected OrderService orderService;

    @Inject
    protected Logger log;

}
