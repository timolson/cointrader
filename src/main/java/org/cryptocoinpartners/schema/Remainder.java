package org.cryptocoinpartners.schema;


import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * This is a grouping of DiscreteAmount.RemainderHandlers for convenience
 *
 * @author Tim Olson
 */
public class Remainder {


    public static final DecimalAmount.RemainderHandler DISCARD = new Amount.RemainderHandler() {
        public void handleRemainder(Amount result, BigDecimal remainder) { }
    };


    public static final DecimalAmount.RemainderHandler TO_HOUSE = new Amount.RemainderHandler() {
        public void handleRemainder(Amount result, BigDecimal remainder) {
            // todo
        }
        public RoundingMode getRoundingMode() { return RoundingMode.FLOOR; }
    };


}
