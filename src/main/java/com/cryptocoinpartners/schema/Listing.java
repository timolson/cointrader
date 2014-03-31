package com.cryptocoinpartners.schema;

import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.*;
import java.util.Collection;
import java.util.List;


/**
 * Represents a possibility to trade one Fungible for another at a specific Market.
 *
 * @author Tim Olson
 */
@Entity
public class Listing extends EntityBase {

    /**
     * @param market
     * @return all Securities listed on the given Market
     */
    public static Collection<Listing> forMarket(Market market) {
        EntityManager entityManager = PersistUtil.createEntityManager();
        try {
            TypedQuery<Listing> query = entityManager.createQuery("select s from Listing s where market=?1",
                                                                   Listing.class);
            query.setParameter(1,market);
            return query.getResultList();
        }
        finally {
            entityManager.close();
        }
    }


    @OneToOne(optional = false)
    public Fungible getBase() { return base; }


    @OneToOne(optional = false)
    public Fungible getQuote() { return quote; }


    @OneToOne(optional = false)
    public Market getMarket() { return market; }


    public Listing(Market market, Fungible base, Fungible quote) {
        this.base = base;
        this.quote = quote;
        this.market = market;
    }


    public String toString() {
        return market.toString()+':'+base+'.'+quote;
    }


    // JPA
    protected Listing() {}
    protected void setMarket(Market market) { this.market = market; }
    protected void setBase(Fungible base) { this.base = base; }
    protected void setQuote(Fungible quote) { this.quote = quote; }


    private Fungible base;
    private Fungible quote;
    private Market market;
}
