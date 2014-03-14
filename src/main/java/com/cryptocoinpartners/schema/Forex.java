package com.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;


/**
 * @author Tim Olson
 */
@Entity
public class Forex extends Security {


    @Enumerated(EnumType.STRING)
    public Currency getBaseCurrency() {
        return baseCurrency;
    }


    @Enumerated(EnumType.STRING)
    public Currency getQuoteCurrency() {
        return quoteCurrency;
    }


    public Forex(Market market, Currency baseCurrency, Currency quoteCurrency) {
        this(market, baseCurrency, quoteCurrency,'.');
    }


    public Forex(Market market, Currency baseCurrency, Currency quoteCurrency, char delimiter) {
        this(market, baseCurrency, quoteCurrency,baseCurrency.toString()+delimiter+quoteCurrency);
    }


    public Forex(Market market, Currency baseCurrency, Currency quoteCurrency, String symbol) {
        super(market, symbol);
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }


    // JPA
    protected Forex() { }
    protected void setBaseCurrency(Currency baseCurrency) { this.baseCurrency = baseCurrency; }
    protected void setQuoteCurrency(Currency quoteCurrency) { this.quoteCurrency = quoteCurrency; }

    private Currency baseCurrency;
    private Currency quoteCurrency;
}
