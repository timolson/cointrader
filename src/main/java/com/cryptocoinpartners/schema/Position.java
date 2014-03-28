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

    public Position(Account account, BigDecimal amount, Listing listing) {
        this.account = account;
        this.amount = amount;
        this.listing = listing;
    }


    @ManyToOne
    public Account getAccount() {
        return account;
    }


    public BigDecimal getAmount() {
        return amount;
    }


    @ManyToOne
    public Listing getListing() {
        return listing;
    }


    protected void setAccount(Account account) {
        this.account = account;
    }


    protected void setAmount(BigDecimal amount) {
        this.amount = amount;
    }


    protected void setListing(Listing listing) {
        this.listing = listing;
    }


    protected Position() { }


    private Account account;
    private BigDecimal amount;
    private Listing listing;
}
