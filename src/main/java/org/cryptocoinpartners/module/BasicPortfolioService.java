package org.cryptocoinpartners.module;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.OrderBuilder;
import org.cryptocoinpartners.schema.OrderBuilder.SpecificOrderBuilder;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.PortfolioServiceException;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public class BasicPortfolioService implements PortfolioService {

	public BasicPortfolioService() {

	}

	@Override
	@Nullable
	public ArrayList<Position> getPositions(Portfolio portfolio) {
		//log.info("Last Tick Recived: " + getLastTrade(portfolio).toString());
		return (ArrayList<Position>) portfolio.getPositions();
	}

	@Override
	@Nullable
	public ArrayList<Position> getPositions(Portfolio portfolio, Exchange exchange) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DiscreteAmount getLastTrade(Portfolio portfolio) {

		List<Object> events = null;
		try {
			events = context.loadStatementByName("GET_LAST_TICK");
			if (events.size() > 0) {
				Trade trade = ((Trade) events.get(events.size() - 1));
				return (trade.getPrice());

			}
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DeploymentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	@Transient
	public Amount getCashBalance(Portfolio portfolio) {

		// sum of all transactions that belongs to this strategy
		BigDecimal balance = BigDecimal.ZERO;
		Collection<Transaction> transactions = getTrades(portfolio);
		Iterator<Transaction> balItr = transactions.iterator();
		while (balItr.hasNext()) {
			balance.add(balItr.next().getValue().asBigDecimal());

		}

		// plus part of all cashFlows
		BigDecimal cashFlows = BigDecimal.ZERO;

		Collection<Transaction> cashFlowTransactions = getCashFlows(portfolio);
		Iterator<Transaction> cashlItr = cashFlowTransactions.iterator();
		while (cashlItr.hasNext()) {
			cashFlows.add(cashlItr.next().getValue().asBigDecimal());

		}

		Amount amount = DecimalAmount.of(balance.add(cashFlows));
		return amount;
	}

	@Override
	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getCashFlows(Portfolio portfolio) {
		// return all CREDIT,DEBIT,INTREST and FEES
		ArrayList<Transaction> cashFlows = null;
		Collection<Transaction> transactions = portfolio.getTransactions();
		Iterator<Transaction> cashItr = transactions.iterator();
		while (cashItr.hasNext()) {
			Transaction transaction = cashItr.next();
			if (transaction.getType() == TransactionType.CREDIT || transaction.getType() == TransactionType.DEBIT
					|| transaction.getType() == TransactionType.INTREST || transaction.getType() == TransactionType.FEES) {
				cashFlows.add(transaction);
			}

		}
		return cashFlows;
	}

	@Override
	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getTrades(Portfolio portfolio) {
		//return all BUY and SELL
		ArrayList<Transaction> trades = null;
		Collection<Transaction> transactions = portfolio.getTransactions();
		Iterator<Transaction> balItr = transactions.iterator();
		while (balItr.hasNext()) {
			Transaction transaction = balItr.next();
			if (transaction.getType() == TransactionType.BUY || transaction.getType() == TransactionType.SELL) {
				trades.add(transaction);
			}

		}
		return trades;
	}

	@Override
	@Transient
	public DiscreteAmount getMarketPrice(Position postion) {

		if (postion.isOpen()) {
			if (postion.isShort()) {
				DiscreteAmount price = quotes.getLastAskForMarket(postion.getMarket()).getPrice();
				return price;

			} else {

				DiscreteAmount price = quotes.getLastBidForMarket(postion.getMarket()).getPrice();

				return price;
			}
		} else {
			return new DiscreteAmount(0, postion.getMarket().getVolumeBasis());

		}
	}

	@Override
	@Transient
	public Amount getMarketValue(Position postion) {

		if (postion.isOpen()) {

			return postion.getVolume().times(getMarketPrice(postion), Remainder.ROUND_EVEN);

		} else {
			return new DiscreteAmount(0, postion.getMarket().getVolumeBasis());

		}
	}

	@Override
	public void CreateTransaction(Portfolio portfolio, Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price) {
		Transaction transaction = new Transaction(portfolio, exchange, asset, type, amount, price);
		context.publish(transaction);
	}

	@Override
	public void exitPosition(Position position) throws Exception {

		reducePosition(position, (position.getVolume().abs()));
	}

	@Override
	public void reducePosition(final Position position, final Amount amount) {
		try {
			this.handleReducePosition(position, amount);
		} catch (Throwable th) {
			throw new PortfolioServiceException("Error performing 'PositionService.reducePosition(int positionId, long quantity)' --> " + th, th);
		}
	}

	@Override
	public void handleReducePosition(Position position, Amount amount) throws Exception {

		Market market = position.getMarket();
		OrderBuilder orderBuilder = new OrderBuilder(position.getPortfolio(), orderService);
		if (orderBuilder != null) {
			SpecificOrderBuilder exitOrder = orderBuilder.create(market, amount.negate());
			log.info("Entering trade with order " + exitOrder);
			orderService.placeOrder(exitOrder.getOrder());
		}

		if (!position.isOpen()) {
			//TOFO remove subsrcption
		}
	}

	@Override
	public void handleSetExitPrice(Position position, Amount exitPrice, boolean force) throws PortfolioServiceException {

		// there needs to be a position
		if (position == null) {
			throw new PortfolioServiceException("position does not exist: ");
		}
		if (!force && position.getExitPrice() == null) {
			log.warn("no exit value was set for position: " + position);
			return;
		}

		// we don't want to set the exitValue to Zero
		if (exitPrice.isZero()) {
			log.warn("setting of exit Pirice of zero is prohibited: " + exitPrice);
			return;
		}

		if (!force) {
			if (position.isShort() && exitPrice.compareTo(position.getExitPrice()) > 0) {
				log.warn("exit value " + exitPrice + " is higher than existing exit value " + position.getExitPrice() + " of short position " + position);
				return;
			} else if (position.isLong() && exitPrice.compareTo(position.getExitPrice()) < 0) {
				log.warn("exit value " + exitPrice + " is lower than existing exit value " + position.getExitPrice() + " of long position " + position);
				return;
			}
		}

		// exitValue cannot be lower than currentValue
		Amount currentPrice = getMarketPrice(position);

		if (position.isShort() && exitPrice.compareTo(currentPrice) < 0) {
			throw new PortfolioServiceException("ExitValue (" + exitPrice + ") for short-position " + position + " is lower than currentValue: " + exitPrice);
		} else if (position.isLong() && exitPrice.compareTo(currentPrice) > 0) {
			throw new PortfolioServiceException("ExitValue (" + exitPrice + ") for long-position " + position + " is higher than currentValue: " + currentPrice);
		}

		position.setExitPrice(exitPrice);

		log.info("set exit value " + position + " to " + exitPrice);
	}

	@Override
	public void handleSetMargin(Position position) throws Exception {
		//TODO manage setting and mainuplating margin
	}

	@Override
	public void handleSetMargins() throws Exception {
		//TODO manage setting and mainuplating margin
	}

	@Inject
	protected Context context;
	@Inject
	protected QuoteService quotes;
	@Inject
	protected OrderService orderService;
	@Inject
	private Logger log;
}
