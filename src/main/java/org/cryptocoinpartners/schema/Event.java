package org.cryptocoinpartners.schema;

import java.io.Serializable;

import javax.persistence.MappedSuperclass;

import org.joda.time.Instant;

/**
 * Subclasses of Event may be posted to Context
 *
 * @author Tim Olson
 */
@MappedSuperclass
public abstract class Event extends Temporal implements Serializable {

    /**
     * If the Event's time has not been set when it is being published, it will be set to the current time of the
     * publishing Context
     */
    public void publishedAt(Instant instant) {
        if (getTime() == null)
            setTime(instant);
    }

    /** Most events should use this constructor to provide the time of the original happening, not the time of
     *  object creation */
    protected Event(Instant time) {
        super(time);
    }

    protected Event() {
    }

}
