package com.cryptocoinpartners.schema;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import javax.persistence.MappedSuperclass;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class Event extends EntityBase {

    /**
     * this is the time the event itself occured, not the time we received the event.  It should be remote server
     * time if available.
     * @return
     */
    @Type(type="org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    public Instant getTime() { return time; }


    /** Most events should use this constructor to provide the time of the original happening, not the time of
     *  object creation */
    public Event(Instant time) {
        super();
        this.time = time;
    }


    public Event() { this(Instant.now()); }


    // JPA
    protected void setTime(Instant time) { this.time = time; }


    private Instant time;
}
