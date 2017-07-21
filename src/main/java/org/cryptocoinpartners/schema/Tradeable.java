package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.PostPersist;
import javax.persistence.PrePersist;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.TradeableDao;
import org.cryptocoinpartners.util.EM;

import com.google.inject.Inject;

/**
 * Represents the possibility to trade one Asset for another at a specific Exchange.
 * 
 * @author Tim Olson
 */
@Entity
@Cacheable
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@SuppressWarnings({"JpaDataSourceORMInspection", "UnusedDeclaration"})
//@NamedQuery(name = "Market.findByMarket", query = "select m from Market m where exchange=?1 and listing=?2", hints = { @QueryHint(name = "org.hibernate.cacheable", value = "true") })
//@Table(indexes = { @Index(columnList = "exchange"), @Index(columnList = "listing"), @Index(columnList = "active"), @Index(columnList = "version"),
//      @Index(columnList = "revision") })
public abstract class Tradeable extends EntityBase {

  /**
     * 
     */
  private static final long serialVersionUID = 3040864081511509663L;
  @Inject
  protected transient TradeableDao tradeableDao;

  @Override
  public synchronized void persit() {

    this.setPeristanceAction(PersistanceAction.NEW);

    this.setRevision(this.getRevision() + 1);
    tradeableDao.persist(this);
  }

  @Override
  public synchronized EntityBase refresh() {
    return tradeableDao.refresh(this);
  }

  @Override
  public synchronized void detach() {
    tradeableDao.detach(this);

  }

  @Override
  public synchronized void merge() {

    this.setPeristanceAction(PersistanceAction.MERGE);

    this.setRevision(this.getRevision() + 1);
    tradeableDao.merge(this);

  }

  @Override
  @Transient
  public Dao getDao() {
    return tradeableDao;
  }

  @Override
  @Transient
  public synchronized void setDao(Dao dao) {
    tradeableDao = (TradeableDao) dao;
    // TODO Auto-generated method stub
    //  return null;
  }

  @Override
  public void delete() {
    // TODO Auto-generated method stub

  }

  public static List<Tradeable> findAll() {
    // return EM.queryList(Market.class, "select m from Market m");
    ArrayList<Tradeable> markets = new ArrayList<Tradeable>();
    for (Tradeable tradeable : EM.queryList(Tradeable.class, "select m from Tradeable m")) {
      // market.getExchange();
      // forSymbolOrCreate
      // Exchange injectedExchange = exchangeFactory.create(market.getExchange().getSymbol());
      if (!tradeable.isSynthetic()) {
        Market market = (Market) tradeable;
        market.setExchange(market.getExchange().forSymbolOrCreate(market.getExchange().getSymbol()));
        markets.add(market);
      } else {
        //lets load all the markets and return it.
        SyntheticMarket synthetic = (SyntheticMarket) tradeable;

        markets.add(synthetic);

      }

    }

    return markets;
  }

  /** @return true iff the Listing is currently traded at the Exchange. The Market could have been retired. */
  public boolean isActive() {
    return active;
  }

  @Transient
  public abstract Amount getBalance(Asset currency);

  @Transient
  public abstract boolean isSynthetic();

  @Override
  @PrePersist
  public abstract void prePersist();

  @Override
  @PostPersist
  public abstract void postPersist();

  @Transient
  public abstract String getSymbol();

  @Override
  public abstract String toString();

  public static Tradeable forSymbol(String marketSymbol) {
    for (Tradeable market : findAll()) {
      if (market.getSymbol().equalsIgnoreCase(marketSymbol)) {
        return market;
      }
    }
    return null;
  }

  public static List<String> allSymbols() {
    List<String> result = new ArrayList<>();
    List<Market> markets = EM.queryList(Market.class, "select m from Market m");
    for (Market market : markets)
      result.add((market.getSymbol()));
    return result;
  }

  public static class MarketAmountBuilder {
  }

  protected synchronized void setPriceBasis(double quoteBasis) {
    this.priceBasis = quoteBasis;
  }

  protected synchronized void setVolumeBasis(double volumeBasis) {
    this.volumeBasis = volumeBasis;
  }

  @Basic(optional = false)
  @Column(insertable = false, updatable = false)
  public abstract double getVolumeBasis();

  @Basic(optional = false)
  @Column(insertable = false, updatable = false)
  public abstract double getPriceBasis();

  @Transient
  public int getScale() {

    int length = (int) (Math.log10(getPriceBasis()));
    return length;
  }

  // JPA
  protected Tradeable() {
  }

  protected synchronized void setActive(boolean active) {
    this.active = active;
  }

  protected boolean active;
  protected double priceBasis;
  protected double volumeBasis;

}
