package com.cryptocoinpartners.schema;

import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Collection;


/**
 * Represents the possibility to trade one Fungible for another at a specific Market.
 *
 * @author Tim Olson
 */
@Entity
public class MarketListing extends EntityBase
{

    public static Collection<MarketListing> findAll()
    {
        return PersistUtil.queryList(MarketListing.class,"select ml from MarketListing");
    }


    /** adds the MarketListing to the database if it does not already exist */
    public static MarketListing findOrCreate(Market market, Listing listing) {
        return findOrCreate(market, listing, listing.getQuote().getBasis(), listing.getBase().getBasis());
    }


    public static MarketListing findOrCreate(Market market, Listing listing, double quoteBasis, double volumeBasis) {
        final String queryStr = "select m from MarketListing m where market=?1 and listing=?2";
        try {
            return PersistUtil.queryOne(MarketListing.class, queryStr, market, listing);
        }
        catch( NoResultException e ) {
            final MarketListing ml = new MarketListing(market, listing, quoteBasis, volumeBasis);
            PersistUtil.insert(ml);
            return ml;
        }
    }


    /**
     @return active MarketListings for the given market
     */
    public static Collection<MarketListing> find(Market market) {
        return PersistUtil.queryList(MarketListing.class, "select s from MarketListing s where market=?1 and active=?2", market, true);
    }


    /**
     @return active MarketListings for the given listing
     */
    public static Collection<MarketListing> find(Listing listing) {
        return PersistUtil.queryList(MarketListing.class, "select s from MarketListing s where listing=?1 and active=?2", listing, true);
    }


    @ManyToOne(optional = false)
    public Market getMarket() { return market; }


    @ManyToOne(optional = false)
    public Listing getListing() { return listing; }


    @Basic(optional = false)
    public double getPriceBasis() { return quoteBasis; }


    @Basic(optional = false)
    public double getVolumeBasis() { return volumeBasis; }


    public DiscreteAmount discretePrice( double price ) { return DiscreteAmount.fromValueRounded(price,getPriceBasis()); }


    public DiscreteAmount discreteVolume( double vol ) { return DiscreteAmount.fromValueRounded(vol, getVolumeBasis()); }


    /** @return true iff the Listing is currently traded at the Market.  The MarketListing could have been retired. */
    public boolean isActive() { return active; }


    @Transient
    public Fungible getBase() { return listing.getBase(); }


    @Transient
    public Fungible getQuote() { return listing.getQuote(); }


    @Transient
    private String getSymbol() { return market.toString()+':'+listing.toString(); }


    public String toString() { return getSymbol(); }


    // JPA
    protected MarketListing() {}
    protected void setMarket( Market market ) { this.market = market; }
    protected void setListing( Listing listing ) { this.listing = listing; }
    protected void setActive( boolean active ) { this.active = active; }
    protected void setPriceBasis(double quoteBasis) { this.quoteBasis = quoteBasis; }
    protected void setVolumeBasis(double volumeBasis) { this.volumeBasis = volumeBasis; }


    private MarketListing( Market market, Listing listing, double quoteBasis, double volumeBasis ) {
        this.market = market;
        this.listing = listing;
        this.quoteBasis = quoteBasis;
        this.volumeBasis = volumeBasis;
        this.active = true;
    }


    private Market market;
    private Listing listing;
    private double quoteBasis;
    private double volumeBasis;
    private boolean active;
}
