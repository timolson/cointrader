package org.cryptocoinpartners.schema;

import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.When;
import org.slf4j.Logger;

/**
 * PortfolioManagers are allowed to control the Positions within a Portfolio
 *
 * @author Tim Olson
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class PortfolioManager extends EntityBase {

	// todo we need to get the tradeable portfolio separately from the "reserved" portfolio (assets needed for open orders)
	@OneToOne
	public Portfolio getPortfolio() {
		return portfolio;
	}

	@When("select * from Transaction as transaction")
	public void handleTransaction(Transaction transaction) {

		if (transaction.getPortfolio() == (portfolio)) {
			Portfolio portfolio = transaction.getPortfolio();
			Asset asset = transaction.getAsset();
			Market market = transaction.getMarket();
			Amount amount = transaction.getAmount();
			TransactionType type = transaction.getType();
			Amount price = transaction.getPrice();
			Exchange exchange = transaction.getExchange();
			// Add transaction to approraite portfolio
			portfolio.addTransactions(transaction);
			// update postion
			if (type == TransactionType.BUY || type == TransactionType.SELL) {
				Position position = new Position(portfolio, exchange, market, asset, amount, price);
				portfolio.modifyPosition(position, new Authorization("Fill for " + transaction.toString()));

			}
			log.info("Transaction Processed for: " + portfolio + " " + transaction);
			log.info("Current Positions: " + portfolio + " " + portfolio.getPositions());
		} else {
			return;
		}
	}

	/** for subclasses */
	protected PortfolioManager(String portfolioName) {
		this.portfolio = new Portfolio(portfolioName, this);
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
