package org.cryptocoinpartners.schema;


/**
 * This is a grouping of DiscreteAmount.RemainderHandlers for convenience
 *
 * @author Tim Olson
 */
public class Remainder {


    public static final DiscreteAmount.RemainderHandler DISCARD = new DiscreteAmount.RemainderHandler() {
        public void handleRemainder(DiscreteAmount result, double remainder) {
        }
    };


    public static final DiscreteAmount.RemainderHandler ROUND_HALF_UP = new DiscreteAmount.RemainderHandler() {
        public void handleRemainder(DiscreteAmount result, double remainder) {
            if( remainder >= result.getBasis()/2 )
                result.increment();
        }
    };


    public static DiscreteAmount.RemainderHandler toAccount(Account a,Fungible f) {
        return new DiscreteAmount.RemainderHandler() {
            public void handleRemainder(DiscreteAmount result, double remainder) {

            }
        };
    }
}
