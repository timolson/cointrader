package com.cryptocoinpartners.schema;


import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import java.util.UUID;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class MarketData extends RemoteEvent {


    public MarketData(Instant time, Security security) {
        super(time);
        this.security = security;
    }


    public @ManyToOne Security getSecurity() { return security; }


    // JPA
    protected MarketData() {
        super();
    }

    protected void setSecurity(Security security) { this.security = security; }


    private Security security;
}
