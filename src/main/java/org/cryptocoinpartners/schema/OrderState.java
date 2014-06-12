package org.cryptocoinpartners.schema;

import javax.persistence.Transient;


/**
 * @author Mike Olson
 * @author Tim Olson
 */
public enum OrderState
{ 
    /** the Order is created but it has not been sent to an Exchange yet.  Subsequent states may be: PLACED, CANCELLING, EXPIRED, REJECTED */
    NEW, 
    /** At least part of the Order has been routed to an Exchange.  Subsequent states may be: PARTFILLED, FILLED, CANCELLING, EXPIRED, REJECTED  */
    PLACED,
    /** Some of the requested volume has been filled, but not all of it.  Subsequent states may be: FILLED, CANCELLING, EXPIRED */
    PARTFILLED, 
    /** All of the Order's volume has been filled.  This is a terminal state. */
    FILLED, 
    /** A cancellation request has been sent to the Exchange.  Subsequent states may be: CANCELLED, EXPIRED */
    CANCELLING, 
    /** The Exchange has confirmed a cancellation request.  This is a terminal state. */
    CANCELLED, 
    /** If the cancellation was due to expiry, this is the terminal state instead of CANCELLED */
    EXPIRED,
    /** The Order cannot be filled as specified.  This is a terminal state.  */
    REJECTED;


    public boolean isOpen() {
        return this == OrderState.NEW || this == OrderState.PLACED || this == OrderState.PARTFILLED;
    }

}