package org.cryptocoinpartners.module;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.util.FeesUtil;
import org.cryptocoinpartners.util.Remainder;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * This simple Strategy first waits for Book data to arrive about the target Market, then it places a buy order
 * at demostrategy.spread below the current bestAsk.  Once it enters the trade, it places a sell order at
 * demostrategy.spread above the current bestBid.
 * This strategy ignores the available Positions in the Portfolio and always trades the amount set by demostrategy.volume on
 * the Market specified by demostrategy.market
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class MarketMakerStrategy extends TestStrategy {

    static double percentEquityRisked = 0.0001;
    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    static double atrStop = 1;
    static double atrTarget = 10;
    //* atrStop;
    static long slippage = 1;
    static long minSpread = 10;
    static double maxLossTarget = 0.0005;
    private DiscreteAmount lastAsk;
    private DiscreteAmount lastBid;
    private Amount lastHighAsk;
    private Amount lastLowBid;
    private Amount lastShortExitLimit;
    private Amount lastLongExitLimit;
    private Amount lastShortEntryLimit;
    private Amount lastLongEntryLimit;
    private static ExecutorService service = Executors.newFixedThreadPool(1);

    //double maxLossTarget = 0.25;

    @Inject
    public MarketMakerStrategy(Context context, Configuration config, Market market) {
        super(context, config);
        String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        market = (Market) Market.forSymbol(marketSymbol);
        setPercentEquityRisked(percentEquityRisked);
        setAtrStop(atrStop);
        setAtrTarget(atrTarget);
        setMinSpread(minSpread);
        setSlippage(slippage);
        setMaxLossTarget(maxLossTarget);

        setTrendInterval(900);
        setRunsInterval(900);

    }

    private class handleMarketMakerRunnable implements Runnable {
        private final Book book;
        private final Market market;

        // protected Logger log;

        public handleMarketMakerRunnable(Market market, Book book) {
            this.book = book;
            this.market = market;

        }

        @Override
        public void run() {
            makeMarket(market, book);

        }
    }

    // when ask is rising enter long (SMA>LSMA)

    //  @When("@Priority(3) on LongAskMovIndicator as trigger select trigger.high from LongAskMovIndicator")
    private void handleLongEntry(Market market, Double d) {
        Book b = quotes.getLastBook(market);
        if (b == null)
            return;
        //so if the limit price < current best bid cancel replace  longs     so if limit price > ask price, cancel replace for shorts
        cancellationService.submit(new handleCancelAllShortOpeningSpecificOrders(portfolio, market));

        if ((lastLongEntryLimit == null || lastLongEntryLimit.compareTo(b.getBestBid().getPrice()) < 0 || orderService.getPendingLongOpenOrders(portfolio,
                market).isEmpty())
                && (lastBid == null || (lastBid != b.getBestBid().getPrice() || orderService.getPendingLongOpenOrders(portfolio, market).isEmpty()))) {

            // if ((postion == null || postion.isFlat())) {
            cancellationService.submit(new handleCancelAllLongOpeningSpecificOrders(portfolio, market));

            if (positionMap.get(market) == null || positionMap.get(market).get(interval).getType() != PositionType.ENTERING) {
                createLongOpenOrders(market, b);
            }
        }
    }

    private void createLongOpenOrders(Market market, Book b) {

        // log.info("Long High Indicator Recived");
        //double tragetPrice = d * atrTarget;
        // double tragetPrice = bestBid.getPriceCount() - (tragetAmount * priceScaling);
        // DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), getMarket().getPriceBasis());

        //super.handleLongHighIndicator(targetDiscrete.getCount(), ExecutionInstruction.MAKER);

        //service.submit(new handleMarketMakerRunnable(b));

        if (!orderService.getPendingLongOpenOrders(portfolio, market).isEmpty())
            return;

        updatePositionMap(market, interval, PositionType.ENTERING);

        //set volume to minumum of current best bid/ask
        DiscreteAmount volume = ((b.getBestAsk().getVolume()).compareTo(b.getBestBid().getVolume()) < 0) ? b.getBestBid().getVolume() : b.getBestAsk()
                .getVolume();

        // buildEnterLongOrders bidBuilder = new buildEnterLongOrders(0, ExecutionInstruction.TAKER);

        ArrayList<Order> longOrderList = new ArrayList<Order>();
        //    bidBuilder.buildEnterLongOrders();
        if (longOrderList != null) {

            Order bidOrder = null;
            for (Order order : longOrderList)
                bidOrder = order;
            try {
                orderService.placeOrder(bidOrder);
            } catch (Throwable e) {
                log.error("Unable to place bid order" + bidOrder);
            }
            lastLongEntryLimit = bidOrder.getLimitPrice();

        } else {
            revertPositionMap(market, interval);
        }
    }

    // @When("@Priority(3) on LongBidMovIndicator as trigger select trigger.low from LongBidMovIndicator")
    private void handleShortEntry(Market market, Double d) {
        Book b = quotes.getLastBook(market);
        if (b == null)
            return;
        cancellationService.submit(new handleCancelAllLongOpeningSpecificOrders(portfolio, market));
        // so if limit price > ask price, cancel replace
        // if ask price (price I can buy at/other can sell at) if I go long, I hit the ask. I put a limt of 100 to sell, if the price falls, then I need to replace 100<20
        if ((lastShortEntryLimit == null || lastShortEntryLimit.compareTo(b.getBestAsk().getPrice()) > 0 || orderService.getPendingShortOpenOrders(portfolio,
                market).isEmpty())
                && (lastAsk == null || (lastAsk != b.getBestAsk().getPrice() || orderService.getPendingShortOpenOrders(portfolio, market).isEmpty()))) {

            // if ((postion == null || postion.isFlat())) {
            cancellationService.submit(new handleCancelAllShortOpeningSpecificOrders(portfolio, market));

            if (positionMap.get(market) == null || positionMap.get(market).get(interval).getType() != PositionType.ENTERING) {
                createShortOpenOrders(market, b);
            }
        }

    }

    private void createShortOpenOrders(Market market, Book b) {

        if (!orderService.getPendingShortOpenOrders(portfolio, market).isEmpty())
            return;

        updatePositionMap(market, interval, PositionType.ENTERING);

        // buildEnterShortOrders askBuilder = new buildEnterShortOrders(0, ExecutionInstruction.TAKER);

        ArrayList<Order> shortOrderList = new ArrayList<Order>();
        // askBuilder.buildEnterShortOrders();
        if (shortOrderList != null) {

            Order askOrder = null;

            for (Order order : shortOrderList)
                askOrder = order;

            try {
                orderService.placeOrder(askOrder);
            } catch (Throwable e) {
                log.error("Unable to place ask order" + askOrder);

            }
            lastShortEntryLimit = askOrder.getLimitPrice();

            //   }
        } else {

        }

    }

    // when bid is failing enter short (SAM<LSMA)

    // @Whe=n("@Priority(4) select * from Book")
    //(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    ///@When("select talib(\"rsi\", askPriceAsDouble, 14) as value from Book")
    // Strategy is 2.5 times more profitable without shifting stops

    @When("@Priority(2) select * from Book(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    public void handleMakeMarket(Market market, Book b) {

        //        if (getPositions(market) != null && !getPositions(market).isEmpty() && positionMap.get(market).get(interval).getType() != PositionType.EXITING) {
        //
        //            createExits(b);
        //        }
        //
        //        //  }
        //
        //        // }
        //
        //        //  }
        //        lastAsk = b.getBestAsk().getPrice();
        //        lastBid = b.getBestBid().getPrice();
        makeMarket(market, b);

        //

        //service.submit(new handleMarketMakerRunnable(b));
    }

    //@When("@Priority(2) select * from Book(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    public void handleBook(Market market, Book b) {

        if (getPositions(market) != null && !getPositions(market).isEmpty() && positionMap.get(market).get(interval).getType() != PositionType.EXITING) {

            createExits(market, b);
        }

        //  }

        // }

        //  }
        lastAsk = b.getBestAsk().getPrice();
        lastBid = b.getBestBid().getPrice();
        makeMarket(market, b);

        //

        //service.submit(new handleMarketMakerRunnable(b));
    }

    void createOpenLongOrders(Market market, Book b) {

        if (!orderService.getPendingOrders(portfolio, market).isEmpty())
            return;

        updatePositionMap(market, interval, PositionType.ENTERING);

        //set volume to minumum of current best bid/ask
        // we will join current ask
        // buying leg places at bid

        // the volume will already be negative for a sell order

        //   if ((bid.minus(ask.decrement(2))).compareTo(commission) < 0) {
        // only enter orders when spread is wide enough
        //bidOrderBuilder

        // buildEnterLongOrders()
        //   buildEnterLongOrders bidBuilder = new buildEnterLongOrders(0, ExecutionInstruction.TAKER);

        ArrayList<Order> longOrderList = new ArrayList<Order>();
        //bidBuilder.buildEnterLongOrders();
        if (longOrderList != null) {

            Order bidOrder = null;
            for (Order order : longOrderList)
                bidOrder = order;

            //OrderBuilder.SpecificOrderBuilder bidOrderBuilder = new OrderBuilder(portfolio).create(context.getTime(), market, ammount, "OCO Long Entry");

            // Order bidOrder = bidOrderBuilder.getOrder();
            // askOrder = askOrderBuilder.getOrder();
            //  askOrder.persit();
            // bidOrder.persit();
            try {
                orderService.placeOrder(bidOrder);
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                log.error("Unable to place ask order" + bidOrder);
            }

            //   }
        } else {
            revertPositionMap(market, interval);
        }

    }

    void createOpenShortOrders(Market market, Book b) {

        if (!orderService.getPendingOrders(portfolio, market).isEmpty())
            return;

        updatePositionMap(market, interval, PositionType.ENTERING);

        //so seleling at the bid

        //selling leg placed at ask
        // buying leg places at bid

        // the volume will already be negative for a sell order

        //   if ((bid.minus(ask.decrement(2))).compareTo(commission) < 0) {
        // only enter orders when spread is wide enough
        //bidOrderBuilder

        // buildEnterLongOrders()
        //  buildEnterShortOrders askBuilder = new buildEnterShortOrders(0, ExecutionInstruction.TAKER);

        ArrayList<Order> shortOrderList = new ArrayList<Order>();
        //askBuilder.buildEnterShortOrders();
        if (shortOrderList != null) {

            Order askOrder = null;

            for (Order order : shortOrderList)
                askOrder = order;

            //OrderBuilder.SpecificOrderBuilder bidOrderBuilder = new OrderBuilder(portfolio).create(context.getTime(), market, ammount, "OCO Long Entry");

            //  OrderBuilder.SpecificOrderBuilder askOrderBuilder = new OrderBuilder(portfolio).create(context.getTime(), market, ammount.negate(),
            //        "OCO Short Entry");

            // Order bidOrder = bidOrderBuilder.getOrder();
            // askOrder = askOrderBuilder.getOrder();
            //  askOrder.persit();
            // bidOrder.persit();
            try {
                orderService.placeOrder(askOrder);
            } catch (Throwable e) {

            }

            //   }
        } else {
            revertPositionMap(market, interval);
        }

    }

    void createOpenOrders(Market market, Book b) {

        if (!orderService.getPendingOrders(portfolio, market).isEmpty())
            return;

        updatePositionMap(market, interval, PositionType.ENTERING);

        //selling leg placed at ask
        // buying leg places at bid

        // the volume will already be negative for a sell order

        //   if ((bid.minus(ask.decrement(2))).compareTo(commission) < 0) {
        // only enter orders when spread is wide enough
        //bidOrderBuilder

        // buildEnterLongOrders()
        //   buildEnterLongOrders bidBuilder = new buildEnterLongOrders(0, ExecutionInstruction.MAKER);

        ArrayList<Order> longOrderList = new ArrayList<Order>();
        //            bidBuilder.buildEnterLongOrders();
        //   buildEnterShortOrders askBuilder = new buildEnterShortOrders(0, ExecutionInstruction.MAKER);

        ArrayList<Order> shortOrderList = new ArrayList<Order>();
        //           askBuilder.buildEnterShortOrders();
        if (shortOrderList != null && longOrderList != null) {

            Order bidOrder = null;
            for (Order order : longOrderList)
                bidOrder = order;
            Order askOrder = null;

            for (Order order : shortOrderList)
                askOrder = order;

            //OrderBuilder.SpecificOrderBuilder bidOrderBuilder = new OrderBuilder(portfolio).create(context.getTime(), market, ammount, "OCO Long Entry");
            bidOrder.withFillType(FillType.ONE_CANCELS_OTHER);

            //  OrderBuilder.SpecificOrderBuilder askOrderBuilder = new OrderBuilder(portfolio).create(context.getTime(), market, ammount.negate(),
            //        "OCO Short Entry");
            askOrder.withFillType(FillType.ONE_CANCELS_OTHER);

            // Order bidOrder = bidOrderBuilder.getOrder();
            // askOrder = askOrderBuilder.getOrder();
            //  askOrder.persit();
            // bidOrder.persit();
            bidOrder.addChildOrder(askOrder);
            //askOrder.addChild(askOrder);
            // bidOrder.setParentOrder(askOrder);
            askOrder.setParentOrder(bidOrder);
            try {
                orderService.placeOrder(bidOrder);
                orderService.placeOrder(askOrder);

            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            //   }
        } else {
            revertPositionMap(market, interval);
        }

    }

    void createLongExit(Market market, Book b) {
        Amount highAsk = null;
        Amount lowBid = null;
        Order askOrder = null;
        Order stopAskOrder = null;
        DiscreteAmount limitPrice = null;
        for (Position position : getPositions(market)) {
            if (position.isLong())
                for (Fill positionFill : position.getFills()) {
                    if (positionFill.isLong() && positionFill.getPositionEffect() == PositionEffect.OPEN && positionFill.getOpenVolume().isPositive()) {
                        if (highAsk == null)
                            highAsk = positionFill.getPrice();
                        highAsk = (positionFill.getPrice().compareTo(highAsk) > 0) ? positionFill.getPrice() : highAsk;
                        // get highest price cos I need to exit for this or more
                        // highAsk
                    } else if (positionFill.isShort() && positionFill.getPositionEffect() == PositionEffect.OPEN && positionFill.getOpenVolume().isNegative()) {
                        // get lowest price cos I need to exit for this or less
                        if (lowBid == null)
                            lowBid = positionFill.getPrice();
                        lowBid = (positionFill.getPrice().compareTo(lowBid) < 0) ? positionFill.getPrice() : lowBid;

                    }

                }
        }
        if (positionMap.get(market) == null || (getNetPosition(market, interval).getLongVolume().isPositive())) {

            //&& positionMap.get(market).get(interval).getType() != PositionType.EXITING)) {
            //  cancellationService.submit(new handleCancelAllLongClosingSpecificOrders(portfolio, market, ExecutionInstruction.MAKER));

            for (Position position : getPositions(market)) {
                if (position.isLong())
                    positionLoop: for (Fill positionFill : position.getFills()) {
                        updatePositionMap(market, interval, PositionType.EXITING);

                        Collection<GeneralOrder> longStopOrders = new ArrayList<GeneralOrder>();
                        askOrder = null;
                        Amount highestLongClosingLimit = null;
                        Collection<SpecificOrder> longClosingOrders = new ArrayList<SpecificOrder>();

                        ArrayList<Order> childOrders = new ArrayList<>();
                        positionFill.getAllOrdersByParentFill(childOrders);
                        for (Order stopOrder : childOrders) {
                            if (stopOrder instanceof GeneralOrder && stopOrder.getPositionEffect() == PositionEffect.CLOSE && stopOrder.getStopPrice() != null
                                    && !orderService.getOrderState(stopOrder).isCancelled()) {
                                longStopOrders.add((GeneralOrder) stopOrder);

                            } else if (stopOrder instanceof SpecificOrder && stopOrder.getPositionEffect() == PositionEffect.CLOSE
                                    && orderService.getOrderState(stopOrder).isOpen()) {
                                longClosingOrders.add((SpecificOrder) stopOrder);

                            }
                        }

                        if (longClosingOrders != null && !longClosingOrders.isEmpty())

                            for (SpecificOrder longClosingOrder : longClosingOrders) {
                                if (highestLongClosingLimit == null)
                                    highestLongClosingLimit = longClosingOrder.getLimitPrice();

                                highestLongClosingLimit = (longClosingOrder.getLimitPrice().compareTo(highestLongClosingLimit) > 0) ? longClosingOrder
                                        .getLimitPrice() : highestLongClosingLimit;
                            }

                        if (positionFill.isLong() && positionFill.getPositionEffect() == PositionEffect.OPEN && position.getLongVolume().isPositive()

                        ) {
                            Amount commission = FeesUtil.getCommission(highAsk, positionFill.getOpenVolume(), position.getMarket(), PositionEffect.OPEN);
                            Amount breakEvenPrice = (highAsk.invert().plus(commission.dividedBy(
                                    positionFill.getOpenVolume().times(market.getContractSize(market), Remainder.ROUND_EVEN), Remainder.ROUND_EVEN))).invert();
                            DiscreteAmount askPrice = (new DiscreteAmount(
                                    (breakEvenPrice.asBigDecimal().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue()), market.getPriceBasis()))
                                    .increment(2);

                            limitPrice = (askPrice.compareTo(b.getBestAsk().getPrice().decrement()) > 0) ? askPrice : b.getBestAsk().getPrice().decrement();

                            if (limitPrice.compareTo(positionFill.getPrice()) < 0)
                                log.debug("loss making trade");// cancel corresponng stop order
                            for (GeneralOrder stopOrder : longStopOrders) {
                                OrderState stopOrderState = (orderService.getOrderState(stopOrder));

                                Order orderchk = orderService.getPendingTriggerOrder(stopOrder);
                                GeneralOrder longStopOrder = stopOrder;
                                //                                if ((orderchk != null && stopOrderState.isCancelled()) || (orderchk == null && !stopOrderState.isCancelled())) {
                                //                                    log.info("Stop Order : " + stopOrder + " has order state of : " + stopOrderState + " and pending order of " + orderchk);
                                //                                    orderchk = orderService.getPendingTriggerOrder(stopOrder);
                                //                                    stopOrderState = (orderService.getOrderState(stopOrder));
                                //                                }

                            }

                            if (highestLongClosingLimit == null || limitPrice.compareTo(highestLongClosingLimit) < 0) {
                                if (longClosingOrders != null && !longClosingOrders.isEmpty()) {
                                    for (SpecificOrder longClosingOrder : longClosingOrders) {
                                        if (orderService.getOrderState(longClosingOrder).isOpen()) {
                                            // we already have a working order out there for this fill, so lets move onto the next fill
                                            revertPositionMap(market, interval);
                                            continue positionLoop;
                                        }
                                    }
                                    GeneralOrder restingLongStopOrder = null;
                                    GeneralOrder longStopOrder = null;
                                    if (longStopOrders != null && !longStopOrders.isEmpty()) {
                                        boolean childrenCancelled = true;

                                        for (GeneralOrder stopOrder : longStopOrders) {
                                            if (orderService.getOrderState(stopOrder).isOpen())
                                                restingLongStopOrder = stopOrder;
                                            else {
                                                OrderState orderState = orderService.getOrderState(stopOrder);
                                                longStopOrder = stopOrder;
                                            }

                                        }

                                        if (restingLongStopOrder != null) {
                                            askOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    restingLongStopOrder, "Replacment Exit Long with resting stop");

                                            askOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                    .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                    .withParentFill(positionFill);

                                            restingLongStopOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);
                                        } else if (longStopOrder != null) {
                                            OrderState orderState = orderService.getOrderState(longStopOrder);

                                            stopAskOrder = longStopOrder;
                                            askOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    stopAskOrder, "Replacment Exit Long resubmitted stop");
                                            askOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                    .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                    .withParentFill(positionFill);
                                            stopAskOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);

                                        } else {
                                            askOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    "Replacement Exit Long No Stop");
                                            askOrder.withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(ExecutionInstruction.TAKER)
                                                    .withFillType(FillType.MARKET).withParentFill(positionFill);
                                            askOrder.setParentFill(positionFill);

                                            positionFill.addChildOrder(askOrder);
                                        }

                                    } else {
                                        askOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                "Replacement Exit Long No Stop");
                                        askOrder.withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(ExecutionInstruction.TAKER)
                                                .withFillType(FillType.MARKET).withParentFill(positionFill);
                                        positionFill.addChildOrder(askOrder);
                                    }

                                } else {
                                    GeneralOrder restingLongStopOrder = null;
                                    GeneralOrder longStopOrder = null;

                                    for (GeneralOrder stopOrder : longStopOrders)
                                        if (orderService.getOrderState(stopOrder).isOpen())
                                            restingLongStopOrder = stopOrder;
                                        else
                                            longStopOrder = stopOrder;

                                    if (restingLongStopOrder != null) {
                                        askOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                restingLongStopOrder, "New Exit Long with resting stop");
                                        askOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                .withParentFill(positionFill);

                                        restingLongStopOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);
                                    } else if (longStopOrder != null) {
                                        OrderState orderState = orderService.getOrderState(longStopOrder);

                                        stopAskOrder = longStopOrder;
                                        askOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                stopAskOrder, "Replacment Exit Long resubmitted stop");
                                        askOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                .withParentFill(positionFill);

                                        stopAskOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);
                                    } else {

                                        askOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                "New Exit Long no resting Stop");
                                        askOrder.withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(ExecutionInstruction.TAKER)
                                                .withFillType(FillType.MARKET).withParentFill(positionFill);
                                        positionFill.addChildOrder(askOrder);
                                    }
                                }
                            }
                            if (stopAskOrder != null) {
                                log.info("resubmitting stop order  " + stopAskOrder);
                                try {
                                    orderService.placeOrder(stopAskOrder);
                                } catch (Throwable e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }

                            }
                            if (askOrder != null) {
                                log.info("Submitting new exit long order " + askOrder);

                                try {
                                    orderService.placeOrder(askOrder);
                                } catch (Throwable e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                lastLongExitLimit = limitPrice;
                            }
                        } else
                            revertPositionMap(market, interval);

                    }
                else
                    revertPositionMap(market, interval);

            }
        }
    }

    void createShortExit(Market market, Book b) {

        Amount highAsk = null;
        Amount lowBid = null;
        Order bidOrder = null;
        Order stopBidOrder = null;
        DiscreteAmount limitPrice = null;
        for (Position position : getPositions(market)) {
            if (position.isShort())
                for (Fill positionFill : position.getFills()) {
                    if (positionFill.isLong() && positionFill.getPositionEffect() == PositionEffect.OPEN && positionFill.getOpenVolume().isPositive()) {
                        if (highAsk == null)
                            highAsk = positionFill.getPrice();
                        highAsk = (positionFill.getPrice().compareTo(highAsk) > 0) ? positionFill.getPrice() : highAsk;
                        // get highest price cos I need to exit for this or more
                        // highAsk
                    } else if (positionFill.isShort() && positionFill.getPositionEffect() == PositionEffect.OPEN && positionFill.getOpenVolume().isNegative()) {
                        // get lowest price cos I need to exit for this or less
                        if (lowBid == null)
                            lowBid = positionFill.getPrice();
                        lowBid = (positionFill.getPrice().compareTo(lowBid) < 0) ? positionFill.getPrice() : lowBid;

                    }

                }
        }
        if (positionMap.get(market) == null || (getNetPosition(market, interval).getShortVolume().isNegative())) {
            //&& positionMap.get(market).get(interval).getType() != PositionType.EXITING)) {
            //}
            // cancellationService.submit(new handleCancelAllShortClosingSpecificOrders(portfolio, market, ExecutionInstruction.MAKER));

            for (Position position : getPositions(market)) {
                if (position.isShort())
                    positionLoop: for (Fill positionFill : position.getFills()) {

                        updatePositionMap(market, interval, PositionType.EXITING);
                        Collection<GeneralOrder> shortStopOrders = new ArrayList<GeneralOrder>();
                        bidOrder = null;
                        Collection<SpecificOrder> shortClosingOrders = new ArrayList<SpecificOrder>();
                        Amount lowestShortClosingLimit = null;

                        // currently we cancel the stop but never get filled on the other leg.

                        ArrayList<Order> childOrders = new ArrayList<>();
                        positionFill.getAllOrdersByParentFill(childOrders);

                        for (Order stopOrder : childOrders) {
                            if (stopOrder instanceof GeneralOrder && stopOrder.getPositionEffect() == PositionEffect.CLOSE && stopOrder.getStopPrice() != null
                                    && !orderService.getOrderState(stopOrder).isCancelled()) {
                                shortStopOrders.add((GeneralOrder) stopOrder);
                            } else if (stopOrder instanceof SpecificOrder && stopOrder.getPositionEffect() == PositionEffect.CLOSE
                                    && orderService.getOrderState(stopOrder).isOpen()) {
                                shortClosingOrders.add((SpecificOrder) stopOrder);
                            }
                        }

                        if (shortClosingOrders != null && !shortClosingOrders.isEmpty())
                            for (SpecificOrder shortClosingOrder : shortClosingOrders) {
                                if (lowestShortClosingLimit == null)
                                    lowestShortClosingLimit = shortClosingOrder.getLimitPrice();
                                lowestShortClosingLimit = (shortClosingOrder.getLimitPrice().compareTo(lowestShortClosingLimit) < 0) ? shortClosingOrder
                                        .getLimitPrice() : lowestShortClosingLimit;
                            }

                        if (positionFill.isShort() && positionFill.getPositionEffect() == PositionEffect.OPEN && position.getShortVolume().isNegative()
                                && positionFill.getOpenVolumeCount() != 0) {

                            Amount commission = FeesUtil.getCommission(lowBid, positionFill.getOpenVolume(), position.getMarket(), PositionEffect.OPEN);
                            Amount breakEvenPrice = (lowBid.invert().minus(commission.dividedBy(
                                    positionFill.getOpenVolume().negate().times(market.getContractSize(market), Remainder.ROUND_EVEN), Remainder.ROUND_EVEN)))
                                    .invert();

                            DiscreteAmount bidPrice = (new DiscreteAmount(
                                    (breakEvenPrice.asBigDecimal().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue()), market.getPriceBasis()))
                                    .decrement(2);
                            limitPrice = (bidPrice.compareTo(b.getBestBid().getPrice().increment()) < 0) ? bidPrice : b.getBestBid().getPrice().increment();

                            if (limitPrice.compareTo(positionFill.getPrice()) > 0)
                                log.debug("loss making trade");
                            for (GeneralOrder stopOrder : shortStopOrders) {
                                OrderState stopOrderState = (orderService.getOrderState(stopOrder));

                                Order orderchk = orderService.getPendingTriggerOrder(stopOrder);
                                //                                if ((orderchk != null && stopOrderState.isCancelled()) || (orderchk == null && !stopOrderState.isCancelled())) {
                                //                                    log.info("Stop Order : " + stopOrder + " has order state of : " + stopOrderState + " and pending order of " + orderchk);
                                //
                                //                                    orderchk = orderService.getPendingTriggerOrder(stopOrder);
                                //                                    stopOrderState = (orderService.getOrderState(stopOrder));
                                //                                }

                            }

                            if (lowestShortClosingLimit == null || limitPrice.compareTo(lowestShortClosingLimit) > 0) {
                                if (shortClosingOrders != null && !shortClosingOrders.isEmpty()) {
                                    for (SpecificOrder shortClosingOrder : shortClosingOrders) {
                                        if (orderService.getOrderState(shortClosingOrder).isOpen()) {
                                            revertPositionMap(market, interval);
                                            continue positionLoop;
                                        }
                                    }

                                    GeneralOrder restingShortStopOrder = null;
                                    GeneralOrder shortStopOrder = null;
                                    if (shortStopOrders != null && !shortStopOrders.isEmpty()) {
                                        boolean childrenCancelled = true;
                                        for (GeneralOrder stopOrder : shortStopOrders) {
                                            if (orderService.getOrderState(stopOrder).isOpen())
                                                restingShortStopOrder = stopOrder;
                                            else
                                                shortStopOrder = stopOrder;

                                        }

                                        if (restingShortStopOrder != null) {
                                            bidOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    restingShortStopOrder, "Exit Short with resting stop");
                                            bidOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                    .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                    .withParentFill(positionFill);

                                            restingShortStopOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);

                                        } else if (shortStopOrder != null) {
                                            stopBidOrder = shortStopOrder;
                                            OrderState orderState = orderService.getOrderState(shortStopOrder);
                                            bidOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    stopBidOrder, "Exit Short with resubmitted stop");
                                            bidOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                    .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                    .withParentFill(positionFill);

                                            stopBidOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);

                                        } else {
                                            bidOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    "Exit Short No Stop");

                                            bidOrder.withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(ExecutionInstruction.TAKER)
                                                    .withFillType(FillType.MARKET).withParentFill(positionFill);

                                            positionFill.addChildOrder(bidOrder);
                                        }

                                    } else {
                                        bidOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                "Exit Short no Stop");
                                        bidOrder.withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(ExecutionInstruction.TAKER)
                                                .withFillType(FillType.MARKET).withParentFill(positionFill);

                                        positionFill.addChildOrder(bidOrder);
                                    }

                                } else {
                                    if (shortStopOrders != null && !shortStopOrders.isEmpty()) {
                                        boolean childrenCancelled = true;
                                        GeneralOrder restingShortStopOrder = null;
                                        GeneralOrder shortStopOrder = null;

                                        for (GeneralOrder stopOrder : shortStopOrders) {
                                            if (orderService.getOrderState(stopOrder).isOpen())
                                                restingShortStopOrder = stopOrder;
                                            else {
                                                OrderState orderState = orderService.getOrderState(stopOrder);
                                                shortStopOrder = stopOrder;
                                            }

                                        }
                                        if (restingShortStopOrder != null) {
                                            bidOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    restingShortStopOrder, "Exit Short with resting stop");
                                            bidOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                    .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                    .withParentFill(positionFill);

                                            restingShortStopOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);
                                        } else if (shortStopOrder != null) {
                                            OrderState orderState = orderService.getOrderState(shortStopOrder);

                                            stopBidOrder = shortStopOrder;
                                            bidOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    shortStopOrder, "Exit Short with resubmitted stop");
                                            bidOrder.withLimitPrice(limitPrice).withPositionEffect(PositionEffect.CLOSE)
                                                    .withExecutionInstruction(ExecutionInstruction.MAKER).withFillType(FillType.COMPLETED_CANCELS_OTHER)
                                                    .withParentFill(positionFill);

                                            shortStopOrder.setFillType(FillType.COMPLETED_CANCELS_OTHER);

                                        } else {
                                            bidOrder = specificOrderFactory.create(context.getTime(), portfolio, market, positionFill.getOpenVolume().negate(),
                                                    "Exit No Stop Short");
                                            bidOrder.withPositionEffect(PositionEffect.CLOSE).withExecutionInstruction(ExecutionInstruction.TAKER)
                                                    .withFillType(FillType.MARKET).withParentFill(positionFill);
                                            positionFill.addChildOrder(bidOrder);

                                        }
                                    } else
                                        revertPositionMap(market, interval);
                                }
                            } else
                                revertPositionMap(market, interval);
                            try {
                                if (stopBidOrder != null) {
                                    log.info("resubmitting stop order  " + stopBidOrder);

                                    orderService.placeOrder(stopBidOrder);

                                }
                                if (bidOrder != null) {
                                    log.info("Submitting new short exit Order " + bidOrder);

                                    orderService.placeOrder(bidOrder);
                                    lastShortExitLimit = limitPrice;
                                }
                            } catch (Throwable e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                        } else
                            revertPositionMap(market, interval);

                    }

            }
        }
    }

    void makeMarket(Market market, Book b) {
        if ((lastAsk == null || lastBid == null) || lastAsk.compareTo(b.getBestAsk().getPrice()) > 0 || lastBid.compareTo(b.getBestBid().getPrice()) < 0) {

            // if ((postion == null || postion.isFlat())) {
            cancellationService.submit(new handleCancelAllOpeningSpecificOrders(portfolio, market));

            if (positionMap.get(market) == null || positionMap.get(market).get(interval).getType() != PositionType.ENTERING) {
                createOpenOrders(market, b);
            }
        }
        // if (getPosition(market) != null && getPosition(market).getLongVolume().isPositive()) {
        // if ((positionMap.get(market).get(interval).getType() == PositionType.SHORT || positionMap.get(market).get(interval).getType() == PositionType.LONG)) {

        //  if (postion.getLongVolume().isPositive())
        //  cancellationService.submit(new handleCancelAllLongClosingSpecificOrders(portfolio, market, ExecutionInstruction.MAKER));
        //if (postion.getShortVolume().isNegative())
        //cancellationService.submit(new handleCancelAllShortClosingSpecificOrders(portfolio, market, ExecutionInstruction.MAKER));

        //if (!orderService.getPendingCloseOrders(portfolio).isEmpty())
        //   return;

        //I want to join ask 

        // I want to loop over all long fills

        //     if (orderService.getPendingShortCloseOrders(portfolio, ExecutionInstruction.MAKER).isEmpty()
        //           && (orderService.getPendingLongCloseOrders(portfolio, ExecutionInstruction.MAKER).isEmpty())) {
        // if (orderService.getPendingLongCloseOrders(portfolio, ExecutionInstruction.MAKER).isEmpty())
        //createLongExit(b);
        //if (orderService.getPendingShortCloseOrders(portfolio, ExecutionInstruction.MAKER).isEmpty())
        //createShortExit(b);
        if (getPositions(market) != null && !getPositions(market).isEmpty() && positionMap.get(market).get(interval).getType() != PositionType.EXITING) {

            createExits(market, b);
        }

        //  }

        // }

        //  }
        lastAsk = b.getBestAsk().getPrice();
        lastBid = b.getBestBid().getPrice();

    }

    //|| ((b.getBestBid().getPrice() != (lastBid) || b.getBestAsk().getPrice() != (lastAsk)))) {
    // if ((b.getBestBid().getPrice().equals(lastBid) || b.getBestAsk().getPrice().equals(lastAsk)))
    //   return;
    // lets cancel our orders and replace them

    //super.handleBook(b);

    //exit long
    //@When("@Priority(4) on ShortHighRunBarIndicator as trigger select trigger.high from ShortHighRunBarIndicator")
    //  @When("@Priority(4) on ShortLowMovIndicator as trigger select trigger.low from ShortLowMovIndicator")
    void handleRunsShortLowIndicator(Market market, double d) {
        super.handleShortLowIndicator(1.0, d, ExecutionInstruction.TAKER, market, null, false, false);
    }

    // exit short
    // @When("@Priority(4) on ShortHighMovIndicator as trigger select trigger.high from ShortHighMovIndicator")
    //counter @When("@Priority(4) on ShortLowRunBarIndicator as trigger select trigger.low from ShortLowRunBarIndicator")
    void handleRunsShortHighIndicator(Market market, double d) {
        super.handleShortHighIndicator(1.0, d, ExecutionInstruction.TAKER, market, null, false);
    }

    // enter long
    //@When("@Priority(3) on LongHighMovIndicator as trigger select trigger.high from LongHighMovIndicator")
    //counter@When("@Priority(3) on LongLowRunBarIndicator as trigger select trigger.low from LongLowRunBarIndicator")
    void handleRunsLongHighIndicator(Market market, double d) {
        super.handleLongHighIndicator(1.0, 1.0, d, ExecutionInstruction.MAKER, FillType.STOP_LOSS, market, null, false);
    }

    //enter short
    //counter @When("@Priority(3) on LongHighRunBarIndicator as trigger select trigger.high from LongHighRunBarIndicator")
    // @When("@Priority(3) on LongLowMovIndicator as trigger select trigger.low from LongLowMovIndicator")
    void handleRunsLongLowIndicator(Market market, double d) {
        super.handleLongLowIndicator(1.0, 1.0, d, ExecutionInstruction.MAKER, FillType.STOP_LOSS, market, null, false);
    }

    @Transient
    public static Double getRunsInterval() {
        return runsInterval;
    }

    @Transient
    public static Double getTrendInterval() {
        return trendInterval;
    }

    @Transient
    public double getBidATR(Market market) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_RUN_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @Transient
    public double getTradeATR(Market market) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_RUN_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @Transient
    public double getAskATR(Market market) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_RUN_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @Override
    public double getAskATR(Tradeable market, double interval) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getBidATR(Tradeable market, double interval) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTradeATR(Tradeable market, double interval) {
        // TODO Auto-generated method stub
        return 0;
    }
}
