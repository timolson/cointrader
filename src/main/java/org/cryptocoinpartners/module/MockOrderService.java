package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.EM;

/**
 * MockOrderService simulates the Filling of Orders by looking at broadcast Book data for price and volume information.
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class MockOrderService extends BaseOrderService {
    private static ExecutorService mockOrderService = Executors.newFixedThreadPool(1);
    // static Double doubleSlippage = ConfigUtil.combined().getDouble("mock.exchange.slippage", 0.02);
    private static double slippage = ConfigUtil.combined().getDouble("mock.exchange.slippage", 0.002);
    protected final Lock updateOrderBookLock = new ReentrantLock();

    // Object orderProcessingLock;

    // protected final Lock orederMatchingLock = new ReentrantLock();

    @Override
    protected void handleSpecificOrder(SpecificOrder specificOrder) {
        if (specificOrder.getStopPrice() != null) {

            reject(specificOrder, "Stop prices unsupported");
        }
        specificOrder.setEntryTime(context.getTime());

        addOrder(specificOrder);

        updateOrderState(specificOrder, OrderState.PLACED, true);
        updateBook(quotes.getLastBook(specificOrder.getMarket()));
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
    // @When("@Priority(9) select * from Book(Book.market in (TrendStrategy.getMarkets()), TrendStrategy.getMarketAllocation(Book.market)>0, Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0 )")
    // @When("@Priority(9) select * from Book")
    @When("@Priority(8) select * from LastBookWindow(market.synthetic=false)")
    private void handleBook(Book b) {
        log.trace("handleBook: Book Recieved: " + b);
        updateBook(b);
        //mockOrderService.submit(new updateBookRunnable(b));

    }

    @SuppressWarnings("ConstantConditions")
    @When("@Priority(8) select * from LastTradeWindow(market.synthetic=false)")
    private void handleTrade(Trade t) {
        log.trace("handleTrade: Book Recieved: " + t);
        updateBook(t);
        // mockOrderService.submit(new updateBookRunnable(t));
    }

    @SuppressWarnings("ConstantConditions")
    private void updateBook(Event event) {
        if (event == null)
            log.debug("test");
        //   log.trace(this.getClass().getSimpleName() + " : updateBook to called from stack " + Thread.currentThread().getStackTrace()[2]);

        Tradeable market = null;
        List<Offer> asks = new ArrayList<>();
        Book b = null;
        Trade t = null;
        List<Offer> bids = new ArrayList<>();
        if (event instanceof Book) {
            b = (Book) event;
            if (b.getMarket().isSynthetic())
                return;
            market = b.getMarket();
            asks = b.getAsks();
            bids = b.getBids();
        }

        if (event instanceof Trade) {
            t = (Trade) event;
            if (t.getMarket().isSynthetic())
                return;
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

        // todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
        // we will loop over the orders that are looking to buy against the sell orders from the book i.e. the asks.
        if (pendingOrders != null && pendingOrders.get(market) != null && pendingOrders.get(market).get(TransactionType.BUY) != null) {
            ArrayList<SpecificOrder> buyOrdersToBeRemoved = new ArrayList<SpecificOrder>();

            try {
                // log.debug(this.getClass().getSimpleName() + ":updateBook locking buy updateOrderBookLock");

                // updateOrderBookLock.lock();
                log.trace(this.getClass().getSimpleName() + ":updateBook - looping over buy pending orders "
                        + pendingOrders.get(market).get(TransactionType.BUY));

                synchronized (pendingOrders.get(market).get(TransactionType.BUY)) {

                    BIDORDERSLOOP: for (Iterator<SpecificOrder> itbpo = pendingOrders.get(market).get(TransactionType.BUY).iterator(); itbpo.hasNext();) {
                        SpecificOrder order = itbpo.next();
                        //  synchronized (order) {

                        // BIDORDERSLOOP: for (SpecificOrder pendingOrder : pendingOrders.get(market).get(TransactionType.BUY)) {
                        //       : while (itbpo.hasNext()) {

                        //      SpecificOrder pendingOrder = itbpo.next();

                        // for (Order pendingOrder : pendingOrders) {
                        //  if (pendingOrder instanceof SpecificOrder) {
                        //    SpecificOrder order = pendingOrder;
                        if (order.getUnfilledVolumeCount() == 0) {
                            log.trace(this.getClass().getSimpleName() + ":updateBook - removing buy order filled order " + order + " from order book "
                                    + pendingOrders.get(market).get(TransactionType.BUY));
                            // pendingOrders.get(market).get(TransactionType.BUY).remove(order);
                            itbpo.remove();
                            continue;
                        }

                        /*                            if (order.getUnfilledVolumeCount() == 0) {
                                                        // buyOrdersToBeRemoved.add(order);
                                                        order.getUnfilledVolumeCount();
                                                        itbpo.remove();
                                                        // pendingOrders.remove(order);
                                                        continue;
                                                    }
                        */
                        // 10.15, 10.18 ok
                        // 10.15, 10.14 not ok, so if the order time is > then the even time we should not process

                        if (order.getMarket().equals(market) && (order.getTimestamp() <= event.getTimestamp())
                                && pendingOrders.get(market).get(TransactionType.BUY).contains(order)) {
                            // buy order, so hit ask

                            //  long bidRemainingVolume = order.getUnfilledVolumeCount();
                            log.trace(this.getClass().getSimpleName() + ":UpdateBook - determining fills for buy order " + order.getId()
                                    + " with working volume " + order.getUnfilledVolumeCount());

                            ASKSLOOP: for (Offer ask : asks) {
                                if (order.getLimitPrice() == null)
                                    log.debug("null limit");
                                if (order.getTimestamp() > ask.getTimestamp()) {
                                    log.debug(this.getClass().getSimpleName() + ":UpdateBook - ask " + ask + " older than order " + order);

                                    continue ASKSLOOP;
                                }
                                // 
                                if (ask == null || (order.getLimitPrice() != null && ask != null && order.getLimitPrice().getCount() < ask.getPriceCount())) {
                                    //  || ask.getVolumeCount() == 0 || ask.getPriceCount() == 0)
                                    log.trace(this.getClass().getSimpleName() + ":UpdateBook - ask price " + ask + " greater than limit price "
                                            + order.getLimitPrice() + " for order " + order);
                                    //return;
                                    break BIDORDERSLOOP;
                                }
                                //  synchronized (lock) {
                                if (t != null) {
                                    log.debug("filled by a trade");

                                }

                                long buyFillVolume = Math.min(Math.abs(ask.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount()));
                                log.trace(this.getClass().getSimpleName() + ":UpdateBook - set fillVolume to " + buyFillVolume + " for order " + order
                                        + " with fills " + order.getFills());

                                if (buyFillVolume == 0) {
                                    log.trace(this.getClass().getSimpleName() + ":UpdateBook fill voume zero for ask" + ask + " and order " + order);

                                    continue ASKSLOOP;
                                }
                                //I am buying, so I want to buy at cheapest price, so we will add to ask price the slppage
                                // fillPrice long = ask.getPriceCount()
                                long slippageDiff = Math.round(ask.getPriceCount() * slippage);
                                // I am buying so limit price is 100, current ask is 99, get filled at 99
                                // order price is  636.36 and buying at ask 635.90, so I get the I want to buy at lowest rpice so 
                                long fillPriceCount = Math.min(order.getLimitPrice().getCount(), (ask.getPriceCount() + slippageDiff));
                                log.debug(this.getClass().getSimpleName() + ":updateBook - Creating fill with ask " + ask);

                                Fill fill = fillFactory.create(order, ask.getTime(), ask.getTime(), order.getMarket(), fillPriceCount, buyFillVolume,
                                        Long.toString(ask.getTime().getMillis()));
                                logFill(order, ask, fill);
                                log.debug(this.getClass().getSimpleName() + ":UpdateBook - set askVolume " + ask.getVolumeCount() + " to "
                                        + (ask.getVolumeCount() - buyFillVolume));
                                ask.setVolumeCount(ask.getVolumeCount() + buyFillVolume);

                                if (order.getUnfilledVolumeCount() == 0) {
                                    log.trace(this.getClass().getSimpleName() + ":updateBook - Buy order(" + order.hashCode() + ") fully filled " + order
                                            + " removing from order book " + pendingOrders.get(market).get(TransactionType.BUY));
                                    itbpo.remove();
                                    //   if (pendingOrders.get(market).get(TransactionType.BUY).remove(order))
                                    log.debug(this.getClass().getSimpleName() + ":updateBook - Buy order(" + order.hashCode() + ") fully filled " + order
                                            + " removed from order book" + pendingOrders.get(market).get(TransactionType.BUY));
                                    // else
                                    //   log.error(this.getClass().getSimpleName() + ":updateBook - Buy order(" + order.hashCode() + ") fully filled "
                                    //         + order + " not removed from order book" + pendingOrders.get(market).get(TransactionType.BUY));
                                    // buyOrdersToBeRemoved.add(order);
                                    //itbpo.remove();
                                }
                                if (fill.getVolume() == null || (fill.getVolume() != null && fill.getVolume().isZero()))
                                    log.debug("fill " + fill.getId() + " zero lots " + (order.getUnfilledVolumeCount()));
                                if (fill.getVolume().abs().compareTo(order.getVolume().abs()) > 0)
                                    log.debug("overfilled " + fill.getId() + " " + (order.getUnfilledVolumeCount()));
                                handleFillProcessing(fill);
                                // fills.add(fill);
                                //TODO Update the volume on the bid/ask to remove the filled volume and if it is zero remove it from book.
                                // bidRemainingVolume -= order.getUnfilledVolumeCount();;
                                // so we are setting remaining volume to 37 then back to 62, 

                                //  if (bidRemainingVolume > order.getUnfilledVolumeCount())
                                //    log.error("overfilled" + order.getUnfilledVolumeCount() + " " + order.getUnfilledVolumeCount());

                                //  logFill(order, ask, fill);
                                if (order.getUnfilledVolumeCount() == 0) {

                                    //order is fully filled so move onto next order
                                    continue BIDORDERSLOOP;
                                }
                            }

                        }
                        //  }

                    }
                }
                log.trace(this.getClass().getSimpleName() + ":updateBook - looped over buy pending orders. Removing filled orders " + buyOrdersToBeRemoved);

                //}
            } catch (Exception e) {
                log.error(
                        this.getClass().getSimpleName() + ": updateBook - Unable to itterate over order book "
                                + pendingOrders.get(market).get(TransactionType.BUY) + " stack trace: ", e);
            }
        }

        if (pendingOrders != null && pendingOrders.get(market) != null && pendingOrders.get(market).get(TransactionType.SELL) != null) {
            log.trace(this.getClass().getSimpleName() + ":updateBook - looping over sell pending orders " + pendingOrders.get(market).get(TransactionType.SELL));
            ArrayList<SpecificOrder> sellOrdersToBeRemoved = new ArrayList<SpecificOrder>();

            try {
                synchronized (pendingOrders.get(market).get(TransactionType.SELL)) {
                    //    log.debug(this.getClass().getSimpleName() + ":updateBook locking sell updateOrderBookLock");

                    //  updateOrderBookLock.lock();
                    ASKORDERSLOOP: for (Iterator<SpecificOrder> itspo = pendingOrders.get(market).get(TransactionType.SELL).iterator(); itspo.hasNext();) {
                        SpecificOrder order = itspo.next();
                        //       synchronized (order) {
                        //    for (SpecificOrder pendingOrder : pendingOrders.get(market).get(TransactionType.SELL)) {

                        //    Iterator<SpecificOrder> itspo = pendingOrders.get(market).get(TransactionType.SELL).iterator();
                        //  ASKORDERSLOOP: while (itspo.hasNext()) {

                        //   SpecificOrder pendingOrder = itspo.next();

                        if (order.getUnfilledVolumeCount() == 0) {
                            log.debug(this.getClass().getSimpleName() + ":updateBook - removing sell order(" + order.hashCode() + ") " + order
                                    + " from order book " + pendingOrders.get(market).get(TransactionType.SELL));
                            // pendingOrders.get(market).get(TransactionType.SELL).remove(order);
                            itspo.remove();
                            continue ASKORDERSLOOP;
                        }

                        /*   if (order.getUnfilledVolumeCount() == 0) {
                               order.getUnfilledVolumeCount();
                               //sellOrdersToBeRemoved.add(order);
                               itspo.remove();
                               continue;
                           }*/
                        if (order.getMarket().equals(market) && (order.getTimestamp() <= event.getTimestamp())
                                && pendingOrders.get(market).get(TransactionType.SELL).contains(order)) {
                            //  long askRemainingVolume = order.getUnfilledVolumeCount(); // this will be negative
                            log.trace(this.getClass().getSimpleName() + ":UpdateBook - determining fills for sell order " + order.getId()
                                    + " with working volume " + order.getUnfilledVolumeCount());

                            // we need to thingk about maker and taker
                            //which side of the book do we hit
                            BIDSLOOP: for (Offer bid : bids) {
                                if (order.getLimitPrice() == null)
                                    log.debug("null limit");
                                //   long bidPriceCount = bid.getPriceCount() - slippageDiff;
                                if (order.getTimestamp() > bid.getTimestamp()) {
                                    log.trace(this.getClass().getSimpleName() + ":UpdateBook - bid " + bid + " older than order " + order);

                                    continue BIDSLOOP;
                                }
                                if (bid == null || (order.getLimitPrice() != null && bid != null && order.getLimitPrice().getCount() > bid.getPriceCount())) {

                                    //|| bid.getVolumeCount() == 0 || bid.getPriceCount() == 0)
                                    log.trace(this.getClass().getSimpleName() + ":UpdateBook - bid price " + bid + " greater than limit price "
                                            + order.getLimitPrice() + " for order " + order);

                                    break ASKORDERSLOOP;
                                }
                                // synchronized (lock) {
                                if (t != null) {
                                    log.debug("filled by a trade");

                                }
                                long askFillVolume = -Math.min(Math.abs(bid.getVolumeCount()), Math.abs(order.getUnfilledVolumeCount()));
                                if (askFillVolume == 0) {
                                    log.trace(this.getClass().getSimpleName() + ":UpdateBook fill volume zero for bid" + bid + " and order " + order);

                                    continue BIDSLOOP;
                                }
                                long slippageDiff = Math.round(bid.getPriceCount() * slippage);
                                // I am selling so limit price is 100, current bid is 101, get filled at 101
                                //selling at 634.57, 63470
                                long fillPriceCount = Math.max(order.getLimitPrice().getCount(), (bid.getPriceCount() - slippageDiff));
                                log.debug(this.getClass().getSimpleName() + ":updateBook - Creating fill with bid " + bid);

                                Fill fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(), fillPriceCount, askFillVolume,
                                        Long.toString(bid.getTime().getMillis()));
                                logFill(order, bid, fill);
                                log.debug(this.getClass().getSimpleName() + ":UpdateBook - set bidVolume " + bid.getVolumeCount() + " to "
                                        + (bid.getVolumeCount() - askFillVolume));
                                bid.setVolumeCount(bid.getVolumeCount() - askFillVolume);
                                if (order.getUnfilledVolumeCount() == 0) {
                                    log.debug(this.getClass().getSimpleName() + ":updateBook - Sell order(" + order.hashCode() + ") fully filled " + order
                                            + " removing from order book" + pendingOrders.get(market).get(TransactionType.SELL));

                                    itspo.remove();
                                    //   if (pendingOrders.get(market).get(TransactionType.SELL).remove(order))
                                    log.debug(this.getClass().getSimpleName() + ":updateBook - Sell order(" + order.hashCode() + ") fully filled " + order
                                            + " removed from order book" + pendingOrders.get(market).get(TransactionType.SELL));
                                    // else
                                    //   log.error(this.getClass().getSimpleName() + ":updateBook - Sell order(" + order.hashCode() + ") fully filled "
                                    //         + order + " not removed from order book" + pendingOrders.get(market).get(TransactionType.SELL));
                                    //  sellOrdersToBeRemoved.add(order);
                                }

                                if (fill.getVolume() == null || (fill.getVolume() != null && fill.getVolume().isZero()))
                                    log.debug("fill zero lots " + fill.getId() + " " + (order.getUnfilledVolumeCount()));

                                if (fill.getVolume().abs().compareTo(order.getVolume().abs()) > 0)
                                    log.debug("overfilled " + fill.getId());
                                handleFillProcessing(fill);
                                // fills.add(fill);

                                //      log.debug(this.getClass().getSimpleName() + ":UpdateBook - setting askRemainingVolume to " + order.getUnfilledVolumeCount()
                                //            + " for fill volume " + askFillVolume + " for order " + order);

                                // askRemainingVolume -= askFillVolume;

                                if (order.getUnfilledVolumeCount() == 0) {

                                    continue ASKORDERSLOOP;
                                }
                            }
                        }
                        //   }

                    }
                }

            } catch (Exception e) {
                log.error(
                        this.getClass().getSimpleName() + ": addOrder - Unable to itterate over order book "
                                + pendingOrders.get(market).get(TransactionType.SELL) + " stack trace: ", e);
            }
        }

    }

    @SuppressWarnings("finally")
    @Override
    protected boolean cancelSpecificOrder(SpecificOrder order) {
        boolean deleted = false;
        if (!orderStateMap.get(order).isOpen()) {
            log.error("Unable to cancel order as is " + orderStateMap.get(order) + " :" + order);
            deleted = true;
            return deleted;

        }

        try {
            if (pendingOrders == null || pendingOrders.get(order.getMarket()) == null
                    || pendingOrders.get(order.getMarket()).get(order.getTransactionType()) == null)
                return deleted;
            log.trace(this.getClass().getSimpleName() + ":cancelSpecificOrder - removing order(" + order.hashCode() + ") " + order + " from orderbook "
                    + pendingOrders.get(order.getMarket()).get(order.getTransactionType()));

            // synchronized (pendingOrders.get(order.getMarket()).get(order.getTransactionType())) {
            //   } catch (Exception e) {
            //     log.error(this.getClass().getSimpleName() + ": addOrder - Unable to itterate over order book " + pendingOrders.get(market).get(TransactionType.BUY) + " stack trace: ", e);
            // } finally {
            //   updateOrderBookLock.unlock();

            //}  
            //      log.debug(this.getClass().getSimpleName() + ":cancelSpecificOrder Locking updateOrderBookLock");
            //    updateOrderBookLock.lock();

            synchronized (pendingOrders.get(order.getMarket()).get(order.getTransactionType())) {
                if (pendingOrders.get(order.getMarket()).get(order.getTransactionType()).remove(order)) {
                    log.debug(this.getClass().getSimpleName() + ":cancelSpecificOrder - removed order(" + order.hashCode() + ") " + order + " from orderbook "
                            + pendingOrders.get(order.getMarket()).get(order.getTransactionType()));

                    updateOrderState(order, OrderState.CANCELLED, true);

                    deleted = true;

                } else {
                    //       if (!pendingOrders.get(order.getMarket()).get(order.getTransactionType()).contains(order)) {
                    log.error("Unable to cancel order as not present in mock order book. Order:" + order + " order book "
                            + pendingOrders.get(order.getMarket()).get(order.getTransactionType()));
                    updateOrderState(order, OrderState.REJECTED, true);
                    deleted = true;
                }
            }

            //  }
            //   }
            // }
        } catch (Error | Exception e) {
            log.error("Unable to cancel order :" + order + ". full stack trace", e);

        } finally {
            //log.debug(this.getClass().getSimpleName() + ":cancelSpecificOrder unlocking updateOrderBookLock");

            //  updateOrderBookLock.unlock();

            return deleted;
        }

    }

    private synchronized void addOrder(SpecificOrder order) {
        //   targetDiscrete = (DiscreteAmount) (limitPrice.minus(new DiscreteAmount((long) (targetPrice), market.getPriceBasis()))).abs();
        //   targetDiscrete = (DiscreteAmount) (limitPrice.minus(new DiscreteAmount((long) (targetPrice), market.getPriceBasis()))).abs();

        try {
            //   log.debug(this.getClass().getSimpleName() + ":addOrder Locking updateOrderBookLock");

            //   updateOrderBookLock.lock();

            if (pendingOrders.get(order.getMarket()) == null
                    || (pendingOrders.get(order.getMarket()) != null && pendingOrders.get(order.getMarket()).isEmpty())) {
                ConcurrentSkipListSet<SpecificOrder> orders = new ConcurrentSkipListSet<SpecificOrder>(order.isBid() ? descendingPriceComparator
                        : ascendingPriceComparator);

                //  ArrayList<SpecificOrder> orders = new ArrayList<SpecificOrder>();
                orders.add(order);
                ConcurrentHashMap<TransactionType, ConcurrentSkipListSet<SpecificOrder>> orderBook = new ConcurrentHashMap<TransactionType, ConcurrentSkipListSet<SpecificOrder>>();
                orderBook.put(order.getTransactionType(), orders);
                pendingOrders.put(order.getMarket(), orderBook);
                log.trace(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + "): " + order + " added to mock order book "
                        + pendingOrders.get(order.getMarket()).get(order.getTransactionType()));
                return;

            } else if (pendingOrders.get(order.getMarket()).get(order.getTransactionType()) == null
                    || (pendingOrders.get(order.getMarket()).get(order.getTransactionType()) != null && pendingOrders.get(order.getMarket())
                            .get(order.getTransactionType()).isEmpty())) {
                ConcurrentSkipListSet<SpecificOrder> orders = new ConcurrentSkipListSet<SpecificOrder>(order.isBid() ? descendingPriceComparator
                        : ascendingPriceComparator);
                orders.add(order);
                pendingOrders.get(order.getMarket()).put(order.getTransactionType(), orders);
                log.trace(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + "): " + order + " added to mock order book "
                        + pendingOrders.get(order.getMarket()).get(order.getTransactionType()));
                return;

            } else {
                //      synchronized (pendingOrders.get(order.getMarket()).get(order.getTransactionType())) {
                if (pendingOrders.get(order.getMarket()).get(order.getTransactionType()).add(order)) {
                    //orderBook.add(order);
                    // Comparator<SpecificOrder> bookComparator = order.isBid() ? bidComparator : askComparator;

                    //   Collections.sort(pendingOrders.get(order.getMarket()).get(order.getTransactionType()), order.isBid() ? bidComparator : askComparator);
                    log.trace(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + "):  -" + order + " added to mock order book "
                            + pendingOrders.get(order.getMarket()).get(order.getTransactionType()));

                    return;
                } else {
                    log.error(this.getClass().getSimpleName() + ":addOrder(" + order.hashCode() + ") -" + order + " unable to add order to mock order book "
                            + pendingOrders.get(order.getMarket()).get(order.getTransactionType()));
                    pendingOrders.get(order.getMarket()).get(order.getTransactionType()).add(order);
                }
                //       }

                // askComparator
            }

        } catch (Exception e) {
            log.error(this.getClass().getSimpleName() + ": addOrder - Unable to add order " + order + "stack trace: ", e);
        } finally {
            //       log.debug(this.getClass().getSimpleName() + ":addOrder Unlocking updateOrderBookLock");
            //     updateOrderBookLock.unlock();

        }
        // }
        //  }

    }

    private void logFill(SpecificOrder order, Offer offer, Fill fill) {
        //  if (log.isDebugEnabled())
        if (order != null && offer != null && fill != null)
            log.info("Mock fill of Order " + order + " with Offer " + offer + ": " + fill);
    }

    // private static Object lock = new Object();
    // private static ConcurrentHashMap<Market, ConcurrentHashMap<TransactionType, ArrayList<SpecificOrder>>> pendingOrders = new ConcurrentHashMap<Market, ConcurrentHashMap<TransactionType, ArrayList<SpecificOrder>>>();
    private static transient ConcurrentHashMap<Market, ConcurrentHashMap<TransactionType, ConcurrentSkipListSet<SpecificOrder>>> pendingOrders = new ConcurrentHashMap<Market, ConcurrentHashMap<TransactionType, ConcurrentSkipListSet<SpecificOrder>>>();

    //new ConcurrentSkipListSet<>
    //  new ConcurrentLinkedQueue<SpecificOrder>();

    // new CopyOnWriteArrayList<SpecificOrder>();

    //private QuoteService quotes;

    //  @Override

    // }

    @Override
    public void init() {
        Set<org.cryptocoinpartners.schema.Order> cointraderOpenOrders = new HashSet<org.cryptocoinpartners.schema.Order>();

        super.init();
        // Once we have all the order loaded, let's add all the open specific orders to the mock order book (pendingOrders)
        //if (stateOrderMap.get(OrderState.NEW) != null)
        //    cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.NEW));
        if (stateOrderMap.get(OrderState.PLACED) != null)
            cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PLACED));
        if (stateOrderMap.get(OrderState.PARTFILLED) != null)
            cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PARTFILLED));
        if (stateOrderMap.get(OrderState.ROUTED) != null)
            cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.ROUTED));
        if (stateOrderMap.get(OrderState.CANCELLING) != null)
            cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.CANCELLING));
        for (org.cryptocoinpartners.schema.Order openOrder : cointraderOpenOrders) {
            if (openOrder instanceof SpecificOrder)
                addOrder((SpecificOrder) openOrder);
        }
    }

    @Override
    protected OrderState getOrderStateFromOrderService(Order order) throws Throwable {
        // so let's get order from database
        log.debug(this.getClass().getSimpleName() + ":getOrderStateFromOrderService - Loading order update from DB for " + order);
        OrderUpdate orderUpdate = EM.namedQueryOne(OrderUpdate.class, "orderUpdate.findStateByOrder", order);
        log.debug(this.getClass().getSimpleName() + ":getOrderStateFromOrderService - Loaded order update " + orderUpdate);
        if (orderUpdate != null)
            return orderUpdate.getState();
        else
            return null;

    }

    @Override
    public void updateWorkingOrderQuantity(Order order, Amount quantity) {
        // TODO Auto-generated method stub

    }

}
