package org.cryptocoinpartners.schema;

/**
 * @author Tim Olson
 */
public interface FeeStructure {


    /**
     * returns the amount of the exchange fee for the given order o.  The returned Position must have a negative
     * volume since it is a debit.
     */
    public Position fee( SpecificOrder o );


    /**
     * returns the largest volume of m.quoteFungible() which can be purchased with non-reserved Positions in the
     * tradeablePortfolio
     */
    public DiscreteAmount maximumPurchaseOf( Market m, Portfolio tradeablePortfolio );


}
