package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import java.util.Collection;
import java.util.Collections;


/**
 * Many Owners may have Stakes in the Fund, but there is only one FundManager, who is not necessarily an Owner.  The
 * Fund has multiple Positions.
 *
 * @author Tim Olson
 */
@Entity
public class Fund extends EntityBase {

    public @OneToMany Collection<Position> getPositions() { return positions; }


    public Fund(String name) { this.name = name; }


    public String getName() { return name; }


    @OneToMany
    public Collection<Stake> getStakes() { return stakes; }


    @ManyToOne
    public FundManager getManager() { return manager; }


    // get open orders
    @Transient
    Collection<Order> getOpenOrders() {
        // todo
        return Collections.emptyList();
    }


    // returns the execution price of the Order's Fills weighted by volume.
    @Transient double getAverageFillPrice() {
        return Double.NaN; // todo
    }


    public Order createOrder(
        MarketListing listing,
        double amount,
        double limit,          // (null [default] or specify a limit price relative to the quote fungible)
        double stop,           // (null [default] or specify the quote price at which this order is activated)
        Order.FillType fill,             // ("good until cancel or filled to margin limit" [default], "good until cancel", or "cancel any remainder")
        Order.MarginType marginType,     // ("do not use margin" [default] or "margin allowed")
        Instant expiration,       // (null [default] or time on or after which this order will be automatically canceled)
        Market market,             // (null [default] or specify the exchange on which to make the trade)
        boolean force,          // (null [default] or allow this order to override various types of panic)
        boolean emulation   // ("allow order type emulation" [default] or "only use exchange's native functionality")
    ) {
        // TODO: see Order.OrderBuilder
        return null;
    }


    // JPA
    protected Fund() {}
    protected void setPositions(Collection<Position> positions) { this.positions = positions; }
    protected void setName(String name) { this.name = name; }
    protected void setStakes(Collection<Stake> stakes) { this.stakes = stakes; }
    protected void setManager(FundManager manager) { this.manager = manager; }


    private String name;
    private FundManager manager;
    private Collection<Position> positions;
    private Collection<Stake> stakes;
}
