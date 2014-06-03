package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Transient;
import java.util.Collection;
import java.util.Collections;


/**
 * A Strategy represents an approach to trading.  Every Strategy is attached to one and only one Fund.  The Strategy
 * places Orders to manipulate Positions in the Fund on behalf of the owners of the Fund.
 *
 * @author Tim Olson
 */
@Entity
public class Strategy extends EntityBase {

    @OneToOne Fund getFund() { return fund; }


    // for JPA only
    protected void setFund(Fund fund) { this.fund = fund; }


    // get open orders
    @Transient Collection<Order> getOpenOrders() {
        // todo
        return Collections.emptyList();
    }


    // returns the execution price of the Order's Fills weighted by volume.
    @Transient double getAverageFillPrice() {
        return Double.NaN; // todo
    }


    public static Order createOrder(
        Listing listing,
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
        // todo
        return null;
    }


    private Collection<Order> orders;
    private Fund fund;
}
