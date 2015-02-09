package org.cryptocoinpartners.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.cryptocoinpartners.schema.Amount;

/**
 * This is a delegate interface which is called when there are remainders or errors in a calcualation.
 */
public abstract class RemainderHandler {
    /**
     * @param result    is the final Amount produced by the operation
     * @param remainder is a leftover amount x where |x| < basis for discrete amounts and |x| ~ double roundoff error for doubles
     */
    public void handleRemainder(Amount result, BigDecimal remainder) {
    }

    public RoundingMode getRoundingMode() {
        return RoundingMode.HALF_EVEN;
    }

    public MathContext getMathContext() {
        return new MathContext(Amount.mc.getPrecision(), getRoundingMode());
    }
}
