package org.cryptocoinpartners.schema;

import java.util.List;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.NoResultException;

import org.cryptocoinpartners.util.PersistUtil;

/**
 * @author Tim Olson
 */
@Entity
@Cacheable
public class Currency extends Asset {

    public boolean isFiat() {
        return fiat;
    }

    public static Currency forSymbol(String symbol) {
        return PersistUtil.queryOne(Currency.class, "select c from Currency c where symbol=?1", symbol);
    }

    public static List<String> allSymbols() {
        return PersistUtil.queryList(String.class, "select symbol from Currency");
    }

    // JPA
    protected Currency() {
    }

    protected void setFiat(boolean fiat) {
        this.fiat = fiat;
    }

    // used by Currencies
    static Currency forSymbolOrCreate(String symbol, boolean isFiat, double basis) {
        try {
            return forSymbol(symbol);
        } catch (NoResultException e) {
            final Currency currency = new Currency(isFiat, symbol, basis);
            PersistUtil.insert(currency);
            return currency;
        }
    }

    // used by Currencies
    static Currency forSymbolOrCreate(String symbol, boolean isFiat, double basis, double multiplier) {
        try {
            return forSymbol(symbol);
        } catch (NoResultException e) {
            final Currency currency = new Currency(isFiat, symbol, basis, multiplier);
            PersistUtil.insert(currency);
            return currency;
        }
    }

    private Currency(boolean fiat, String symbol, double basis) {
        super(symbol, basis);
        this.fiat = fiat;
    }

    private Currency(boolean fiat, String symbol, double basis, double multiplier) {
        super(symbol, basis);
        this.fiat = fiat;
        this.multiplier = multiplier;
    }

    private boolean fiat;
    private double multiplier;
}
