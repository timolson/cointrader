package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.OneToMany;

/**
 * An Owner is a person or corporate entity who holds Stakes in Portfolios.  Every Owner has a 100% stake in their deposit
 * portfolio, which is how
 * @author Tim Olson
 */
@Entity
public class Owner extends PortfolioManager {

	public Owner(String name) {
		super(name + "'s deposit account");
		this.name = name;
		stakes.add(new Stake(this, BigDecimal.ONE, getPortfolio())); // 100% Stake in the deposit portfolio
	}

	@Basic(optional = false)
	public String getName() {
		return name;
	}

	@OneToMany
	public Collection<Stake> getStakes() {
		return stakes;
	}

	// JPA only
	protected Owner() {
	}

	protected void setName(String name) {
		this.name = name;
	}

	protected void setStakes(Collection<Stake> stakes) {
		this.stakes = stakes;
	}

	private Collection<Stake> stakes = new ArrayList<>();
	private String name;
}
