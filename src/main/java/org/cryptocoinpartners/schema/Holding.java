package org.cryptocoinpartners.schema;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.HoldingDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * A Holding represents an Asset on an Exchange. It does not specify how much of the asset is held. See Position
 * 
 * @author Tim Olson
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
//@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
//@Cacheable
public abstract class Holding extends EntityBase {
  @Inject
  protected transient HoldingDao holdingDao;
  @Inject
  protected transient static BalanceFactory balanceFactory;
  protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.holding");

  //private Amount volume;
  // private long volumeCount;

  public Holding(Exchange exchange, Asset asset) {
    this.exchange = exchange;
    this.asset = asset;
  }

  public static Holding forSymbol(String symbol) {
    log.debug("Holding - forSymbol: called from class " + Thread.currentThread().getStackTrace()[2]);
    Matcher matcher = Pattern.compile("(\\w+):(\\w+)").matcher(symbol);
    if (!matcher.matches())
      throw new IllegalArgumentException("Could not parse Holding symbol " + symbol);
    log.debug("Holding - forSymbol creating balance for exchange " + matcher.group(1) + " asset: " + matcher.group(2));

    return balanceFactory.create(Exchange.forSymbol(matcher.group(1)), Asset.forSymbol(matcher.group(2)));
  }

  //  @ManyToOne
  /*
   * @ManyToOne(optional = true) //(cascade = { CascadeType.MERGE })
   * @JoinColumn(name = "portfolio") public Portfolio getPortfolio() { return portfolio; } public synchronized void setPortfolio(Portfolio portfolio)
   * { this.portfolio = portfolio; }
   */

  @ManyToOne(optional = true)
  @JoinColumn(insertable = false, updatable = false)
  public abstract Exchange getExchange();

  public abstract void setExchange(Exchange exchange);

  @OneToOne(optional = true, fetch = FetchType.EAGER)
  @JoinColumn(insertable = false, updatable = false)
  public abstract Asset getAsset();

  public abstract void setAsset(Asset asset);

  @Override
  public String toString() {
    return exchange.getSymbol() + asset.getSymbol();
  }

  // JPA
  protected Holding() {
  }

  protected Exchange exchange;
  protected Asset asset;

  @Override
  public synchronized void persit() {
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
    return holdingDao;
  }

  @Override
  public synchronized void delete() {
    // TODO Auto-generated method stub

  }

  @Override
  public synchronized EntityBase refresh() {
    // TODO Auto-generated method stub
    return null;
  }
}
