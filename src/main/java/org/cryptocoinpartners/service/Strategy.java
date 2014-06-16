package org.cryptocoinpartners.service;

import org.cryptocoinpartners.schema.StrategyFundManager;


/**
 * A Strategy represents a configurable approach to trading, but not a specific trading algorithm.  StrategyFundManager
 * instantiates a Strategy by loading to a module which contains a Strategy service using a specific configuration set
 * by the StrategyFundManager.  The Strategy may then place Orders against Positions in the StrategyFundManager's Fund.
 * BaseStrategy helps implement Strategies by providing injected fields for a QuoteService and OrderBuilder.
 *
 * @author Tim Olson
 */
public interface Strategy {
    public void setStrategyFundManager( StrategyFundManager strategyFundManager );
}
