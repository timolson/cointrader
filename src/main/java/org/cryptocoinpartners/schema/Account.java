package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;


/**
 * An `Account` differs from a `Fund` in a couple ways: `Account`s do not have any `Owner`, and they are
 * reconciled 1-for-1 against external records (account data gathered from XChange).  `Account`s generally relate to
 * external holdings, but there may be `Account`s attached to `Markets.SELF`, meaning the account is internal to this
 * organizition.  All `Positions` have both an `Account` and a `Fund`.  The total of all `Position`s in an `Account`
 * should match the external entity's records, while the internal ownership of the `Positions` is tracked through the
 * `Fund` via `Stake`s and `Owner`s.
 *
 * @author Tim Olson
 */
public class Account {

    public Account(Exchange exchange) {
        this.exchange = exchange;
        this.positions = new ArrayList<>();
    }


    public Collection<Position> getPositions() { return positions; }
    public Exchange getExchange() { return exchange; }


    // JPA only
    protected Account() { }
    protected void setPositions(Collection<Position> positions) { this.positions = positions; }
    protected void setExchange(Exchange exchange) { this.exchange = exchange; }


    private Collection<Position> positions;
    private Owner owner;
    private Exchange exchange;
}
