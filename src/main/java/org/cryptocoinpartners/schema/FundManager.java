package org.cryptocoinpartners.schema;

import javax.persistence.MappedSuperclass;
import javax.persistence.OneToOne;


/**
 * FundManagers are allowed to control the Positions within a Fund
 *
 * @author Tim Olson
 */
@MappedSuperclass
public class FundManager extends EntityBase {

    @OneToOne
    public Fund getFund() { return fund; }


    // JPA
    protected FundManager() { }
    protected FundManager(String fundName) { this.fund = new Fund(fundName); }
    protected void setFund(Fund fund) { this.fund = fund; }


    private Fund fund;
}
