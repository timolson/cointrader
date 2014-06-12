package org.cryptocoinpartners.schema;


public class Exchanges
{
    /** An imaginary Exchange used for testing */
    public static final Exchange MOCK = Exchange.forSymbol("MOCK");

    /** Our own "Exchange".  Internal Accounts have this as their Exchange */
    public static final Exchange SELF = Exchange.forSymbol("SELF");

    public static final Exchange BITFINEX = Exchange.forSymbol("BITFINEX");
    public static final Exchange BTCCHINA = Exchange.forSymbol("BTCCHINA");
    public static final Exchange BITSTAMP = Exchange.forSymbol("BITSTAMP");
    public static final Exchange BTCE = Exchange.forSymbol("BTCE");
    public static final Exchange CRYPTSY = Exchange.forSymbol("CRYPTSY");
}
