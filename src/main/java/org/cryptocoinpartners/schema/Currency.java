package org.cryptocoinpartners.schema;


import org.cryptocoinpartners.util.PersistUtil;

import javax.persistence.Entity;
import javax.persistence.NoResultException;
import java.util.List;


/**
 * @author Tim Olson
 */
@Entity
public class Currency extends Fungible {

    public boolean isFiat() { return fiat; }


    public static Currency forSymbol( String symbol ) {
        return PersistUtil.queryOne(Currency.class, "select c from Currency c where symbol=?1", symbol);
    }


    public static List<String> allSymbols() {
        return PersistUtil.queryList(String.class, "select symbol from Currency");
    }


    // JPA
    protected Currency() {}
    protected void setFiat(boolean fiat) { this.fiat = fiat; }


    // used by Currencies
    static Currency forSymbolOrCreate( String symbol, boolean isFiat, double basis )
    {
        try {
            return forSymbol(symbol);
        }
        catch( NoResultException e ) {
            final Currency currency = new Currency(isFiat, symbol, basis);
            PersistUtil.insert(currency);
            return currency;
        }
    }


    private Currency(boolean fiat, String symbol, double basis) {
        super(symbol,basis);
        this.fiat = fiat;
    }


    private boolean fiat;
}
