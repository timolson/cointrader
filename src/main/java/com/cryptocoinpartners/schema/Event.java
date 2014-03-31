package com.cryptocoinpartners.schema;

import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import javax.persistence.MappedSuperclass;


/**
 * Subclasses of Event may be posted to Esper
 *
 * @author Tim Olson
 */
@MappedSuperclass
public abstract class Event extends EntityBase {

    /**
     * this is the time the event itself occured, not the time we received the event.  It should be remote server
     * time if available.
     * @return
     */
    @Type(type="org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    public Instant getTime() { return time; }


    /** Most events should use this constructor to provide the time of the original happening, not the time of
     *  object creation */
    protected Event(Instant time) {
        super();
        this.time = time;
    }


    protected Event() { this(Instant.now()); }


    // JPA
    protected void setTime(Instant time) { this.time = time; }


    private Instant time;
}
