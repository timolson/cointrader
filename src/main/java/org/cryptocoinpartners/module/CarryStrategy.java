package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

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
public class CarryStrategy extends BaseVolatilityStrategy {
    //TODO we are trading too quickly, as each time we go long/short it is every time we see a higher price.
    //so we should we only go long short when bar is higher than other. 
    // so if the current bar is higher than the x pervious bars enter trade

    //what about order size to total equity?
    // need to add positions in units up unitl our max limit. 
    //  unit size is determined by eqiuty risked, and max psotins size is a set numner of units, but we ave it as max loss
    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    static double atrStop = 7; // 3 is the optimased value
    static double maxUnits = 10;
    static boolean monthlyReset = false;
    static double atrTarget = 99;
    static double atrTrigger = 1;
    boolean useNotional = false;
    static boolean limit = false;
    static long timeToLive = 300000; // 5 mins 
    static long exitTimeToLive = 60000; // 1 min
    // static double stopPercentage = 0.15;
    //* atrStop;
    //
    static double slippage = 0.002;
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
    static double mediumWeight = 0.00;
    static double longWeight = 0.00;
    static double triggerInterval = 900;
    static double shortIntervalFactor = 2; //2;
    static double mediumIntervalFactor = 4;
    static double volatilityInterval = 86400;
    static double volatilityTarget = 0.25;

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
    protected static Market currentMarket;
    protected static Market nearMarket;
    private static Double forecastScalar = 30.0;

    //double maxLossTarget = 0.25;
    @Transient
    public static double getDeviations() {

        return Double.valueOf("1");

    }

    @Inject
    public CarryStrategy(Context context, Configuration config) {
        super(context, config);

        setTrendInterval(43200);
        setLongTrendIntervalFactor(longIntervalFactor);
        setMediumTrendIntervalFactor(mediumIntervalFactor);
        setShortTrendIntervalFactor(shortIntervalFactor);
        setVolatilityInterval(volatilityInterval);

        setTriggerTrendInterval(triggerInterval);
        weights.put(getTrendInterval(), shortWeight);
        weights.put(getMedTrendInterval(), mediumWeight);
        weights.put(getLongTrendInterval(), longWeight);

        String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");

        nearMarket = (Market) nearMarket.forSymbol(marketSymbol);
        if (nearMarket == null) {
            Exchange exchange = Exchange.forSymbol("OKCOIN_THISWEEK");
            Listing listing = Listing.forSymbol("BTC.USD.THISWEEK");
            Market.findOrCreate(exchange, listing);
            nearMarket = (Market) nearMarket.forSymbol(marketSymbol);

        }
        addMarket(nearMarket, 1.0);
        String farMarketSymbol = ("OKCOIN_NEXTWEEK:BTC.USD.NEXTWEEK");

        currentMarket = (Market) currentMarket.forSymbol(farMarketSymbol);
        if (currentMarket == null) {
            Exchange exchange = Exchange.forSymbol("OKCOIN_NEXTWEEK");
            Listing listing = Listing.forSymbol("BTC.USD.NEXTWEEK");
            Market.findOrCreate(exchange, listing);
            currentMarket = (Market) currentMarket.forSymbol(farMarketSymbol);

        }
        addMarket(currentMarket, 1.0);
        String market2Symbol = ("POLONIEX:ETH.BTC");

        Market market2 = (Market) Market.forSymbol(market2Symbol);
        if (market2 == null) {
            Exchange exchange = Exchange.forSymbol("POLONIEX");
            Listing listing = Listing.forSymbol("ETH.BTC");
            Market.findOrCreate(exchange, listing);
            market2 = (Market) market2.forSymbol(market2Symbol);

        }
        setLimit(limit);
        setTimeToLive(timeToLive);
        setUseNotional(useNotional);
        setExitTimeToLive(exitTimeToLive);
        setVolatilityTarget(volatilityTarget);
        setStopPercentage(stopPercentage);
        setPercentEquityRisked(equityRisked);
        setAtrStop(atrStop);
        setMinSpread(minSpread);
        setAtrTarget(atrTarget);
        setAtrTrigger(atrTrigger);
        setSlippage(slippage);
        setTriggerBuffer(triggerBuffer);
        setMaxLossTarget(maxLossTarget);
    }

