package org.cryptocoinpartners.service;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Transaction;

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
	public ArrayList<Position> getPositions(Portfolio portfolio);

	public void CreateTransaction(Portfolio portfolio, Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price);

	/** returns all Postions for the given Exchange.  NOTE: if you have open orders, you will not be able to trade
	 * all the Positions returned by this method.  Use getTradeablePositions() instead. */
	@Nullable
	public ArrayList<Position> getPositions(Portfolio portfolio, Exchange exchange);

	public DiscreteAmount getLastTrade(Portfolio portfolio);

	@Transient
	public Amount getCashBalance(Portfolio portfolio);

	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getCashFlows(Portfolio portfolio);

	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getTrades(Portfolio portfolio);

	@Transient
	public DiscreteAmount getMarketPrice(Position postion);

	@Transient
	public Amount getMarketValue(Position postion);

	void exitPosition(Position position) throws Exception;

	void reducePosition(Position position, Amount amount);

	void handleReducePosition(Position position, Amount amount) throws Exception;

	void handleSetExitPrice(Position position, Amount exitPrice, boolean force) throws PortfolioServiceException;

	void handleSetMargin(Position position) throws Exception;

	void handleSetMargins() throws Exception;

}
