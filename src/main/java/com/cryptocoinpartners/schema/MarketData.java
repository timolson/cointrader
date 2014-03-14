package com.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.persistence.ManyToOne;
import java.util.UUID;


/**
 * @author Tim Olson
 */
public class MarketData extends Event implements HasGuid {


    public MarketData(Instant time, Security security) {
        super(time);
        this.security = security;
    }


    @ManyToOne
    public Security getSecurity() { return security; }
    public String getGuid() { return guid; }

    /**
     * this is the time when this event object was created.  it may be later than getTime() due to transmission delays
     * @return
     */
    public Instant getCreatedTime() {
        return createdTime;
    }


    private Instant createdTime = Instant.now();
    private Security security;
    private String guid = UUID.randomUUID().toString();
}
