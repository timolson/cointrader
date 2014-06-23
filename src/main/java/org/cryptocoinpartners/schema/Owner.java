package org.cryptocoinpartners.schema;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.math.BigDecimal;
import java.util.Collection;


/**
 * An Owner is a person or corporate entity who holds Stakes in Funds.  Every Owner has a 100% stake in their deposit
 * fund, which is how
 * @author Tim Olson
 */
@Entity
public class Owner extends FundManager {


    public Owner(String name) {
        super(name+"'s deposit account");
        this.name = name;
        stakes.add(new Stake(this, BigDecimal.ONE, getFund())); // 100% Stake in the deposit fund
    }


    @Basic(optional = false)
    public String getName() { return name; }


    @OneToMany
    public Collection<Stake> getStakes() { return stakes; }


    // JPA only
    protected Owner() {}
    protected void setName(String name) { this.name = name; }
    protected void setStakes(Collection<Stake> stakes) { this.stakes = stakes; }


    private Collection<Stake> stakes;
    private String name;
}
