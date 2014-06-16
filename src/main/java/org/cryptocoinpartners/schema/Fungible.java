package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.util.PersistUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.NoResultException;


/**
 * Represents anything valuable and tradable, like currency or stock
 *
 * @author Tim Olson
 */
@Entity
public abstract class Fungible extends BaseEntity {


    public static Fungible forSymbol( String symbol )
    {
        try {
            return PersistUtil.queryOne(Fungible.class,"select f from Fungible f where symbol=?1",symbol);
        }
        catch( NoResultException e ) {
            log.error("Could not find Fungible for symbol "+symbol);
            return null;
        }
    }


    @Basic(optional = false)
    public String getSymbol() { return symbol; }


    @Basic(optional = false)
    public double getBasis() { return basis; }


    public String toString() { return symbol; }


    protected Fungible( String symbol, double basis ) { this.symbol = symbol; this.basis = basis; }


    // JPA
    protected Fungible() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }
    protected void setBasis(double basis) { this.basis = basis; }


    private String symbol;
    private double basis;

    private static Logger log = LoggerFactory.getLogger(Fungible.class);
}
