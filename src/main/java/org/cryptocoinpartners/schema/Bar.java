package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

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

    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    private static final String SEPARATOR = ",";

    public Bar(long timestamp, Double open, Double close, Double high, Double low, Market market) {
        this(new Instant(timestamp), Instant.now(), null, open, close, high, low, market);

    }

    public Bar(Instant time, Instant recievedTime, @Nullable String remoteKey, Double open, Double close, Double high, Double low, Market market) {
        super(time, remoteKey, market);
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;

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
}
