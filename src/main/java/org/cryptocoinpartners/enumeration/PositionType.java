package org.cryptocoinpartners.enumeration;

/**
 * @author Mike Olson
 * @author Tim Olson
 */
public enum PositionType {
	/** the position is net long */
	LONG,
	/** the position is net short */
	SHORT,
	/** no position exists  */
	FLAT,
	/** no position exists  */
	ENTERING,
	/** no position exists  */
	EXITING,

	;

	/** return true if Long  */
	public boolean isLong() {
		return this == PositionType.LONG;
	}

	/** return true if short  */
	public boolean isShort() {
		return this == PositionType.SHORT;
	}

	/** return true if flat  */
	public boolean isFlat() {
		return this == PositionType.FLAT;
	}

}
