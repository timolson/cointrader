package org.cryptocoinpartners.module;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.util.FeesUtil;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import weka.classifiers.evaluation.NumericPrediction;
import weka.classifiers.timeseries.WekaForecaster;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instances;

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
public class ProbabilityTrendStrategy extends TestStrategy {
    static double percentEquityRisked = 0.005;
    static double atrStop = 2;
    static double minSpread = 2;
    private DiscreteAmount lastAsk;
    private DiscreteAmount lastBid;
    static double maxLossTarget = 0.01;//500 per trade
    private Amount lastHighAsk;
    private Amount lastLowBid;
    private Amount lastShortExitLimit;
    private Amount lastLongExitLimit;
    private Amount lastShortEntryLimit;
    private Amount lastLongEntryLimit;
    //atrStop * percentEquityRisked;

    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    // 1 hours bars with 4 atr and 2 GAP
    static double atrGap = 2;
    double interval = 3600; // 1hour
    static double exitInterval = 300; // 5 min

    static double atrTarget = 999;
    double train = Math.max((86400 / interval), 24);
    double priceScaling = 0.5;

    //* atrStop;
    static long slippage = 1;
    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    double winner;
    double loser;
    int bars;
    FastVector atts;
    FastVector attsRel;
    FastVector attVals;
    FastVector attValsRel;
    Instances data;
    Instances dataRel;
    private double lastPredictedOpen;
    private double lastPredictedHigh;
    private double lastPredictedLow;
    private double lastPredictedClose;
    private Bar previousBar;

    //double maxLossTarget = 0.25;

    @Inject
    public ProbabilityTrendStrategy(Context context, Configuration config) {

        super(context, config);

        int i;

        // 1. set up attributes
        atts = new FastVector();
        // - numeric
        atts.addElement(new Attribute("open"));
        // - numeric
        atts.addElement(new Attribute("high"));
        // - numeric
        atts.addElement(new Attribute("low"));
        // - numeric
        atts.addElement(new Attribute("close"));
        // - date
        atts.addElement(new Attribute("date", "yyyy-MM-dd HH:mm:ss"));

        // 2. create Instances object
        data = new Instances("MyRelation", atts, 0);

        String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        addMarket(Market.forSymbol(marketSymbol), 1.0);

        // setRunsInterval(86400);
        setTrendInterval(interval);
        setExitInterval(exitInterval);
        setMinSpread(minSpread);

        //setTrendInterval(600);
        setPercentEquityRisked(percentEquityRisked);
        setAtrStop(atrStop);
        setAtrTarget(atrTarget);
        setSlippage(slippage);
        setMaxLossTarget(maxLossTarget);

    }

    protected void setExitInterval(double exitInterval) {
        this.exitInterval = exitInterval;
    }

    // @When("select * from Trade(Trade.market=TestStrategy.getMarket())")
    void handleTrade(Trade t) {

        if (t.getPriceCountAsDouble().doubleValue() < lastPredictedHigh) {

            // exist shorts
            //log.info("Short High Indicator Recived");
            double d = t.getPriceCountAsDouble().doubleValue();
            // super.handleShortHighIndicator(d, ExecutionInstruction.TAKER);
        }
        if (t.getPriceCountAsDouble().doubleValue() > lastPredictedLow) {

            // exist long
            //  log.info("Short Low Indicator Recived");
            double d = t.getPriceCountAsDouble().doubleValue();
            // super.handleShortLowIndicator(d, ExecutionInstruction.TAKER);
        }
    }

    @When("select * from Book(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    void handleMarketMaker(Tradeable market, Book b) {

        if (getPositions(market) != null && !getPositions(market).isEmpty() && positionMap.get(market).get(interval).getType() != PositionType.EXITING) {

            createExits((Market) market, b);
        }

        //  }

        // }

        //  }
        lastAsk = b.getBestAsk().getPrice();
        lastBid = b.getBestBid().getPrice();
        // makeMarket(b);
        //   super.handleBook(b);
    }

