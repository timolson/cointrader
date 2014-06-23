package org.cryptocoinpartners.module;

import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;


/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class BaseOrderService implements OrderService {


    public void placeOrder(Order order) {
        updateOrderState(order, OrderState.NEW);
        log.info("Created new order "+order);
        if( order instanceof GeneralOrder ) {
            GeneralOrder generalOrder = (GeneralOrder) order;
            handleGeneralOrder(generalOrder);
        }
        else if( order instanceof SpecificOrder ) {
            SpecificOrder specificOrder = (SpecificOrder) order;
            handleSpecificOrder(specificOrder);
        }
    }


    public OrderState getOrderState(Order o) {
        OrderState state = orderStateMap.get(o);
        if( state == null )
            throw new IllegalStateException("Untracked order "+o);
        return state;
    }


    @When("select * from Fill")
    public void handleFill( Fill fill ) {
        Order order = fill.getOrder();
        if( log.isInfoEnabled() )
            log.info("Received Fill "+fill);
        OrderState state = orderStateMap.get(order);
        if( state == null ) {
            log.warn("Untracked order "+order);
            state = OrderState.PLACED;
        }
        if( state == OrderState.NEW )
            log.warn("Fill received for Order in NEW state: skipping PLACED state");
        if( state.isOpen() ) {
            OrderState newState = order.isFilled() ? OrderState.FILLED : OrderState.PARTFILLED;
            updateOrderState(order,newState);
        }
    }


    protected void handleGeneralOrder(GeneralOrder generalOrder) {
        Book b = generalOrder.isBid() ? quotes.getBestBidForListing(generalOrder.getListing())
                                      : quotes.getBestAskForListing(generalOrder.getListing());
        if( b == null ) {
            log.warn("No offers on the book for "+generalOrder.getListing());
            reject(generalOrder, "Stop-limit orders are not supported");
        }
        @SuppressWarnings("ConstantConditions")
        Market market = b.getMarket();
        @SuppressWarnings("ConstantConditions")
        SpecificOrder specificOrder = convertGeneralOrderToSpecific(generalOrder, market);
        log.info("Routing order "+generalOrder+" to "+ market.getExchange().getSymbol());
        handleSpecificOrder(specificOrder);
    }


    private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, Market market) {
        DiscreteAmount volume = generalOrder.getVolume().toBasis(market.getVolumeBasis(), Remainder.DISCARD);

        // the volume will already be negative for a sell order
        OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(generalOrder.getFund()).create(market, volume);

        DecimalAmount.RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
        final DecimalAmount limitPrice = generalOrder.getLimitPrice();
        if( limitPrice != null ) {
            DiscreteAmount discreteLimit = limitPrice.toBasis(market.getPriceBasis(), priceRemainderHandler);
            builder.withLimitPrice(discreteLimit);
        }
        final DecimalAmount stopPrice = generalOrder.getStopPrice();
        if( stopPrice != null ) {
            DiscreteAmount discreteStop = stopPrice.toBasis(market.getPriceBasis(),priceRemainderHandler);
            builder.withStopPrice(discreteStop);
        }
        SpecificOrder specificOrder = builder.getOrder();
        specificOrder.copyCommonOrderProperties(generalOrder);
        specificOrder.setParentOrder(generalOrder);
        return specificOrder;
    }


    protected void reject(Order order, String message) {
        log.warn("Order "+order+" rejected: "+message);
        updateOrderState(order, OrderState.REJECTED);
    }


    protected abstract void handleSpecificOrder(SpecificOrder specificOrder);


    protected void updateOrderState(Order order, OrderState state) {
        OrderState oldState = orderStateMap.get(order);
        if( oldState == null )
            oldState = OrderState.NEW;
        orderStateMap.put(order,state);
        context.publish(new OrderUpdate(order, oldState, state));
    }


    // rounding depends on whether it is a buy or sell order.  we round to the "best" price
    private static final DecimalAmount.RemainderHandler sellHandler = new Amount.RemainderHandler() {
        public RoundingMode getRoundingMode() { return RoundingMode.CEILING; }
    };


    private static final DecimalAmount.RemainderHandler buyHandler = new Amount.RemainderHandler() {
        public RoundingMode getRoundingMode() { return RoundingMode.FLOOR; }
    };


    @Inject
    protected Context context;
    @Inject
    private Logger log;
    private Map<Order, OrderState> orderStateMap = new HashMap<>();
    @Inject
    private QuoteService quotes;
}
