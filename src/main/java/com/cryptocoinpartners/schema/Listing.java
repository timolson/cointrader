package com.cryptocoinpartners.schema;

import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.*;


/**
 * Represents the possibility to trade one Fungible for another
 */
@SuppressWarnings( "UnusedDeclaration" )
@Entity
public class Listing extends EntityBase
{
    @ManyToOne(optional = false)
    public Fungible getBase() { return base; }


    @ManyToOne(optional = false)
    public Fungible getQuote() { return quote; }


    public static Listing forPair( Fungible base, Fungible quote ) {
        try {
            return PersistUtil.queryOne(Listing.class, "select a from Listing a where base=?1 and quote=?2", base, quote);
        }
        catch( NoResultException e ) {
            final Listing listing = new Listing(base, quote);
            PersistUtil.insert(listing);
            return listing;
        }
    }


    public String toString()
    {
        return base.getSymbol()+'.'+quote.getSymbol();
    }


    // JPA
    protected Listing() { }
    protected void setBase(Fungible base) { this.base = base; }
    protected void setQuote(Fungible quote) { this.quote = quote; }


    protected Fungible base;
    protected Fungible quote;


    private Listing( Fungible base, Fungible quote ) {
        this.base = base;
        this.quote = quote;
    }


    private static Listing listing( Fungible base, Fungible quote ) {
        if( PersistUtil.generatingDefaultData )
            return new Listing(base,quote);
        else
            return forPair(base,quote);
    }


    public static Listing forSymbol( String symbol )
    {
        final int dot = symbol.indexOf('.');
        if( dot == -1 )
            throw new IllegalArgumentException("Invalid Listing symbol: \""+symbol+"\"");
        final String baseSymbol = symbol.substring(0, dot);
        Fungible base = Fungible.forSymbol(baseSymbol);
        if( base == null )
            throw new IllegalArgumentException("Invalid base symbol: \""+baseSymbol+"\"");
        final String quoteSymbol = symbol.substring(dot + 1, symbol.length());
        Fungible quote = Fungible.forSymbol(quoteSymbol);
        if( quote == null )
            throw new IllegalArgumentException("Invalid quote symbol: \""+quoteSymbol+"\"");
        return PersistUtil.queryOne(Listing.class,"select x from Listing x where base=?1 and quote=?2",base,quote);
    }
}
