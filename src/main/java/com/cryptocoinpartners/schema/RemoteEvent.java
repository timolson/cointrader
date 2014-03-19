package com.cryptocoinpartners.schema;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import javax.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Created with IntelliJ IDEA.
 * User: mike_d_olson
 * Date: 3/19/14
 * Time: 2:53 PM
 * To change this template use File | Settings | File Templates.
 */
@MappedSuperclass
public class RemoteEvent extends Event implements HasGuid {
    private Instant timeReceived = Instant.now();
    private String guid;

    public RemoteEvent(Instant time) {
        super(time);
    }

    protected RemoteEvent() {
    }

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

    protected void setTimeReceived(Instant timeReceived) { this.timeReceived = timeReceived; }

    protected void setGuid(String guid) { this.guid = guid; }
}
