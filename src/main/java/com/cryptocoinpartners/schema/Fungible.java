package com.cryptocoinpartners.schema;

import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.Basic;
import javax.persistence.Entity;


/**
 * Represents anything valuable and tradable, like currency or stock
 *
 * @author Tim Olson
 */
@Entity
public abstract class Fungible extends EntityBase {


    public static Fungible forSymbol( String symbol )
    {
        return PersistUtil.queryOne(Fungible.class,"select f from Fungible f where symbol=?1",symbol);
    }


    @Basic(optional = false)
    public String getSymbol() { return symbol; }


    public String toString() { return symbol; }


    protected Fungible(String symbol) { this.symbol = symbol; }


    // JPA
    protected Fungible() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }


    protected String symbol;


}
