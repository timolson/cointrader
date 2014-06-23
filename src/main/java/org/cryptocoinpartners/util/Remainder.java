package org.cryptocoinpartners.util;


import org.cryptocoinpartners.schema.Amount;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * This is a grouping of DiscreteAmount.RemainderHandlers for convenience
 *
 * @author Tim Olson
 */
public class Remainder {


    public static final RemainderHandler DISCARD = new RemainderHandler() {
        public RoundingMode getRoundingMode() { return RoundingMode.FLOOR; }
    };


    public static final RemainderHandler TO_HOUSE = new RemainderHandler() {
        public void handleRemainder(Amount result, BigDecimal remainder) {
            // todo
        }
        public RoundingMode getRoundingMode() { return RoundingMode.FLOOR; }
    };


    public static final RemainderHandler ROUND_EVEN = new RemainderHandler() {
        public RoundingMode getRoundingMode() {
            return RoundingMode.HALF_EVEN;
        }
    };


    public static final RemainderHandler ROUND_CEILING = new RemainderHandler() {
        public RoundingMode getRoundingMode() {
            return RoundingMode.CEILING;
        }
    };


    public static final RemainderHandler ROUND_FLOOR = new RemainderHandler() {
        public RoundingMode getRoundingMode() {
            return RoundingMode.FLOOR;
        }
    };


}
