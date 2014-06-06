package org.cryptocoinpartners.schema;

import javax.persistence.*;


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
    protected FundManager(Fund fund) { this.fund = fund; }


    // JPA
    protected FundManager() { }
    protected void setFund(Fund fund) { this.fund = fund; }


    private Fund fund;
}
