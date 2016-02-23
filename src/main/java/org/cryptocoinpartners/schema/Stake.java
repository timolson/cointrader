package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.Dao;

/**
 * Connects an Owner to a Portfolio
 *
 * @author Tim Olson
 */
@Entity
@Cacheable
public class Stake extends EntityBase {

    public Stake(Owner owner, BigDecimal stake, Portfolio portfolio) {
        this.owner = owner;
        this.stake = stake;
        this.portfolio = portfolio;
    }

    @ManyToOne
    public Owner getOwner() {
        return owner;
    }

    @Column(precision = 30, scale = 15)
    public BigDecimal getStake() {
        return stake;
    }

    @ManyToOne(optional = false)
    public Portfolio getPortfolio() {
        return portfolio;
    }

    // JPA
    protected Stake() {
    }

    protected void setOwner(Owner owner) {
        this.owner = owner;
    }

    protected void setStake(BigDecimal stake) {
        if (stake.compareTo(BigDecimal.ZERO) < 0 || stake.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("stake must be in range [0,1]: " + stake);
        this.stake = stake;
    }

    protected void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    private Owner owner;
    private BigDecimal stake;
    private Portfolio portfolio;

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
    public void delete() {
        // TODO Auto-generated method stub

    }

    @Override
    public EntityBase refresh() {
        // TODO Auto-generated method stub
        return null;
    }
}
