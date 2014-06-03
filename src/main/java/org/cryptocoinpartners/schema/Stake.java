package org.cryptocoinpartners.schema;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.math.BigDecimal;


/**
 * Connects an Owner to a Fund by an amount
 *
 * @author Tim Olson
 */
@Entity
public class Stake extends Fungible {


    public Stake(Owner owner, BigDecimal stake, Fund fund) {
        this.owner = owner;
        this.stake = stake;
        this.fund = fund;
    }


    @ManyToOne
    public Owner getOwner() { return owner; }

    @Column(precision = 30, scale = 15)
    public BigDecimal getStake() { return stake; }

    @ManyToOne
    public Fund getFund() { return fund; }


    // JPA
    protected Stake() {}
    protected void setOwner(Owner owner) { this.owner = owner; }
    protected void setStake(BigDecimal stake) {
        if( stake.compareTo(BigDecimal.ZERO) < 0 || stake.compareTo(BigDecimal.ONE) > 0 )
            throw new IllegalArgumentException("stake must be in range [0,1]: "+stake);
        this.stake = stake;
    }
    protected void setFund(Fund fund) { this.fund = fund; }


    private Owner owner;
    private BigDecimal stake;
    private Fund fund;
}
