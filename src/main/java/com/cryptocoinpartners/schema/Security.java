package com.cryptocoinpartners.schema;

import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.*;
import java.util.Collection;
import java.util.List;


/**
 * Represents an asset at a Market.  The same asset on different Markets are different Securities
 *
 * @author Tim Olson
 */
@Entity
public class Security extends DbEntity {

    /**
     * @param market
     * @return all Securities listed on the given Market
     */
    public static Collection<Security> forMarket(Market market) {
        TypedQuery<Security> query = PersistUtil.createEntityManager()
                                                .createQuery("select s from Security s where market=?1", Security.class);
        query.setParameter(1,market);
        List<Security> resultList = query.getResultList();
        return resultList;
    }


    /** This symbol may be unique to the market */
    public String getSymbol() {
        return symbol;
    }


    @Enumerated(EnumType.STRING)
    public Market getMarket() {
        return market;
    }


    protected Security(Market market, String symbol) {
        this.market = market;
        this.symbol = symbol;
    }


    public String toString() {
        return market.toString()+':'+symbol;
    }


    public Security withLowercaseSymbol() { setSymbol(getSymbol().toLowerCase()); return this; }
    public Security withUppercaseSymbol() { setSymbol(getSymbol().toLowerCase()); return this; }


    // JPA
    protected Security() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }
    protected void setMarket(Market market) { this.market = market; }


    private String symbol;
    private Market market;
}