    @Transient
    public static Market getCurrentMarket() {
        return currentMarket;
    }

    @Transient
    public static Market getNearMarket() {
        return nearMarket;
    }

    @Transient
    public static double getDecay() {
        double decay = (2.0 / 26.0);
        return decay;
    }

    @Transient
    public static double getShortDecay() {
        double decay = (2.0 / 9.0);
        return decay;
    }

    public static class DataSubscriber {

        private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        @SuppressWarnings("rawtypes")
        public synchronized void reset(Long timestamp, Portfolio portfolio) {
            //so we need to delte allthe transactoins
            // and create on trnation eaul to our oringal balance
            DateTime currentDateTime = new DateTime(timestamp);

            if (!monthlyReset || (monthlyReset && currentDateTime.getDayOfMonth() != currentDateTime.dayOfMonth().getMaximumValue()))
                return;
            log.debug("resetting porfolio at " + portfolio.context.getTime());
            PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);
            QuoteService quoteService = portfolio.context.getInjector().getInstance(QuoteService.class);

            Amount totalBal = portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).plus(
                    portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()));
            totalBal = totalBal.minus(startingCashBal).plus(originalBaseNotionalBalance);
            OrderService orderService = portfolio.context.getInjector().getInstance(OrderService.class);
            TransactionFactory transactionFactory = portfolio.context.getInjector().getInstance(TransactionFactory.class);
            log.info("Cancelling All Orders");
            for (Tradeable tradeable : portfolio.getMarkets()) {
                if (!tradeable.isSynthetic()) {
                    Market market = (Market) tradeable;

                    orderService.handleCancelAllShortOpeningSpecificOrders(portfolio, market);
                    orderService.handleCancelAllShortClosingSpecificOrders(portfolio, market);
                    orderService.handleCancelAllShortOpeningGeneralOrders(portfolio, market);

                    orderService.handleCancelAllShortStopOrders(portfolio, market);
                    orderService.handleCancelAllLongOpeningSpecificOrders(portfolio, market);
                    orderService.handleCancelAllLongClosingSpecificOrders(portfolio, market);
                    orderService.handleCancelAllLongOpeningGeneralOrders(portfolio, market);
                    orderService.handleCancelAllLongStopOrders(portfolio, market);

                    //       portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset());
                    List<Fill> fills = new ArrayList<Fill>();
                    for (Position position : portfolioService.getPositions())
                        fills.addAll(position.getFills());

                    Collections.sort(fills, timeOrderIdComparator);
                    // tradedCurencyt
                    Asset tradedCurrency = (market.getTradedCurrency(market) == null) ? market.getBase() : market.getTradedCurrency(market);
                    Listing listing = Listing.forPair(tradedCurrency, portfolio.getBaseAsset());

                    Offer rate = quoteService.getImpliedBestAskForListing(listing);
                    Amount baseRealisedPnL = portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset());
                    if (fills != null && !fills.isEmpty() && !baseRealisedPnL.isZero()) {
                        Fill lastFill = fills.get(fills.size() - 1);
                        Transaction unRealisedPnL = transactionFactory.create(lastFill, portfolio, market.getExchange(), portfolio.getBaseAsset(),
                                TransactionType.REALISED_PROFIT_LOSS, baseRealisedPnL, new DiscreteAmount(0, portfolio.getBaseAsset().getBasis()));

                        portfolio.context.setPublishTime(unRealisedPnL);
                        log.info("Publishing base unrealised PnL with rate " + rate + " as realised PnL : " + unRealisedPnL);
                        unRealisedPnL.persit();
                        portfolio.context.publish(unRealisedPnL);
                    }
                }
            }

            log.debug("Resetting all positions : " + portfolioService.getPositions());
            portfolio.positionReset();
            unrealisedPnLPositionMap.clear();
            log.debug("Resetting all blanaces : " + portfolioService.getAvailableBalances());
            portfolioService.reset();
            for (Tradeable tradeable : portfolio.getMarkets())
                if (!tradeable.isSynthetic()) {
                    Market market = (Market) tradeable;

                    market.getExchange().removeBalances();

                }
            previousBal = startingBaseNotionalBalance;

            originalBaseNotionalBalance = startingBaseNotionalBalance;

            portfolio.setBaseCashBalanceCount(portfolio.getStartingBaseCashBalance().toBasis(portfolio.getBaseAsset().getBasis(), Remainder.ROUND_EVEN)
                    .getCount());
            portfolio.setBaseNotionalBalanceCount(portfolio.getStartingBaseNotionalBalance().toBasis(portfolio.getBaseAsset().getBasis(), Remainder.ROUND_EVEN)
                    .getCount());

            portfolioService.loadBalances();
            log.info(this.getClass().getSimpleName() + ":update - Reset Balances Total Value (" + portfolio.getBaseAsset() + "):"
                    + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).plus(portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset())));

        }

        private static final Comparator<Fill> timeOrderIdComparator = new Comparator<Fill>() {
            @Override
            public int compare(Fill event, Fill event2) {
                int sComp = event.getTime().compareTo(event2.getTime());
                if (sComp != 0) {
                    return sComp;
                } else {
                    return (event.getId().compareTo(event2.getId()));

                }
            }
        };

        @SuppressWarnings("rawtypes")
        public synchronized void update(Portfolio portfolio) {

            // get the netliquidatingvalue of each postions by market by interval
            //  Amount marketIntevalBalance;
            PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);

            // PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);
            OrderService orderService = portfolio.context.getInjector().getInstance(OrderService.class);
            TransactionFactory transactionFactory = portfolio.context.getInjector().getInstance(TransactionFactory.class);
            Amount scalcingFactor = DecimalAmount.of(Double.toString(rebalancingFactor));

            Amount cashBal = ((portfolioService.getBaseCashBalance(portfolio.getBaseAsset())).minus(previousRestBal)).times(scalcingFactor,
                    Remainder.ROUND_EVEN);
            log.info(portfolio.context.getTime() + "Calculating rebalancing portfolio equity risked:" + percentEquityRisked + "portfolioBaseNotionalBalance: "
                    + portfolio.getBaseNotionalBalance() + " previousRestBal: " + previousRestBal + " originalNotionalBalanceUSD: "
                    + originalBaseNotionalBalance + " balanceScalingAmount: " + balanceScalingAmount + " startingNotionalBalanceUSD: "
                    + startingBaseNotionalBalance + " baseCashBalance:" + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " baseRealisedPnL:"
                    + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()));

        }

        @SuppressWarnings("rawtypes")
        public synchronized void update(Long timestamp, Portfolio portfolio) {

            DateTime currentDateTime = new DateTime(timestamp);
            if (monthlyReset || previousRestBal == null || previousRestBal.isZero()
                    || currentDateTime.getDayOfMonth() != currentDateTime.dayOfMonth().getMaximumValue())
                return;
            //   Amount cashBal = portfolioService.getAvailableBaseBalance(market.getTradedCurrency()); 
            if (originalBaseNotionalBalance != null) {

                PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);
                OrderService orderService = portfolio.context.getInjector().getInstance(OrderService.class);
                TransactionFactory transactionFactory = portfolio.context.getInjector().getInstance(TransactionFactory.class);
                Amount scalcingFactor = DecimalAmount.of(Double.toString(rebalancingFactor));

                Amount cashBal = ((portfolioService.getBaseCashBalance(portfolio.getBaseAsset())).minus(previousRestBal)).times(scalcingFactor,
                        Remainder.ROUND_EVEN);
                log.info(portfolio.context.getTime() + "Calculating rebalancing portfolio equity risked:" + percentEquityRisked
                        + "portfolioBaseNotionalBalance: " + portfolio.getBaseNotionalBalance() + " previousRestBal: " + previousRestBal
                        + " originalNotionalBalanceUSD: " + originalBaseNotionalBalance + " balanceScalingAmount: " + balanceScalingAmount
                        + " startingNotionalBalanceUSD: " + startingBaseNotionalBalance + " baseCashBalance:"
                        + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " baseRealisedPnL:"
                        + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()));
                if (cashBal.isPositive()) {

                    Amount percentProfit = cashBal.divide(previousRestBal, Remainder.ROUND_EVEN);
                    if (percentProfit.compareTo(DecimalAmount.ZERO) > 0) {
                        log.info(portfolio.context.getTime() + " / " + timestamp + " :Rebalancing portfolio with scale factor " + scalcingFactor
                                + " percentage profit " + percentProfit + " equity risked:" + percentEquityRisked + " previousRestBal: " + previousRestBal
                                + " originalNotionalBalanceUSD: " + originalBaseNotionalBalance + " balanceScalingAmount: " + balanceScalingAmount
                                + " startingNotionalBalanceUSD: " + startingBaseNotionalBalance + " baseCashBalance:"
                                + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " baseRealisedPnL:"
                                + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()));
                        originalBaseNotionalBalance = originalBaseNotionalBalance.times(percentProfit.plus(DecimalAmount.ONE), Remainder.ROUND_EVEN);
                        previousRestBal = portfolioService.getBaseCashBalance(portfolio.getBaseAsset());

                        portfolio.setBaseNotionalBalanceCount(originalBaseNotionalBalance.toBasis(portfolio.getBaseAsset().getBasis(), Remainder.ROUND_EVEN)
                                .getCount());
                        portfolio.merge();
                        //}
                        log.info(portfolio.context.getTime() + "Rebalanced portfolio equity risked:" + percentEquityRisked + "portfolioBaseNotionalBalance: "
                                + portfolio.getBaseNotionalBalance() + " previousRestBal: " + previousRestBal + " originalNotionalBalanceUSD: "
                                + originalBaseNotionalBalance + " balanceScalingAmount: " + balanceScalingAmount + " startingNotionalBalanceUSD: "
                                + startingBaseNotionalBalance + " baseCashBalance:" + portfolioService.getBaseCashBalance(portfolio.getBaseAsset())
                                + " baseRealisedPnL:" + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()));

                    }

                }

            }
        }
    }

    //   @When("@Priority(4) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<ShortLowTradeIndicator.low and trigger.market=ShortLowTradeIndicator.market and ShortLowTradeIndicator.lookback>=12")
    synchronized void handleExitLong(double d, Market market, double interval) {
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0 || weights.get(interval) == null || weights.get(interval) == 0)
            return;
        double fastEWMA = getFastEWMAClose(market, 3600.0);
        double slowEWMA = getEWMAClose(market, 3600.0);
        log.info("Short Low Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval
                + " Long Moving Average (should be positive):" + slowEWMA + " and fast ewma " + fastEWMA);
        double tragetPrice = d * atrTarget;
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());

        super.handleShortLowIndicator(interval, targetDiscrete.getCount(), ExecutionInstruction.TAKER, market, null, false, false);
    }

    //  @When("@Priority(4) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>ShortHighTradeIndicator.high and trigger.market=ShortHighTradeIndicator.market and ShortHighTradeIndicator.lookback>=12")
    synchronized void handleExitShort(double d, Market market, double interval) {
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0 || weights.get(interval) == null || weights.get(interval) == 0)
            return;
        double fastEWMA = getFastEWMAClose(market, 3600.0);
        double slowEWMA = getEWMAClose(market, 3600.0);
        log.info("Short High Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval
                + " Long Moving Average (should be positive):" + slowEWMA + " and fast ewma " + fastEWMA);
        double tragetPrice = d * atrTarget;
        // double tragetPrice = bestBid.getPriceCount() - (tragetAmount * priceScaling);
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());
        super.handleShortHighIndicator(interval, targetDiscrete.getCount(), ExecutionInstruction.TAKER, market, null, false);
    }

    @Transient
    public static Double getForecastScalar() {
        return forecastScalar;
    }

    @When("@Priority(4) select * from carryForecastWindow")
    //@When("@Priority(4) select * from priceDifference")
    // 
    // @When("@Priority(4) select * from SmoothedLongForecastWindow")
    //  @When("@Priority(4) select last(*) from LongForecastROCWindow")
    //  @When("@Priority(4) select * from LongForecastROCWindow")
    //  @When("@Priority(4) select * from EWMAForecastWindow")
    synchronized void handleForecast(Map forecastMap) {
        if ((forecastMap.get("market") != null && getMarketAllocation((Market) forecastMap.get("market")) == null)
                || (forecastMap.get("market") != null && getMarketAllocation((Market) forecastMap.get("market")) == 0.0) || interval == 0.0
                || weights.get(forecastMap.get("interval")) == null || forecastMap.get("forecast") == null)
            //|| (double) forecastMap.get("lookback") < getLongLookBack())
            return;
        Market market = (Market) forecastMap.get("market");
        //   double pricePointsVolatility = (getPricePointsVol((Market) forecastMap.get("market"), getVolatilityInterval()));
        //  if (pricePointsVolatility == 0) {
        //    log.info("Handle long forecast with prevented as pricePointsVolatility is zero at: " + context.getTime());
        //  return;

        // }
        double forecast = ((double) forecastMap.get("forecast"));
        if (Double.isInfinite(forecast))
            return;
        forecast = (forecast < 0) ? Math.max(forecast, -20.0) : Math.min(forecast, 20.0);
        //   double longForecast = getLongForecast((Market) forecastMap.get("market"), (double) forecastMap.get("interval"));
        log.info("At " + context.getTime() + " Handle long forecast with " + market + " interval " + forecastMap.get("interval") + " rawforecast "
                + forecastMap.get("forecast") + " cappedforecast " + forecast + " lookback " + forecastMap.get("lookback") + " close " + forecastMap.get("raw")
                + " underlyingForecast ");
        //  if (forecast == 20.0)

        super.handleEnterOrder(market, (double) forecastMap.get("forecast"), weights.get(forecastMap.get("interval")), (double) forecastMap.get("interval"),
                ExecutionInstruction.MAKERTOTAKER, forecast, false);
    }

    //  @When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>LongHighTradeIndicator.high and trigger.market=LongHighTradeIndicator.market and LongHighTradeIndicator.lookback>=24")
    // enter a long if the next_week>this_week
    // @When("on pattern [every timer:at (*/30, [7,8], *,*,5,0,'UTC')] select high, market, interval from LastBarWindow group by market,interval limit 1")
    synchronized void handleEnterLong(double d, Market market, double interval) {
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0)
            return;
        log.info("Long High Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval);
        if (quotes.getLastBar(market, getTriggerTrendInterval()) == null) {
            log.info("Long High Indicator not processed as fast EWMA" + " is not greater than 5 or less than 45");

            return;
        }
        Book nearBook = quotes.getLastBook(nearMarket);
        Book farBook = quotes.getLastBook(currentMarket);

        log.debug("Spread Price at " + context.getTime() + " " + nearMarket + ":" + nearBook.getBidPriceAsDouble() + ":" + nearBook.getAskPriceAsDouble() + ","
                + currentMarket + ":" + farBook.getBidPriceAsDouble() + ":" + farBook.getAskPriceAsDouble() + ", " + nearMarket + "1Hr:"
                + quotes.getLastBar(nearMarket, TrendStrategy.getTriggerTrendInterval() * 4).getVolume() + "," + nearMarket + "24hr:"
                + quotes.getLastBar(nearMarket, TrendStrategy.getVolatilityInterval()).getVolume() + "," + nearMarket + "7day:"
                + quotes.getLastBar(nearMarket, TrendStrategy.getVolatilityInterval() * 7).getVolume() + currentMarket + "1Hr:"
                + quotes.getLastBar(currentMarket, TrendStrategy.getTriggerTrendInterval() * 4).getVolume() + "," + currentMarket + "24hr:"
                + quotes.getLastBar(currentMarket, TrendStrategy.getVolatilityInterval()).getVolume() + "," + currentMarket + "7day:"
                + quotes.getLastBar(currentMarket, TrendStrategy.getVolatilityInterval() * 7).getVolume());
        //  if (quotes.getLastBar(market, getTriggerTrendInterval()) == null) {
        // DiscreteAmount targetDiscrete = new DiscreteAmount(quotes.getLastBar(market, getTriggerTrendInterval()).getHigh().longValue(), market.getPriceBasis());

        // super.handleLongHighIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, market, null);
    }

    //   @When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<LongLowTradeIndicator.low and LongLowTradeIndicator.lookback>=24")
    synchronized void handleEnterShort(double d, Market market, double interval) {
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0 || weights.get(interval) == null || weights.get(interval) == 0)
            return;
        double er = getTradeER(market, interval);
        log.info("Long Low Indicator Recived at " + context.getTime() + " with price " + d + " interval " + interval + " efficency ratio:" + er);
        if (quotes.getLastBar(market, getTriggerTrendInterval()) == null) {
            log.info("Long Low Indicator not processed as fast EWMA" + er + " is not less than slow ewma " + er);
            return;
        }
        DiscreteAmount targetDiscrete = new DiscreteAmount(quotes.getLastBar(market, getTriggerTrendInterval()).getLow().longValue(), market.getPriceBasis());
        if (weights.get(interval) != null && weights.get(interval) != 0)
            super.handleLongLowIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, FillType.STOP_LOSS, market, null, false);

    }

    //  @When("@Priority(1) select * from LastBarWindow(interval=TrendStrategy.getTrendInterval()) group by market,interval")
    @Override
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
            log.debug(this.getClass().getSimpleName() + ":getBidATR - Getting Bid ATR for " + market.getSymbol() + " with events " + events);
            if (events.size() > 0) {
                HashMap attibutes;
                //
                // ArrayList<HashMap> values = (ArrayList<HashMap>) events;
                //  Object HashMap;
                for (Object value : events) {
                    attibutes = ((HashMap) value);
                    if (attibutes.get("market").equals(market) && attibutes.get("interval").equals(interval)) {
                        HashMap keyValue = (HashMap) attibutes.get("atr");
                        log.debug(this.getClass().getSimpleName() + ":getBidATR - Getting Bid ATR for " + market.getSymbol() + " with keyValue "
                                + attibutes.get("atr"));

                        if (keyValue.get(market) != null) {
                            log.debug(this.getClass().getSimpleName() + ":getBidATR - Getting Bid ATR for " + market.getSymbol() + " with atr "
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
            log.debug(this.getClass().getSimpleName() + ":getTradeATR - Getting Trade ATR for " + market.getSymbol() + " with events " + events);

            if (events.size() > 0) {
                HashMap attibutes;
                //
                // ArrayList<HashMap> values = (ArrayList<HashMap>) events;
                //  Object HashMap;
                for (Object value : events) {
                    attibutes = ((HashMap) value);
                    if (attibutes.get("market").equals(market) && attibutes.get("interval").equals(interval)) {
                        HashMap keyValue = (HashMap) attibutes.get("atr");
                        log.debug(this.getClass().getSimpleName() + ":getTradeATR - Getting Trade ATR for " + market.getSymbol() + " with keyValue "
                                + attibutes.get("atr"));

                        if (keyValue.get(market) != null) {
                            log.debug(this.getClass().getSimpleName() + ":getTradeATR - Getting Trade ATR for " + market.getSymbol() + " with atr "
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
                    log.debug(this.getClass().getSimpleName() + ":getAskATR - Getting Ask ATR for " + market.getSymbol() + " with events " + events);

                    attibutes = ((HashMap) value);
                    if (attibutes.get("market").equals(market) && attibutes.get("interval").equals(interval)) {
                        HashMap keyValue = (HashMap) attibutes.get("atr");
                        log.debug(this.getClass().getSimpleName() + ":getAskATR - Getting Ask ATR for " + market.getSymbol() + " with keyValue "
                                + attibutes.get("atr"));

                        if (keyValue.get(market) != null) {
                            log.debug(this.getClass().getSimpleName() + ":getAskATR - Getting Ask ATR for " + market.getSymbol() + " with atr "
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

    @Override
    public double getAskATR(Market market) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getBidATR(Market market) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTradeATR(Market market) {
        // TODO Auto-generated method stub
        return 0;
    }

}
