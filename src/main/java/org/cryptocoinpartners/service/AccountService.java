package org.cryptocoinpartners.service;

import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.FeeStructure;
import org.cryptocoinpartners.schema.Portfolio;


/**
 * AccountService provides information about Positions in external Accounts and is used for reconciliation and other
 * external queries.  Strategies must use PortfolioService instead to query what funds they have available for
 * trading, since the Strategy might have less than 100% allocation.
 *
 * @author Tim Olson
 */
@Service
public interface AccountService {

    ///** returns a fee calculator for the given exchange */
    //public FeeStructure getFeeStructure( Exchange e );

}
