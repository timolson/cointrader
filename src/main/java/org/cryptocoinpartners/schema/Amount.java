package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.math.MathContext;

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
public abstract class Amount implements Comparable<Amount> {

	public static final MathContext mc = MathContext.DECIMAL128; //  IEEE 128-bit decimal, scale 34

	/**
	 * only when absolutely necessary
	 */
	public abstract double asDouble();

	public abstract BigDecimal asBigDecimal();

	public abstract double getBasis();

	public abstract int getPrecision();

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

	public abstract boolean isPositive();

	public abstract boolean isZero();

	public abstract boolean isNegative();

	public abstract Amount negate();

	public abstract Amount plus(Amount o);

	public abstract Amount minus(Amount o);

	public DecimalAmount times(BigDecimal o, RemainderHandler remainderHandler) {
		return new DecimalAmount(asBigDecimal().multiply(o, remainderHandler.getMathContext()));
	}

	public DecimalAmount dividedBy(BigDecimal o, RemainderHandler remainderHandler) {
		BigDecimal[] divideAndRemainder = asBigDecimal().divideAndRemainder(o, remainderHandler.getMathContext());
		DecimalAmount result = new DecimalAmount(divideAndRemainder[0]);
		remainderHandler.handleRemainder(result, divideAndRemainder[1]);
		return result;
	}

	public Amount times(int o, RemainderHandler remainderHandler) {
		return times(new BigDecimal(o), remainderHandler);
	}

	public Amount dividedBy(int o, RemainderHandler remainderHandler) {
		return dividedBy(new BigDecimal(o), remainderHandler);
	}

	public Amount times(double o, RemainderHandler remainderHandler) {
		return times(new BigDecimal(o), remainderHandler);
	}

	public Amount dividedBy(double o, RemainderHandler remainderHandler) {
		return dividedBy(new BigDecimal(o), remainderHandler);
	}

	public abstract Amount times(Amount o, RemainderHandler remainderHandler);

	public abstract Amount dividedBy(Amount o, RemainderHandler remainderHandler);

	protected static final Logger log = LoggerFactory.getLogger(Amount.class);

	public long asLong() {
		// TODO Auto-generated method stub
		return 0;
	}
}
