package com.cryptocoinpartners.schema;


import org.joda.time.Instant;

import javax.persistence.Entity;
import java.math.BigDecimal;


/**
 * Trade represents a single known exchange of a Security
 *
 * @author Tim Olson
 */
@Entity
public class Trade extends Pricing {
    public Trade(Security security, Instant time, BigDecimal price, BigDecimal amount) {
        super(time, security, price, amount);
    }


    protected Trade() {}  // JPA only
}
