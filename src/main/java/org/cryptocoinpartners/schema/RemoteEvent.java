package org.cryptocoinpartners.schema;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;


@MappedSuperclass
public abstract class RemoteEvent extends Event {

    protected RemoteEvent( Instant time, @Nullable String remoteKey) {
        super(time);
        this.remoteKey = remoteKey;
    }

    /**
     * @return the time when this event object was created.  it may be later than getTime() due to transmission delays
     */
    @Type(type="org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    @Basic(optional = false)
    public Instant getTimeReceived() { return timeReceived; }


    @Basic(optional = true)
    public String getRemoteKey() { return remoteKey; }


    // JPA
    protected RemoteEvent() {}
    protected void setTimeReceived(Instant timeReceived) { this.timeReceived = timeReceived; }
    protected void setRemoteKey(@Nullable String remoteKey) { this.remoteKey = remoteKey; }


    private Instant timeReceived = Instant.now();
    private String remoteKey;
}
