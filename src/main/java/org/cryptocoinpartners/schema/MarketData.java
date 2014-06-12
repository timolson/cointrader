package org.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;


/**
 * @author Tim Olson
 */
@Entity
public abstract class MarketData extends RemoteEvent {


    protected MarketData(Instant time, @Nullable String remoteKey, Market market) {
        super(time,remoteKey);
        this.market = market;
    }


    @ManyToOne(optional = false)
    public Market getMarket() { return market; }


    // JPA
    protected MarketData() {
        super();
    }

    protected void setMarket(Market market) { this.market = market; }


    private Market market;
}
