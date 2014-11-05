package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.math.MathContext;

import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.RemainderHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Amount has a polymorphic base representation of either a BigDecimal or a special long/long format
 * which captures discrete amounts as a count plus a basis.  The basis is stored inverted so it can be represented
 * as a long instead of a double.  Long/long is the preferred format for discrete amounts like trading volumes and
 * prices, while I/O prefers BigDecimal.
 * Whenever a downcast or rounding happens, the remainders are recorded, along with a RemainderHandler delegate which
 * is to dispatch of the remainder.  No calls to the RemainderHandlers are invoked until settle() is called on the
 * Amount, at which time all accumulated remainders since the last settle() are pushed to their respective
 * RemainderHandlers.
 *
 * @author Tim Olson
 */
@MappedSuperclass
public abstract class Amount implements Comparable<Amount> {

	public static final MathContext mc = MathContext.DECIMAL128; //  IEEE 128-bit decimal, scale 34

	/**
	 * only when absolutely necessary
	 */
	public abstract double asDouble();

	public abstract BigDecimal asBigDecimal();

	@Transient
	public abstract int getScale();

	public DiscreteAmount toBasis(double newBasis, RemainderHandler remainderHandler) {
		long newIBasis = DiscreteAmount.invertBasis(newBasis);
		return toIBasis(newIBasis, remainderHandler);
	}

	public abstract DiscreteAmount toIBasis(long newIBasis, RemainderHandler remainderHandler);

	public void assertBasis(double basis) throws BasisError {
		long otherIBasis = DiscreteAmount.invertBasis(basis);
		assertIBasis(otherIBasis);
	}

	public abstract void assertIBasis(long otherIBasis);

	@Override
	public String toString() {
		return asBigDecimal().toString();
	}

	public class BasisError extends Error {
	}

	public Amount abs() {
		return isNegative() ? negate() : this;
	}

	@Transient
	public abstract boolean isPositive();

	@Transient
	public abstract boolean isZero();

	@Transient
	public abstract boolean isNegative();

	@Transient
	public abstract Amount negate();

	@Transient
	public abstract Amount plus(Amount o);

	@Transient
	public abstract Amount minus(Amount o);

	@Transient
	public DecimalAmount times(BigDecimal o, RemainderHandler remainderHandler) {
		return new DecimalAmount(asBigDecimal().multiply(o, remainderHandler.getMathContext()));
	}

	@Transient
	public DecimalAmount dividedBy(BigDecimal o, RemainderHandler remainderHandler) {
		BigDecimal[] divideAndRemainder = asBigDecimal().divideAndRemainder(o, remainderHandler.getMathContext());
		DecimalAmount result = new DecimalAmount(divideAndRemainder[0]);
		remainderHandler.handleRemainder(result, divideAndRemainder[1]);
		return result;
	}

	@Transient
	public DecimalAmount divide(BigDecimal o, RemainderHandler remainderHandler) {
		o.setScale(Math.min(o.scale(), mc.getPrecision()));
		BigDecimal division = asBigDecimal().divide(o, remainderHandler.getRoundingMode());
		DecimalAmount result = DecimalAmount.of(division);
		return result;
	}

	@Transient
	public Amount times(int o, RemainderHandler remainderHandler) {
		return times(new BigDecimal(o), remainderHandler);
	}

	@Transient
	public Amount dividedBy(int o, RemainderHandler remainderHandler) {
		return dividedBy(new BigDecimal(o), remainderHandler);
	}

	@Transient
	public DecimalAmount divide(int o, RemainderHandler remainderHandler) {

		BigDecimal division = asBigDecimal().divide(BigDecimal.valueOf(o), remainderHandler.getRoundingMode());
		DecimalAmount result = DecimalAmount.of(division);
		return result;
	}

	@Transient
	public Amount times(double o, RemainderHandler remainderHandler) {
		return times(new BigDecimal(o), remainderHandler);
	}

	@Transient
	public Amount dividedBy(double o, RemainderHandler remainderHandler) {
		return dividedBy(new BigDecimal(o), remainderHandler);
	}

	@Transient
	public abstract Amount times(Amount o, RemainderHandler remainderHandler);

	@Transient
	public abstract Amount dividedBy(Amount o, RemainderHandler remainderHandler);

	protected static final Logger log = LoggerFactory.getLogger(Amount.class);

}
