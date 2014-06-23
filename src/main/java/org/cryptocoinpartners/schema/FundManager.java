package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;


/**
 * FundManagers are allowed to control the Positions within a Fund
 *
 * @author Tim Olson
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class FundManager extends EntityBase {

    @OneToOne
    public Fund getFund() { return fund; }


    /** for subclasses */
    protected FundManager(String fundName) { this.fund = new Fund(fundName,this); }


    // JPA
    protected FundManager() { }
    protected void setFund(Fund fund) { this.fund = fund; }


    private Fund fund;
}
