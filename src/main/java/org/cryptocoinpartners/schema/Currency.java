package org.cryptocoinpartners.schema;

import java.util.List;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.NoResultException;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.CurrencyJpaDao;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.util.EM;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

// import org.cryptocoinpartners.util.EM;

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
  protected transient static CurrencyJpaDao currencyDao;

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

  @Override
  @Transient
  public EntityBase getParent() {

    return null;
  }

  // JPA
  protected Currency() {
  }

  protected synchronized void setFiat(boolean fiat) {
    this.fiat = fiat;
  }

  // used by Currencies
  static Currency forSymbolOrCreate(String symbol, boolean isFiat, double basis) {
    try {
      Currency currency = forSymbol(symbol);
      //  currency.setRevision(currency.getRevision() + 1);

      // currencyDao.persistEntities(currency);
      return currency;
    } catch (NoResultException e) {

      //
      final Currency currency = new Currency(isFiat, symbol, basis);
      //Injector.root().injectMembers(currency);
      // final Currency currency = currencyFactory.create(isFiat, symbol, basis);
      currency.setRevision(currency.getRevision() + 1);
      try {
        currencyDao.persistEntities(currency);
      } catch (Throwable e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      return currency;
    }
  }

  // used by Currencies
  static Currency forSymbolOrCreate(String symbol, boolean isFiat, double basis, double multiplier) {
    try {
      return forSymbol(symbol);
    } catch (NoResultException e) {
      final Currency currency = new Currency(isFiat, symbol, basis, multiplier);
      currency.setRevision(currency.getRevision() + 1);
      try {
        currencyDao.persistEntities(currency);
      } catch (Throwable e1) {
        // TODO Auto-generated catch block

      }
      return currency;
    }
  }

  @AssistedInject
  public Currency(@Assisted boolean fiat, @Assisted String symbol, @Assisted double basis) {
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
  public synchronized void persit() {
    this.setPeristanceAction(PersistanceAction.NEW);

    this.setRevision(this.getRevision() + 1);
    try {
      currencyDao.persistEntities(this);
    } catch (Throwable e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    // TODO Auto-generated method stub

  }

  @Override
  public synchronized void detach() {
    // TODO Auto-generated method stub

  }

  @Override
  public synchronized void merge() {
    // TODO Auto-generated method stub

  }

  @Override
  @Transient
  public Dao getDao() {
    return currencyDao;
  }

  @Override
  @Transient
  public synchronized void setDao(Dao dao) {
    currencyDao = (CurrencyJpaDao) dao;
    // TODO Auto-generated method stub
    //  return null;
  }

  @Override
  public void delete() {
    // TODO Auto-generated method stub

  }

  @Override
  public synchronized void prePersist() {
    // TODO Auto-generated method stub

  }

  @Override
  public synchronized void postPersist() {
    // TODO Auto-generated method stub

  }
}
