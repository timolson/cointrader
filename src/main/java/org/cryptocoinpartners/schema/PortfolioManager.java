package org.cryptocoinpartners.schema;

// import java.util.logging.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
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
//@Cacheable
public class PortfolioManager extends EntityBase implements Context.AttachListener {

	// private BasicPortfolioService portfolioService;

	// todo we need to get the tradeable portfolio separately from the "reserved" portfolio (assets needed for open orders)
	@OneToOne
	public Portfolio getPortfolio() {
		return portfolio;
	}

	public void setContext(Context context) {
		this.context = context;
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return getPortfolio();
	}

	@Transient
	public Context getContext() {
		return context;
	}

	@Transient
	public PortfolioService getPortfolioService() {
		return portfolioService;
	}

	@When("@Priority(6) @Audit select * from OrderUpdate where state.open=true")
	private void updateReservation(OrderUpdate update) {

		//removes the reservation from the transactions
		if (update.getOrder().getPortfolio().equals(portfolio)) {
			Transaction reservation = update.getOrder().getReservation();
			if (reservation != null && update.getState() != OrderState.NEW) {
				if (reservation.getType() == (TransactionType.BUY_RESERVATION) || reservation.getType() == (TransactionType.SELL_RESERVATION)) {
					Amount price = (update.getOrder().getLimitPrice() == null)
							? ((update.getOrder().getVolume().isNegative()) ? quotes.getLastBidForMarket(update.getOrder().getMarket()).getPrice()
									: quotes.getLastAskForMarket(update.getOrder().getMarket()).getPrice())
							: update.getOrder().getLimitPrice();
					Amount updateAmount = reservation.getType() == (TransactionType.BUY_RESERVATION)
							? (update.getOrder().getUnfilledVolume().times(price, Remainder.ROUND_EVEN)).negate()
							: update.getOrder().getVolume();
					reservation.setAmountDecimal(updateAmount.asBigDecimal());
					//  reservation.setAsset(reservation.getAsset());
				}
			}
		}
	}

