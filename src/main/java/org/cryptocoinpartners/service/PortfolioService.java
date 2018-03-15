package org.cryptocoinpartners.service;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Trade;

/**
 * PortfolioService reports
 *
 * @author Tim Olson
 */
@Service
public interface PortfolioService {

	/** returns all Positions in all Exchanges.  NOTE: if you have open orders, you will not be able to trade
	 * all the Positions returned by this method.  Use getTradeablePositions() instead. */
	@Nullable
	public Collection<Position> getPositions();

	// public void CreateTransaction(Portfolio portfolio, Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price);

	/** returns all Postions for the given Exchange.  NOTE: if you have open orders, you will not be able to trade
	 * all the Positions returned by this method.  Use getTradeablePositions() instead. */
	@Nullable
	public Collection<Position> getPositions(Exchange exchange);

	@Transient
	public Amount getCashBalance(Asset quoteAsset);

	@Transient
	public Trade getMarketPrice(Position postion);

	@Transient
	public Amount getMarketValue(Position postion);

	@Transient
	public Amount getMarketValue(Position postion, Asset quoteAsset);

	@Transient
	public Amount getMarketValue(Asset quoteAsset);

	void exitPosition(Position position) throws Exception;

	void reducePosition(Position position, Amount amount);

	void handleReducePosition(Position position, Amount amount) throws Exception;

	void handleSetMargin(Position position) throws Exception;

	void handleSetMargins() throws Exception;

	Map<Asset, Amount> getMarketValues();

	Map<Asset, Amount> getCashBalances();

	Map<Asset, Amount> getRealisedPnLs();

	Amount getRealisedPnL(Asset quoteAsset);

	Collection<Portfolio> getPortfolios();

	abstract void setPortfolios(Collection<Portfolio> Portfolios);

	void addPortfolio(Portfolio portfolio);

	Map<Asset, Map<Exchange, Map<Listing, Amount>>> getRealisedPnLByMarket();

	Map<Asset, Amount> getUnrealisedPnLs();

	Amount getUnrealisedPnL(Position postion, Amount markToMarketPrice);

	Amount getBaseUnrealisedPnL(Position postion, Asset quoteAsset);

	Amount getBaseUnrealisedPnL(Position postion, Asset quoteAsset, DiscreteAmount marketPrice);

	Amount getUnrealisedPnL(Asset quoteAsset);

	Collection<Position> getPositions(Asset asset, Exchange exchange);

	Map<Asset, Amount> getAvailableBalances();

	Amount getAvailableBalance(Asset quoteAsset);

	Amount getAvailableBalance(Asset quoteAsset, Exchange exchange);

	Amount getBaseCashBalance(Asset quoteAsset);

	Amount getBaseUnrealisedPnL(Asset quoteAsset);

	Amount getBaseRealisedPnL(Asset quoteAsset);

	Amount getBaseRealisedPnL(Asset quoteAsset, Market market);

	Amount getBaseMarketValue(Asset quoteAsset);

	Amount getAvailableBaseBalance(Asset quoteAsset);

	Amount getAvailableBaseBalance(Asset quoteAsset, Exchange exchange);

	DiscreteAmount getNetPosition(Asset base, Exchange exchange);

	void resetBalances();

	void init();

	void reset();

	void loadBalances();

	Map<Asset, Amount> getUnrealisedPnLs(Market market);

	Amount getBaseUnrealisedPnL(Asset quoteAsset, Market market);

	Map<Asset, Amount> getRealisedPnLs(Market market);

	Map<Asset, Amount> getUnrealisedPnLs(Exchange exchange);

	Trade getMarketPrice(Listing listing);

	Trade getMarketPrice(Market market);

}
