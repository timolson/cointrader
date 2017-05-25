package org.cryptocoinpartners.module;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.math3.filter.DefaultMeasurementModel;
import org.apache.commons.math3.filter.DefaultProcessModel;
import org.apache.commons.math3.filter.KalmanFilter;
import org.apache.commons.math3.filter.MeasurementModel;
import org.apache.commons.math3.filter.ProcessModel;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.SyntheticMarket;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Tradeable;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;
import com.google.common.collect.Ordering;

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
public class PairsStrategy extends TestStrategy {
    //TODO we are trading too quickly, as each time we go long/short it is every time we see a higher price.
    //so we should we only go long short when bar is higher than other. 
    // so if the current bar is higher than the x pervious bars enter trade

    //what about order size to total equity?
    // need to add positions in units up unitl our max limit. 
    //  unit size is determined by eqiuty risked, and max psotins size is a set numner of units, but we ave it as max loss
    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    static double atrStop = 2; // 3 is the optimased value
    static double maxUnits = 10;
    static boolean monthlyReset = false;
    static double atrTarget = 99;
    static double atrTrigger = 1;
    boolean useNotional = false;
    static boolean limit = false;
    static long timeToLive = 300000; // 5 mins 
    static long exitTimeToLive = 60000; // 1 min
    static double stopPercentage = 1.0;
    //* atrStop;
    //
    static double slippage = 0.0;
    static long triggerBuffer = 250;
    static double equityRisked = 0.02; // 0.01;
    static double maxLossTarget = 0.25; //0.1;

    static double rebalancingFactor = 1.0;
    // static double shortWeight = 0.0;
    // static double mediumWeight = 0.0;
    // static double longWeight = 1.0;

    // static double shortWeight = 0.42;
    // static double mediumWeight = 0.16;
    // static double longWeight = 0.42;
    //optimal
    static double shortWeight = 1.00;
    static double mediumWeight = 0.0;
    static double longWeight = 0.00;
    static double triggerInterval = 900;
    static double shortIntervalFactor = 1; //2;
    static double mediumIntervalFactor = 4;
    static double volatilityInterval = 86400;
    static double volatilityTarget = 1.0;
    boolean accelerateStops;

    static double longIntervalFactor = 8;
    static HashMap<Double, Double> weights = new HashMap<Double, Double>();

    /// (lossScalingFactor);
    // atrStop * percentEquityRisked;
    private final ExecutionInstruction exeuctionMode = ExecutionInstruction.MAKERTOTAKER;
    private DiscreteAmount lastLongExitLimit;
    private DiscreteAmount lastShortExitLimit;
    private DiscreteAmount lastAsk;
    private DiscreteAmount lastBid;
    static double minSpread = 0.5;
    static double forecastScalar = 0.02027483758841;
    protected static Market farMarket;
    protected static Market nearMarket;
    protected static SyntheticMarket spreadMarket;
    protected static SyntheticMarket scoresMarket;
    private final FillType fillType = FillType.TRAILING_STOP_LOSS;

    //double maxLossTarget = 0.25;
    @Transient
    public static double getDeviations() {

        return Double.valueOf("1");

    }

