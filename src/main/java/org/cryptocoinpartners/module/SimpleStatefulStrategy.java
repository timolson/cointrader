package org.cryptocoinpartners.module;

import javax.annotation.Nullable;

import org.cryptocoinpartners.schema.BaseStrategy;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
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
    protected abstract OrderBuilder.CommonOrderBuilder buildEntryOrder(Market market);

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
    protected void enterTrade(Market market) {
        if (state == State.READY) {
            OrderBuilder.CommonOrderBuilder orderBuilder = buildEntryOrder(market);
            if (orderBuilder != null) {
                entryOrder = orderBuilder.getOrder();
                //entryOrder.
                state = State.ENTERING;
                log.info("Entering trade with order " + entryOrder);
                try {
                    orderService.placeOrder(entryOrder);
                } catch (Throwable e) {
                    log.info("Unable to place order " + entryOrder);

                }
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
                state = State.EXITING;
                log.debug("Exiting trade with order " + exitOrder);
                try {
                    orderService.placeOrder(exitOrder);
                } catch (Throwable e) {
                    // TODO Auto-generated catch block
                    log.info("Unable to place order " + exitOrder);

                }
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

    protected void setInterval(double interval) {
        this.interval = interval;
    }

    protected void setTrendInterval(double trendInterval) {
        this.trendInterval = trendInterval;
    }

    protected void setRunsInterval(double runsInterval) {
        this.runsInterval = runsInterval;
    }

    protected enum State {
        /** the Strategy has started but does not have enough data to begin trading */
        WARMUP, READY, ENTERING, INVESTED, EXITING, PANICKING, STOP, LONG, SHORT
    }

    private Order entryOrder;
    private Order exitOrder;
    private Order stopOrder;
    protected State state = State.WARMUP;
    //defualt 24Hr bars
    protected static double interval = 86400;
    protected static double runsInterval = 86400;
    protected static double trendInterval = 86400;

}
