package com.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.math.MathContext;


/**
 * DiscreteAmount acts like an integer except the amounts do not need to be whole values.  The value is stored as a
 * long count of double basis count. It is useful for both money and volume amount calculations.  For example, the
 * Swiss Franc rounds to nickels, so CHF 0.20 would be represented as a DiscreteAmount with count=4 and basis=0.05
 *
 * @author Tim Olson
 */
public class DiscreteAmount {

    /**
     * This is a delegate interface which is called when there are remainders or errors in a calcualation.
     */
    public interface RemainderHandler {
        public void handleRemainder(DiscreteAmount result, double remainder);
    }


    public static DiscreteAmount fromValue( double value, double basis, RemainderHandler remainderHandler ) {
        return fromValuePrivate(value, basis, remainderHandler);
    }


    /**
     * The value is rounded to the nearest amount of the basis.  The amount rounded is ignored / discarded.
     */
    public static DiscreteAmount fromValueRounded( double value, double basis ) {
        return new DiscreteAmount( Math.round(value/basis), basis );
    }


    public static long countForValueRounded(BigDecimal value, double basis) {
        return countForValueRounded(value, new BigDecimal(basis));
    }


    public static long countForValueRounded(BigDecimal value, BigDecimal basis) {
        return Math.round(value.divide(basis, MathContext.DECIMAL128).doubleValue());
    }


    public static long countForValueRounded( double value, double basis ) {
        return Math.round(value/basis);
    }


    /**
     *
     * @param count
     * @param basis
     */
    public DiscreteAmount(long count, double basis) {
        this.count = count;
        this.basis = basis;
    }


    public long getCount() { return count; }


    public double getBasis() { return basis; }


    /** This should be used for display purposes only, not calculation! */
    public double asDouble() {
        return count*basis;
    }


    public BigDecimal asBigDecimal() {
        return new BigDecimal(count).multiply(new BigDecimal(basis));
    }


    public DiscreteAmount toBasis( double newBasis, RemainderHandler remainderHandler ) {
        return fromValuePrivate(asDouble(),newBasis,remainderHandler);
    }


    private static DiscreteAmount fromValuePrivate( double value, double basis, RemainderHandler remainderHandler) {
        double countD = value/basis;
        long count = (long) countD;
        DiscreteAmount result = new DiscreteAmount(count, basis);
        remainderHandler.handleRemainder(result,countD-count);
        return result;
    }


    private long count;
    private double basis;
}
