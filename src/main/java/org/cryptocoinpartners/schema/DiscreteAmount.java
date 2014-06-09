package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.math.MathContext;


/**
 * DiscreteAmount acts like an integer except the amounts do not need to be whole values.  The value is stored as a
 * long count of double basis count. It is useful for both money and volume amount calculations.  For example, the
 * Swiss Franc rounds to nickels, so CHF 0.20 would be represented as a DiscreteAmount with count=4 and basis=0.05
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class DiscreteAmount {


    /**
     * This is a delegate interface which is called when there are remainders or errors in a calcualation.
     */
    public interface RemainderHandler {
        /**
         * @param result is the final DiscreteAmount produced by the operation
         * @param remainder is a leftover amount x where |x| < basis
         */
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


    public DiscreteAmount(long count, double basis) {
        this.count = count;
        this.invertedBasis = Math.round(1/basis);
    }


    public long getCount() { return count; }


    public double getBasis() { return 1d/invertedBasis; }


    /**
     * The invertedBasis is 1/basis, which is what DiscreteAmount stores internally.  This is done because we can then
     * use a long integer instead of double, knowing that all bases must be integral factors of 1.<br/>
     * Example inverted bases: quarters=4, dimes=10, nickels=20, pennies=100, satoshis=1e8
     */
    public long getInvertedBasis() { return invertedBasis; }


    /** This should be used for display purposes only, not calculation! */
    public double asDouble() {
        return ((double) count)/invertedBasis;
    }


    public BigDecimal asBigDecimal() {
        return new BigDecimal(count).divide(new BigDecimal(invertedBasis));
    }


    public DiscreteAmount convertBasis(double newBasis, RemainderHandler remainderHandler) {
        return fromValuePrivate(asDouble(),newBasis,remainderHandler);
    }


    /** adds one basis to the value by incrementing the count */
    public void increment() { count++; }


    /** adds to the value by incrementing the count by pips */
    public void increment( long pips ) { count += pips; }


    /** subtracts one basis from the value by decrementing the count */
    public void decrement() { count++; }


    /** adds to the value by decrementing the count by pips */
    public void decrement( long pips ) { count -= pips; }


    private static DiscreteAmount fromValuePrivate( double value, double basis, RemainderHandler remainderHandler) {
        long iBasis = Math.round(1/basis);
        double countD = value/basis;
        long count = (long) countD;
        DiscreteAmount result = new DiscreteAmount(count, basis);
        remainderHandler.handleRemainder(result,countD-count);
        return result;
    }


    private long count;
    /**
     * The invertedBasis is 1/basis.  This is done because we can then use a long integer instead of double, knowing
     * that all bases must be integral factors of 1.<br/>
     * Example inverted bases: quarters=4, dimes=10, nickels=20, pennies=100, satoshis=1e8
     */
    private long invertedBasis;
}