    @Inject
    public PairsStrategy(Context context, Configuration config) {
        super(context, config);
        setTrendInterval(3600);

        //Profitabl setTrendInterval(36000);
        setLongTrendIntervalFactor(longIntervalFactor);
        setMediumTrendIntervalFactor(mediumIntervalFactor);
        setShortTrendIntervalFactor(shortIntervalFactor);
        setTriggerTrendInterval(triggerInterval);
        setAccelerateStops(accelerateStops);
        setDecay(30.0);
        setShortDecay(15.0);
        setPositionInertia(0.1);

        //   setTrendInterval(1800);

        // 12600
        // 25200 3.5/7 (7 day high)
        //100800 14/28 (1 month high)
        //403200 28/112 (4 month high)
        //3600
        // 7200 (1/2 day)
        // 28800 (8/4 day)
        // 115200 (32/16d days)

        //  String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.MONTH");
        weights.put(getTrendInterval(), shortWeight);
        weights.put(getMedTrendInterval(), mediumWeight);
        weights.put(getLongTrendInterval(), longWeight);

        String nearMarketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");

        nearMarket = (Market) Market.forSymbol(nearMarketSymbol);
        if (nearMarket == null) {
            Exchange exchange = Exchange.forSymbol("OKCOIN_THISWEEK");
            Listing listing = Listing.forSymbol("BTC.USD.THISWEEK");
            Market.findOrCreate(exchange, listing);
            nearMarket = (Market) nearMarket.forSymbol(nearMarketSymbol);

        }
        context.getInjector().injectMembers(nearMarket);
        addMarket(nearMarket, 2.0);
        //
        // addMarket(market, 0.3);
        String farMarketSymbol = ("OKCOIN_NEXTWEEK:BTC.USD.NEXTWEEK");

        farMarket = (Market) farMarket.forSymbol(farMarketSymbol);
        if (farMarket == null) {

            Exchange exchange = Exchange.forSymbol("OKCOIN_NEXTWEEK");
            Listing listing = Listing.forSymbol("BTC.USD.NEXTWEEK");
            Market.findOrCreate(exchange, listing);
            farMarket = (Market) farMarket.forSymbol(farMarketSymbol);

        }
        context.getInjector().injectMembers(farMarket);
        addMarket(farMarket, 2.0);

        String spreadMarketSymbol = ("OKCOIN_NEXTWEEK:BTC.USD.THISWEEK_NEXTWEEK");

        spreadMarket = (SyntheticMarket) spreadMarket.forSymbol(spreadMarketSymbol);
        if (spreadMarket == null) {
            List<Market> markets = new ArrayList<Market>();
            markets.add(nearMarket);
            markets.add(farMarket);

            // Exchange exchange = Exchange.forSymbol("OKCOIN_NEXTWEEK");
            //Listing listing = Listing.forSymbol("BTC.USD.THISWEEK_NEXTWEEK");
            spreadMarket = SyntheticMarket.findOrCreate(spreadMarketSymbol, markets);

        } else {
            spreadMarket.addMarket(nearMarket);
            spreadMarket.addMarket(farMarket);
        }
        addMarket(spreadMarket, 1.0);

        String scoresMarketSymbol = ("SELF:BTC.USD.THISWEEK_NEXTWEEK");

        scoresMarket = (SyntheticMarket) scoresMarket.forSymbol(scoresMarketSymbol);
        if (scoresMarket == null) {
            List<Market> markets = new ArrayList<Market>();
            markets.add(nearMarket);
            markets.add(farMarket);

            // Exchange exchange = Exchange.forSymbol("OKCOIN_NEXTWEEK");
            //Listing listing = Listing.forSymbol("BTC.USD.THISWEEK_NEXTWEEK");
            scoresMarket = SyntheticMarket.findOrCreate(scoresMarketSymbol, markets);

        } else {
            scoresMarket.addMarket(nearMarket);
            scoresMarket.addMarket(farMarket);
        }
        addMarket(scoresMarket, 1.0);
        // addMarket(farMarket, 0.0);

        // addMarket(market2, 0.4);

        //addMarket(market2, 0.0);
        //  context.getInjector().injectMembers(market.getExchange());
        //context.getInjector().injectMembers(market);

        //  if (market.getExchange().getBalances() == null || market.getExchange().getBalances().isEmpty())
        //    market.getExchange().loadBalances(portfolio);
        setLimit(limit);
        setTimeToLive(timeToLive);
        setUseNotional(useNotional);
        setExitTimeToLive(exitTimeToLive);
        setVolatilityInterval(volatilityInterval);
        setVolatilityTarget(volatilityTarget);
        // setRunsInterval(3600);
        // setTrendInterval(86400);
        //1hr,2hrs,6hrs,12 hrs,24 hrs
        //
        // setTrendInterval(14400);
        //1 Day 3600, 5 day 18000, 10 day 36000, 20 day 72000
        // 5 Day

        //setTrendInterval(57600);
        setStopPercentage(stopPercentage);
        setPercentEquityRisked(equityRisked);
        setAtrStop(atrStop);
        setMinSpread(minSpread);
        setAtrTarget(atrTarget);
        setAtrTrigger(atrTrigger);
        setSlippage(slippage);
        setTriggerBuffer(triggerBuffer);
        setMaxLossTarget(maxLossTarget);
        //notionalBalanceUSD = new DiscreteAmount(Long.parseLong("10000000"), getMarket().getQuote().getBasis());

        //
        //  startingNotionalBalanceUSD = new DiscreteAmount(Long.parseLong("10000000"), getMarket().getQuote().getBasis());
        //
        //originalNotionalBalanceUSD = new DiscreteAmount(Long.parseLong("10000000"), getMarket().getQuote().getBasis());

        //startingOriginalNotionalBalanceUSD = new DiscreteAmount(Long.parseLong("10000000"), getMarket().getQuote().getBasis());

    }