    //exit shorts
    //lowest low
    // @When("@Priority(4) on LastTradeWindow as trigger select trigger.priceCountAsDouble from ShortLowTradeIndicator where trigger.priceCountAsDouble<ShortLowTradeIndicator.low")
    //  @When("@Priority(4) on LastExitBarWindow as trigger select trigger.low from ShortLowBidIndicator where trigger.low<ShortLowBidIndicator.low")
    void handleShortLowIndicator(Market market, double d) {
        // When we have the lowest low, we want to exit short positions.
        log.info("Short Low Indicator Recived");
        super.handleShortHighIndicator(1.0, d, ExecutionInstruction.MAKER, market, null, false);

    }

    //exit longs
    //highest high
    //@When("@Priority(4) on LastTradeWindow as trigger select trigger.priceCountAsDouble from ShortHighTradeIndicator where trigger.priceCountAsDouble>ShortHighTradeIndicator.high")
    // counter @When("@Priority(4) on ShortLowRunBarIndicator as trigger select trigger.low from ShortLowRunBarIndicator")
    // I have a new high so I want to exit longs
    //  @When("@Priority(4) on LastExitBarWindow as trigger select trigger.high from ShortHighAskIndicator where trigger.high>ShortHighAskIndicator.high")
    void handleShortHighIndicator(Market market, double d) {
        // when we have the highest high, we want to exit long postions.
        log.info("Short High Indicator Recived");
        super.handleShortLowIndicator(1.0, d, ExecutionInstruction.MAKER, market, null, false, false);

    }

