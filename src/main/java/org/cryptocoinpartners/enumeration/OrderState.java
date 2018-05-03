package org.cryptocoinpartners.enumeration;

/**
 * @author Mike Olson
 * @author Tim Olson
 */
public enum OrderState {
	/** 0 the Order is created but it has not been sent to an Exchange yet. Subsequent states may be: ROUTED, PLACED, CANCELLING, EXPIRED, REJECTED */
	NEW,
	/** 1 the Order is created but it has not been sent to an Exchange yet. Subsequent states may be: ROUTED, PLACED, CANCELLING, EXPIRED, REJECTED */
	TRIGGER,
	/**
	 * 2 Indicates that a specifc order has is awaiting a trigger before being sent to exchange. Subsequent states may be: PLACED, CANCELLING, EXPIRED,
	 * REJECTED
	 */
	ROUTED,
	/**
	 * 3 Indicates that a specifc order has is failed to be placed on exhcnage but no exchange error retured . Subsequent states may be: PLACED,
	 * CANCELLING, EXPIRED, REJECTED
	 */
	ERROR,
	/** 4 At least part of the Order has been routed to an Exchange. Subsequent states may be: PARTFILLED, FILLED, CANCELLING, EXPIRED, REJECTED */
	PLACED,
	/** 5 Some of the requested volume has been filled, but not all of it. Subsequent states may be: FILLED, CANCELLING, EXPIRED */
	PARTFILLED,
	/** 6 A cancellation request has been sent to the Exchange. Subsequent states may be: CANCELLED, EXPIRED */
	EXPIRED,
	/** 7 The Exchange has confirmed a cancellation request. This is a terminal state. */
	REJECTED,
	/** 8 If the cancellation was due to expiry, this is the terminal state instead of CANCELLED */
	CANCELLING,
	/** 9 The Order cannot be filled as specified. This is a terminal state. */
	CANCELLED,
	/** 10 All of the Order's volume has been filled. This is a terminal state. */
	FILLED;

	/** return true iff NEW || ROUTED || PLACED || PARTFILLED */
	public boolean isOpen() {
		return this == OrderState.NEW || this == OrderState.TRIGGER || this == OrderState.ROUTED || this == OrderState.PLACED || this == OrderState.PARTFILLED
				|| this == OrderState.CANCELLING;
	}

	public boolean isWorking() {
		return this == OrderState.PLACED || this == OrderState.PARTFILLED || this == OrderState.CANCELLING;
	}

	public boolean isCancelled() {
		return this == OrderState.CANCELLED;
	}

	public boolean isNew() {
		return this == OrderState.NEW;
	}

	public boolean isTrigger() {
		return this == OrderState.TRIGGER;
	}

	public boolean isError() {
		return this == OrderState.ERROR;
	}

	public boolean isRouted() {
		return this == OrderState.ROUTED;
	}

	public boolean isFilled() {
		return this == OrderState.FILLED;
	}

	public boolean isExpired() {
		return this == OrderState.EXPIRED;
	}

	public boolean isRejected() {
		return this == OrderState.REJECTED;
	}

	public boolean isPartfilled() {
		return this == OrderState.PARTFILLED;
	}

	public boolean isPlaced() {
		return this == OrderState.PLACED;
	}

	public boolean isCancelling() {
		return this == OrderState.CANCELLING;
	}

}
