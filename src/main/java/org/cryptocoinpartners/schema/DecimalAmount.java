package org.cryptocoinpartners.schema;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import java.math.BigDecimal;


/** Best used for I/O */
@SuppressWarnings("ConstantConditions")
@Embeddable
public class DecimalAmount extends Amount {


    public static DecimalAmount of(Amount amount) { return new DecimalAmount(amount.asBigDecimal()); }
    public static DecimalAmount of(BigDecimal bigDecimal) { return new DecimalAmount(bigDecimal); }
    public static DecimalAmount of(String decimalStr) { return new DecimalAmount(new BigDecimal(decimalStr)); }


    public DecimalAmount(BigDecimal bd) { this.bd = bd; }


    public DecimalAmount negate() { return new DecimalAmount(bd.negate()); }


    public Amount plus(Amount o) {
        return null;
    }


    public Amount minus(Amount o) {
        return null;
    }


    public Amount times(Amount o, RemainderHandler remainderHandler) {
        // todo
        throw new Error("unimplemented");
    }


    public Amount dividedBy(Amount o, RemainderHandler remainderHandler) {
        // todo
        throw new Error("unimplemented");
    }


    public int compareTo(@SuppressWarnings("NullableProblems") Amount o) {
        if( o instanceof DecimalAmount ) {
            DecimalAmount decimalAmount = (DecimalAmount) o;
            return bd.compareTo(decimalAmount.bd);
        }
        return bd.compareTo(o.asBigDecimal());
    }


    public void assertIBasis(long otherIBasis) { throw new BasisError(); }


    @Transient
    public boolean isPositive() {
        return bd.compareTo(BigDecimal.ZERO) > 0;
    }


    @Transient
    public boolean isZero() {
        return bd.compareTo(BigDecimal.ZERO) == 0;
    }


    @Transient
    public boolean isNegative() {
        return bd.compareTo(BigDecimal.ZERO) < 0;
    }


    /** This should be used for display purposes only, not calculation! */
    public double asDouble() { return bd.doubleValue(); }


    public BigDecimal asBigDecimal() { return bd; }


    public DiscreteAmount toIBasis(long newIBasis, RemainderHandler remainderHandler)
    {
        BigDecimal oldAmount = bd;
        long newCount = oldAmount.multiply(new BigDecimal(newIBasis),remainderHandler.getMathContext()).longValue();
        DiscreteAmount newAmount = new DiscreteAmount(newCount, newIBasis);
        BigDecimal remainder = oldAmount.subtract(newAmount.asBigDecimal(),remainderHandler.getMathContext());
        remainderHandler.handleRemainder(newAmount,remainder);
        return newAmount;
    }


    public String toString() { return bd.toString(); }


    // JPA
    protected DecimalAmount() { }


    @Column(name = "bd",columnDefinition = "varchar(255)")
    @Basic(optional = false)
    protected BigDecimal getBd() { return bd; }
    protected void setBd(BigDecimal bd) { this.bd = bd; }


    private BigDecimal bd;

}
