package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
import org.cryptocoinpartners.schema.DiscreteAmount;
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
    private static ExecutorService mockOrderService = Executors.newFixedThreadPool(1);

    @Override
    protected void handleSpecificOrder(SpecificOrder specificOrder) {

        if (specificOrder.getStopPrice() != null)
            reject(specificOrder, "Stop prices unsupported");
        specificOrder.setEntryTime(context.getTime());

        addOrder(specificOrder);

        updateOrderState(specificOrder, OrderState.PLACED, true);
        specificOrder.persit();

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
        // mockOrderService.submit(new updateBookRunnable(b));

    }

    @SuppressWarnings("ConstantConditions")
    @When("@Priority(8) select * from Trade(Trade.volumeCount!=0)")
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
        List<SpecificOrder> filledOrders = new ArrayList<SpecificOrder>();

        // todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
        for (SpecificOrder order : getPendingOrders()) {
            //        Iterator<SpecificOrder> itOrder = getPendingOrders().iterator();
            //        while (itOrder.hasNext()) {
            //            SpecificOrder order = itOrder.next();

            if (order.getUnfilledVolumeCount() == 0) {
                filledOrders.add(order);
                // pendingOrders.remove(order);
                break;
            }

            if (order.getMarket().equals(market)) {
                // buy order, so hit ask
                if (order.isBid()) {
                    long remainingVolume = order.getUnfilledVolumeCount();
                    for (Offer ask : asks) {
                        if (order.getLimitPrice() == null)
                            log.debug("null limit");
                        // 
                        if ((order.getLimitPrice() != null && order.getLimitPrice().getCount() < ask.getPriceCount()) || ask == null)
                            //  || ask.getVolumeCount() == 0 || ask.getPriceCount() == 0)
                            break;
                        //  synchronized (lock) {
                        if (t != null) {
                            log.debug("filled by a trade");

                        }
                        long fillVolume = Math.min(Math.abs(ask.getVolumeCount()), Math.abs(remainingVolume));
                        if (fillVolume == 0)
                            return;
                        Fill fill = fillFactory.create(order, ask.getTime(), ask.getTime(), ask.getMarket(), ask.getPriceCount(),
                                Math.min(Math.abs(ask.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount())), Long.toString(ask.getTime().getMillis()));
                        if (fill.getVolume() == null || fill.getVolume().isZero())
                            log.debug("fille zero lots" + (order.getUnfilledVolumeCount()));
                        if (fill.getVolume().abs().compareTo(order.getVolume().abs()) > 0)
                            log.debug("overfilled" + (order.getUnfilledVolumeCount()));

                        fills.add(fill);
                        remainingVolume -= fillVolume;
                        logFill(order, ask, fill);
                        if (remainingVolume == 0) {
                            filledOrders.add(order);
                            //pendingOrders.remove(order);
                            break;
                        }
                    }

                }

                // i--;
                // --removeOrder(order);

                //  }
                //   break;

                // if sell order, fill if limint<=Bid
                if (order.isAsk()) {
                    long remainingVolume = order.getUnfilledVolumeCount(); // this will be negative
                    // we need to thingk about maker and taker
                    //which side of the book do we hit
                    for (Offer bid : bids) {
                        if (order.getLimitPrice() == null)
                            log.debug("null limit");

                        if ((order.getLimitPrice() != null && order.getLimitPrice().getCount() > bid.getPriceCount()) || bid == null)

                            //|| bid.getVolumeCount() == 0 || bid.getPriceCount() == 0)

                            break;
                        // synchronized (lock) {
                        if (t != null) {
                            log.debug("filled by a trade");

                        }
                        long fillVolume = -Math.min(Math.abs(bid.getVolumeCount()), Math.abs(remainingVolume));
                        if (fillVolume == 0)
                            return;
                        Fill fill = fillFactory.create(order, context.getTime(), context.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume,
                                Long.toString(bid.getTime().getMillis()));
                        if (fill.getVolume() == null || fill.getVolume().isZero())
                            log.debug("fille zero lots" + (order.getUnfilledVolumeCount()));

                        if (fill.getVolume().abs().compareTo(order.getVolume().abs()) > 0)
                            log.debug("overfilled");
                        fills.add(fill);
                        remainingVolume -= fillVolume;
                        logFill(order, bid, fill);
                        if (remainingVolume == 0) {
                            filledOrders.add(order);
                            // pendingOrders.remove(order);
                            break;
                        }

                    } //  }
                      // break;

                }
            }
        }
        pendingOrders.removeAll(filledOrders);

        for (Fill fill : fills) {

            context.route(fill);
            //   context.publish(fill);

            log.debug("filled");
        }

    }

    private synchronized boolean removeOrder(Order order) {

        return (pendingOrders.remove(order));

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
    protected static final Collection<SpecificOrder> pendingOrders = new ArrayList<SpecificOrder>();

    //private QuoteService quotes;

    @Override
    public synchronized Collection<SpecificOrder> getPendingOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();
        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio)) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public synchronized Collection<SpecificOrder> getPendingOpenOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect().equals(PositionEffect.OPEN)) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public synchronized Collection<SpecificOrder> getPendingLongOpenOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect().equals(PositionEffect.OPEN) && pendingOrder.isBid()) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public synchronized Collection<SpecificOrder> getPendingShortOpenOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.getPortfolio().equals(portfolio) && pendingOrder.getPositionEffect().equals(PositionEffect.OPEN) && pendingOrder.isAsk()) {

                portfolioPendingOrders.add(pendingOrder);
            }

        }
        return portfolioPendingOrders;
    }

    @Override
    public synchronized Collection<SpecificOrder> getPendingCloseOrders(Portfolio portfolio) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

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

        ArrayList<Order> allChildOrders = new ArrayList<Order>();
        getAllSpecificOrdersByParentFill(parentFill, allChildOrders);

        for (Order childOrder : allChildOrders) {
            if (childOrder instanceof SpecificOrder) {
                try {
                    handleCancelSpecificOrder((SpecificOrder) childOrder);
                } catch (OrderNotFoundException e) {
                    throw e;
                }
            }
        }

        //        while (itPending.hasNext()) {
        //            // closing position
        //            SpecificOrder cancelledOrder = itPending.next();
        //
        //            if (cancelledOrder != null && cancelledOrder.getParentFill() != null && cancelledOrder.getParentFill().equals(parentFill)) {
        //               
        //
        //                }
        //
        //            }
        //        }
    }

    @Override
    public void getAllSpecificOrdersByParentFill(Fill parentFill, List allChildren) {
        for (Order child : parentFill.getFillChildOrders()) {
            if (child instanceof SpecificOrder)
                allChildren.add(child);
            getAllSpecificOrderByParentOrder(child, allChildren);
        }
    }

    void getAllSpecificOrderByParentOrder(Order paretnOrder, List allChildren) {
        for (Order child : paretnOrder.getOrderChildren()) {
            if (child instanceof SpecificOrder)
                allChildren.add(child);
            getAllSpecificOrderByParentOrder(child, allChildren);
        }
    }

    @Override
    public void handleCancelSpecificOrder(SpecificOrder specificOrder) throws OrderNotFoundException {
        if (removeOrder(specificOrder)) {
            if (specificOrder.getParentFill() != null)
                specificOrder.getParentFill().setPositionType(specificOrder.getParentFill().getOpenVolumeCount() > 0 ? PositionType.LONG : PositionType.SHORT);
            // need to remove it from any parent
            //            if (specificOrder.getParentOrder() != null && specificOrder.getParentOrder().getOrderChildren() != null
            //                    && !specificOrder.getParentOrder().getOrderChildren().isEmpty())
            //
            //                specificOrder.getParentOrder().removeChildOrder(specificOrder);

            updateOrderState(specificOrder, OrderState.CANCELLED, false);
            log.info("handleCancelSpecificOrder cancelled Specific Order:" + specificOrder);

        }

        else
            throw new OrderNotFoundException("Unable to cancelled order");
        //cancelledOrders.add(cancelledOrder);
        // pendingOrders.removeAll(cancelledOrders);

    }

    // }

    @Override
    public synchronized void handleCancelAllShortClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market)
                    && specificOrder.getPositionEffect().equals(PositionEffect.CLOSE)
                    && specificOrder.isBid()
                    && (specificOrder.getExecutionInstruction() == null || (specificOrder.getExecutionInstruction() != null && specificOrder
                            .getExecutionInstruction() == execInst))) {
                cancelledOrders.add(specificOrder);
                log.info("handleCancelAllShortClosingSpecificOrders cancelling order : " + specificOrder);

            }
        }

        if (!pendingOrders.removeAll(cancelledOrders))
            log.info("handleCancelAllShortClosingSpecificOrders Order not found: " + cancelledOrders);

    }

    @Override
    public synchronized void handleCancelAllLongClosingSpecificOrders(Portfolio portfolio, Market market, ExecutionInstruction execInst) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market)
                    && specificOrder.getPositionEffect().equals(PositionEffect.CLOSE)
                    && specificOrder.isAsk()
                    && (specificOrder.getExecutionInstruction() == null || (specificOrder.getExecutionInstruction() != null && specificOrder
                            .getExecutionInstruction() == execInst))) {
                cancelledOrders.add(specificOrder);
                log.info("handleCancelAllLongClosingSpecificOrders cancelling order : " + specificOrder);
            }
        }
        if (!pendingOrders.removeAll(cancelledOrders))
            log.info("handleCancelAllLongClosingSpecificOrders Order not found: " + cancelledOrders);

    }

    @Override
    public synchronized void handleCancelAllClosingSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //   synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.CLOSE)) {
                //cancelledOrders.add(specificOrder);
                cancelledOrders.add(specificOrder);
                log.info("handleCancelAllLongClosingSpecificOrders cancelling order : " + specificOrder);
            }
            if (!pendingOrders.removeAll(cancelledOrders))

                log.info("handleCancelAllLongClosingSpecificOrders Order not found: " + specificOrder);

        }
    }

    @Override
    public synchronized void handleCancelAllLongOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //  synchronized (lock) {
        for (SpecificOrder specificOrder : pendingOrders) {
            if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.OPEN) && specificOrder.isBid()) {
                cancelledOrders.add(specificOrder);
                log.info("handleCancelAllLongOpeningSpecificOrders cancelling order : " + specificOrder);
            }

        }
        if (!pendingOrders.removeAll(cancelledOrders))

            log.info("handleCancelAllLongOpeningSpecificOrders Orders not found: " + cancelledOrders);

    }

    @Override
    public synchronized void handleCancelAllShortOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //  synchronized (lock) {
        for (SpecificOrder specificOrder : pendingOrders) {
            if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.OPEN) && specificOrder.isAsk()) {
                cancelledOrders.add(specificOrder);
                log.info("handleCancelAllShortOpeningSpecificOrders cancelling order : " + specificOrder);
            }

        }
        if (!pendingOrders.removeAll(cancelledOrders))

            log.info("handleCancelAllShortOpeningSpecificOrders Orders not found: " + cancelledOrders);

    }

    @Override
    public synchronized void handleCancelAllOpeningSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        //  synchronized (lock) {
        for (SpecificOrder specificOrder : pendingOrders) {
            if (specificOrder.getMarket().equals(market) && specificOrder.getPositionEffect().equals(PositionEffect.OPEN)) {
                cancelledOrders.add(specificOrder);
                log.info("handleCancelAllOpeningSpecificOrders cancelling order : " + specificOrder);
            }

        }
        if (!pendingOrders.removeAll(cancelledOrders))

            log.info("handleCancelAllOpeningSpecificOrders Orders not found: " + cancelledOrders);

    }

    @Override
    public synchronized void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
        Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
        // synchronized (lock) {
        for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
            SpecificOrder specificOrder = it.next();
            if (specificOrder.getMarket().equals(market)) {
                cancelledOrders.add(specificOrder);
                log.info("handleCancelAllSpecificOrders cancelling order : " + specificOrder);
            }
        }
        if (!pendingOrders.removeAll(cancelledOrders))

            log.info("handleCancelAllSpecificOrders Orders not found: " + cancelledOrders);

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
    public synchronized Collection<SpecificOrder> getPendingOpenOrders(Market market, Portfolio portfolio) {
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
    public synchronized Collection<SpecificOrder> getPendingLongCloseOrders(Portfolio portfolio, ExecutionInstruction execInst) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

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
    public synchronized Collection<SpecificOrder> getPendingShortCloseOrders(Portfolio portfolio, ExecutionInstruction execInst) {
        Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<SpecificOrder>();

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
    public synchronized Collection<SpecificOrder> getPendingOrders(Market market, Portfolio portfolio) {
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

    @Override
    public void getAllSpecificOrdersByParentOrder(Order parentOrder, List allChildren) {
        // TODO Auto-generated method stub

    }

    @Override
    protected synchronized void handleUpdateSpecificOrderWorkingQuantity(SpecificOrder specificOrder, DiscreteAmount quantity) {
        for (SpecificOrder pendingOrder : pendingOrders) {
            if (pendingOrder.equals(specificOrder)) {
                // so we need to ensure that the unfilled wuanity is waulty to the qauntity.

                // 200 lots order, 100 lots fill, want to update to 10 lots, so 110.
                long updatedQuantity = (quantity.isNegative()) ? -1
                        * (Math.abs(specificOrder.getVolume().getCount()) - Math.abs(specificOrder.getUnfilledVolumeCount()) + Math.abs(quantity.getCount()))
                        : Math.abs(specificOrder.getVolume().getCount()) - Math.abs(specificOrder.getUnfilledVolumeCount()) + Math.abs(quantity.getCount());

                //&& pendingOrder.getMarket().equals(market)) {
                pendingOrder.setVolumeCount(updatedQuantity);

            }

        }

    }
}
