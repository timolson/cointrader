package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;


/**
 * A Transaction represents the modification of multiple Positions, whether it is a purchase on a Market or a
 * Transfer of Fungibles between Accounts
 * @author Tim Olson
 */
@Entity
public class Transaction extends EntityBase {

    enum TransactionStatus { OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED }

     // todo add basis rounding

    private Instant acceptedTime;
    private Instant closedTime;
    private Instant settledTime;
}