    @Transient
    public static Market getFarMarket() {
        return farMarket;
    }

    @Transient
    public static Market getNearMarket() {
        return nearMarket;
    }

    @Transient
    public static double getForecastScalar() {
        return forecastScalar;
    }

    @Transient
    public static SyntheticMarket getScoresMarket() {
        return scoresMarket;
    }

    @Transient
    public static void priceFilter() {
        double constantVoltage = 10d;
        double measurementNoise = 0.1d;
        double processNoise = 1e-5d;

        // A = [ 1 ]
        RealMatrix A = new Array2DRowRealMatrix(new double[] { 1d });
        // B = null
        RealMatrix B = null;
        // H = [ 1 ]
        RealMatrix H = new Array2DRowRealMatrix(new double[] { 1d });
        // x = [ 10 ]
        RealVector x = new ArrayRealVector(new double[] { constantVoltage });
        // Q = [ 1e-5 ]
        RealMatrix Q = new Array2DRowRealMatrix(new double[] { processNoise });
        // P = [ 1 ]
        RealMatrix P0 = new Array2DRowRealMatrix(new double[] { 1d });
        // R = [ 0.1 ]
        RealMatrix R = new Array2DRowRealMatrix(new double[] { measurementNoise });

        ProcessModel pm = new DefaultProcessModel(A, B, Q, x, P0);
        MeasurementModel mm = new DefaultMeasurementModel(H, R);
        KalmanFilter filter = new KalmanFilter(pm, mm);

        // process and measurement noise vectors
        RealVector pNoise = new ArrayRealVector(1);
        RealVector mNoise = new ArrayRealVector(1);

        RandomGenerator rand = new JDKRandomGenerator();
        // iterate 60 steps
        for (int i = 0; i < 60; i++) {
            filter.predict();

            // simulate the process
            pNoise.setEntry(0, processNoise * rand.nextGaussian());

            // x = A * x + p_noise
            x = A.operate(x).add(pNoise);

            // simulate the measurement
            mNoise.setEntry(0, measurementNoise * rand.nextGaussian());

            // z = H * x + m_noise
            RealVector z = H.operate(x).add(mNoise);

            filter.correct(z);

            double voltage = filter.getStateEstimation()[0];
        }

    }

