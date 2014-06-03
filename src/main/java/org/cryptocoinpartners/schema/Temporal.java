package org.cryptocoinpartners.schema;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import javax.persistence.MappedSuperclass;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class Temporal extends EntityBase {

    public Temporal(Instant time) {
        super();
        this.time = time;
    }


    /** For Events, this is the time the Event itself occured, not the time we received the Event.  It should be remote
     * server time if available, and local time if the object was created locally */
    @Type(type="org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    public Instant getTime() { return time; }


    // JPA
    protected Temporal() {}
    protected void setTime(Instant time) { this.time = time; }


    protected Instant time;
}
