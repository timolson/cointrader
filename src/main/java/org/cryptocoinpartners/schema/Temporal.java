package org.cryptocoinpartners.schema;

import java.util.Date;

import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

/**
 * @author Tim Olson
 */
@MappedSuperclass
public class Temporal extends EntityBase {

    public Temporal(Instant time) {
        super();
        this.time = time;
        this.dateTime = time.toDate();
        this.timestamp = time.getMillis();
    }

    /** For Events, this is the time the Event itself occured, not the time we received the Event.  It should be remote
     * server time if available, and local time if the object was created locally */
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    @Basic(optional = false)
    public Instant getTime() {
        return time;
    }

    @Transient
    public Date getDateTime() {
        return dateTime;
    }

    @Transient
    public long getTimestamp() {
        return timestamp;
    }

    // JPA
    protected Temporal() {
    }

    protected void setTime(Instant time) {
        this.time = time;
        this.dateTime = time.toDate();
        this.timestamp = time.getMillis();
    }

    protected Instant time;
    private long timestamp;
    private Date dateTime;

}
