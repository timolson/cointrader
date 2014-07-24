package org.cryptocoinpartners.schema;

import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;

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
    public Portfolio getPortfolio() { return portfolio; }

    @When("select * from Fill")
    public void handleFill( Fill fill ) {
    	Exchange exchange=fill.getMarket().getExchange();
    	Asset asset=fill.getMarket().getListing().base;
    	
    	 Amount amount = fill.getVolume();
         
         Position position = new Position(exchange, asset, amount);
         portfolio.modifyPosition( position, new Authorization("Fill for " + fill.toString() ));
       
    }

    /** for subclasses */
    protected PortfolioManager(String portfolioName) { this.portfolio = new Portfolio(portfolioName,this); }


    // JPA
    protected PortfolioManager() { }
    protected void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    @Inject private Logger log;
    private Portfolio portfolio;
}
