package com.cryptocoinpartners.schema;

import javax.persistence.Entity;


/**
 * A Transfer represents the modification of multiple Positions, whether it is the purchase of a Listing or transfer
 * of funds between Accounts
 * @author Tim Olson
 */
@Entity
public class Transfer extends EntityBase {
    enum TransferStatus { OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED }
    // todo
}