    public static Trade createTrade(Trade near, Trade far) {
        //  tradeFactory.crea

        //     if (near.getPriceAsBigDecimal().compareTo(far.getPriceAsBigDecimal()) < 0)
        //       log.debug("test2");
        if (near == null || far == null
                || ((near.getVolume().isNegative() && far.getVolume().isNegative()) || (near.getVolume().isPositive() && far.getVolume().isPositive())))
            return null;
        BigDecimal spread = (near.getPriceAsBigDecimal().subtract(far.getPriceAsBigDecimal()));

        //    double shortPriceVolatility = getPriceVol(spreadMarket, getTrendInterval());

        //    BigDecimal zscore = spread - (near.getPriceAsBigDecimal().subtract(far.getPriceAsBigDecimal()));
        //   log.debug("createTrade - Creating spread trade " + Precision.round(spread.doubleValue(), 2) + " with near trade:" + near + " and far trade:" + far);
        Trade trade = tradeFactory.create(spreadMarket, Ordering.natural().max(near.getTime(), far.getTime()), null, spread,
                Ordering.natural().min(near.getVolumeAsBigDecimal(), far.getVolumeAsBigDecimal()));
        log.debug("createTrade - Created spread trade " + trade + " with near trade:" + near + " and far trade:" + far);

        // price is near - far,
        //when near price > far price  +ve
        // when near price < far price -ve.
        //price near < price of far, sell go short, so this would be a longlowindicator on near..

        //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
        // near price - far price, so when +ve then near pirce is > far price, so we go short near, long far
        //  Trade trade = tradeFactory.create(getSpreadMarket(), near.getTime(), near.getId(), near.getPriceAsBigDecimal() , near.getPriceAsBigDecimal());

        return trade;
    }

    public static Trade createTrade(Trade near, Trade far, Long timeDiff, Double nearMA, Double farMA) {
        //  tradeFactory.crea

        //     if (near.getPriceAsBigDecimal().compareTo(far.getPriceAsBigDecimal()) < 0)
        //       log.debug("test2");
        if (near == null || far == null
                || ((near.getVolume().isNegative() && far.getVolume().isNegative()) || (near.getVolume().isPositive() && far.getVolume().isPositive())))
            return null;
        (new DiscreteAmount((long) (nearMA - farMA), spreadMarket.getPriceBasis())).asBigDecimal();
        BigDecimal spread = (new DiscreteAmount((long) (nearMA - farMA), spreadMarket.getPriceBasis())).asBigDecimal();

        //    double shortPriceVolatility = getPriceVol(spreadMarket, getTrendInterval());

        //    BigDecimal zscore = spread - (near.getPriceAsBigDecimal().subtract(far.getPriceAsBigDecimal()));
        //   log.debug("createTrade - Creating spread trade " + Precision.round(spread.doubleValue(), 2) + " with near trade:" + near + " and far trade:" + far);
        Trade trade = tradeFactory.create(spreadMarket, Ordering.natural().max(near.getTime(), far.getTime()), null, spread,
                Ordering.natural().min(near.getVolumeAsBigDecimal(), far.getVolumeAsBigDecimal()));
        long diff = Math.abs(near.getTimestamp() - far.getTimestamp());
        log.debug("createTrade - Created spread trade " + trade + " from near " + near + " and far " + far + " with averages trade ma:" + nearMA
                + " and far trade ma:" + farMA + " and diff:" + diff + " and time diff " + timeDiff);

        // price is near - far,
        //when near price > far price  +ve
        // when near price < far price -ve.
        //price near < price of far, sell go short, so this would be a longlowindicator on near..

        //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
        // near price - far price, so when +ve then near pirce is > far price, so we go short near, long far
        //  Trade trade = tradeFactory.create(getSpreadMarket(), near.getTime(), near.getId(), near.getPriceAsBigDecimal() , near.getPriceAsBigDecimal());

        return trade;
    }

