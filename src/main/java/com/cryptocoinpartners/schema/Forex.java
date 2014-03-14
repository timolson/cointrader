package com.cryptocoinpartners.schema;

/**
 * @author Tim Olson
 */
public class Forex extends Security {


    public Currency getBaseCurrency() {
        return baseCurrency;
    }


    public Currency getQuoteCurrency() {
        return quoteCurrency;
    }


    public Forex(Market market, Currency baseCurrency, Currency quoteCurrency) {
        super(market, baseCurrency.toString()+'.'+quoteCurrency);
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }


    private Currency baseCurrency;
    private Currency quoteCurrency;
}
