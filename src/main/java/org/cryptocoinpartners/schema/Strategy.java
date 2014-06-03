package org.cryptocoinpartners.schema;

import javax.persistence.Entity;


/**
 * A Strategy represents an approach to trading.  Every Strategy is attached to one and only one Fund.  The Strategy
 * places Orders to manipulate Positions in the Fund on behalf of the owners of the Fund.
 *
 * @author Tim Olson
 */
@Entity
public class Strategy extends FundManager {
}