    public static Trade createTrade(Book near, Book far) {
        //  tradeFactory.crea

        //     if (near.getPriceAsBigDecimal().compareTo(far.getPriceAsBigDecimal()) < 0)
        //       log.debug("test2");
        if (near == null || far == null || near.getBids() == null || near.getBids().isEmpty() || far.getBids() == null || far.getBids().isEmpty()
                || near.getAsks() == null || near.getAsks().isEmpty() || far.getAsks() == null || far.getAsks().isEmpty())
            return null;

        // when spread price is postive we go long, so when near-far. if we go long we long near month so near bid-far ask, short far month far. 
        BigDecimal spread = (near.getBidPrice().asBigDecimal().subtract(far.getAskPrice().asBigDecimal()));

        //    double shortPriceVolatility = getPriceVol(spreadMarket, getTrendInterval());

        //    BigDecimal zscore = spread - (near.getPriceAsBigDecimal().subtract(far.getPriceAsBigDecimal()));
        Trade trade = tradeFactory.create(spreadMarket, Ordering.natural().max(near.getTime(), far.getTime()), null, spread,
                Ordering.natural().min(near.getBidVolume().asBigDecimal(), far.getAskVolume().asBigDecimal()));
        log.debug("createTrade - Created spread trade " + trade + " with near book:" + near + " and far book:" + far);

        // price is near - far,
        //when near price > far price  +ve
        // when near price < far price -ve.
        //price near < price of far, sell go short, so this would be a longlowindicator on near..

        //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
        // near price - far price, so when +ve then near pirce is > far price, so we go short near, long far
        //  Trade trade = tradeFactory.create(getSpreadMarket(), near.getTime(), near.getId(), near.getPriceAsBigDecimal() , near.getPriceAsBigDecimal());

        return trade;
    }

    public static Trade createTrade(Trade spread, Double ma, Double sd) {
        //  tradeFactory.crea

        //     if (near.getPriceAsBigDecimal().compareTo(far.getPriceAsBigDecimal()) < 0)
        //       log.debug("test2");
        if (spread == null || ma == 0 || sd == 0)
            return null;
        //  BigDecimal spread = (near.getPriceAsBigDecimal().subtract(far.getPriceAsBigDecimal()));

        //    double shortPriceVolatility = getPriceVol(spreadMarket, getTrendInterval());

        double zscore = (spread.getPriceCountAsDouble() - ma) / sd;

        Trade trade = tradeFactory.create(scoresMarket, spread.getTime(), null, BigDecimal.valueOf(zscore), spread.getVolumeAsBigDecimal());
        log.debug("createTrade - Created zscore " + trade + "from spread " + spread + " with moving averagee:" + ma + " stanrd deviation: " + sd);
        // price is near - far,
        //when near price > far price  +ve
        // when near price < far price -ve.
        //price near < price of far, sell go short, so this would be a longlowindicator on near..

        //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
        // near price - far price, so when +ve then near pirce is > far price, so we go short near, long far
        //  Trade trade = tradeFactory.create(getSpreadMarket(), near.getTime(), near.getId(), near.getPriceAsBigDecimal() , near.getPriceAsBigDecimal());

        return trade;
    }

    @Transient
    public static SyntheticMarket getSpreadMarket() {
        return spreadMarket;
    }

    @Transient
    public static double getDecay() {
        double decay = (2.0 / 30.0);
        return decay;
    }

    @Transient
    public static double getShortDecay() {
        double decay = (2.0 / 15.0);
        return decay;
    }

    @When("@Priority(3) select * from carryForecast")
    // @When("@Priority(5) select * from SmoothedShortForecastWindow")
    synchronized void handleShortForecast(Map forecastMap) {
        if ((forecastMap.get("market") != null && getMarketAllocation((Tradeable) forecastMap.get("market")) == null)
                || (forecastMap.get("market") != null && getMarketAllocation((Tradeable) forecastMap.get("market")) == 0.0) || interval == 0.0
                || weights.get(forecastMap.get("interval")) == null)
            return;
        Market market = getFarMarket();
        //(Market) forecastMap.get("market");
        //  double pricePointsVolatility = (getPricePointsVol((Market) forecastMap.get("market"), getVolatilityInterval()));
        // if (pricePointsVolatility == 0) {
        //    log.info("Handle short prevented as pricePointsVolatility is zero at: " + context.getTime());
        //  return;

        //}

        double forecast = ((double) forecastMap.get("forecast"));
        log.info("At " + context.getTime() + " Handle  forecast with " + market + " interval " + forecastMap.get("interval") + " forecast " + forecast);
        if (forecast < 0)
            super.handleEnterOrder(market, 0, weights.get(forecastMap.get("interval")), (double) forecastMap.get("interval"),
                    ExecutionInstruction.MAKERTOTAKER, forecast, true);
        if (forecast >= 0)
            super.handleEnterOrder(market, 0, weights.get(forecastMap.get("interval")), (double) forecastMap.get("interval"),
                    ExecutionInstruction.MAKERTOTAKER, forecast, false);

    }

