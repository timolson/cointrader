package org.cryptocoinpartners.service;

import org.cryptocoinpartners.schema.*;

import java.math.BigDecimal;
import java.util.Map;


/**
 * This depends on a QuoteService being attached to the Esper first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class BaseOrderService extends BaseService implements OrderService {


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


    protected void handleGeneralOrder(GeneralOrder generalOrder) {
        Book b = generalOrder.isBid() ? quotes.getBestBidForListing(generalOrder.getListing())
                                      : quotes.getBestAskForListing(generalOrder.getListing());
        if( b == null ) {
            log.warn("No offers on the book for "+generalOrder.getListing());
            reject(generalOrder, "Stop-limit orders are not supported");
        }
        @SuppressWarnings("ConstantConditions")
        MarketListing marketListing = b.getMarketListing();
        @SuppressWarnings("ConstantConditions")
        SpecificOrder specificOrder = convertGeneralOrderToSpecific(generalOrder, marketListing);
        log.info("Routing order "+generalOrder+" to "+marketListing.getMarket().getSymbol());
        handleSpecificOrder(specificOrder);
    }


    private SpecificOrder convertGeneralOrderToSpecific(GeneralOrder generalOrder, MarketListing marketListing) {
        DiscreteAmount amount = DiscreteAmount.fromValue(generalOrder.getAmount(), marketListing.getVolumeBasis(),
                                                         Remainder.DISCARD);
        // the amount will already be negative for a sell order
        OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(generalOrder.getFund()).buy(marketListing, amount);

        DiscreteAmount.RemainderHandler priceRemainderHandler = generalOrder.isBid() ? buyHandler : sellHandler;
        final BigDecimal limitPrice = generalOrder.getLimitPrice();
        if( limitPrice != null ) {
            DiscreteAmount price = DiscreteAmount.fromValue(limitPrice,marketListing.getPriceBasis(),priceRemainderHandler);
            builder.withLimitPriceCount(price.getCount());
        }
        final BigDecimal stopPrice = generalOrder.getStopPrice();
        if( stopPrice != null ) {
            DiscreteAmount price = DiscreteAmount.fromValue(stopPrice,marketListing.getPriceBasis(),priceRemainderHandler);
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


    public OrderState getOrderState(Order o) {
        OrderState state = orderStateMap.get(o);
        if( state == null )
            throw new IllegalStateException("Untracked order "+o);
        return state;
    }


    protected void updateOrderState(Order order, OrderState state) {
        OrderState oldState = orderStateMap.get(order);
        if( oldState == null )
            oldState = OrderState.NEW;
        orderStateMap.put(order,state);
        esper.publish(new OrderUpdate(order, oldState, state));
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


    private Map<Order, OrderState> orderStateMap;
    private QuoteService quotes;
}
