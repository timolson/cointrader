package org.cryptocoinpartners.schema;

import java.util.Collection;
import java.util.HashMap;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.service.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Strategy represents a configurable approach to trading, but not a specific trading algorithm. StrategyPortfolioManager instantiates a Strategy by
 * loading to a module which contains a Strategy service using a specific configuration set by the StrategyPortfolioManager. The Strategy may then
 * place Orders against Positions in the StrategyPortfolioManager's Portfolio. BaseStrategy helps implement Strategies by providing injected fields
 * for a QuoteService and OrderBuilder.
 * 
 * @author Tim Olson
 */
public class BaseStrategy implements Strategy {

	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.baseStrategy");
	protected static HashMap<Tradeable, Double[]> marketAllocations = new HashMap<Tradeable, Double[]>();

	@Override
	public synchronized void setPortfolio(Portfolio portfolio) {
		// portfolioService
		//    this.portfolio = portfolio;
		this.portfolio = portfolio;
		// 
		if (getMarkets() != null) {
			for (Tradeable market : getMarkets())
				portfolio.addMarket(market);

			portfolio.merge();

		}

		//  if (getMarket() != null && getMarket().getExchange() != null && (getMarket().getExchange().getBalances() == null)
		//        || getMarket().getExchange().getBalances().isEmpty())

		//  getMarket().getExchange().loadBalances(portfolio);

		SubscribePortfolio portfolioSubcribeEvent = new SubscribePortfolio(portfolio);
		portfolio.getContext().publish(portfolioSubcribeEvent);
		originalBaseNotionalBalance = portfolio.getBaseNotionalBalance();
		// originalNotionalBalanceUSD
		startingOriginalBaseNotionalBalance = portfolio.getBaseNotionalBalance();

		// PersistUtil.insert(portfolio);
		//  order = new OrderBuilder(portfolio, orderService);
		log = portfolio.getLogger();
	}

	// @Inject
	protected synchronized void setPortfolioService(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	protected synchronized void setQuotes(QuoteService quotes) {
		this.quotes = quotes;
	}

	protected synchronized void setOrderService(OrderService orderService) {
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

	@Override
	@Transient
	public Portfolio getPortfolio() {
		return this.portfolio;
	}

	@Transient
	public static Collection<Tradeable> getMarkets() {

		return getMarketAllocations().keySet();

	}

	@Transient
	public static HashMap<Tradeable, Double[]> getMarketAllocations() {

		return marketAllocations;

	}

	@Transient
	public static Double getMarketAllocation(Tradeable market) {

		return marketAllocations.get(market)[0];

	}

	@Transient
	public static Double getMarginMultiplier(Tradeable market) {

		return marketAllocations.get(market)[1];

	}

	@Transient
	public static Tradeable getMarket(Tradeable market) {
		for (Tradeable strategyMarket : getMarkets())
			if (market.equals(strategyMarket))

				return strategyMarket;
		return null;

	}

	@Transient
	public boolean hasMarkets() {
		return (getMarkets() != null && !getMarkets().isEmpty());
	}

	public synchronized Tradeable addMarket(Tradeable market, Double allocation) {
		//   synchronized (lock) {
		if (market != null && !getMarkets().contains(market)) {

			getMarketAllocations().put(market, new Double[] { allocation, 1d });

		}
		if (market != null)
			return getMarket(market);
		else
			return null;
	}

	public synchronized Tradeable addMarket(Tradeable market, Double allocation, Double multiplier) {
		//   synchronized (lock) {
		if (market != null && !getMarkets().contains(market)) {

			getMarketAllocations().put(market, new Double[] { allocation, multiplier });

		}
		if (market != null)
			return getMarket(market);
		else
			return null;
	}

	public synchronized void addMarket(Collection<Market> markets) {
		getMarkets().addAll(markets);

	}

	public synchronized void removeMarkets(Collection<Market> removedMarkets) {
		if (getMarkets().removeAll(removedMarkets))
			getMarketAllocations().remove(removedMarkets);
	}

	public synchronized void removeAllMarkets() {

		getMarkets().clear();
		getMarketAllocations().clear();

	}

	public synchronized void removeMarket(Market market) {
		log.info("removing market: " + market + " from portfolio: " + this);
		if (getMarkets().remove(market)) {
			getMarketAllocations().remove(market);

			log.info("removed market: " + market + " from portfolio: " + this);
		}

	}

	/** This tracks the assets you have for trading */

	/**
	 * This is what you use to place orders:
	 * 
	 * <pre>
	 * order.create(Listing.BTC_USD, 1.00).withLimit(651.538).place();
	 * </pre>
	 */
	protected static OrderBuilder order;
	public static Amount originalBaseNotionalBalance;
	public static Amount startingOriginalBaseNotionalBalance;

	/** You may use this service to query the most recent Trades and Books for all Listings and Markets. */
	@Inject
	protected transient QuoteService quotes;
	@Inject
	protected transient Portfolio portfolio;
	@Inject
	protected transient Context context;
	@Inject
	protected transient OrderService orderService;
	@Inject
	protected transient PortfolioService portfolioService;
	@Inject
	protected transient GeneralOrderFactory generalOrderFactory;

	@Inject
	protected transient SpecificOrderFactory specificOrderFactory;

	@Inject
	protected transient TransactionFactory transactionFactory;

	@Inject
	protected transient ExchangeFactory exchangeFactory;

	@Inject
	protected transient static TradeFactory tradeFactory;

	//   @Inject

	@Override
	public void init() {
		portfolioService.init();
		orderService.init();

	}
}
