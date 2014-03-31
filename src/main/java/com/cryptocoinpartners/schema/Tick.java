package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import java.math.BigDecimal;


/**
 * A Tick is a point-in-time snapshot of a Listing
 *
 * @author Tim Olson
 */
@Entity
public class Tick extends Pricing {

    public Tick(Listing listing, String remoteKey, Instant time, BigDecimal price, BigDecimal amount) {
        super(time, remoteKey, listing, price, amount);
    }


    // todo


    // JPA
    protected Tick() {}

}
