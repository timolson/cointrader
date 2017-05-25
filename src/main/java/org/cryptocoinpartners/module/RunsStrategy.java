package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Tradeable;

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
public class RunsStrategy extends TestStrategy {

    static double percentEquityRisked = 0.1;
    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    static double atrStop = 2;
    static double atrTarget = 20;
    //* atrStop;
    static long slippage = 1;
    static long minSpread = 30;
    static double maxLossTarget = atrStop * percentEquityRisked;
    private DiscreteAmount lastAsk;
    private DiscreteAmount lastBid;
    private Amount lastShortEntryLimit;
    private Amount lastLongEntryLimit;

    //double maxLossTarget = 0.25;

    @Inject
    public RunsStrategy(Context context, Configuration config) {
        super(context, config);
        String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        addMarket(Market.forSymbol(marketSymbol), 1.0);
        setRunsInterval(300);
        setPercentEquityRisked(percentEquityRisked);
        setAtrStop(atrStop);
        setMinSpread(minSpread);
        setAtrTarget(atrTarget);
        setSlippage(slippage);
        setMaxLossTarget(atrStop * percentEquityRisked);
    }

    @When("@Priority(2) select * from Book(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    //@When("select talib(\"rsi\", askPriceAsDouble, 14) as value from Book")
    void handleMarketMaker(Tradeable market, Book b) {
        if (getPositions(market) != null && !getPositions(market).isEmpty() && positionMap.get(market).get(interval).getType() != PositionType.EXITING) {

            createExits((Market) market, b);
        }

        //  }

        // }

        //  }
        lastAsk = b.getBestAsk().getPrice();
        lastBid = b.getBestBid().getPrice();

    }

    //exit long
    //@When("@Priority(4) on ShortHighRunBarIndicator as trigger select trigger.high from ShortHighRunBarIndicator")
    //   @When("@Priority(4) on ShortLowRunBarIndicator as trigger select trigger.low from ShortLowRunBarIndicator")
    void handleRunsShortLowIndicator(Market market, double d) {
        super.handleShortLowIndicator(1.0, d * atrTarget, ExecutionInstruction.TAKER, market, null, false, false);
    }

    // exit short
    // @When("@Priority(4) on ShortHighRunBarIndicator as trigger select trigger.high from ShortHighRunBarIndicator")
    //counter @When("@Priority(4) on ShortLowRunBarIndicator as trigger select trigger.low from ShortLowRunBarIndicator")
    void handleRunsShortHighIndicator(Market market, double d) {
        super.handleShortHighIndicator(1.0, d * atrTarget, ExecutionInstruction.TAKER, market, null, false);
    }

    // enter long
    // normal @When("@Priority(3) on LongHighRunBarIndicator as trigger select trigger.high from LongHighRunBarIndicator")
    @When("@Priority(3) on LongLowRunBarIndicator as trigger select trigger.low from LongLowRunBarIndicator")
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

        buildEnterOrders(1.0, 1.0, 0, ExecutionInstruction.MAKER, FillType.STOP_LOSS, PositionType.LONG, entryPrice, false, null, null, market, null, false);

        ArrayList<Order> longOrderList = new ArrayList<Order>();
        //.buildEnterLongOrders();
        if (longOrderList != null) {

            Order bidOrder = null;
            for (Order order : longOrderList)
                bidOrder = order;
            try {
                orderService.placeOrder(bidOrder);
            } catch (Throwable e) {

            }
            lastLongEntryLimit = bidOrder.getLimitPrice();

        } else {
            revertPositionMap(market, interval);
        }
    }

    //counter@When("@Priority(3) on LongLowRunBarIndicator as trigger select trigger.low from LongLowRunBarIndicator")
    void handleRunsLongHighIndicator(double d) {
    }

    //enter short
    //counter @When("@Priority(3) on LongHighRunBarIndicator as trigger select trigger.high from LongHighRunBarIndicator")
    // normal  @When("@Priority(3) on LongLowRunBarIndicator as trigger select trigger.low from LongLowRunBarIndicator")
    @When("@Priority(3) on LongHighRunBarIndicator as trigger select trigger.high from LongHighRunBarIndicator")
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

        //  buildEnterShortOrders askBuilder = new ArrayList<Order>();
        //new buildEnterShortOrders(0, ExecutionInstruction.MAKER);

        ArrayList<Order> shortOrderList = new ArrayList<Order>();
        //askBuilder.buildEnterShortOrders();
        if (shortOrderList != null) {

            Order askOrder = null;

            for (Order order : shortOrderList)
                askOrder = order;

            try {
                orderService.placeOrder(askOrder);
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            lastShortEntryLimit = askOrder.getLimitPrice();

            //   }
        } else {
            revertPositionMap(market, interval);
        }

    }

    void handleRunsLongLowIndicator(Market market, double d) {
        super.handleLongLowIndicator(1.0, 1.0, d * atrTarget, ExecutionInstruction.TAKER, FillType.STOP_LOSS, market, null, false);
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
