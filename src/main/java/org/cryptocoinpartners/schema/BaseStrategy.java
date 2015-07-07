package org.cryptocoinpartners.schema;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.service.Strategy;
import org.cryptocoinpartners.util.PersistUtil;
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

    @Override
    public void setPortfolio(Portfolio portfolio) {
        // portfolioService
        //    this.portfolio = portfolio;
        this.portfolio = portfolio;
        SubscribePortfolio portfolioSubcribeEvent = new SubscribePortfolio(portfolio);
        portfolio.getContext().publish(portfolioSubcribeEvent);
        Asset baseAsset = Asset.forSymbol(portfolio.getContext().getConfig().getString("base.symbol", "USD"));
        portfolio.setBaseAsset(baseAsset);
        PersistUtil.merge(portfolio);
        order = new OrderBuilder(portfolio, orderService);
        log = portfolio.getLogger();
    }

    // @Inject
    protected void setPortfolioService(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    protected void setQuotes(QuoteService quotes) {
        this.quotes = quotes;
    }

    protected void setOrderService(OrderService orderService) {
        this.orderService = orderService;
    }

    @Transient
    protected PortfolioService getPortfolioService() {
        return this.portfolioService;
    }

    @Transient
    protected OrderService getOrderService() {
        return this.orderService;
    }

    @Transient
    protected QuoteService getQuotes() {
        return this.quotes;
    }

    @Transient
    protected Portfolio getPortfolio() {
        return this.portfolio;
    }

    /** This tracks the assets you have for trading */

    /** This is what you use to place orders:
     * <pre>
     * order.create(Listing.BTC_USD,1.00).withLimit(651.538).place();
     * </pre>
     */
    protected static OrderBuilder order;

    /** You may use this service to query the most recent Trades and Books for all Listings and Markets. */
    @Inject
    protected transient QuoteService quotes;

    protected transient Portfolio portfolio;
    @Inject
    protected transient Context context;
    @Inject
    protected transient OrderService orderService;
    @Inject
    protected transient PortfolioService portfolioService;
    // @Inject
    protected static Logger log;

    @Override
    public void init() {
        portfolioService.init();
        orderService.init();

    }
}
