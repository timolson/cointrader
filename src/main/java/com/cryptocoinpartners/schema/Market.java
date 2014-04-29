package com.cryptocoinpartners.schema;


import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Tim Olson
 */
@Entity
public class Market extends EntityBase {


    public static Market forSymbol( String symbol ) {
        Market found = PersistUtil.queryZeroOne(Market.class, "select m from Market m where symbol=?1", symbol);
        if( found == null ) {
            found = new Market(symbol);
            PersistUtil.insert(found);
        }
        return found;
    }


    @Basic(optional = false)
    public String getSymbol() { return symbol; }


    public String toString() { return symbol; }


    // JPA
    protected Market() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }


    private Market(String symbol) { this.symbol = symbol; }


    private String symbol;
}
