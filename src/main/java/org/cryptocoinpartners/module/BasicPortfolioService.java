package org.cryptocoinpartners.module;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.schema.OrderBuilder.SpecificOrderBuilder;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.PortfolioServiceException;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
		} catch (ParseException | DeploymentException | IOException e) {
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
        for( Transaction transaction : transactions ) {
            balance.add(transaction.getValue().asBigDecimal());
        }

		// plus part of all cashFlows
		BigDecimal cashFlows = BigDecimal.ZERO;

		Collection<Transaction> cashFlowTransactions = getCashFlows(portfolio);
        for( Transaction cashFlowTransaction : cashFlowTransactions ) {
            cashFlows.add(cashFlowTransaction.getValue().asBigDecimal());
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
        for( Transaction transaction : transactions ) {
            if( transaction.getType() == TransactionType.CREDIT || transaction.getType() == TransactionType.DEBIT
                        || transaction.getType() == TransactionType.INTREST || transaction
                                                                                       .getType() == TransactionType.FEES ) {
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
        for( Transaction transaction : transactions ) {
            if( transaction.getType() == TransactionType.BUY || transaction.getType() == TransactionType.SELL ) {
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
				@SuppressWarnings("ConstantConditions")
                DiscreteAmount price = quotes.getLastAskForMarket(postion.getMarket()).getPrice();
				return price;

			} else {
                @SuppressWarnings("ConstantConditions")
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
			//TODO remove subsrcption
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
