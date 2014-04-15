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
        return PersistUtil.queryOne(Listing.class,"select a from Listing a where base=?1 and quote=?2",base,quote);
    }


    // BITCOIN
    public static Listing BTCUSD = listing(Currency.BTC, Currency.USD);
    public static Listing BTCEUR = listing(Currency.BTC, Currency.EUR);
    public static Listing BTCCNY = listing(Currency.BTC, Currency.CNY);
    public static Listing BTCJPY = listing(Currency.BTC, Currency.JPY);

    // LITECOIN
    public static Listing LTCUSD = listing(Currency.LTC, Currency.USD);
    public static Listing LTCBTC = listing(Currency.LTC, Currency.BTC);

    // PRIMECOIN
    public static Listing XPMBTC = listing(Currency.XPM, Currency.BTC);
    public static Listing XPMLTC = listing(Currency.XPM, Currency.LTC);

    // OTHERS
    // todo


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


}
