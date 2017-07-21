package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.PostPersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.TradeDao;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.Visitor;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * Trade represents a single known transaction of a Market
 * 
 * @author Tim Olson
 */
@Entity
//@Cacheable(false)
@Table(indexes = {@Index(columnList = "time"), @Index(columnList = "timeReceived"), @Index(columnList = "market"),
    @Index(columnList = "market,time"), @Index(columnList = "market,remoteKey")})
public class Trade extends PriceData {

  @Inject
  protected transient TradeDao tradeDao;
  private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

  // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
  private static final String SEPARATOR = ",";

  public <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {

    //  em = createEntityManager();
    return tradeDao.queryZeroOne(resultType, queryStr, params);

  }

  @Override
  @Transient
  public EntityBase getParent() {

    return null;
  }

  @Override
  @Transient
  public TradeDao getDao() {
    return tradeDao;
  }

  @Override
  @Transient
  public synchronized void setDao(Dao dao) {
    tradeDao = (TradeDao) dao;
    // TODO Auto-generated method stub
    //  return null;
  }

  public static Trade fromDoubles(Tradeable market, Instant time, @Nullable String remoteKey, double price, double volume) {
    long priceCount;
    long volumeCount;
    priceCount = Math.round(price / market.getPriceBasis());
    volumeCount = Math.round(volume / market.getVolumeBasis());
    return new Trade(market, time, remoteKey, priceCount, volumeCount);

  }

  public static Trade fromDoubles(Tradeable tradeable, Instant time, Instant timeRecieved, @Nullable String remoteKey, double price, double volume) {
    long priceCount;
    long volumeCount;
    Market market = (Market) tradeable;
    priceCount = Math.round(price / market.getPriceBasis());
    volumeCount = Math.round(volume / market.getVolumeBasis());
    return new Trade(market, time, remoteKey, priceCount, volumeCount);

  }

  @Override
  public synchronized void persit() {

    this.setPeristanceAction(PersistanceAction.NEW);

    this.setRevision(this.getRevision() + 1);
    tradeDao.persist(this);
  }

  @Override
  public synchronized EntityBase refresh() {
    return tradeDao.refresh(this);
  }

  /**
   * @param market what Market was traded
   * @param time when the trade originally occured
   * @param remoteKey the unique key assigned by the market data provider to this trade. helps prevent duplication of market data
   * @param priceCount the trade price as a count of "pips," where the size of the pip is the market's priceBasis()
   * @param volumeCount the trade price as a count of "pips," where the size of the pip is the market's volumeBasis()
   * @return
   */
  @AssistedInject
  public Trade(@Assisted Tradeable market, @Assisted Instant time, @Assisted @Nullable String remoteKey, @Assisted("tradePrice") double price,
      @Assisted("tradeVolume") double volume) {
    this(market, time, remoteKey, Math.round(price / market.getPriceBasis()), Math.round(volume / market.getVolumeBasis()));

    //   super(tradeable, time, remoteKey, BigDecimal.valueOf(price), BigDecimal.valueOf(volume));
    //this(tradeable, time, remoteKey, BigDecimal.valueOf(price), BigDecimal.valueOf(volume));

  }

  @AssistedInject
  public Trade(@Assisted Tradeable market, @Assisted("tradeTime") Instant time, @Assisted("timeRecieved") Instant timeRecieved,
      @Assisted @Nullable String remoteKey, @Assisted("tradePrice") double price, @Assisted("tradeVolume") double volume) {
    this(market, time, timeRecieved, remoteKey, Math.round(price / market.getPriceBasis()), Math.round(volume / market.getVolumeBasis()));

    // super(time, timeRecieved, remoteKey, tradeable, BigDecimal.valueOf(price), BigDecimal.valueOf(volume));

  }

  @AssistedInject
  public Trade(@Assisted Tradeable market, @Assisted Instant time, @Assisted @Nullable String remoteKey,
      @Assisted("tradePriceCount") long priceCount, @Assisted("tradeVolumeCount") long volumeCount) {
    super(time, remoteKey, market, priceCount, volumeCount);
  }

  @AssistedInject
  public Trade(@Assisted Tradeable market, @Assisted("tradeTime") Instant time, @Assisted("tradeTimeRecieved") Instant timeRecieved,
      @Assisted @Nullable String remoteKey, @Assisted("tradePriceCount") long priceCount, @Assisted("tradeVolumeCount") long volumeCount) {
    super(time, timeRecieved, remoteKey, market, priceCount, volumeCount);
  }

  @AssistedInject
  public Trade(@Assisted Tradeable tradeable, @Assisted Instant time, @Assisted @Nullable String remoteKey, @Assisted("tradePrice") BigDecimal price,
      @Assisted("tradeVolume") BigDecimal volume) {
    super(time, remoteKey, tradeable, price, volume);
  }

  public static void find(Interval timeInterval, Visitor<Trade> visitor) {
    EM.queryEach(Trade.class, visitor, "select t from Trade t where time > ?1 and time < ?2", timeInterval.getStartMillis(),
        timeInterval.getEndMillis());
  }

  public static void forAll(Visitor<Trade> visitor) {
    EM.queryEach(Trade.class, visitor, "select t from Trade t");
  }

  @Override
  @PostPersist
  public synchronized void postPersist() {

    detach();

  }

  @Override
  public String toString() {

    return "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "Market=" + getMarket() + SEPARATOR + "Price="
        + getPriceAsDouble() + SEPARATOR + "Volume=" + getVolumeAsDouble();
  }

  public Trade() {
  } // JPA only

  @Override
  public synchronized void detach() {
    // tradeDao.detach(this);

  }

  @Override
  public synchronized void merge() {

    this.setPeristanceAction(PersistanceAction.MERGE);

    this.setRevision(this.getRevision() + 1);
    tradeDao.merge(this);
    // TODO Auto-generated method stub

  }

  @Override
  public synchronized void delete() {
    // TODO Auto-generated method stub

  }

  @Override
  public synchronized void prePersist() {
    // TODO Auto-generated method stub

  }

}
