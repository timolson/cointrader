package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.util.RemainderHandler;

import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class DiscreteAmount extends Amount {


    /** helper for constructing an Amount with a double basis instead of long inverted basis.  a double basis like
     * 0.05 is easier for human configurers than the inverted basis of 20 */
    public static long invertBasis(double basis) { return Math.round(1/basis); }


    /** for convenience of representation in properties files.  we round to the nearest whole iBasis */
    public static DiscreteAmountBuilder withBasis(double basis) {
        assert basis > 0;
        return new DiscreteAmountBuilder(invertBasis(basis));
    }


    public static DiscreteAmountBuilder withIBasis(long iBasis) {
        assert iBasis > 0;
        return new DiscreteAmountBuilder(iBasis);
    }


    public static long roundedCountForBasis(BigDecimal amount, double basis) {
        return amount.divide(new BigDecimal(basis), mc).round(mc).longValue();
    }


    public static class DiscreteAmountBuilder {
        public DiscreteAmount fromCount(long count) { return new DiscreteAmount(count,iBasis); }
        public DiscreteAmount fromValue(BigDecimal input, RemainderHandler remainderHandler) {
            return new DecimalAmount(input).toIBasis(iBasis, remainderHandler);
        }

        private DiscreteAmountBuilder(long iBasis) { this.iBasis = iBasis; }
        private final long iBasis;
    }


    public DiscreteAmount(long count, long invertedBasis) {
        this.count = count;
        this.iBasis = invertedBasis;
    }


    public DiscreteAmount(long count, double basis) {
        this(count,invertBasis(basis));
    }


    public long getCount() { return count; }


    /** adds one basis to the value by incrementing the count */
    public DiscreteAmount increment() { return new DiscreteAmount(count+1,iBasis); }


    /** adds to the value by incrementing the count by pips */
    public DiscreteAmount increment( long pips ) { return new DiscreteAmount(count+pips,iBasis); }


    /** subtracts one basis from the value by decrementing the count */
    public DiscreteAmount decrement() { return new DiscreteAmount(count-1,iBasis); }


    /** adds to the value by decrementing the count by pips */
    public DiscreteAmount decrement( long pips ) { return new DiscreteAmount(count-pips,iBasis); }


    public DiscreteAmount negate() {
        return new DiscreteAmount(-count,iBasis);
    }


    public Amount plus(Amount o) {
        // todo
        throw new Error("unimplemented");
    }


    public Amount minus(Amount o) {
        // todo
        throw new Error("unimplemented");
    }


    public Amount times(Amount o, RemainderHandler remainderHandler) {
        // todo shouldn't this always maintain the basis?
        if( o instanceof DiscreteAmount ) {
            DiscreteAmount discreteOther = (DiscreteAmount) o;
            if( iBasis == discreteOther.iBasis )
                return new DiscreteAmount(count*discreteOther.count,iBasis);
        }
        return new DecimalAmount(asBigDecimal().multiply(o.asBigDecimal()));
    }


    public Amount dividedBy(Amount o, RemainderHandler remainderHandler) {
        throw new Error("unimplemented");
    }


    public double asDouble() {
        return ((double)count)/iBasis;
    }


    public BigDecimal asBigDecimal() {
        if( bd == null )
            bd = new BigDecimal(count).divide(new BigDecimal(iBasis), mc);
        return bd;
    }


    public DiscreteAmount toIBasis( long newIBasis, RemainderHandler remainderHandler )
    {
        if( newIBasis % iBasis == 0 ) {
            // new basis is a multiple of old basis and has higher resolution.  no remainder
            return new DiscreteAmount(count*(newIBasis/iBasis),newIBasis);
        }
        BigDecimal oldAmount = asBigDecimal();
        long newCount = oldAmount.multiply(new BigDecimal(newIBasis),remainderHandler.getMathContext()).longValue();
        DiscreteAmount newAmount = new DiscreteAmount(newCount, newIBasis);
        BigDecimal remainder = oldAmount.subtract(newAmount.asBigDecimal(),remainderHandler.getMathContext());
        remainderHandler.handleRemainder(newAmount,remainder);
        return newAmount;
    }


    public int compareTo(@SuppressWarnings("NullableProblems") Amount o) {
        if( o instanceof DiscreteAmount ) {
            DiscreteAmount discreteAmount = (DiscreteAmount) o;
            if( discreteAmount.iBasis == iBasis )
                return Long.compare(count, discreteAmount.count);
        }
        return asBigDecimal().compareTo(o.asBigDecimal());
    }


    public void assertIBasis(long otherIBasis) {
        if( iBasis != otherIBasis )
            throw new BasisError();
    }


    public boolean isPositive() {
        return count > 0;
    }


    public boolean isZero() {
        return count == 0;
    }


    public boolean isNegative() {
        return count < 0;
    }


    /**
     * The invertedBasis is 1/basis.  This is done because we can then use a long integer instead of double, knowing
     * that all bases must be integral factors of 1.<br/>
     * Example inverted bases: quarters=4, dimes=10, nickels=20, pennies=100, satoshis=1e8
     */
    private long iBasis;
    protected long count;
    private BigDecimal bd;
}