	@When("@Priority(5) @Audit select * from OrderUpdate where state.open=false")
	private void removeReservation(OrderUpdate update) {
		//removes the reservation from the transactions
		Order order = update.getOrder();
		if (order.getPortfolio().equals(portfolio)) {
			Transaction reservation = order.getReservation();
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
					if (order instanceof SpecificOrder)
						portfolio.removeTransaction(reservation);
					break;
				case CANCELLING:
					break;
				case CANCELLED:
					if (order instanceof SpecificOrder)
						portfolio.removeTransaction(reservation);
					break;
				case REJECTED:
					if (order instanceof SpecificOrder)
						portfolio.removeTransaction(reservation);
					break;
				case EXPIRED:
					if (order instanceof SpecificOrder)
						portfolio.removeTransaction(reservation);
					break;
				case ERROR:
					break;
				default:

					log.error(this.getClass().getSimpleName() + ":removeReservation - Called from class " + Thread.currentThread().getStackTrace()[2]
							+ " Unknown order state: " + update.getState());
					break;
			}

		}
	}

	private class handleTransactionRunnable implements Runnable {
		private final Transaction transaction;

		// protected Logger log;

		public handleTransactionRunnable(Transaction transaction) {
			this.transaction = transaction;

		}

		@Override
		public void run() {
			updatePortfolio(transaction);

		}
	}

	private class handleFillRunnable implements Runnable {
		private final Fill fill;

		// protected Logger log;

		public handleFillRunnable(Fill fill) {
			this.fill = fill;

		}

		@Override
		public void run() {
			// fill.persit();
			portfolio.modifyPosition(fill, new Authorization("Fill for " + fill.toString()));

		}
	}

	//  @When("@Priority(9) select * from Fill")
	// public void handleFill(Fill fill) {
	//    service.submit(new handleFillRunnable(fill));

	// }

	//  @When("@Priority(8) select * from Transaction where NOT (Transaction.type=TransactionType.BUY and Transaction.type=TransactionType.SELL)")
	@When("@Priority(8) @Audit select * from Transaction")
	public void handleTransaction(Transaction transaction) {
		//
		updatePortfolio(transaction);
		// service.submit(new handleTransactionRunnable(transaction));

	}

	public void updatePortfolio(Transaction transaction) {
		//  PersistUtil.insert(transaction);
		//	Transaction tans = new Transaction(this, position.getExchange(), position.getAsset(), TransactionType.CREDIT, position.getVolume(),
		//		position.getAvgPrice());
		//context.route(transaction);
		//TODO need to ensure transacations are not duplicated, i.e. we should use a set.
		log.info("transaction: " + transaction + " Recieved.");
		if (transaction.getPortfolio().equals(portfolio)) {

			Portfolio portfolio = transaction.getPortfolio();
			// Add transaction to approraite portfolio
			portfolio.addTransaction(transaction);
			if (transaction.getExchange() != null && (transaction.getType().isBookable())) {

				log.debug(this.getClass().getSimpleName() + "- updatePortfolio determing updates to exchange " + transaction.getExchange() + " balances "
						+ transaction.getExchange().getBalances());

				//  Amount currentBalance =DecimalAmount.ZERO;
				Amount currentBalance = (transaction.getExchange().getBalances().isEmpty()
						|| transaction.getExchange().getBalances().get(transaction.getCurrency()) == null) ? DecimalAmount.ZERO
								: transaction.getExchange().getBalances().get(transaction.getCurrency()).getAmount();

				Balance newBalance;
				if (transaction.getType() == TransactionType.BUY || transaction.getType() == TransactionType.SELL) {
					if ((transaction.getExchange().getBalances().isEmpty()
							|| transaction.getExchange().getBalances().get(transaction.getCommissionCurrency()) == null)) {
						log.debug(this.getClass().getSimpleName() + "- updatePortfolio creating buy/sell for " + transaction.getType()
								+ " balance for exchange " + transaction.getExchange() + " asset: " + transaction.getCommissionCurrency() + " amount: "
								+ currentBalance.plus(transaction.getCommission()));

						newBalance = balanceFactory.create(transaction.getExchange(), transaction.getCommissionCurrency(),
								currentBalance.plus(transaction.getCommission()));
						transaction.getExchange().getBalances().put(transaction.getCommissionCurrency(), newBalance);
						newBalance.persit();
						log.debug(this.getClass().getSimpleName() + "- updatePortfolio new balance " + newBalance + " added to balances "
								+ transaction.getExchange().getBalances() + " for " + transaction.getType() + " on exahgne " + transaction.getExchange()
								+ " asset: " + transaction.getCurrency());
					} else {
						Amount updatedBalanceAmount;
						updatedBalanceAmount = transaction.getCommission()
								.plus(transaction.getExchange().getBalances().get(transaction.getCommissionCurrency()).getAmount());
						transaction.getExchange().getBalances().get(transaction.getCommissionCurrency()).setAmount(updatedBalanceAmount);
						if (transaction.getExchange().getBalances().get(transaction.getCommissionCurrency()).getId() == null) {
							log.debug("test");

						}
						transaction.getExchange().getBalances().get(transaction.getCommissionCurrency()).merge();
						log.debug(this.getClass().getSimpleName() + "- updatePortfolio updated balance "
								+ transaction.getExchange().getBalances().get(transaction.getCommissionCurrency()) + " in balances "
								+ transaction.getExchange().getBalances() + " for " + transaction.getType() + " on exchange " + transaction.getExchange()
								+ " with buy.sell: " + updatedBalanceAmount + "/" + updatedBalanceAmount.asBigDecimal() + " and transactino amount "
								+ transaction.getCommission() + "/" + transaction.getCommission().asBigDecimal());
					}

				} else {
					if ((transaction.getExchange().getBalances().isEmpty() || transaction.getExchange().getBalances().get(transaction.getCurrency()) == null)) {

						log.debug(this.getClass().getSimpleName() + "- updatePortfolio creating balance for" + transaction.getType() + " on exchange "
								+ transaction.getExchange() + " asset: " + transaction.getCurrency() + " current balance " + currentBalance + " amount: "
								+ transaction.getAmount() + ", transaction:" + transaction + ",balanceFactory:" + balanceFactory);

						newBalance = (transaction.getType().isDebit()
								? balanceFactory.create(transaction.getExchange(), transaction.getCurrency(), (currentBalance.minus(transaction.getAmount())))
								: balanceFactory.create(transaction.getExchange(), transaction.getCurrency(), (currentBalance.plus(transaction.getAmount()))));
						transaction.getExchange().getBalances().put(transaction.getCurrency(), newBalance);
						newBalance.persit();
						log.debug(this.getClass().getSimpleName() + "- updatePortfolio new balance " + newBalance + " added to balances "
								+ transaction.getExchange().getBalances() + " for " + transaction.getType() + " on exahgne " + transaction.getExchange()
								+ " asset: " + transaction.getCurrency());

					} else {
						Amount updatedBalanceAmount;
						updatedBalanceAmount = transaction.getAmount().plus(transaction.getExchange().getBalances().get(transaction.getCurrency()).getAmount());
						transaction.getExchange().getBalances().get(transaction.getCurrency()).setAmount(updatedBalanceAmount);
						transaction.getExchange().getBalances().get(transaction.getCurrency()).merge();
						log.debug(this.getClass().getSimpleName() + "- updatePortfolio updated balance "
								+ transaction.getExchange().getBalances().get(transaction.getCurrency()) + " in balances "
								+ transaction.getExchange().getBalances() + " for " + transaction.getType() + " on exchange " + transaction.getExchange()
								+ " with : " + updatedBalanceAmount + "/" + updatedBalanceAmount.asBigDecimal() + " and transactino amount "
								+ transaction.getAmount() + "/" + transaction.getAmount().asBigDecimal());

					}
				}

				transaction.getExchange().merge();
			}
			for (Asset asset : transaction.getExchange().getBalances().keySet())
				if (!asset.getSymbol().equals(transaction.getExchange().getBalances().get(asset).getAsset().getSymbol()))
					log.debug("balance inccorectly placed");

			log.info("transaction: " + transaction + " Proccessed.");

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
		// new PortfolioManager();
		//   portfolio.setName(portfolioName);
		// portfolio.setManager(this);
		// this.portfolio = new Portfolio(portfolioName, this);
		// portfolioService.getPortfolio().setName(portfolioName);
		// portfolioService.getPortfolio().setManager(this);
		//  this.portfolioService = new BasicPortfolioService(portfolio);
	}

	public static class DataSubscriber {

		private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

		@SuppressWarnings("rawtypes")
		public void update(SubscribePortfolio subscribePortfolio) {
			log.info("Subscribed portfolio : " + subscribePortfolio.getPortfolio());
		}

		@SuppressWarnings("rawtypes")
		public void update(Long Timestamp, Portfolio portfolio) {
			// PortfolioService portfolioService = context.
			PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);

			QuoteService quoteService = portfolio.context.getInjector().getInstance(QuoteService.class);
			//      ..getManager().getPortfolioService();
			//portfolio.getPositions();
			log.info("Date: " + (Timestamp != null ? (FORMAT.print(Timestamp)) : "") + " Portfolio: " + portfolio + " Total Cash Value ("
					+ portfolio.getBaseAsset() + "):"
					+ portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).plus(portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()))
					+ ", Total Notional Value (" + portfolio.getBaseAsset() + "):"
					+ portfolio.getStartingBaseNotionalBalance().plus(portfolioService.getBaseCashBalance(portfolio.getBaseAsset()))
							.plus(portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset())).minus(portfolio.getStartingBaseCashBalance())
					+ " (Cash Balance:" + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " Realised PnL (M2M):"
					+ portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()) + " Commissions And Fees:"
					+ portfolioService.getBaseComissionAndFee(portfolio.getBaseAsset()) + " Open Trade Equity:"
					+ portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()) + " MarketValue:"
					+ portfolioService.getBaseMarketValue(portfolio.getBaseAsset()) + ")");
			for (Position position : portfolio.getNetPositions()) {
				log.info("Date: " + (Timestamp != null ? (FORMAT.print(Timestamp)) : "") + " Portfolio: " + portfolio + " Instrument: " + position.getAsset()
						+ " Last trade time "
						+ (quoteService.getLastTrade(position.getMarket()) == null ? "" : quoteService.getLastTrade(position.getMarket()).getTime())
						+ " Last book time "
						+ (quoteService.getLastBook(position.getMarket()) == null ? "" : quoteService.getLastBook(position.getMarket()).getTime())
						+ " Position: " + position.toString() + " fills " + position.getFills());

			}

			//log.info(portfolio.getNetPositions().toString());
			log.info(portfolio.getDetailedPositions().toString());
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
		// portfolio.setManager(this);
		// portfolioService.setManager(this);

	}

	// @Inject
	protected PortfolioManager(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	// @Inject
	protected void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;

	}

	@Transient
	protected void setPortfolioService(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.portfolioManager");

	@Inject
	protected transient Context context;
	@Inject
	protected QuoteService quotes;
	@Inject
	protected PortfolioService portfolioService;
	@Inject
	protected BalanceFactory balanceFactory;

	protected Portfolio portfolio;
	private static ExecutorService service = Executors.newFixedThreadPool(1);

	@Override
	public void persit() {
		// TODO Auto-generated method stub

	}

	@Override
	public void detach() {
		// TODO Auto-generated method stub

	}

	@Override
	public void merge() {
		// TODO Auto-generated method stub

	}

	@Override
	@Transient
	public Dao getDao() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	@Transient
	public void setDao(Dao dao) {
		// TODO Auto-generated method stub
		//  return null;
	}

	@Override
	public void delete() {
		// TODO Auto-generated method stub

	}

	@Override
	public EntityBase refresh() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void prePersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void persitParents() {
		// TODO Auto-generated method stub

	}

}
