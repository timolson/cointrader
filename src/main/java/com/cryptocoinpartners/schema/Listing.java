package com.cryptocoinpartners.schema;

import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.*;
import java.util.Collection;
import java.util.List;


/**
 * Represents an Asset at a Market.  The same Asset on different Markets are different Securities
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


    /** This symbol may be unique to the associated Market */
    public String getSymbol() {
        return symbol;
    }


    @Enumerated(EnumType.STRING)
    public Market getMarket() {
        return market;
    }


    protected Listing(Market market, String symbol) {
        this.market = market;
        this.symbol = symbol;
    }


    public String toString() {
        return market.toString()+':'+symbol;
    }


    public Listing withLowercaseSymbol() { setSymbol(getSymbol().toLowerCase()); return this; }
    public Listing withUppercaseSymbol() { setSymbol(getSymbol().toLowerCase()); return this; }


    // JPA
    protected Listing() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }
    protected void setMarket(Market market) { this.market = market; }


    private String symbol;
    private Market market;
}