    // @Override
    // @When("select * from Book(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    // @When("select * from Trade(Trade.market=TestStrategy.getMarket(),")
    //@When("select * from Book")
    //@When("select talib(\"rsi\", askPriceAsDouble, 14) as value from Book")
    @When("select  * from LastBarWindow")
    void handleBar(Market market, Bar b) {
        Offer bestBid = quotes.getLastBidForMarket(market);
        if (bestBid == null || bestBid.getPriceCount() == 0) {
            return;
        }
        Offer bestAsk = quotes.getLastAskForMarket(market);

        //  Offer bestAsk = quotes.getLastAskForMarket(market);
        if ((bestAsk == null || bestAsk.getPriceCount() == 0)) {
            return;
        }
        double[] vals;
        double[] valsRel;
        int i;
        bars++;

        // super.handleBook(b);
        vals = new double[data.numAttributes()];
        // - numeric
        vals[0] = b.getOpen().doubleValue();
        // - numeric
        vals[1] = b.getHigh().doubleValue();
        // - numeric
        vals[2] = b.getLow().doubleValue();
        // - numeric
        vals[3] = b.getClose().doubleValue();
        // - date
        try {

            vals[4] = data.attribute(4).parseDate(FORMAT.print(b.getTimestamp()));
        } catch (java.text.ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        // add
        data.add(new DenseInstance(1.0, vals));
        data.sort(4);
        WekaForecaster forecaster = new WekaForecaster();
        if (data.size() < 2)
            return;
        try {
            forecaster.setFieldsToForecast("open,high,low,close");

            forecaster.setBaseForecaster(new RandomForest());

            forecaster.getTSLagMaker().setTimeStampField("date");
            forecaster.getTSLagMaker().setMinLag(12);
            forecaster.getTSLagMaker().setMaxLag(24);

            forecaster.buildForecaster(data);

            forecaster.primeForecaster(data);
            List<List<NumericPrediction>> forecast = forecaster.forecast(1, System.out);

            List<NumericPrediction> predsAtStep = forecast.get(0);
            NumericPrediction predOpen = predsAtStep.get(0);
            NumericPrediction predHigh = predsAtStep.get(1);
            NumericPrediction predLow = predsAtStep.get(2);
            NumericPrediction predClose = predsAtStep.get(3);
            // I win if the prediction was in same directino as the price.
            // if the currnet closing price > predicited closing price, the price will fall so I enter short
            //   double open = new BigDecimal((predOpen.predicted()).setScale(0, RoundingMode.HALF_UP)).doubleValue();
            if (bars > train) {
                //     log.debug("Predicited Bar: Open=" + predOpen.predicted() + " High=" + predHigh.predicted() + " Low=" + predLow.predicted() + " close="
                //             + predClose.predicted());
                // enter short
                //   (((b.getLow() - (predHigh.predicted() + (atrGap * getAskATR()))) > 0))
                // if (b.getLow() > (predLow.predicted() + (atrGap * getAskATR())))
                // enter short when 
                //if (((predClose.predicted() > b.getClose()) && (b.getClose() < b.getOpen()))
                //     && ((previousBar.getClose() > predClose.predicted()) && (b.getClose() < predClose.predicted()))) {
                log.info("Predicted Open: " + predOpen.predicted() + " Predicted High: " + predHigh.predicted() + " Predicted Low: " + predLow.predicted()
                        + "Predicted Close: " + predClose.predicted());
                if ((predLow.predicted() + (atrGap * getBidATR(market))) < bestBid.getPriceCount()) {
                    log.info("Long Low Indicator Recived");
                    // if there is enoough of a price difference to cover the fees I will take it.
                    double tragetAmount = (bestBid.getPriceCount() - predLow.predicted()) * atrTarget;
                    double tragetPrice = bestBid.getPriceCount() - (tragetAmount * priceScaling);
                    DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());

                    // expecting prices to fall so let's add some slippage
                    Amount commission = FeesUtil.getCommission(bestBid.getPrice(), DecimalAmount.ONE, market, PositionEffect.OPEN);

                    Amount breakEvenPrice = (bestBid.getPrice().invert().minus(commission.dividedBy(
                            DecimalAmount.ONE.times(market.getContractSize(market), Remainder.ROUND_EVEN), Remainder.ROUND_EVEN))).invert();
                    DiscreteAmount bidPrice = (new DiscreteAmount(
                            (breakEvenPrice.asBigDecimal().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue()), market.getPriceBasis()))
                            .decrement(slippage);
                    if (targetDiscrete.increment(slippage).getCount() < bidPrice.getCount()) {
                        //   if (predHigh.predicted() < (bestBid.getPriceCount() + (atrStop * getBidATR()))) {

                        log.info("Long Low Indicator Recived");
                        double d = b.getClose().doubleValue();
                        super.handleLongLowIndicator(1.0, 1.0, targetDiscrete.getCount(), ExecutionInstruction.TAKER, FillType.STOP_LOSS, market, null, false);
                        //  }

                    }
                } else {
                    log.info("predicte low: " + predLow.predicted() + " and ATR multipler of: " + atrGap * getBidATR(market)
                            + " not suffient to place short order at: " + bestBid.getPriceCount());
                }
                // enter long
                //if ((((b.getHigh() + (atrGap * getAskATR())) - predLow.predicted()) < 0)) {
                // when closing price is great than prediction and a up bar

                // enter long when i have just crossed on a rising ba
                // if (((b.getClose() > predClose.predicted()) && (b.getClose() > b.getOpen()))
                //       && ((previousBar.getClose() < predClose.predicted()) && (b.getClose() > predClose.predicted()))) {
                if (predHigh.predicted() > (bestAsk.getPriceCount() + (atrGap * getAskATR(market)))) {
                    log.info("Long High Indicator Recived");

                    double tragetAmount = (predHigh.predicted() - (bestAsk.getPriceCount())) * atrTarget;
                    double tragetPrice = bestAsk.getPriceCount() + (tragetAmount * priceScaling);
                    DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());

                    Amount commission = FeesUtil.getCommission(bestAsk.getPrice(), DecimalAmount.ONE, market, PositionEffect.OPEN);

                    Amount breakEvenPrice = (bestAsk.getPrice().invert().plus(commission.dividedBy(
                            DecimalAmount.ONE.times(market.getContractSize(market), Remainder.ROUND_EVEN), Remainder.ROUND_EVEN))).invert();
                    DiscreteAmount askPrice = (new DiscreteAmount(
                            (breakEvenPrice.asBigDecimal().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue()), market.getPriceBasis()))
                            .increment(slippage);
                    if (targetDiscrete.decrement(slippage).getCount() > askPrice.getCount()) {
                        //  if ((predLow.predicted() + atrStop * getAskATR()) > bestAsk.getPriceCount()) {

                        //                if ((b.getHigh() + (atrGap * getAskATR())) < predHigh.predicted()) {

                        //&& b.getClose() > b.getOpen()
                        //    
                        // 
                        // prices are rising back to a norm
                        /*  double d = b.getClose().doubleValue();
                          buildEnterLongOrders bidBuilder = new buildEnterLongOrders(0, ExecutionInstruction.TAKER);

                          ArrayList<Order> longOrderList = bidBuilder.buildEnterLongOrders();
                          if (longOrderList != null) {

                              Order bidOrder = null;
                              for (Order order : longOrderList)
                                  bidOrder = order;
                              orderService.placeOrder(bidOrder);
                              lastLongEntryLimit = bidOrder.getLimitPrice();

                          } else {
                              revertPositionMap(market);
                          }
                          */

                        super.handleLongHighIndicator(1.0, 1.0, targetDiscrete.getCount(), ExecutionInstruction.TAKER, FillType.STOP_LOSS, market, null, false);
                        //   }

                    }
                } else {
                    log.info("predicte high: " + predHigh.predicted() + " and ATR multipler of: " + atrGap * getAskATR(market)
                            + " not suffient to place long order at: " + bestAsk.getPriceCount());
                }
                if ((b.getClose() > b.getOpen()) && (b.getClose() > previousBar.getClose())) {

                    // exist shorts
                    //   log.info("Short High Indicator Recived");
                    double d = b.getClose().doubleValue();
                    //  super.handleShortHighIndicator(d, ExecutionInstruction.TAKER);
                }
                // exit longs on the down bar
                if ((b.getClose() < b.getOpen()) && (previousBar.getClose() > b.getClose())) {

                    // exist long
                    // log.info("Short Low Indicator Recived");
                    double d = b.getClose().doubleValue();
                    //  super.handleShortLowIndicator(d, ExecutionInstruction.TAKER);
                }
            }
            lastPredictedOpen = predOpen.predicted();
            lastPredictedHigh = predHigh.predicted();
            lastPredictedLow = predLow.predicted();
            lastPredictedClose = predClose.predicted();

            //            if (((lastPrice - lastPrediction) > 0 && (lastPrice - (b.getClose()) > 0)) || (lastPrice - lastPrediction) < 0 && (lastPrice - (b.getClose()) < 0)) {
            //                winner++;
            //                log.debug("Winner: " + winner / (loser + winner));
            //            } else {
            //                loser++;
            //                log.debug("Loser: " + loser / (loser + winner));
            //            }
            //
            //            // log.debug("Last Price: " + lastPrice + " Last Prediction: " + lastPrediction + " Actual Price: " + b.getAskPriceAsDouble().toString());
            //            lastPrediction = new BigDecimal((predForTarget.predicted() / 100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            //            lastPrice = b.getClose();
            // Advance the current date to the next prediction date
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            previousBar = b;
        }
    }

    //@When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble from LongHighTradeIndicator where trigger.priceCountAsDouble>LongHighTradeIndicator.high")
    //
    void handleLongHighIndicator(double d) {
        //    if (getLongMov() > 0)
        log.info("Long High Indicator Recived");
        //  super.handleLongHighIndicator(d, ExecutionInstruction.MAKER);
    }

    //@When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble from LongLowTradeIndicator where trigger.priceCountAsDouble<LongLowTradeIndicator.low")
    void handleLongLowIndicator(Market market, double d) {
        //   if (getShortMov() > 0)
        log.info("Long Low Indicator Recived");

        super.handleLongLowIndicator(1.0, 1.0, d, ExecutionInstruction.MAKER, FillType.STOP_LOSS, market, null, false);
    }

    @Transient
    public static Double getTrendInterval() {
        return trendInterval;
    }

    @Transient
    public static Double getExitInterval() {
        return exitInterval;
    }

    @Transient
    public double getBidATR(Market market) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            log.error("Threw a Execption, full stack trace follows:", e1);

            e1.printStackTrace();
        } catch (IOException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

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
            events = context.loadStatementByName("GET_TRADE_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            log.error("Threw a Execption, full stack trace follows:", e1);

            e1.printStackTrace();
        } catch (DeploymentException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

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
            events = context.loadStatementByName("GET_TRADE_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @Transient
    public double getLongMov() {
        List<Object> events = null;
        double mov = 0;
        try {
            events = context.loadStatementByName("GET_MOV_LONG");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("mov") != null) {
                    mov = (double) value.get("mov");
                }

            }
        } catch (ParseException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return mov;

    }

    @Transient
    public double getShortMov() {
        List<Object> events = null;
        double mov = 0;
        try {
            events = context.loadStatementByName("GET_MOV_SHORT");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("mov") != null) {
                    mov = (double) value.get("mov");
                }

            }
        } catch (ParseException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            log.error("Threw a Execption, full stack trace follows:", e1);

            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return mov;

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
