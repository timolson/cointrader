package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;


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


    /** for subclasses */
    protected PortfolioManager(String portfolioName) { this.portfolio = new Portfolio(portfolioName,this); }


    // JPA
    protected PortfolioManager() { }
    protected void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }


    private Portfolio portfolio;
}
