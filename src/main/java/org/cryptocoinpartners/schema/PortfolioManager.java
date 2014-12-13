package org.cryptocoinpartners.schema;

// import java.util.logging.Logger;

import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PortfolioManagers are allowed to control the Positions within a Portfolio
 *
 * @author Tim Olson
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class PortfolioManager extends EntityBase implements Context.AttachListener {

	private BasicPortfolioService portfolioService;

	// todo we need to get the tradeable portfolio separately from the "reserved" portfolio (assets needed for open orders)
	@OneToOne
	public Portfolio getPortfolio() {
		return portfolio;
	}

	@Transient
	public BasicPortfolioService getPortfolioService() {
		return portfolioService;
	}

	@When("@Priority(9) select * from OrderUpdate where state.open=true`")
	private void updateReservation(OrderUpdate update) {

		//removes the reservation from the transactions
		Transaction reservation = update.getOrder().getReservation();
		if (reservation != null && update.getState() != OrderState.NEW) {
			if (reservation.getType().equals(TransactionType.BUY_RESERVATION) || reservation.getType().equals(TransactionType.SELL_RESERVATION)) {
				Amount updateAmount = reservation.getType().equals(TransactionType.BUY_RESERVATION) ? (update.getOrder().getUnfilledVolume().times(update
						.getOrder().getLimitPrice(), Remainder.ROUND_EVEN)).negate() : update.getOrder().getVolume();
				reservation.setAmount(updateAmount);
			}
		}
	}

	@When("@Priority(9) select * from OrderUpdate where state.open=false")
	private void removeReservation(OrderUpdate update) {
		//removes the reservation from the transactions
		Transaction reservation = update.getOrder().getReservation();
		switch (update.getState()) {
			case NEW:
				break;
			case TRIGGER:
				break;
			case ROUTED:
				break;
			case PLACED:
				break;
			case PARTFILLED:
				break;
			case FILLED:
				portfolio.removeTransaction(reservation);
				break;
			case CANCELLING:
				break;
			case CANCELLED:
				portfolio.removeTransaction(reservation);
				break;
			case REJECTED:
				//portfolio.removeTransaction(reservation);
				break;
			case EXPIRED:
				//portfolio.removeTransaction(reservation);
				break;
			default:
				log.warn("Unknown order state: " + update.getState());
				break;
		}

	}

	@When("@Priority(8) select * from Transaction as transaction")
	public void handleTransaction(Transaction transaction) {
		PersistUtil.insert(transaction);

		if (transaction.getPortfolio() == (portfolio)) {

			Portfolio portfolio = transaction.getPortfolio();

			Asset baseAsset = transaction.getAmount().isPositive() ? transaction.getCurrency() : transaction.getAsset();

			Market market = transaction.getMarket();
			Amount amount = transaction.getAmount().isPositive() ? transaction.getAmount() : transaction.getAssetAmount();
			TransactionType type = transaction.getType();
			Amount price = transaction.getPrice();
			Exchange exchange = transaction.getExchange();
			// Add transaction to approraite portfolio
			portfolio.addTransaction(transaction);
			Position position;
			// update postion
			if (type == TransactionType.BUY || type == TransactionType.SELL) {
				if (transaction.getFill().getOrder().getStopPrice() != null) {
					position = new Position(portfolio, exchange, market, baseAsset, amount, price, transaction.getFill().getOrder().getStopPrice());
				} else {
					position = new Position(portfolio, exchange, market, baseAsset, amount, price);
				}

				portfolio.modifyPosition(position, new Authorization("Fill for " + transaction.toString()));

			}
		} else {
			return;
		}
	}

	@Override
	public void afterAttach(Context context) {
		context.attachInstance(getPortfolio());
		context.attachInstance(getPortfolioService());
	}

	/** for subclasses */
	protected PortfolioManager(String portfolioName) {
		this.portfolio = new Portfolio(portfolioName, this);
		this.portfolioService = new BasicPortfolioService(portfolio);
	}

	public static class DataSubscriber {

		private static Logger logger = LoggerFactory.getLogger(DataSubscriber.class.getName());
		private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

		@SuppressWarnings("rawtypes")
		public void update(Long Timestamp, Portfolio portfolio) {
			BasicPortfolioService portfolioService = portfolio.getManager().getPortfolioService();
			//portfolio.getPositions();
			logger.info("Date: " + (Timestamp != null ? (FORMAT.print(Timestamp)) : "") + " Portfolio: " + portfolio + " Total Value ("
					+ portfolio.getBaseAsset() + "):" + portfolioService.getCashBalance().plus(portfolioService.getMarketValue()) + " (Cash Balance:"
					+ portfolioService.getCashBalance() + " Realised PnL:" + portfolioService.getRealisedPnL() + " Open Trade Equity:"
					+ portfolioService.getUnrealisedPnL() + " MarketValue:" + portfolioService.getMarketValue() + ")");
			logger.info(portfolio.getPositions().toString());
			//			Object itt = portfolio.getPositions().iterator();
			//			while (itt.hasNext()) {
			//				Position postion = itt.next();
			//				logger.info("Date: " + (Timestamp != null ? (FORMAT.print(Timestamp)) : "") + " Asset: " + postion.getAsset() + " Position: "
			//						+ postion.getVolume());
			//			}
		}

	}

	// JPA
	protected PortfolioManager() {
	}

	protected void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	@Inject
	private static Logger log;

	private Portfolio portfolio;

}
