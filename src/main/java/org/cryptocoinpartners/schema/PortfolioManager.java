package org.cryptocoinpartners.schema;

import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.util.PersistUtil;
import org.slf4j.Logger;

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

	@When("select * from Transaction as transaction")
	public void handleTransaction(Transaction transaction) {

		if (transaction.getPortfolio() == (portfolio)) {
			Portfolio portfolio = transaction.getPortfolio();
			Asset baseAsset = transaction.getAsset();
			Asset quoteAsset = transaction.getCurrency();
			Market market = transaction.getMarket();
			Amount amount = transaction.getAmount();
			TransactionType type = transaction.getType();
			Amount price = transaction.getPrice();
			Exchange exchange = transaction.getExchange();
			// Add transaction to approraite portfolio
			portfolio.addTransactions(transaction);
			// update postion
			if (type == TransactionType.BUY || type == TransactionType.SELL) {
				Position position = new Position(portfolio, exchange, market, baseAsset, amount, price);
				portfolio.modifyPosition(position, new Authorization("Fill for " + transaction.toString()));

			}
			try {
				PersistUtil.insert(transaction);
			} catch (Throwable e) {
				throw new Error("Could not insert " + transaction, e);
			}

			log.info("Transaction Processed for: " + portfolio + " " + transaction);
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

	// JPA
	protected PortfolioManager() {
	}

	protected void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	@Inject
	private Logger log;

	private Portfolio portfolio;

}
