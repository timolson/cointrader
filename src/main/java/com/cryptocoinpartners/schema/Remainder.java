package com.cryptocoinpartners.schema;

import com.cryptocoinpartners.schema.DiscreteAmount.RemainderHandler;


/**
 * This is a grouping of DiscreteAmount.RemainderHandlers for convenience
 *
 * @author Tim Olson
 */
public class Remainder {


    public static final RemainderHandler DISCARD = new RemainderHandler() {
        public void handleRemainder(DiscreteAmount result, double remainder) {
        }
    };


    public static final RemainderHandler ROUND_HALF_UP = new RemainderHandler() {
        public void handleRemainder(DiscreteAmount result, double remainder) {
            if( remainder >= result.getBasis()/2 )
                result.increment();
        }
    };


    public static RemainderHandler toAccount(Account a,Fungible f) {
        return new RemainderHandler() {
            public void handleRemainder(DiscreteAmount result, double remainder) {

            }
        };
    }
}
