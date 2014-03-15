package com.cryptocoinpartners.schema;

import java.math.BigDecimal;


/**
 * Connects an Owner to an Account by an amount
 *
 * @author Tim Olson
 */
public class Stake {

    public Stake(Owner owner, BigDecimal stake, Account account) {
        this.owner = owner;
        this.stake = stake;
        this.account = account;
    }


    public Owner getOwner() { return owner; }
    public BigDecimal getStake() { return stake; }
    public Account getAccount() { return account; }


    // JPA
    protected Stake() {}
    protected void setOwner(Owner owner) { this.owner = owner; }
    protected void setStake(BigDecimal stake) {
        if( stake.compareTo(BigDecimal.ZERO) < 0 || stake.compareTo(BigDecimal.ONE) > 0 )
            throw new IllegalArgumentException("stake must be in range [0,1]: "+stake);
        this.stake = stake;
    }
    protected void setAccount(Account account) { this.account = account; }

    private Owner owner;
    private BigDecimal stake;
    private Account account;
}
