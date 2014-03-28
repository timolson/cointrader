package com.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import java.math.BigDecimal;


/**
 * Trade represents a single known exchange of a Listing
 *
 * @author Tim Olson
 */
@Entity
public class Trade extends Pricing {
    public Trade(Listing listing, Instant time, @Nullable String remoteKey, BigDecimal price, BigDecimal amount) {
        super(time, remoteKey, listing, price, amount);
    }


    protected Trade() {}  // JPA only
}
