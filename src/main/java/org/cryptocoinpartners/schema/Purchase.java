package org.cryptocoinpartners.schema;

import javax.persistence.Entity;


/**
 * A Purchase is a type of Transaction which moves Position(s) between two Portfolios internally.  The Position's
 * Account remains the same.
 *
 * @author Tim Olson
 */
@Entity
public class Purchase extends Transaction {


    private long volumeCount;
    private Market market;
}
