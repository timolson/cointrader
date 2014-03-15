package com.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.math.BigDecimal;


/**
 * An Account has many Positions.  A Position represents an amount of some Security.
 *
 * @author Tim Olson
 */
@Entity
public class Position extends EntityBase {

    public Position(Account account, BigDecimal amount, Security security) {
        this.account = account;
        this.amount = amount;
        this.security = security;
    }


    @ManyToOne
    public Account getAccount() {
        return account;
    }


    public BigDecimal getAmount() {
        return amount;
    }


    @ManyToOne
    public Security getSecurity() {
        return security;
    }


    protected void setAccount(Account account) {
        this.account = account;
    }


    protected void setAmount(BigDecimal amount) {
        this.amount = amount;
    }


    protected void setSecurity(Security security) {
        this.security = security;
    }


    protected Position() { }


    private Account account;
    private BigDecimal amount;
    private Security security;
}
