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
public abstract class Temporal extends EntityBase {

    public Temporal(Instant time) {
        super();
        this.id = getId();
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

    //  @Transient
    // @AttributeOverride(name = "timestamp", column = @Column(name = "version")) we need ot set this to last update time.
    public long getTimestamp() {
        if (time == null)
            return 0L;
        else
            return time.getMillis();
        // return timestamp;
    }

    //@Override
    //@AttributeOverride(name = "version", column = @Column(name = "version"))
    // public long getVersion() {
    //if (timestamp == null)
    //  return 0;
    //   version = timestamp;
    // return version;
    // }

    // JPA
    protected Temporal() {
    }

    protected void setTimestamp(long timestamp) {
        //  this.time = time;
        //this.dateTime = time.toDate();
        this.timestamp = timestamp;
    }

    protected void setTime(Instant time) {
        this.time = time;
        this.dateTime = time.toDate();
        setTimestamp(time.getMillis());
    }

    protected Instant time;
    private long timestamp;
    private Date dateTime;

}
