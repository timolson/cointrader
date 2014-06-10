package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.*;
import java.util.Collection;


/**
 * This is the base class for GeneralOrder and SpecificOrder.  To create orders, see OrderBuilder or Strategy.order
 *
 * @author Mike Olson
 * @author Tim Olson
 */
@Entity
@Table(name="\"Order\"") // This is required because ORDER is a SQL keyword and must be escaped
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@SuppressWarnings({"JpaDataSourceORMInspection", "UnusedDeclaration"})
public abstract class Order extends Event {


    public enum FillType {
        GOOD_TIL_CANCELLED, // Order stays open until explicitly cancelled or expired
        GTC_OR_MARGIN_CAP, // Order stays open until explicitly cancelled, expired, or the order is filled to the capacity of the currently available Positions
        CANCEL_REMAINDER, // This will cancel any remaining volume after a partial fill
    }


    public enum MarginType {
        USE_MARGIN, // trade up to the limit of credit in the quote fungible
        CASH_ONLY, // do not trade more than the available cash-on-hand (quote fungible)
    }


    @ManyToOne(optional = false)
    public Fund getFund() { return fund; } 

    
    public FillType getFillType() { return fillType; }


    public MarginType getMarginType() { return marginType; }


    public Instant getExpiration() { return expiration; }


    public boolean getPanicForce() { return force; }


    public boolean setPanicForce() { return force; }


    public boolean isEmulation() { return emulation; }


    @OneToMany
    public Collection<Fill> getFills() { return fills; }

    
    @Transient
    public boolean hasFills() { return !getFills().isEmpty(); }


    @Transient
    public abstract boolean isBid();


    @Transient
    public boolean isAsk() { return !isBid(); }


    // JPA
    protected Order() {}
    protected void setFills(Collection<Fill> fills) { this.fills = fills; }
    protected void setFillType(FillType fillType) { this.fillType = fillType; }
    protected void setMarginType(MarginType marginType) { this.marginType = marginType; }
    protected void setExpiration(Instant expiration) { this.expiration = expiration; }
    protected void setPanicForce(boolean force) { this.force = force; }
    protected void setEmulation(boolean emulation) { this.emulation = emulation; }
    protected void setFund(Fund fund) { this.fund = fund; } 

    
    private Fund fund;
    private Collection<Fill> fills;
    private FillType fillType;
    private MarginType marginType;
    private Instant expiration;
    private boolean force;     // allow this order to override various types of panic
    private boolean emulation; // ("allow order type emulation" [default, true] or "only use exchange's native functionality")
}
