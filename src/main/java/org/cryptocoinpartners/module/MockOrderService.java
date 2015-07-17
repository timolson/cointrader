package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.exceptions.OrderNotFoundException;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.service.QuoteService;

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
        specificOrder.persit();
        if (specificOrder.getStopPrice() != null)
            reject(specificOrder, "Stop prices unsupported");

        addOrder(specificOrder);
        //TODO when placing the order it is on the same listener so it needs to be routed.
        updateOrderState(specificOrder, OrderState.PLACED, true);
        specificOrder.setEntryTime(context.getTime());
    }

    @SuppressWarnings("ConstantConditions")
    @When("@Priority(9) select * from Book")
    private void handleBook(Book b) {
        List<Fill> fills = new ArrayList<Fill>();

        // todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
        Iterator<SpecificOrder> itOrder = getPendingOrders().iterator();
        while (itOrder.hasNext()) {
            SpecificOrder order = itOrder.next();

            if (order.getUnfilledVolumeCount() == 0) {
                pendingOrders.remove(order);
                break;
            }

            if (order.getMarket().equals(b.getMarket())) {
                // buy order, so hit ask
                if (order.isBid()) {
                    long remainingVolume = order.getUnfilledVolumeCount();
                    for (Offer ask : b.getAsks()) {
                        if ((order.getLimitPrice() != null && order.getLimitPrice().getCount() < ask.getPriceCount()) || ask == null)
                            //  || ask.getVolumeCount() == 0 || ask.getPriceCount() == 0)
                            break;
                        //  synchronized (lock) {
                        long fillVolume = Math.min(Math.abs(ask.getVolumeCount()), remainingVolume);
                        if (fillVolume != 0) {
                            remainingVolume -= fillVolume;
                            if (remainingVolume == 0)
                                pendingOrders.remove(order);
                            Fill fill = new Fill(order, ask.getTime(), ask.getTime(), ask.getMarket(), ask.getPriceCount(), fillVolume, Long.toString(ask
                                    .getTime().getMillis()));
                            fill.getOrder().addFill(fill);
                            context.route(fill);
                            logFill(order, ask, fill);

                            // i--;
                            // --removeOrder(order);

                            //  }
                            //   break;

                        }
                    }
                }
                // if sell order, fill if limint<=Bid
                if (order.isAsk()) {
                    long remainingVolume = order.getUnfilledVolumeCount(); // this will be negative
                    for (Offer bid : b.getBids()) {
                        if ((order.getLimitPrice() != null && order.getLimitPrice().getCount() > bid.getPriceCount()) || bid == null)

                            //|| bid.getVolumeCount() == 0 || bid.getPriceCount() == 0)

                            break;
                        // synchronized (lock) {
                        long fillVolume = -Math.min(bid.getVolumeCount(), Math.abs(remainingVolume));
                        if (fillVolume != 0) {
                            remainingVolume -= fillVolume;

                            if (remainingVolume == 0)
                                pendingOrders.remove(order);
                            Fill fill = new Fill(order, bid.getTime(), bid.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume, Long.toString(bid
                                    .getTime().getMillis()));
                            fill.getOrder().addFill(fill);
                            context.route(fill);

                            logFill(order, bid, fill);

                        } //  }
                          // break;

                    }
                }
            }
        }

    }

    private void removeOrder(Order order) {

        pendingOrders.remove(order);

        // pendingOrders.remove(order);

    }

    protected void addOrder(SpecificOrder order) {

        pendingOrders.add(order);

    }

    @When("@Priority(8) select * from OrderUpdate where state.open=false and NOT (OrderUpdate.state = OrderState.CANCELLED)")
    private void completeOrder(OrderUpdate update) {
        OrderState orderState = update.getState();
        Order order = update.getOrder();

        switch (orderState) {
            case CANCELLING:
                removeOrder(order);
                updateOrderState(order, OrderState.CANCELLED, false);
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

    // private static Object lock = new Object();
    protected static final Collection<SpecificOrder> pendingOrders = new CopyOnWriteArrayList();
    private QuoteService quotes;

    @Override
    public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ConcurrentLinkedQueue<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio)) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingOpenOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ConcurrentLinkedQueue<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect().equals(PositionEffect.OPEN)) {

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
    public void handleCancelSpecificOrder(SpecificOrder specificOrder) throws OrderNotFoundException {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        boolean orderFound = false;
        // synchronized (lock) {
        Iterator<SpecificOrder> itPending = pendingOrders.iterator();

        while (itPending.hasNext()) {
            // closing position
            SpecificOrder cancelledOrder = itPending.next();

            if (cancelledOrder.equals(specificOrder)) {
                orderFound = true;
                pendingOrders.remove(cancelledOrder);
                updateOrderState(specificOrder, OrderState.CANCELLED, false);
                break;

            }
        }
        if (!orderFound)
            throw new OrderNotFoundException("Unable to cancelled order");
        //cancelledOrders.add(cancelledOrder);
        // pendingOrders.removeAll(cancelledOrders);

    }

    // }

    @Override
    public void handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.CLOSE))
                //cancelledOrders.add(specificOrder);
                try {
                    handleCancelSpecificOrder(specificOrder);
                } catch (Exception e) {
                    e.printStackTrace();
                }

        }

        //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
        //              SpecificOrder specificOrder = it.next();
        //
        //              
        //          }
        //  }

    }

    @Override
    public void handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //  synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.OPEN))
                //cancelledOrders.add(specificOrder);
                try {
                    handleCancelSpecificOrder(specificOrder);
                } catch (Exception e) {
                    e.printStackTrace();
                }

        }

        // Order order = new GeneralOrder()
        //updateOrderState(null, OrderState.CANCELLED, false);
        //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
        //              SpecificOrder specificOrder = it.next();
        //
        //              
        //          }
        //  }

    }

    @Override
    public void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        // synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market))
                try {
                    handleCancelSpecificOrder(specificOrder);
                } catch (Exception e) {
                    e.printStackTrace();
                }

        }
        //cancelledOrders.add(specificOrder);
        // updateOrderState(specificOrder, OrderState.CANCELLING);

        //          for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
        //              SpecificOrder specificOrder = it.next();
        //
        //              
        //          }
        //  }

    }

    @Override
    public Collection<SpecificOrder> getPendingOpenOrders(Market market, Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio)) {
                //&& pendingOrder.getMarket().equals(market)) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio)) {
                //&& pendingOrder.getMarket().equals(market)) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public void init() {
        super.init();
        // TODO Auto-generated method stub

    }
}
