package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
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
        //   specificOrder.persit();

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
        log.trace("handleBook: Book Recieved: " + b);
        updateBook(b);
        //mockOrderService.submit(new updateBookRunnable(b));

    }

    @SuppressWarnings("ConstantConditions")
    @When("@Priority(9) select * from Trade(Trade.volumeCount!=0)")
    private void handleTrade(Trade t) {
        log.trace("handleTrade: Book Recieved: " + t);
        updateBook(t);
        // mockOrderService.submit(new updateBookRunnable(t));
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

        List<Fill> fills = Collections.synchronizedList(new ArrayList<Fill>());
        List<SpecificOrder> filledOrders = Collections.synchronizedList(new ArrayList<SpecificOrder>());

        // todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
        for (Order pendingOrder : pendingOrders) {
            if (pendingOrder instanceof SpecificOrder) {
                SpecificOrder order = (SpecificOrder) pendingOrder;
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
                                    Math.min(Math.abs(ask.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount())),
                                    Long.toString(ask.getTime().getMillis()));
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
        }
        cancelSpecificOrder(filledOrders);
        for (Fill fill : fills) {
            log.trace(fills.toString());

            handleFillProcessing(fill);
            //context.route(fill);
            //context.publish(fill);

            log.debug("filled");
        }

    }

    @Override
    protected synchronized boolean cancelSpecificOrder(SpecificOrder order) {

        return (pendingOrders.remove(order));

    }

    private void addOrder(SpecificOrder order) {

        pendingOrders.add(order);
        log.debug("Order: " + order + " added to mock order book");

        // mockOrderService.submit(new updateBookRunnable(quotes.getLastBook(order.getMarket())));

    }

    private void logFill(SpecificOrder order, Offer offer, Fill fill) {
        //  if (log.isDebugEnabled())
        log.info("Mock fill of Order " + order + " with Offer " + offer + ": " + fill);
    }

    // private static Object lock = new Object();
    private static final Collection<SpecificOrder> pendingOrders = new CopyOnWriteArrayList<SpecificOrder>();

    // new CopyOnWriteArrayList<SpecificOrder>();

    //private QuoteService quotes;

    //  @Override

    // }

    @Override
    public void init() {
        super.init();
        // TODO Auto-generated method stub

    }

}
