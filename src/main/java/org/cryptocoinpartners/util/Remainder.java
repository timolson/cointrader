package org.cryptocoinpartners.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.cryptocoinpartners.schema.Amount;

/**
 * This is a grouping of DiscreteAmount.RemainderHandlers for convenience
 *
 * @author Tim Olson
 */
public class Remainder {

    public static final RemainderHandler DISCARD = new RemainderHandler() {
        @Override
        public RoundingMode getRoundingMode() {
            return RoundingMode.FLOOR;
        }
    };

    public static final RemainderHandler TO_HOUSE = new RemainderHandler() {
        @Override
        public void handleRemainder(Amount result, BigDecimal remainder) {
            // todo
        }

        @Override
        public RoundingMode getRoundingMode() {
            return RoundingMode.FLOOR;
        }
    };

    public static final RemainderHandler ROUND_EVEN = new RemainderHandler() {
        @Override
        public RoundingMode getRoundingMode() {
            return RoundingMode.HALF_EVEN;
        }
    };
    public static final RemainderHandler ROUND_DOWN = new RemainderHandler() {
        @Override
        public RoundingMode getRoundingMode() {
            return RoundingMode.HALF_DOWN;
        }
    };

    public static final RemainderHandler ROUND_UP = new RemainderHandler() {
        @Override
        public RoundingMode getRoundingMode() {
            return RoundingMode.HALF_UP;
        }
    };
    public static final RemainderHandler ROUND_CEILING = new RemainderHandler() {
        @Override
        public RoundingMode getRoundingMode() {
            return RoundingMode.CEILING;
        }
    };

    public static final RemainderHandler ROUND_FLOOR = new RemainderHandler() {
        @Override
        public RoundingMode getRoundingMode() {
            return RoundingMode.FLOOR;
        }
    };

}
