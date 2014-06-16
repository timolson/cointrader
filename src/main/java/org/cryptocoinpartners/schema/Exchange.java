package org.cryptocoinpartners.schema;


import org.cryptocoinpartners.util.PersistUtil;

import javax.persistence.Basic;
import javax.persistence.Entity;


/**
 * @author Tim Olson
 */
@Entity
public class Exchange extends BaseEntity {


    public static Exchange forSymbol( String symbol ) {
        Exchange found = PersistUtil.queryZeroOne(Exchange.class, "select m from Exchange m where symbol=?1", symbol);
        if( found == null ) {
            found = new Exchange(symbol);
            PersistUtil.insert(found);
        }
        return found;
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
