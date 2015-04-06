package org.cryptocoinpartners.service;


/**
 * A Strategy represents a configurable approach to trading, but not a specific trading algorithm.  StrategyPortfolioManager
 * instantiates a Strategy by loading to a module which contains a Strategy service using a specific configuration set
 * by the StrategyPortfolioManager.  The Strategy may then place Orders against Positions in the StrategyPortfolioManager's Portfolio.
 * BaseStrategy helps implement Strategies by providing injected fields for a QuoteService and OrderBuilder.
 *
 * @author Tim Olson
 */
public interface Strategy {
    public void init();

}
