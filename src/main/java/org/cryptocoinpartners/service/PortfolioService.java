package org.cryptocoinpartners.service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;

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

    public void CreateTransaction(Portfolio portfolio, Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price);

    /** returns all Postions for the given Exchange.  NOTE: if you have open orders, you will not be able to trade
     * all the Positions returned by this method.  Use getTradeablePositions() instead. */
    @Nullable
    public Collection<Position> getPositions(Exchange exchange);

    public DiscreteAmount getLastTrade();

    @Transient
    public Amount getCashBalance(Asset quoteAsset);

    @Transient
    public DiscreteAmount getMarketPrice(Position postion);

    @Transient
    public Amount getMarketValue(Position postion);

    @Transient
    public Amount getMarketValue(Asset quoteAsset);

    void exitPosition(Position position) throws Exception;

    void reducePosition(Position position, Amount amount);

    void handleReducePosition(Position position, Amount amount) throws Exception;

    void handleSetMargin(Position position) throws Exception;

    void handleSetMargins() throws Exception;

    ConcurrentHashMap<Asset, Amount> getMarketValues();

    Map<Asset, Amount> getCashBalances();

    ConcurrentHashMap<Asset, Amount> getRealisedPnLs();

    Amount getRealisedPnL(Asset quoteAsset);

    Collection<Portfolio> getPortfolios();

    abstract void setPortfolios(Collection<Portfolio> Portfolios);

    void addPortfolio(Portfolio portfolio);

    ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> getRealisedPnLByMarket();

    ConcurrentHashMap<Asset, Amount> getUnrealisedPnLs();

    Amount getUnrealisedPnL(Position postion);

    Amount getUnrealisedPnL(Asset quoteAsset);

    Collection<Position> getPositions(Asset asset, Exchange exchange);

    ConcurrentHashMap<Asset, Amount> getAvailableBalances();

    Amount getAvailableBalance(Asset quoteAsset);

    Amount getBaseCashBalance(Asset quoteAsset);

    Amount getBaseUnrealisedPnL(Asset quoteAsset);

    Amount getBaseRealisedPnL(Asset quoteAsset);

    Amount getBaseMarketValue(Asset quoteAsset);

    Amount getAvailableBaseBalance(Asset quoteAsset);

    DiscreteAmount getNetPosition(Asset base, Exchange exchange);

    void resetBalances();

    void init();

}
