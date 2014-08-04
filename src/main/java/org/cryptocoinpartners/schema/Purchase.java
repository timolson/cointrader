package org.cryptocoinpartners.schema;

import javax.persistence.Entity;

import org.cryptocoinpartners.enumeration.TransactionType;


/**
 * A Purchase is a type of Transaction which moves Position(s) between two Portfolios internally.  The Position's
 * Account remains the same.
 *
 * @author Tim Olson
 */
@Entity
public class Purchase extends Transaction {

	 public Purchase(Portfolio portfolio,Exchange exchange,  Asset asset, Amount price, Amount volume) {
			super(portfolio,exchange ,asset,  TransactionType.PURCHASE, price, volume);
			// TODO Auto-generated constructor stub
		}
    
	
}
