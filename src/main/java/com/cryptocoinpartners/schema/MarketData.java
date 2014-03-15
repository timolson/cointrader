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
public class MarketData extends Event implements HasGuid {


    public MarketData(Instant time, Security security) {
        super(time);
        this.security = security;
    }


    public @ManyToOne Security getSecurity() { return security; }


    public String getGuid() {
        if( guid == null )
            guid = UUID.randomUUID().toString();
        return guid;
    }


    /**
     * this is the time when this event object was created.  it may be later than getTime() due to transmission delays
     * @return
     */
    @Type(type="org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    public Instant getTimeReceived() {
        return timeReceived;
    }


    // JPA
    protected MarketData() {}
    protected void setTimeReceived(Instant timeReceived) { this.timeReceived = timeReceived; }
    protected void setSecurity(Security security) { this.security = security; }
    protected void setGuid(String guid) { this.guid = guid; }


    private Instant timeReceived = Instant.now();
    private Security security;
    private String guid;
}
