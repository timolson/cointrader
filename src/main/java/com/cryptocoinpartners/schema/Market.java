package com.cryptocoinpartners.schema;

import java.util.Set;


/**
 * @author Tim Olson
 */
public enum Market {

    BITFINEX, BTC_CHINA, BITSTAMP, BTCE, CRYPTSY;


    public Set<Security> getSecurities() { return Security.forMarket(this); }
}
