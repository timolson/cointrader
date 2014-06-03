package org.cryptocoinpartners.schema;

import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Type;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;


/**
 * @author Tim Olson
 */
@Entity
public class MarketDataGapError extends MarketDataError {


    public MarketDataGapError(MarketListing marketListing, Duration gapDuration) {
        super(marketListing);
        gapInterval = new Interval(Instant.now(),Instant.now().withDurationAdded(gapDuration,1));
    }


    public MarketDataGapError(MarketListing marketListing, Duration gapDuration, @Nullable Exception exception) {
        super(marketListing, exception);
        gapInterval = new Interval(Instant.now(),Instant.now().withDurationAdded(gapDuration,1));
    }


    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInterval")
    @Columns(columns = { @Column(name = "gapIntervalStart"), @Column(name = "gapIntervalEnd") })
    public Interval getGapInterval() {
        return gapInterval;
    }


    protected MarketDataGapError() {}
    protected void setGapInterval(Interval gapInterval) { this.gapInterval = gapInterval; }


    private Interval gapInterval;
}
