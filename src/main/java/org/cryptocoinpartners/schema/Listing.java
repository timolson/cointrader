package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.util.PersistUtil;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NoResultException;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;


/**
 * Represents the possibility to trade one Asset for another
 */
@SuppressWarnings( "UnusedDeclaration" )
@Entity
public class Listing extends EntityBase
{
    @ManyToOne(optional = false)
    public Asset getBase() { return base; }


    @ManyToOne(optional = false)
    public Asset getQuote() { return quote; }


    /** will create the listing if it doesn't exist */
    public static Listing forPair( Asset base, Asset quote ) {
        try {
            Listing listing = PersistUtil.queryZeroOne(Listing.class,
                                                       "select a from Listing a where base=?1 and quote=?2",
                                                       base, quote);
            if( listing == null ) {
                listing = new Listing(base,quote);
                PersistUtil.insert(listing);
            }
            return listing;
        }
        catch( NoResultException e ) {
            final Listing listing = new Listing(base, quote);
            PersistUtil.insert(listing);
            return listing;
        }
    }


    public String toString() { return getSymbol(); }


    @Transient
    public String getSymbol() { return base.getSymbol()+'.'+quote.getSymbol(); }


    public static List<String> allSymbols() {
        List<String> result = new ArrayList<>();
        List<Listing> listings = PersistUtil.queryList(Listing.class, "select x from Listing x");
        for( Listing listing : listings )
            result.add((listing.getSymbol()));
        return result;
    }


    // JPA
    protected Listing() { }
    protected void setBase(Asset base) { this.base = base; }
    protected void setQuote(Asset quote) { this.quote = quote; }


    protected Asset base;
    protected Asset quote;


    private Listing( Asset base, Asset quote ) {
        this.base = base;
        this.quote = quote;
    }

    
    public static Listing forSymbol( String symbol )
    {
        symbol = symbol.toUpperCase();
        final int dot = symbol.indexOf('.');
        if( dot == -1 )
            throw new IllegalArgumentException("Invalid Listing symbol: \""+symbol+"\"");
        final String baseSymbol = symbol.substring(0, dot);
        Asset base = Asset.forSymbol(baseSymbol);
        if( base == null )
            throw new IllegalArgumentException("Invalid base symbol: \""+baseSymbol+"\"");
        final String quoteSymbol = symbol.substring(dot + 1, symbol.length());
        Asset quote = Asset.forSymbol(quoteSymbol);
        if( quote == null )
            throw new IllegalArgumentException("Invalid quote symbol: \""+quoteSymbol+"\"");
        return Listing.forPair(base,quote);
    }
}
