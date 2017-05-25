package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;

import org.joda.time.Instant;

/**
 * @author Tim Olson
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
//ce(strategy = InheritanceType.TABLE_PER_CLASS)
//@Cacheable(false)
public abstract class MarketData extends RemoteEvent {

    protected MarketData(Instant time, @Nullable String remoteKey, Tradeable market) {
        this(time, Instant.now(), remoteKey, market);
    }

    protected MarketData(Instant time, Instant timeReceived, String remoteKey, Tradeable market) {
        super(time, timeReceived, remoteKey);
        this.market = market;
    }

    @ManyToOne(optional = false)
    public Tradeable getMarket() {

        return market;
    }

    // JPA
    protected MarketData() {
        super();
    }

    public void setMarket(Tradeable market) {
        this.market = market;
    }

    private Tradeable market;
}
