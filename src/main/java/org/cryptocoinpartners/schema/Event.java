package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.MappedSuperclass;


/**
 * Subclasses of Event may be posted to Context
 *
 * @author Tim Olson
 */
@MappedSuperclass
public abstract class Event extends Temporal {


    /** Most events should use this constructor to provide the time of the original happening, not the time of
     *  object creation */
    protected Event(Instant time) {
        super(time);
    }


    protected Event() { this(Instant.now()); }


}
