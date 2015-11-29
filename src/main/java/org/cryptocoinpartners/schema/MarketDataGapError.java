package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;

import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Type;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Interval;

/**
 * @author Tim Olson
 */
@Entity
public class MarketDataGapError extends MarketDataError {

    /**
     * 
     */
    private static final long serialVersionUID = -6827692162061438210L;

    public MarketDataGapError(Market market, Duration gapDuration) {
        super(market);
        gapInterval = new Interval(Instant.now(), Instant.now().withDurationAdded(gapDuration, 1));
    }

    public MarketDataGapError(Market market, Duration gapDuration, @Nullable Exception exception) {
        super(market, exception);
        gapInterval = new Interval(Instant.now(), Instant.now().withDurationAdded(gapDuration, 1));
    }

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInterval")
    @Columns(columns = { @Column(name = "gapIntervalStart"), @Column(name = "gapIntervalEnd") })
    public Interval getGapInterval() {
        return gapInterval;
    }

    protected MarketDataGapError() {
    }

    protected void setGapInterval(Interval gapInterval) {
        this.gapInterval = gapInterval;
    }

    private Interval gapInterval;
}
