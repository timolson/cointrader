package com.cryptocoinpartners.schema;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.MappedSuperclass;


@MappedSuperclass
public class RemoteEvent extends Event {

    public RemoteEvent(Instant time,@Nullable String remoteKey) {
        super(time);
        this.remoteKey = remoteKey;
    }

    protected RemoteEvent() {
    }


    /**
     * @return the time when this event object was created.  it may be later than getTime() due to transmission delays
     */
    @Type(type="org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    public Instant getTimeReceived() {
        return timeReceived;
    }

    public String getRemoteKey() { return remoteKey; }


    protected void setTimeReceived(Instant timeReceived) { this.timeReceived = timeReceived; }

    protected void setRemoteKey(String remoteKey) { this.remoteKey = remoteKey; }


    private Instant timeReceived = Instant.now();
    private String remoteKey;
}
