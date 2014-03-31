package com.cryptocoinpartners.schema;

import javax.persistence.Entity;


/**
 * Represents anything valuable and tradable, like currency or stock
 *
 * @author Tim Olson
 */
@Entity
public abstract class Fungible extends EntityBase {


    public String getSymbol() { return symbol; }


    public String toString() { return symbol; }


    protected Fungible(String symbol) { this.symbol = symbol; }


    // JPA
    protected Fungible() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }


    protected String symbol;
}
