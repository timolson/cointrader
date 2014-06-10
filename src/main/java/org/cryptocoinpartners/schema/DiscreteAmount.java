package org.cryptocoinpartners.schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;


/**
 * DiscreteAmount acts like an integer except the amounts do not need to be whole values.  The value is stored as a
 * long count of double basis count. It is useful for both money and volume withAmount calculations.  For example, the
 * Swiss Franc rounds to nickels, so CHF 0.20 would be represented as a DiscreteAmount with count=4 and basis=0.05.
 * Internally, DiscreteAmount stores 1/basis as a long integer.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class DiscreteAmount implements Comparable<DiscreteAmount> {


    /**
     * This is a delegate interface which is called when there are remainders or errors in a calcualation.
     */
    public interface RemainderHandler {
        /**
         * @param result is the final DiscreteAmount produced by the operation
         * @param remainder is a leftover withAmount x where |x| < basis
         */
        public void handleRemainder(DiscreteAmount result, double remainder);
    }


    public static DiscreteAmount fromValue( double value, double basis, RemainderHandler remainderHandler ) {
        return fromValuePrivate(value, basis, remainderHandler);
    }


    public static DiscreteAmount fromValue( BigDecimal value, double basis, RemainderHandler remainderHandler ) {
        return fromValuePrivate(value, basis, remainderHandler);
    }


    /**
     * The value is rounded to the nearest withAmount of the basis.  The withAmount rounded is ignored / discarded.
     */
    public static DiscreteAmount fromValueRounded( double value, double basis ) {
        return new DiscreteAmount( Math.round(value/basis), basis );
    }


    /**
     * The value is rounded to the nearest withAmount of the basis.  The withAmount rounded is ignored / discarded.
     */
    public static DiscreteAmount fromValueRounded( BigDecimal value, double basis ) {
        BigDecimal count = value.divide(BigDecimal.valueOf(basis), RoundingMode.HALF_UP);
        return new DiscreteAmount( count.longValueExact(), basis );
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


    /**
     * @return a new DiscreteAmount whose count is -this.count and whose basis is the same as this.basis;
     */
    public DiscreteAmount negate() { return new DiscreteAmount(-count, invertedBasis); }


    public class BasisError extends Error { public BasisError(String msg) { super(msg); } }


    public void assertBasis(double basis) throws BasisError {
        if( Math.round(1d/basis) != invertedBasis )
            throw new BasisError("Basis mismatch.  Expected "+(1d/invertedBasis)+" but got "+basis);
    }


    /** This should be used for display purposes only, not calculation! */
    public double asDouble() {
        return ((double) count)/invertedBasis;
    }


    public BigDecimal asBigDecimal() {
        return new BigDecimal(count).divide(new BigDecimal(invertedBasis));
    }


    public DiscreteAmount convertBasis(double newBasis, RemainderHandler remainderHandler) {
        return fromValuePrivate(asDouble(), newBasis, remainderHandler);
    }


    /** adds one basis to the value by incrementing the count */
    public void increment() { count++; }


    /** adds to the value by incrementing the count by pips */
    public void increment( long pips ) { count += pips; }


    /** subtracts one basis from the value by decrementing the count */
    public void decrement() { count++; }


    /** adds to the value by decrementing the count by pips */
    public void decrement( long pips ) { count -= pips; }


    public int compareTo(DiscreteAmount o) {
        if( this.invertedBasis != o.invertedBasis ) {
            log.warn("Comparing DiscreteAmounts with different bases", new Error("Informative Stacktrace"));
            return Double.compare(this.asDouble(),o.asDouble());
        }
        return Long.compare(this.count,o.count);
    }


    private DiscreteAmount(long count, long invertedBasis) {
        this.count = count;
        this.invertedBasis = invertedBasis;
    }


    private static DiscreteAmount fromValuePrivate( double value, double basis, RemainderHandler remainderHandler) {
        double countD = value/basis;
        long count = (long) countD;
        DiscreteAmount result = new DiscreteAmount(count, basis);
        remainderHandler.handleRemainder(result,countD-count);
        return result;
    }


    private static DiscreteAmount fromValuePrivate( BigDecimal value, double basis, RemainderHandler remainderHandler) {
        BigDecimal[] countAndRemainder = value.divideAndRemainder(BigDecimal.valueOf(basis));
        BigDecimal countBD = countAndRemainder[0];
        BigDecimal remainderBD = countAndRemainder[1];
        DiscreteAmount result = new DiscreteAmount(countBD.longValueExact(), basis);
        remainderHandler.handleRemainder(result, remainderBD.doubleValue());
        return result;
    }


    private long count;
    /**
     * The invertedBasis is 1/basis.  This is done because we can then use a long integer instead of double, knowing
     * that all bases must be integral factors of 1.<br/>
     * Example inverted bases: quarters=4, dimes=10, nickels=20, pennies=100, satoshis=1e8
     */
    private long invertedBasis;

    private static final Logger log = LoggerFactory.getLogger(DiscreteAmount.class);
}
