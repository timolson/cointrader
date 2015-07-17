package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import org.joda.time.Instant;

/**
 * @author Tim Olson
 */
@Entity
@Cacheable(false)
public abstract class MarketData extends RemoteEvent {

    protected MarketData(Instant time, @Nullable String remoteKey, Market market) {
        this(time, Instant.now(), remoteKey, market);
    }

    protected MarketData(Instant time, Instant timeReceived, String remoteKey, Market market) {
        super(time, timeReceived, remoteKey);
        this.market = market;
    }

    @ManyToOne(optional = false)
    public Market getMarket() {
        return market;
    }

    // JPA
    protected MarketData() {
        super();
    }

    protected void setMarket(Market market) {
        this.market = market;
    }

    private Market market;
}
