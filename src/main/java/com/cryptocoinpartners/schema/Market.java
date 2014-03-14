package com.cryptocoinpartners.schema;

import java.util.Collection;


/**
 * @author Tim Olson
 */
public enum Market {

    BITFINEX, BTC_CHINA, BITSTAMP, BTCE, CRYPTSY;


    public Collection<Security> getSecurities() { return Security.forMarket(this); }
}
