package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.exceptions.OrderNotFoundException;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Trade;

/**
 * MockOrderService simulates the Filling of Orders by looking at broadcast Book data for price and volume information.
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class MockOrderService extends BaseOrderService {
    private static ExecutorService mockOrderService = Executors.newFixedThreadPool(10);

    @Override
    protected void handleSpecificOrder(SpecificOrder specificOrder) {
        specificOrder.persit();
        if (specificOrder.getStopPrice() != null)
            reject(specificOrder, "Stop prices unsupported");
        specificOrder.setEntryTime(context.getTime());

        addOrder(specificOrder);

        updateOrderState(specificOrder, OrderState.PLACED, true);

        //TODO when placing the order it is on the same listener so it needs to be routed.

    }

    private class updateBookRunnable implements Runnable {
        private final Event event;

        // protected Logger log;

        public updateBookRunnable(Event event) {
            this.event = event;

        }

        @Override
        public void run() {
            updateBook(event);

        }
    }

    @SuppressWarnings("ConstantConditions")
    @When("@Priority(9) select * from Book(Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    // @When("@Priority(9) select * from Book")
    private void handleBook(Book b) {
        updateBook(b);
        //mockOrderService.submit(new updateBookRunnable(b));

    }

    @SuppressWarnings("ConstantConditions")
    @When("@Priority(8) select * from Trade")
    private void handleTrade(Trade t) {
        updateBook(t);
        //mockOrderService.submit(new updateBookRunnable(t));
    }

    @SuppressWarnings("ConstantConditions")
    private synchronized void updateBook(Event event) {

        Market market = null;
        List<Offer> asks = new ArrayList<>();
        Book b = null;
        Trade t = null;
        List<Offer> bids = new ArrayList<>();
        if (event instanceof Book) {
            b = (Book) event;
            market = b.getMarket();
            asks = b.getAsks();
            bids = b.getBids();
        }

        if (event instanceof Trade) {
            t = (Trade) event;
            market = t.getMarket();
            //if Trade is a sell then it must have big the ask
            if (t.getVolume().isNegative()) {
                Offer bestBid = new Offer(market, t.getTime(), t.getTimeReceived(), t.getPrice().getCount(), t.getVolume().negate().getCount());
                asks.add(bestBid);
            } else {
                Offer bestAsk = new Offer(market, t.getTime(), t.getTimeReceived(), t.getPrice().getCount(), t.getVolume().negate().getCount());
                bids.add(bestAsk);

            }
        }

        List<Fill> fills = new ArrayList<Fill>();

        // todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
        Iterator<SpecificOrder> itOrder = getPendingOrders().iterator();
        while (itOrder.hasNext()) {
            SpecificOrder order = itOrder.next();

            if (order.getUnfilledVolumeCount() == 0) {
                pendingOrders.remove(order);
                break;
            }

            if (order.getMarket().equals(market)) {
                // buy order, so hit ask
                if (order.isBid() && orderStateMap.get(order).isOpen()) {
                    long remainingVolume = order.getUnfilledVolumeCount();
                    for (Offer ask : asks) {
                        if ((order.getLimitPrice() != null && order.getLimitPrice().getCount() < ask.getPriceCount()) || ask == null)
                            //  || ask.getVolumeCount() == 0 || ask.getPriceCount() == 0)
                            break;
                        //  synchronized (lock) {
                        if (t != null) {
                            log.debug("filled by a trade");

                        }
                        long fillVolume = Math.min(Math.abs(ask.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount()));
                        Fill fill = new Fill(order, ask.getTime(), ask.getTime(), ask.getMarket(), ask.getPriceCount(), Math.min(
                                Math.abs(ask.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount())), Long.toString(ask.getTime().getMillis()));
                        if (fill.getVolume().abs().compareTo(fill.getOrder().getVolume().abs()) > 0)
                            log.debug("overfilled" + (order.getUnfilledVolumeCount()));
                        fills.add(fill);
                        remainingVolume -= fillVolume;
                        logFill(order, ask, fill);
                        if (remainingVolume == 0) {
                            pendingOrders.remove(order);
                            break;
                        }
                    }

                }

                // i--;
                // --removeOrder(order);

                //  }
                //   break;

                // if sell order, fill if limint<=Bid
                if (order.isAsk() && orderStateMap.get(order).isOpen()) {
                    long remainingVolume = order.getUnfilledVolumeCount(); // this will be negative
                    // we need to thingk about maker and taker
                    //which side of the book do we hit
                    for (Offer bid : bids) {
                        if ((order.getLimitPrice() != null && order.getLimitPrice().getCount() > bid.getPriceCount()) || bid == null)

                            //|| bid.getVolumeCount() == 0 || bid.getPriceCount() == 0)

                            break;
                        // synchronized (lock) {
                        if (t != null) {
                            log.debug("filled by a trade");

                        }
                        long fillVolume = -Math.min(Math.abs(bid.getVolumeCount()), Math.abs(remainingVolume));
                        Fill fill = new Fill(order, bid.getTime(), bid.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume, Long.toString(bid.getTime()
                                .getMillis()));
                        if (fill.getVolume().abs().compareTo(fill.getOrder().getVolume().abs()) > 0)
                            log.debug("overfilled");
                        fills.add(fill);
                        remainingVolume -= fillVolume;
                        logFill(order, bid, fill);
                        if (remainingVolume == 0) {
                            pendingOrders.remove(order);
                            break;
                        }

                    } //  }
                      // break;

                }
            }
        }

        for (Fill fill : fills) {
            if (fill.getVolume().abs().compareTo(fill.getOrder().getVolume().abs()) > 0) {
                log.debug("order over filled");
            }
            context.route(fill);
            //context.publish(fill);

            log.debug("filled");
        }

    }

    private synchronized void removeOrder(Order order) {

        pendingOrders.remove(order);

        // pendingOrders.remove(order);

    }

    protected void addOrder(SpecificOrder order) {

        pendingOrders.add(order);
        log.debug("Order: " + order + " added to mock order book");

        // mockOrderService.submit(new updateBookRunnable(quotes.getLastBook(order.getMarket())));

    }

    @When("@Priority(7) select * from OrderUpdate where state.open=false and NOT (OrderUpdate.state = OrderState.CANCELLED)")
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
        //  if (log.isDebugEnabled())
        log.info("Mock fill of Order " + order + " with Offer " + offer + ": " + fill);
    }

    // private static Object lock = new Object();
    protected static final Collection<SpecificOrder> pendingOrders = new CopyOnWriteArrayList();

    //private QuoteService quotes;

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
    public Collection<SpecificOrder> getPendingCloseOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ConcurrentLinkedQueue<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect().equals(PositionEffect.CLOSE)) {

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
    public void handleCancelSpecificOrderByParentFill(Fill parentFill) throws OrderNotFoundException {
        if (parentFill == null)
            return;
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();

        Iterator<SpecificOrder> itPending = pendingOrders.iterator();
        boolean orderFound = false;

        while (itPending.hasNext()) {
            // closing position
            SpecificOrder cancelledOrder = itPending.next();

            if (cancelledOrder != null && cancelledOrder.getParentFill() != null && cancelledOrder.getParentFill().equals(parentFill)) {
                try {
                    handleCancelSpecificOrder(cancelledOrder);
                } catch (OrderNotFoundException e) {
                    throw e;

                }

            }
        }
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
                removeOrder(cancelledOrder);
                if (cancelledOrder.getParentFill() != null)
                    cancelledOrder.getParentFill()
                            .setPositionType(cancelledOrder.getParentFill().getVolumeCount() > 0 ? PositionType.LONG : PositionType.SHORT);
                // need to remove it from any parent
                if (cancelledOrder.getParentOrder() != null && cancelledOrder.getParentOrder().getChildren() != null
                        && !cancelledOrder.getParentOrder().getChildren().isEmpty())
                    cancelledOrder.getParentOrder().getChildren().remove(cancelledOrder);

                updateOrderState(specificOrder, OrderState.CANCELLED, false);
                log.info("Cancelled Specific Order:" + specificOrder);
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
    public void handleCancelAllShortClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market)
                    && specificOrder.getPositionEffect().equals(PositionEffect.CLOSE)
                    && specificOrder.isBid()
                    && (specificOrder.getExecutionInstruction() == null || (specificOrder.getExecutionInstruction() != null && specificOrder
                            .getExecutionInstruction() == execInst)))
                //cancelledOrders.add(specificOrder);
                try {
                    handleCancelSpecificOrder(specificOrder);
                } catch (Exception e) {
                    log.info("Order not found: " + specificOrder);

                }
        }
    }

    @Override
    public void handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market)
                    && specificOrder.getPositionEffect().equals(PositionEffect.CLOSE)
                    && specificOrder.isAsk()
                    && (specificOrder.getExecutionInstruction() == null || (specificOrder.getExecutionInstruction() != null && specificOrder
                            .getExecutionInstruction() == execInst)))
                //cancelledOrders.add(specificOrder);
                try {
                    handleCancelSpecificOrder(specificOrder);
                } catch (Exception e) {
                    log.info("Order not found: " + specificOrder);

                }
        }
    }

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
                    log.info("Order not found: " + specificOrder);

                }
        }
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
                    log.info("Order not found: " + specificOrder);
                }

        }

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
                    log.info("Order not found: " + specificOrder);

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
    public Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, ExecutionInstruction execInst) {
        Collection<SpecificOrder> portfolioPendingOrders = new ConcurrentLinkedQueue<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio)
                    && pendingOrder.getPositionEffect().equals(PositionEffect.CLOSE)
                    && pendingOrder.isAsk()
                    && (pendingOrder.getExecutionInstruction() == null || (pendingOrder.getExecutionInstruction() != null && pendingOrder
                            .getExecutionInstruction() == execInst))) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, ExecutionInstruction execInst) {
        Collection<SpecificOrder> portfolioPendingOrders = new ConcurrentLinkedQueue<>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio)
                    && pendingOrder.getPositionEffect().equals(PositionEffect.CLOSE)
                    && pendingOrder.isBid()
                    && (pendingOrder.getExecutionInstruction() == null || (pendingOrder.getExecutionInstruction() != null && pendingOrder
                            .getExecutionInstruction() == execInst))) {

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
