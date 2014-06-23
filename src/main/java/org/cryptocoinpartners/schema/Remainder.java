package org.cryptocoinpartners.schema;


import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * This is a grouping of DiscreteAmount.RemainderHandlers for convenience
 *
 * @author Tim Olson
 */
public class Remainder {


    public static final Amount.RemainderHandler DISCARD = new Amount.RemainderHandler() {
        public RoundingMode getRoundingMode() { return RoundingMode.FLOOR; }
    };


    public static final Amount.RemainderHandler TO_HOUSE = new Amount.RemainderHandler() {
        public void handleRemainder(Amount result, BigDecimal remainder) {
            // todo
        }
        public RoundingMode getRoundingMode() { return RoundingMode.FLOOR; }
    };


    public static final Amount.RemainderHandler ROUND_EVEN = new Amount.RemainderHandler() {
        public RoundingMode getRoundingMode() {
            return RoundingMode.HALF_EVEN;
        }
    };


    public static final Amount.RemainderHandler ROUND_CEILING = new Amount.RemainderHandler() {
        public RoundingMode getRoundingMode() {
            return RoundingMode.CEILING;
        }
    };


    public static final Amount.RemainderHandler ROUND_FLOOR = new Amount.RemainderHandler() {
        public RoundingMode getRoundingMode() {
            return RoundingMode.FLOOR;
        }
    };


}
