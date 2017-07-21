package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.BarJpaDao;
import org.cryptocoinpartners.schema.dao.Dao;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

@SuppressWarnings("UnusedDeclaration")
@Entity
@Table(indexes = {@Index(columnList = "time")})
public class Bar extends MarketData {
  private long timestamp;
  private Double open;
  private Double interval;
  private Double close;
  private Double high;
  private Double low;
  private Double volume;
  private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
  @Inject
  protected transient BarJpaDao barDao;
  // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
  private static final String SEPARATOR = ",";

  @AssistedInject
  public Bar(@Assisted long timestamp, @Assisted("barInterval") Double interval, @Assisted("barOpen") Double open,
      @Assisted("barClose") Double close, @Assisted("barHigh") Double high, @Assisted("barLow") Double low, @Assisted("barVolume") Double volume,
      @Assisted Tradeable market) {
    this(new Instant(timestamp), Instant.now(), null, interval, open, close, high, low, volume, market);

  }

  @AssistedInject
  public Bar(@Assisted Bar bar) {
    super(bar.time, bar.remoteKey, bar.getMarket());
    this.interval = bar.interval;
    this.open = bar.open;
    this.close = bar.close;
    this.high = bar.high;
    this.low = bar.low;
    this.volume = bar.volume;

  }

  @AssistedInject
  public Bar(@Assisted("barTime") Instant time, @Assisted("barRecievedTime") Instant recievedTime, @Nullable @Assisted String remoteKey,
      @Assisted("barInterval") Double interval, @Assisted("barOpen") Double open, @Assisted("barClose") Double close,
      @Assisted("barHigh") Double high, @Assisted("barLow") Double low, @Assisted("barVolume") Double volume, @Assisted Tradeable market) {
    super(time, remoteKey, market);
    this.interval = interval;
    this.open = open;
    this.close = close;
    this.high = high;
    this.low = low;
    this.volume = volume;

  }

  public <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {

    //  em = createEntityManager();
    return barDao.queryZeroOne(resultType, queryStr, params);

  }

  @Override
  public synchronized void persit() {
    this.setPeristanceAction(PersistanceAction.NEW);

    this.setRevision(this.getRevision() + 1);
    barDao.persist(this);
  }

  @Override
  public synchronized EntityBase refresh() {
    return barDao.refresh(this);
  }

  @Override
  @Transient
  public EntityBase getParent() {

    return null;
  }

  public Double getOpen() {
    return open;
  }

  @Column(name = "\"interval\"")
  public Double getInterval() {
    return interval;
  }

  public Double getClose() {
    return close;
  }

  public Double getVolume() {
    return volume;
  }

  public Double getHigh() {
    return high;
  }

  public Double getLow() {
    return low;
  }

  protected synchronized void setOpen(Double open) {
    this.open = open;
  }

  protected synchronized void setInterval(Double interval) {
    this.interval = interval;
  }

  protected synchronized void setHigh(Double high) {
    this.high = high;
  }

  protected synchronized void setVolume(Double volume) {
    this.volume = volume;
  }

  protected synchronized void setLow(Double low) {
    this.low = low;
  }

  protected synchronized void setClose(Double close) {
    this.close = close;
  }

  // JPA
  protected Bar() {
  }

  @Override
  public String toString() {

    return "Bar(" + System.identityHashCode(this) + ") Start=" + (getTimestamp() != 0 ? (FORMAT.print(getTimestamp())) : "") + SEPARATOR + "Market="
        + getMarket() + SEPARATOR + "Interval=" + getInterval() + SEPARATOR + "Open=" + getOpen() + SEPARATOR + "High=" + getHigh() + SEPARATOR
        + "Low=" + getLow() + SEPARATOR + "Close=" + getClose() + SEPARATOR + "Volume=" + getVolume();
  }

  @Override
  public synchronized void detach() {
    barDao.persist(this);

  }

  @Override
  public synchronized void merge() {
    this.setPeristanceAction(PersistanceAction.MERGE);

    this.setRevision(this.getRevision() + 1);
    barDao.merge(this);
    // TODO Auto-generated method stub

  }

  @Override
  @Transient
  public Dao getDao() {
    return barDao;
  }

  @Override
  @Transient
  public synchronized void setDao(Dao dao) {
    barDao = (BarJpaDao) dao;
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
