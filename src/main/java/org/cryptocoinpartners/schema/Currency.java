package org.cryptocoinpartners.schema;

import java.util.List;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.NoResultException;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.CurrencyJpaDao;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.util.EM;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * @author Tim Olson
 */
@Entity
@Cacheable
public class Currency extends Asset {

    /**
     * 
     */
    private static final long serialVersionUID = 5360515183621144962L;

    @Inject
    protected static CurrencyJpaDao currencyDao;

    @Inject
    protected transient static CurrencyFactory currencyFactory;

    public boolean isFiat() {
        return fiat;
    }

    public static Currency forSymbol(String symbol) {
        return EM.queryOne(Currency.class, "select c from Currency c where symbol=?1", symbol);
    }

    public static List<String> allSymbols() {
        return EM.queryList(String.class, "select symbol from Currency");
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
            Currency currency = forSymbol(symbol);
            currencyDao.persistEntities(currency);
            return currency;
        } catch (NoResultException e) {

            //
            final Currency currency = new Currency(isFiat, symbol, basis);
            // final Currency currency = currencyFactory.create(isFiat, symbol, basis);
            currencyDao.persistEntities(currency);
            return currency;
        }
    }

    // used by Currencies
    static Currency forSymbolOrCreate(String symbol, boolean isFiat, double basis, double multiplier) {
        try {
            return forSymbol(symbol);
        } catch (NoResultException e) {
            final Currency currency = new Currency(isFiat, symbol, basis, multiplier);
            currencyDao.persistEntities(currency);
            return currency;
        }
    }

    @AssistedInject
    private Currency(@Assisted boolean fiat, @Assisted String symbol, @Assisted double basis) {
        super(symbol, basis);
        this.fiat = fiat;
    }

    @AssistedInject
    private Currency(@Assisted boolean fiat, @Assisted String symbol, @Assisted("basis") double basis, @Assisted("multiplier") double multiplier) {
        super(symbol, basis);
        this.fiat = fiat;
        this.multiplier = multiplier;
    }

    private boolean fiat;
    private double multiplier;

    @Override
    public EntityBase refresh() {
        return currencyDao.refresh(this);
    }

    @Override
    public void persit() {

        currencyDao.persist(this);
        // TODO Auto-generated method stub

    }

    @Override
    public void detach() {
        // TODO Auto-generated method stub

    }

    @Override
    public void merge() {
        // TODO Auto-generated method stub

    }

    @Override
    @Transient
    public Dao getDao() {
        return currencyDao;
    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }
}
