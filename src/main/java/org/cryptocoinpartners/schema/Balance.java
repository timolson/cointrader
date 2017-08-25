package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.BalanceDao;
import org.cryptocoinpartners.schema.dao.Dao;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * A Balance represents an amount of money in a given asset.
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
//@Cacheable
public class Balance extends Holding {
  /**
     * 
     */
  @Inject
  protected transient static BalanceDao balanceDao;
  // @Inject
  //protected BalanceDao balanceDao;
  private static final long serialVersionUID = 4052753022689960658L;
  protected String description;
  protected Amount amount;
  private volatile long amountCount;

  /**
   * Constructor
   * 
   * @param asset The underlying asset
   * @param amount The amount
   */
  @AssistedInject
  public Balance(@Assisted Exchange exchange, @Assisted Asset asset) {

    super(exchange, asset);
    this.getId();

    this.description = "";
    if (getDao() != null)
      getDao().persist(this);
  }

  @AssistedInject
  public Balance(@Assisted Exchange exchange, @Assisted Asset asset, @Assisted long amountCount) {
    super(exchange, asset);
    this.getId();

    //  this.amount = amount;
    this.amountCount = amountCount;
    this.description = "";
    if (getDao() != null)
      getDao().persist(this);
  }

  //  public static Balance forSymbol(String symbol) {
  //    Matcher matcher = Pattern.compile("(\\w+):(\\w+)").matcher(symbol);
  //       if (!matcher.matches())
  //         throw new IllegalArgumentException("Could not parse Holding symbol " + symbol);
  //      return new Balance(Exchange.forSymbol(matcher.group(1)), Asset.forSymbol(matcher.group(2)));
  // }

  /**
   * Additional constructor with optional description
   * 
   * @param description Optional description to distinguish same asset Balances
   */
  @AssistedInject
  public Balance(@Assisted Exchange exchange, @Assisted Asset asset, @Assisted long amountCount, @Assisted String description) {
    super(exchange, asset);
    this.getId();

    this.amountCount = amountCount;
    this.description = description;
    if (getDao() != null)
      getDao().persist(this);
  }

  protected synchronized void setAmountCount(long amountCount) {

    this.amountCount = amountCount;
  }

  public static Balance forSymbol(Exchange exchange, String symbol) {
    symbol = symbol.toUpperCase();
    final int dot = symbol.indexOf(':');
    if (dot == -1)
      throw new IllegalArgumentException("Invalid Listing symbol: \"" + symbol + "\"");
    final String baseSymbol = symbol.substring(0, dot);
    Asset base = Asset.forSymbol(baseSymbol);
    if (base == null)
      throw new IllegalArgumentException("Invalid base symbol: \"" + baseSymbol + "\"");
    int len = symbol.substring(dot + 1, symbol.length()).indexOf(':');
    len = (len != -1) ? Math.min(symbol.length(), dot + 1 + symbol.substring(dot + 1, symbol.length()).indexOf(':')) : symbol.length();
    //final String amount = symbol.substring(dot + 1, len);
    DiscreteAmount balanceAmount = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(new BigDecimal(symbol.substring(dot + 1, len)),
        base.getBasis()), base.getBasis());

    // long balanceAmount = DiscreteAmount.roundedCountForBasis(new BigDecimal(symbol.substring(dot + 1, len)), base.getBasis());

    return new Balance(exchange, base, balanceAmount.getCount());
    //Balance.forPair(base, quote, prompt);
  }

  public long getAmountCount() {

    return amountCount;
  }

  @Override
  @OneToOne(optional = true, fetch = FetchType.EAGER)
  public Asset getAsset() {
    return asset;
  }

  @Override
  public synchronized void setAsset(Asset asset) {
    this.asset = asset;
  }

  @Transient
  public Amount getAmount() {
    // if (getAmountCount() == 0)
    //   return null;
    return new DiscreteAmount(getAmountCount(), getAsset().getBasis());
  }

  protected synchronized void setAmount(Amount amount) {
    this.amount = amount;
  }

  @Nullable
  public String getDescription() {

    return description;
  }

  protected synchronized void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String toString() {

    return "Balance [asset=" + getAsset() + ", amount=" + getAmount() + ", description=" + getDescription() + "]";
  }

  @Override
  public synchronized void merge() {
    this.setPeristanceAction(PersistanceAction.MERGE);
    this.setRevision(this.getRevision() + 1);
    log.debug("Balance - Merge : Merge of Balance " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);

    try {
      balanceDao.merge(this);
      //if (duplicate == null || duplicate.isEmpty())
    } catch (Exception | Error ex) {

      System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);
      // ex.printStackTrace();

    }
  }

  @Override
  @ManyToOne
  @JoinColumn(name = "exchange")
  public Exchange getExchange() {

    return exchange;
  }

  @Override
  @Transient
  public EntityBase getParent() {

    return getExchange();
  }

  @Override
  public synchronized void setExchange(Exchange exchange) {
    this.exchange = exchange;
  }

  @Override
  @Transient
  public Dao getDao() {
    return balanceDao;
  }

  @Override
  @Transient
  public synchronized void setDao(Dao dao) {
    balanceDao = (BalanceDao) dao;
    // TODO Auto-generated method stub
    //  return null;
  }

  @Override
  public synchronized void persit() {
    this.setPeristanceAction(PersistanceAction.NEW);

    this.setRevision(this.getRevision() + 1);
    log.debug("Balance - Persist : Persist of Balance " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);

    try {
      balanceDao.persist(this);
      //if (duplicate == null || duplicate.isEmpty())
    } catch (Exception | Error ex) {

      System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
      // ex.printStackTrace();

    }

  }

  @Override
  public synchronized void delete() {
    try {
      //log.debug("Balance - delete : Balance of Position " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);
      // this.getExchange().removeBalance(this);
      //   this.getPortfolio().removePosition(this);
      balanceDao.delete(this);
      //if (duplicate == null || duplicate.isEmpty())
    } catch (Exception | Error ex) {

      System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":remove, full stack trace follows:" + ex);
      // ex.printStackTrace();

    }
  }

  @Override
  public int hashCode() {

    final int prime = 31;
    int result = 1;
    result = prime * result + ((exchange == null) ? 0 : exchange.hashCode());
    result = prime * result + ((amount == null) ? 0 : amount.hashCode());
    result = prime * result + ((asset == null) ? 0 : asset.hashCode());
    return result;
  }

  // JPA
  protected Balance() {

  }

  @Override
  public boolean equals(Object obj) {

    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }

    Balance other = (Balance) obj;
    if (exchange == null) {
      if (other.exchange != null) {
        return false;
      }
    } else if (!exchange.equals(other.exchange)) {
      return false;
    }
    if (amount == null) {
      if (other.amount != null) {
        return false;
      }
    } else if (!amount.equals(other.amount)) {
      return false;
    }

    if (asset == null) {
      if (other.asset != null) {
        return false;
      }
    } else if (!asset.equals(other.asset)) {
      return false;
    }

    return true;
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
