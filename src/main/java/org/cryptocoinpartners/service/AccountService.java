package org.cryptocoinpartners.service;

import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.FeeStructure;
import org.cryptocoinpartners.schema.Portfolio;


/**
 * AccountService provides information about Positions in external Accounts
 *
 * @author Tim Olson
 */
@Service
public interface AccountService {

    /** returns all Positions in all Exchanges */
    public Portfolio getPositions();


    /** returns all Postions for the given Exchange */
    public Portfolio getPositions( Exchange e );


    ///** returns a fee calculator for the given exchange */
    //public FeeStructure getFeeStructure( Exchange e );

}
