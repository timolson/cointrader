package com.cryptocoinpartners.schema;


public class Markets
{
    /** An imaginary Market used for testing */
    public static final Market FAKEMARKET = Market.forSymbol("FAKEMARKET");

    /** Our own "Market".  Internal Accounts have this as their Market */
    public static final Market SELF = Market.forSymbol("SELF");

    public static final Market BITFINEX = Market.forSymbol("BITFINEX");
    public static final Market BTCCHINA = Market.forSymbol("BTCCHINA");
    public static final Market BITSTAMP = Market.forSymbol("BITSTAMP");
    public static final Market BTCE = Market.forSymbol("BTCE");
    public static final Market CRYPTSY = Market.forSymbol("CRYPTSY");
}
