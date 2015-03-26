package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.service.QuoteService;
import org.slf4j.Logger;

/**
 * MockOrderService simulates the Filling of Orders by looking at broadcast Book data for price and volume information.
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class MockOrderService extends BaseOrderService {

    @Override
    protected void handleSpecificOrder(SpecificOrder specificOrder) {
        if (specificOrder.getStopPrice() != null)
            reject(specificOrder, "Stop prices unsupported");

        addOrder(specificOrder);
        updateOrderState(specificOrder, OrderState.PLACED);
        specificOrder.setEntryTime(context.getTime());
    }

    @SuppressWarnings("ConstantConditions")
    @When("@Priority(8) select * from Book")
    private void handleBook(Book b) {
        // synchronized (lock) {
        List<Fill> fills = new ArrayList<Fill>();

        // todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
        for (SpecificOrder order : pendingOrders) {

            if (order.getMarket().equals(b.getMarket())) {
                if (order.isBid()) {
                    long remainingVolume = order.getUnfilledVolumeCount();
                    for (Offer ask : b.getAsks()) {
                        if (order.getLimitPrice() != null && order.getLimitPrice().getCount() < ask.getPriceCount())
                            break;
                        synchronized (lock) {
                            long fillVolume = Math.min(Math.abs(ask.getVolumeCount()), remainingVolume);
                            Fill fill = new Fill(order, ask.getTime(), ask.getTime(), ask.getMarket(), ask.getPriceCount(), fillVolume, Long.toString(ask
                                    .getTime().getMillis()));
                            fills.add(fill);
                            remainingVolume -= fillVolume;
                            logFill(order, ask, fill);
                            if (remainingVolume == 0) {
                                pendingOrders.remove(order);
                                // i--;
                                // --removeOrder(order);
                            }
                        }
                        break;
                    }
                }
                if (order.isAsk()) {
                    long remainingVolume = order.getUnfilledVolumeCount(); // this will be negative
                    for (Offer bid : b.getBids()) {
                        if (order.getLimitPrice() != null && order.getLimitPrice().getCount() > bid.getPriceCount())
                            break;
                        synchronized (lock) {
                            long fillVolume = -Math.min(bid.getVolumeCount(), Math.abs(remainingVolume));
                            Fill fill = new Fill(order, bid.getTime(), bid.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume, Long.toString(bid
                                    .getTime().getMillis()));

                            fills.add(fill);
                            remainingVolume -= fillVolume;
                            logFill(order, bid, fill);
                            if (remainingVolume == 0)
                                pendingOrders.remove(order);
                        }
                        break;

                    }
                }
            }
        }
        for (Fill fill : fills) {
            fill.getOrder().addFill(fill);
            context.publish(fill);
            //       context.route(fill);
        }
    }

    // }

    private void removeOrder(Order order) {

        pendingOrders.remove(order);

        // pendingOrders.remove(order);

    }

    private void addOrder(SpecificOrder order) {

        pendingOrders.add(order);

    }

    @When("@Priority(9) select * from OrderUpdate where state.open=false and NOT (OrderUpdate.state = OrderState.CANCELLED)")
    private void completeOrder(OrderUpdate update) {
        OrderState orderState = update.getState();
        Order order = update.getOrder();

        switch (orderState) {
            case CANCELLING:
                removeOrder(order);
                updateOrderState(order, OrderState.CANCELLED);
                break;
            default:
                removeOrder(order);
                break;
        }

    }

    private void logFill(SpecificOrder order, Offer offer, Fill fill) {
        if (log.isDebugEnabled())
            log.debug("Mock fill of Order " + order + " with Offer " + offer + ": " + fill);
    }

    @Inject
    private Logger log;
    private static Object lock = new Object();
    protected static final Collection<SpecificOrder> pendingOrders = new ConcurrentLinkedQueue<SpecificOrder>();
    private QuoteService quotes;

    @Override
    public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio)) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingOrders() {

        return pendingOrders;

    }

    @Override
    public void handleCancelSpecificOrder(SpecificOrder specificOrder) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        synchronized (lock) {
            for (SpecificOrder cancelledOrder : pendingOrders) {

                if (cancelledOrder.equals(specificOrder)) {
                    pendingOrders.remove(cancelledOrder);

                }
                //cancelledOrders.add(cancelledOrder);
                // pendingOrders.removeAll(cancelledOrders);
            }

            updateOrderState(specificOrder, OrderState.CANCELLED);

        }

    }

    @Override
    public void handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        synchronized (lock) {
            for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
                SpecificOrder specificOrder = it.next();
                if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.CLOSE))
                    //cancelledOrders.add(specificOrder);
                    handleCancelSpecificOrder(specificOrder);
            }
            //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
            //              SpecificOrder specificOrder = it.next();
            //
            //              
            //          }
        }

    }

    @Override
    public void handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        synchronized (lock) {
            for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
                SpecificOrder specificOrder = it.next();
                if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.OPEN))
                    //cancelledOrders.add(specificOrder);
                    handleCancelSpecificOrder(specificOrder);
            }
            //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
            //              SpecificOrder specificOrder = it.next();
            //
            //              
            //          }
        }

    }

    @Override
    public void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        synchronized (lock) {
            for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
                SpecificOrder specificOrder = it.next();
                if (specificOrder.getMarket().equals(market))
                    handleCancelSpecificOrder(specificOrder);
                //cancelledOrders.add(specificOrder);
                // updateOrderState(specificOrder, OrderState.CANCELLING);
            }
            //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
            //              SpecificOrder specificOrder = it.next();
            //
            //              
            //          }
        }

    }

    @Override
    public Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio) {
        // TODO Auto-generated method stub
        return null;
    }
}
