package com.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.util.Set;


/**
 * @author Tim Olson
 */
@Entity
public class Security extends DbEntity {


    public static Set<Security> forMarket(Market market) {
        // todo hibernate query
        return null;
    }


    public String getSymbol() {
        return symbol;
    }


    public Market getMarket() {
        return market;
    }


    public Security(Market market, String symbol) {
        this.market = market;
        this.symbol = symbol;
    }


    public String toString() {
        return market.toString()+':'+symbol;
    }


    // JPA
    protected Security() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }
    protected void setMarket(Market market) { this.market = market; }


    private String symbol;
    private Market market;
}
