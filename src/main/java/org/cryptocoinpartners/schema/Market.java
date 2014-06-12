package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.util.PersistUtil;

import javax.persistence.*;
import java.util.Collection;


/**
 * Represents the possibility to trade one Fungible for another at a specific Exchange.
 *
 * @author Tim Olson
 */
@Entity
public class Market extends EntityBase
{

    public static Collection<Market> findAll()
    {
        return PersistUtil.queryList(Market.class, "select ml from Market");
    }


    /** adds the Market to the database if it does not already exist */
    public static Market findOrCreate(Exchange exchange, Listing listing) {
        return findOrCreate(exchange, listing, listing.getQuote().getBasis(), listing.getBase().getBasis());
    }


    public static Market findOrCreate(Exchange exchange, Listing listing, double quoteBasis, double volumeBasis) {
        final String queryStr = "select m from Market m where exchange=?1 and listing=?2";
        try {
            return PersistUtil.queryOne(Market.class, queryStr, exchange, listing);
        }
        catch( NoResultException e ) {
            final Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);
            PersistUtil.insert(ml);
            return ml;
        }
    }


    /**
     @return active Markets for the given exchange
     */
    public static Collection<Market> find(Exchange exchange) {
        return PersistUtil.queryList(Market.class, "select s from Market s where exchange=?1 and active=?2",
                                     exchange, true);
    }


    /**
     @return active Markets for the given listing
     */
    public static Collection<Market> find(Listing listing) {
        return PersistUtil.queryList(Market.class, "select s from Market s where listing=?1 and active=?2", listing, true);
    }


    @ManyToOne(optional = false)
    public Exchange getExchange() { return exchange; }


    @ManyToOne(optional = false)
    public Listing getListing() { return listing; }


    @Basic(optional = false)
    public double getPriceBasis() { return quoteBasis; }


    @Basic(optional = false)
    public double getVolumeBasis() { return volumeBasis; }


    public DiscreteAmount discretePrice( double price ) { return DiscreteAmount.fromValueRounded(price,getPriceBasis()); }


    public DiscreteAmount discreteVolume( double vol ) { return DiscreteAmount.fromValueRounded(vol, getVolumeBasis()); }


    /** @return true iff the Listing is currently traded at the Exchange.  The Market could have been retired. */
    public boolean isActive() { return active; }


    @Transient
    public Fungible getBase() { return listing.getBase(); }


    @Transient
    public Fungible getQuote() { return listing.getQuote(); }


    @Transient
    public String getSymbol() { return exchange.toString()+':'+listing.toString(); }


    public String toString() { return getSymbol(); }


    // JPA
    protected Market() {}
    protected void setExchange(Exchange exchange) { this.exchange = exchange; }
    protected void setListing( Listing listing ) { this.listing = listing; }
    protected void setActive( boolean active ) { this.active = active; }
    protected void setPriceBasis(double quoteBasis) { this.quoteBasis = quoteBasis; }
    protected void setVolumeBasis(double volumeBasis) { this.volumeBasis = volumeBasis; }


    private Market(Exchange exchange, Listing listing, double quoteBasis, double volumeBasis) {
        this.exchange = exchange;
        this.listing = listing;
        this.quoteBasis = quoteBasis;
        this.volumeBasis = volumeBasis;
        this.active = true;
    }


    private Exchange exchange;
    private Listing listing;
    private double quoteBasis;
    private double volumeBasis;
    private boolean active;
}
