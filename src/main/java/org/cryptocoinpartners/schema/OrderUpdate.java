package org.cryptocoinpartners.schema;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

/**
 * When Orders change OrderState, this Event is published
 *
 * @author Tim Olson
 */
@Entity
public class OrderUpdate extends Event {

	@ManyToOne(cascade = { CascadeType.MERGE, CascadeType.REMOVE })
	public Order getOrder() {
		return order;
	}

	public OrderState getLastState() {
		return lastState;
	}

	public OrderState getState() {
		return state;
	}

	public OrderUpdate(Order order, OrderState lastState, OrderState state) {
		this.order = order;
		this.lastState = lastState;
		this.state = state;
	}

	// JPA
	protected OrderUpdate() {
	}

	protected void setOrder(Order order) {
		this.order = order;
	}

	protected void setLastState(OrderState lastState) {
		this.lastState = lastState;
	}

	protected void setState(OrderState state) {
		this.state = state;
	}

	private Order order;
	private OrderState lastState;
	private OrderState state;

}
