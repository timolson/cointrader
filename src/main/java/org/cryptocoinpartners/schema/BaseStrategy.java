package org.cryptocoinpartners.schema;

import javax.inject.Inject;

import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.service.Strategy;
import org.slf4j.Logger;

/**
 * A Strategy represents a configurable approach to trading, but not a specific trading algorithm.  StrategyPortfolioManager
 * instantiates a Strategy by loading to a module which contains a Strategy service using a specific configuration set
 * by the StrategyPortfolioManager.  The Strategy may then place Orders against Positions in the StrategyPortfolioManager's Portfolio.
 * BaseStrategy helps implement Strategies by providing injected fields for a QuoteService and OrderBuilder.
 *
 * @author Tim Olson
 */
public class BaseStrategy implements Strategy {

	@Inject
	protected void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
		Asset baseAsset = Asset.forSymbol(context.getConfig().getString("base.symbol", "USD"));
		portfolio.setBaseAsset(baseAsset);
		order = new OrderBuilder(portfolio, orderService);
	}

	@Inject
	protected void setPortfolioService(BasicPortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	/** This tracks the assets you have for trading */
	protected Portfolio portfolio;
	protected BasicPortfolioService portfolioService;

	/** This is what you use to place orders:
	 * <pre>
	 * order.create(Listing.BTC_USD,1.00).withLimit(651.538).place();
	 * </pre>
	 */
	protected OrderBuilder order;

	/** You may use this service to query the most recent Trades and Books for all Listings and Markets. */
	@Inject
	protected QuoteService quotes;

	@Inject
	protected Context context;
	@Inject
	protected OrderService orderService;

	@Inject
	protected Logger log;

}
