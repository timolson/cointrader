package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.RemainderHandler;

/** Best used for I/O */
@SuppressWarnings("ConstantConditions")
@Embeddable
public class DecimalAmount extends Amount {

    public static final DecimalAmount ZERO = new DecimalAmount(BigDecimal.ZERO);
    public static final DecimalAmount ONE = new DecimalAmount(BigDecimal.ONE);

    public static DecimalAmount of(Amount amount) {
        return new DecimalAmount(amount.asBigDecimal());
    }

    public static DecimalAmount of(BigDecimal bigDecimal) {
        return new DecimalAmount(bigDecimal);
    }

    public static DecimalAmount of(String decimalStr) {
        return new DecimalAmount(new BigDecimal(decimalStr));
    }

    public DecimalAmount(BigDecimal bd) {
        this.bd = bd;
    }

    @Override
    public DecimalAmount negate() {
        return new DecimalAmount(bd.negate());
    }

    @Override
    public DecimalAmount invert() {

        return bd.compareTo(BigDecimal.ZERO) == 0 ? new DecimalAmount(BigDecimal.ZERO) : new DecimalAmount(BigDecimal.ONE.divide(bd, mc));

    }

    @Override
    public Amount plus(Amount o) {
        return new DecimalAmount(bd.add(o.asBigDecimal()));

    }

    @Override
    public Amount minus(Amount o) {
        return new DecimalAmount(bd.subtract(o.asBigDecimal()));

    }

    @Override
    public Amount times(Amount o, RemainderHandler remainderHandler) {
        return new DecimalAmount(bd.multiply(o.asBigDecimal()));

    }

    @Override
    public Amount dividedBy(Amount o, RemainderHandler remainderHandler) {

        int scale = Math.max(o.asBigDecimal().scale(), mc.getPrecision());
        return new DecimalAmount(bd.divide(o.asBigDecimal(), remainderHandler.getRoundingMode()).setScale(scale, remainderHandler.getRoundingMode()));

    }

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") Amount o) {
        if (o instanceof DecimalAmount) {
            DecimalAmount decimalAmount = (DecimalAmount) o;
            return bd.compareTo(decimalAmount.bd);
        }
        return bd.compareTo(o.asBigDecimal());
    }

    @Override
    public void assertIBasis(long otherIBasis) {
        throw new BasisError();
    }

    @Override
    @Transient
    public boolean isPositive() {
        return bd.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    @Transient
    public boolean isZero() {
        return bd.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    @Transient
    public boolean isNegative() {
        return bd.compareTo(BigDecimal.ZERO) < 0;
    }

    /** This should be used for display purposes only, not calculation! */
    @Override
    public double asDouble() {
        return bd.doubleValue();
    }

    @Override
    public BigDecimal asBigDecimal() {
        return bd;
    }

    @Override
    public DiscreteAmount toIBasis(long newIBasis, RemainderHandler remainderHandler) {
        BigDecimal oldAmount = bd;
        long newCount = oldAmount.multiply(new BigDecimal(newIBasis), remainderHandler.getMathContext()).longValue();
        DiscreteAmount newAmount = new DiscreteAmount(newCount, newIBasis);
        BigDecimal remainder = oldAmount.subtract(newAmount.asBigDecimal(), remainderHandler.getMathContext());
        remainderHandler.handleRemainder(newAmount, remainder);
        return newAmount;
    }

    @Override
    public String toString() {
        return bd.toString();
    }

    // JPA
    protected DecimalAmount() {
    }

    @Column(name = "bd", columnDefinition = "varchar(255)")
    @Basic(optional = false)
    protected BigDecimal getBd() {
        return bd;
    }

    protected void setBd(BigDecimal bd) {
        this.bd = bd;
    }

    private BigDecimal bd;

    @Override
    @Transient
    public int getScale() {
        final BigDecimal bigDecimal = new BigDecimal("" + bd);
        final String s = bigDecimal.toPlainString();
        final int index = s.indexOf('.');
        if (index < 0) {
            return 0;
        }
        return s.length() - 1 - index;

    }

}
