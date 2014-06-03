package org.cryptocoinpartners.schema;

import org.joda.time.Duration;

import javax.persistence.Entity;


/**
 * A Transaction represents the modification of multiple Positions, whether it is the purchase of a MarketListing or transfer
 * of funds between Accounts
 * @author Tim Olson
 */
@Entity
public class Transaction extends EntityBase {


    enum TransactionStatus { OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED }

     // todo add basis rounding

    private Duration estimatedDelay;
}
