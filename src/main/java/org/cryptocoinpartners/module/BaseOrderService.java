package org.cryptocoinpartners.module;

import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.QuoteService;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.Map;


/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class BaseOrderService implements OrderService {


    protected BaseOrderService(Context context) {
        this.context = context;
    }


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
    void handleFill( Fill fill ) {
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
        DiscreteAmount volume = DiscreteAmount.fromValue(generalOrder.getVolume(), market.getVolumeBasis(),
                                                         Remainder.DISCARD);
        // the volume will already be negative for a sell order
        OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(generalOrder.getFund()).create(market, volume);

        DiscreteAmount.RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
        final BigDecimal limitPrice = generalOrder.getLimitPrice();
        if( limitPrice != null ) {
            DiscreteAmount price = DiscreteAmount.fromValue(limitPrice, market.getPriceBasis(),priceRemainderHandler);
            builder.withLimitPriceCount(price.getCount());
        }
        final BigDecimal stopPrice = generalOrder.getStopPrice();
        if( stopPrice != null ) {
            DiscreteAmount price = DiscreteAmount.fromValue(stopPrice, market.getPriceBasis(),priceRemainderHandler);
            builder.withStopPriceCount(price.getCount());
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
    private static final DiscreteAmount.RemainderHandler sellHandler = new DiscreteAmount.RemainderHandler() {
        public void handleRemainder(DiscreteAmount result, double remainder) {
            if( remainder > .01 )
                result.increment();
        }
    };


    private static final DiscreteAmount.RemainderHandler buyHandler = new DiscreteAmount.RemainderHandler() {
        public void handleRemainder(DiscreteAmount result, double remainder) {
            if( remainder > .01 )
                result.increment();
        }
    };


    protected Context context;
    private Logger log;
    private Map<Order, OrderState> orderStateMap;
    private QuoteService quotes;
}
