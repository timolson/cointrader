package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Transient;

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
@Table(indexes = { @Index(columnList = "time") })
public class Bar extends MarketData {
    private long timestamp;
    private Double open;
    private Double close;
    private Double high;
    private Double low;
    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    @Inject
    protected BarJpaDao barDao;
    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    private static final String SEPARATOR = ",";

    @AssistedInject
    public Bar(@Assisted long timestamp, @Assisted("barOpen") Double open, @Assisted("barClose") Double close, @Assisted("barHigh") Double high,
            @Assisted("barLow") Double low, @Assisted Market market) {
        this(new Instant(timestamp), Instant.now(), null, open, close, high, low, market);

    }

    @AssistedInject
    public Bar(@Assisted Bar bar) {
        super(bar.time, bar.remoteKey, bar.getMarket());
        this.open = bar.open;
        this.close = bar.close;
        this.high = bar.high;
        this.low = bar.low;

    }

    @AssistedInject
    public Bar(@Assisted("barTime") Instant time, @Assisted("barRecievedTime") Instant recievedTime, @Nullable @Assisted String remoteKey,
            @Assisted("barOpen") Double open, @Assisted("barClose") Double close, @Assisted("barHigh") Double high, @Assisted("barLow") Double low,
            @Assisted Market market) {
        super(time, remoteKey, market);
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;

    }

    public <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {

        //  em = createEntityManager();
        return barDao.queryZeroOne(resultType, queryStr, params);

    }

    @Override
    public void persit() {
        barDao.persist(this);
    }

    @Override
    public EntityBase refresh() {
        return barDao.refresh(this);
    }

    public Double getOpen() {
        return open;
    }

    public Double getClose() {
        return close;
    }

    public Double getHigh() {
        return high;
    }

    public Double getLow() {
        return low;
    }

    protected void setOpen(Double open) {
        this.open = open;
    }

    protected void setHigh(Double high) {
        this.high = high;
    }

    protected void setLow(Double low) {
        this.low = low;
    }

    protected void setClose(Double close) {
        this.close = close;
    }

    // JPA
    protected Bar() {
    }

    @Override
    public String toString() {

        return "Bar Start=" + (getTimestamp() != 0 ? (FORMAT.print(getTimestamp())) : "") + SEPARATOR + "Market=" + getMarket() + SEPARATOR + "Open="
                + getOpen() + SEPARATOR + "High=" + getHigh() + SEPARATOR + "Low=" + getLow() + SEPARATOR + "Close=" + getClose();
    }

    @Override
    public void detach() {
        barDao.persist(this);

    }

    @Override
    public void merge() {
        barDao.merge(this);
        // TODO Auto-generated method stub

    }

    @Override
    @Transient
    public Dao getDao() {
        return barDao;
    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }
}
