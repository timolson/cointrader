package org.cryptocoinpartners.schema;


public class Exchanges
{
    /** An imaginary Exchange used for testing */
    public static final Exchange MOCK = Exchange.forSymbolOrCreate("MOCK");

    /** Our own "Exchange".  Internal Accounts have this as their Exchange */
    public static final Exchange SELF = Exchange.forSymbolOrCreate("SELF");

    public static final Exchange BITFINEX = Exchange.forSymbolOrCreate("BITFINEX");
    public static final Exchange BITSTAMP = Exchange.forSymbolOrCreate("BITSTAMP");
    public static final Exchange BTCCHINA = Exchange.forSymbolOrCreate("BTCCHINA");
    public static final Exchange BTCE = Exchange.forSymbolOrCreate("BTCE");
    public static final Exchange BTER = Exchange.forSymbolOrCreate("BTER");
    public static final Exchange COINBASE = Exchange.forSymbolOrCreate("COINBASE");
    public static final Exchange CRYPTSY = Exchange.forSymbolOrCreate("CRYPTSY");
    public static final Exchange CAMPBX = Exchange.forSymbolOrCreate("CAMPBX");
}
