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


    public Purchase(Portfolio portfolio, Asset asset, TransactionType type, long priceCount, Amount volume) {
		super(portfolio, asset, type, priceCount, volume);
		// TODO Auto-generated constructor stub
	}
	private long volumeCount;
    private Market market;
}