    //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
    //longhigh
    // near price - far price, so when +ve then near pirce is > far price, so we go long far, short near
    //short low we should go short far, long near
    // were were  short near, long far. so want to go long near, short far.
    //so spread<average (so expecting it to revert, spead is negative so near<far, so expecting far to rise, near to fall. but I was in a long position, where I was expecting far to rise and near to fall.
    // @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<ShortLowTradeIndicator.low and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=30")
    //  @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>ShortHighTradeIndicator.high and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=12")
    // @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble where trigger.priceCountAsDouble<=0.0 and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=12")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>=100 and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=12")
    // @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket())  as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<=-100 and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=15")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket())  as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<=-100 and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=15")

    //  @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<ShortLowTradeIndicator.low and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=15")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>=0 and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=15")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>h.high and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=15")

    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<=0.0 and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=15")

    synchronized void handleShortLowIndicator(double d, Tradeable tradeable, double interval) {
        if (getMarketAllocation(tradeable) == null || getMarketAllocation(tradeable) == 0.0 || weights.get(interval) == null || weights.get(interval) == 0)
            return;

        if (tradeable.isSynthetic()) {
            SyntheticMarket markets = (SyntheticMarket) tradeable;
            log.info("Short Low Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval
                    + " Long Moving Average (should be positive)");
            double tragetPrice = d * atrTarget;
            DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), tradeable.getPriceBasis());
            //exit the far long
            super.handleShortLowIndicator(interval, targetDiscrete.getCount(), ExecutionInstruction.TAKER, markets.getMarkets().get(1), null, false, false);
            //exit the neat short

        }
    }

    //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
    // near price - far price, so when +ve then near pirce is > far price, so we go short near, long far
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>ShortHighTradeIndicator.high and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=12")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<=-100 and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=12")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>=0.0 and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=12")

    //  @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble<=0 and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=15")
    // @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>=100 and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=15")

    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>=0 and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=15")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getSpreadMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>0  and trigger.priceCountAsDouble>ShortHighTradeIndicator.high and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=12")

    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<=0 and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=15")
    //@When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<h.low and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=15")

    //  @When("@Priority(4) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>=0.0 and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=15")
    synchronized void handleShortHighIndicator(double d, Tradeable tradeable, double interval) {

        if (getMarketAllocation(tradeable) == null || getMarketAllocation(tradeable) == 0.0 || weights.get(interval) == null || weights.get(interval) == 0)
            return;
        if (tradeable.isSynthetic()) {
            SyntheticMarket markets = (SyntheticMarket) tradeable;
            log.info("Short High Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval
                    + " Long Moving Average (should be positive)");
            double tragetPrice = d * atrTarget;
            DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), tradeable.getPriceBasis());
            super.handleShortHighIndicator(interval, targetDiscrete.getCount(), ExecutionInstruction.TAKER, markets.getMarkets().get(1), null, false);
        }
    }

    //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
    // near price - far price, so when +ve then near pirce is > far price, so we go long near, short far

    //  @When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>LongHighTradeIndicator.high and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=24")
    //largest negative value

    //speard = near-far (USO(y)-GLD(x)
    // positvie zscore means current spread (near>far) price greater than average, so it should mean the spread should fall,  mean revert, so near must fall, and far must rise. so we short near, long far.
    //
    //@When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>LongHighTradeIndicator.high and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=24")
    // @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>=1.0 and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=24")
    // @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble>=500 and LongLowTradeIndicator.lookback>=24")
    // @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>=300 and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=24")

    //  @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>LongHighTradeIndicator.high and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=30")
    // @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<=-400 and LongLowTradeIndicator.lookback>=30")
    //@When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<h.low and LongLowTradeIndicator.lookback>=30")

    //@When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>500.0 and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=30")
    synchronized void handleLongHighIndicator(double d, Tradeable tradeable, double interval) {

        if (getMarketAllocation(tradeable) == null || getMarketAllocation(tradeable) == 0.0 || weights.get(interval) == null || weights.get(interval) == 0)
            return;
        if (tradeable.isSynthetic()) {
            SyntheticMarket markets = (SyntheticMarket) tradeable;
            log.info("Long High Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval);
            //   if (quotes.getLastBar(tradeable, getTriggerTrendInterval()) == null) {
            // || !((er <= 50) && (er >= 30))) {
            //             log.info("Long High Indicator not processed as fast EWMA" + " is not greater than 5 or less than 45");
            ///
            //           return;
            //     }
            DiscreteAmount targetDiscrete = new DiscreteAmount(quotes.getLastBar(tradeable, getTrendInterval()).getHigh().longValue(),
                    tradeable.getPriceBasis());
            if (weights.get(interval) != null && weights.get(interval) != 0) {
                super.handleLongHighIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, fillType, markets.getMarkets().get(1),
                        null, false);
                //       super.handleLongLowIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, markets.getMarkets().get(0), null);
            }
        }
    }

    //near price - far price, so when -ve then far pirce is > near price, so we go short far, long near
    // near price - far price, so when +ve then near pirce is > far price, so we go short near, long far

    //near=market(0), far(marekt1)
    //sprea price is negative, so next_week> this week, so next week fallls, this week rises. so go short next_week(1), go long this_week(0)
    //largest postive value
    //new low, so p-near-far, so the far>near, so we far to fall, near to rise, so we go long near, short far
    //@When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<LongLowTradeIndicator.low and LongLowTradeIndicator.lookback>=24")
    //@When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble<=-500 and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=24")
    // @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<1.0 and LongLowTradeIndicator.lookback>=24")
    //  @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getSpreadMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<0  and trigger.priceCountAsDouble<LongLowTradeIndicator.low and LongLowTradeIndicator.lookback>=24")
    //  @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<=-200 and LongLowTradeIndicator.lookback>=30")
    //@When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>=400 and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=30")
    // @When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>h.high and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=30")
    //@When("@Priority(3) on LastTradeWindow(market=PairsStrategy.getScoresMarket()) as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<-500.0 and LongLowTradeIndicator.lookback>=30")

    synchronized void handleLongLowIndicator(double d, Tradeable tradeable, double interval) {

        if (getMarketAllocation(tradeable) == null || getMarketAllocation(tradeable) == 0.0 || weights.get(interval) == null || weights.get(interval) == 0)
            return;
        if (tradeable.isSynthetic()) {
            SyntheticMarket markets = (SyntheticMarket) tradeable;

            log.info("Long Low Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval + " efficency ratio:");
            //   if (quotes.getLastBar(tradeable, getTriggerTrendInterval()) == null) {
            //  || !(er < -30 && er > -50)) {

            //     log.info("Long Low Indicator not processed as fast EWMA is not less than slow ewma ");

            //   return;
            //  }
            DiscreteAmount targetDiscrete = new DiscreteAmount(quotes.getLastBar(tradeable, getTrendInterval()).getLow().longValue(), tradeable.getPriceBasis());
            if (weights.get(interval) != null && weights.get(interval) != 0) {
                super.handleLongLowIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, fillType, markets.getMarkets().get(1),
                        null, false);
                //   super.handleLongLowIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, markets.getMarkets().get(0), null);
            }
        }

    }

    @Override
    @When("@Priority(1) select * from LastBarWindow(interval=TrendStrategy.getTrendInterval()) group by market,interval")
    public void handlePositionRisk(Bar bar) {
        super.handlePositionRisk(bar);
    }

    @Override
    @Transient
    public double getBidATR(Tradeable market, double interval) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_ATR");
            log.trace(this.getClass().getSimpleName() + ":getBidATR - Getting Bid ATR for " + market.getSymbol() + " with events " + events);
            if (events.size() > 0) {
                HashMap attibutes;
                //
                // ArrayList<HashMap> values = (ArrayList<HashMap>) events;
                //  Object HashMap;
                for (Object value : events) {
                    attibutes = ((HashMap) value);
                    if (attibutes.get("market").equals(market) && attibutes.get("interval").equals(interval)) {
                        HashMap keyValue = (HashMap) attibutes.get("atr");
                        log.trace(this.getClass().getSimpleName() + ":getBidATR - Getting Bid ATR for " + market.getSymbol() + " with keyValue "
                                + attibutes.get("atr"));

                        if (keyValue.get(market) != null) {
                            log.trace(this.getClass().getSimpleName() + ":getBidATR - Getting Bid ATR for " + market.getSymbol() + " with atr "
                                    + keyValue.get(market));

                            atr = (double) keyValue.get(market);

                            break;
                        }
                    }

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

    @Override
    @Transient
    public double getTradeATR(Tradeable market, double interval) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_ATR");
            log.trace(this.getClass().getSimpleName() + ":getTradeATR - Getting Trade ATR for " + market.getSymbol() + " with events " + events);

            if (events.size() > 0) {
                HashMap attibutes;
                //
                // ArrayList<HashMap> values = (ArrayList<HashMap>) events;
                //  Object HashMap;
                for (Object value : events) {
                    attibutes = ((HashMap) value);
                    if (attibutes.get("market").equals(market) && attibutes.get("interval").equals(interval)) {
                        HashMap keyValue = (HashMap) attibutes.get("atr");
                        log.trace(this.getClass().getSimpleName() + ":getTradeATR - Getting Trade ATR for " + market.getSymbol() + " with keyValue "
                                + attibutes.get("atr"));

                        if (keyValue.get(market) != null) {
                            log.trace(this.getClass().getSimpleName() + ":getTradeATR - Getting Trade ATR for " + market.getSymbol() + " with atr "
                                    + keyValue.get(market));

                            atr = (double) keyValue.get(market);
                            break;
                        }
                    }

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

    @Override
    @Transient
    public double getAskATR(Tradeable market, double interval) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_ATR");
            if (events.size() > 0) {
                HashMap attibutes;
                //
                // ArrayList<HashMap> values = (ArrayList<HashMap>) events;
                //  Object HashMap;
                for (Object value : events) {
                    log.trace(this.getClass().getSimpleName() + ":getAskATR - Getting Ask ATR for " + market.getSymbol() + " with events " + events);

                    attibutes = ((HashMap) value);
                    if (attibutes.get("market").equals(market) && attibutes.get("interval").equals(interval)) {
                        HashMap keyValue = (HashMap) attibutes.get("atr");
                        log.trace(this.getClass().getSimpleName() + ":getAskATR - Getting Ask ATR for " + market.getSymbol() + " with keyValue "
                                + attibutes.get("atr"));

                        if (keyValue.get(market) != null) {
                            log.trace(this.getClass().getSimpleName() + ":getAskATR - Getting Ask ATR for " + market.getSymbol() + " with atr "
                                    + keyValue.get(market));

                            atr = (double) keyValue.get(market);
                            break;
                        }
                    }

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

}
