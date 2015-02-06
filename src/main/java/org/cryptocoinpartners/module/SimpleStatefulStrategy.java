package org.cryptocoinpartners.module;

import javax.annotation.Nullable;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.BaseStrategy;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderBuilder;

/**
 * This enhances the BaseStrategy by providing a simple state machine which tracks whether the Strategy is warming up,
 * ready to trade, in a trade, panicking, or stopped.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class SimpleStatefulStrategy extends BaseStrategy {

	/** You must call this method when the Strategy has collected enough data to begin trading */
	protected void ready() {
		if (state == State.WARMUP)
			state = State.READY;
	}

	/** Override this method to build the Order to use when entering a trade.  If you return null, then the trade
	 * entry will be aborted and you will stay out of the trade */
	@Nullable
	protected abstract OrderBuilder.CommonOrderBuilder buildEntryOrder();

	@Nullable
	protected abstract OrderBuilder.CommonOrderBuilder buildStopOrder(Fill fill);

	/** Override this method to build the Order to use when exiting a trade.  If you return null, the trade exit
	 * will be aborted and you will stay in the trade */
	@Nullable
	protected abstract OrderBuilder.CommonOrderBuilder buildExitOrder(Order entryOrder);

	/**
	 * You must call this method when your Strategy wants to enter a trade.  If the strategy is not ready to trade,
	 * nothing will happen
	 */
	protected void enterTrade() {
		if (state == State.READY) {
			OrderBuilder.CommonOrderBuilder orderBuilder = buildEntryOrder();
			if (orderBuilder != null) {
				entryOrder = orderBuilder.getOrder();
				//entryOrder.
				state = State.INVESTING;
				log.info("Entering trade with order " + entryOrder);
				orderService.placeOrder(entryOrder);
			}
		}
	}

	/**
	 * You must call this method when your Strategy wants to exit a trade.  If the strategy is not currently in
	 * a trade, nothing will happen
	 */
	protected void exitTrade() {
		if (state == State.INVESTED) {
			OrderBuilder.CommonOrderBuilder orderBuilder = buildExitOrder(entryOrder);
			if (orderBuilder != null) {
				exitOrder = orderBuilder.getOrder();
				state = State.DIVESTING;
				log.debug("Exiting trade with order " + exitOrder);
				orderService.placeOrder(exitOrder);
			}
		}
	}

	protected void stopTrade(Fill fill) {

		OrderBuilder.CommonOrderBuilder orderBuilder = buildStopOrder(fill);
		//if (orderBuilder != null) {
		//	stopOrder = orderBuilder.getOrder();
		//			if (state == State.INVESTED)
		//				state = State.DIVESTING;
		//			else if (state == State.DIVESTING)
		//				state = State.INVESTING;
		//			log.debug("Exiting trade with order " + stopOrder);

		//orderService.placeOrder(stopOrder);

		//}
	}

	// todo listen for panic notifications
	protected void panic() {
		state = State.PANICKING;
		// todo create panic order
	}

	protected void stop() {
		state = State.STOP;
	}

	@When("select * from Fill((Fill.fillType not in (FillType.STOP_LIMIT, FillType.TRAILING_STOP_LIMIT))) ")
	protected void handleFill(Fill fill) {

		//if (fill.getOrder().getComment() == "Long Entry Order" || fill.getOrder().getComment() == "Short Entry Order") {
		//

		//	stopTrade(fill);
		//}
		//log.info("Stop trade Entered at" + fill.getPrice());

	}

	protected enum State {
		/** the Strategy has started but does not have enough data to begin trading */
		WARMUP, READY, INVESTING, INVESTED, DIVESTING, PANICKING, STOP
	}

	private Order entryOrder;
	private Order exitOrder;
	private Order stopOrder;
	protected State state = State.WARMUP;
}
