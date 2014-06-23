package org.cryptocoinpartners.schema;


import org.cryptocoinpartners.util.PersistUtil;

import javax.persistence.Basic;
import javax.persistence.Entity;
import java.util.List;


/**
 * @author Tim Olson
 */
@Entity
public class Exchange extends EntityBase {


    public static Exchange forSymbolOrCreate(String symbol) {
        Exchange found = forSymbol(symbol);
        if( found == null ) {
            found = new Exchange(symbol);
            PersistUtil.insert(found);
        }
        return found;
    }


    /** returns null if the symbol does not represent an existing exchange */
    public static Exchange forSymbol(String symbol) {
        return PersistUtil.queryZeroOne(Exchange.class,"select e from Exchange e where symbol=?1",symbol);
    }


    public static List<String> allSymbols() {
        return PersistUtil.queryList(String.class, "select symbol from Exchange");
    }


    @Basic(optional = false)
    public String getSymbol() { return symbol; }


    public String toString() { return symbol; }


    // JPA
    protected Exchange() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }


    private Exchange(String symbol) { this.symbol = symbol; }


    private String symbol;
}
