package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.PositionType;
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
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.util.Remainder;
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
public class MomentumStrategy extends BaseMomentumStrategy {
    //TODO we are trading too quickly, as each time we go long/short it is every time we see a higher price.
    //so we should we only go long short when bar is higher than other. 
    // so if the current bar is higher than the x pervious bars enter trade

    //what about order size to total equity?
    // need to add positions in units up unitl our max limit. 
    //  unit size is determined by eqiuty risked, and max psotins size is a set numner of units, but we ave it as max loss
    static double equityRisked = 0.001; // 0.0005; //0.0002; //0.001;
    double positionInertia = 0.1;

    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    static double atrStop = 1; // 3 is the optimased value
    static boolean monthlyReset = false;
    static double atrTarget = 99;
    static double atrTrigger = 1;
    static boolean limit = false;
    static long timeToLive = 300000; // 5 mins 
    static long exitTimeToLive = 60000; // 1 min
    //* atrStop;
    //
    static double slippage = 0.002;
    static long triggerBuffer = 250;
    static double maxLossTarget = 0.01; //0.02;
    static double rebalancingFactor = 1.0;
    static double shortWeight = 0.42;
    static double mediumWeight = 0.29;
    static double longWeight = 0.29;
    static double triggerIntervalFactor = 0.5;
    static double shortIntervalFactor = 2; //2;

    static double mediumIntervalFactor = 8;
    static double volatilityInterval = 3600;
    static double volatilityTarget = 0.05;
    static double forecastScalar = 1;//4.75;
    // 16.67; //2;

    static double longIntervalFactor = 16;
    static HashMap<Double, Double> weights = new HashMap<Double, Double>();

    /// (lossScalingFactor);
    // atrStop * percentEquityRisked;
    private final ExecutionInstruction exeuctionMode = ExecutionInstruction.TAKER;
    private DiscreteAmount lastLongExitLimit;
    private DiscreteAmount lastShortExitLimit;
    private DiscreteAmount lastAsk;
    private DiscreteAmount lastBid;
    static double minSpread = 0.5;
    protected static Market farMarket;

    //double maxLossTarget = 0.25;
    @Transient
    public static double getDeviations() {

        return Double.valueOf("1");

    }

    @Inject
    public MomentumStrategy(Context context, Configuration config) {
        super(context, config);
        setTrendInterval(3600);

        //  String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.MONTH");
        // weights.put(getTrendInterval(), 1.0);
        weights.put(getTrendInterval(), 1.0);
        //    weights.put(getMedTrendInterval(), 0.25);
        //  weights.put(getLongTrendInterval(), 0.50);

        String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");

        Market market = (Market) Market.forSymbol(marketSymbol);
        if (market == null) {
            Exchange exchange = Exchange.forSymbol("OKCOIN_THISWEEK");
            Listing listing = Listing.forSymbol("BTC.USD.THISWEEK");
            Market.findOrCreate(exchange, listing);
            market = (Market) market.forSymbol(marketSymbol);

        }
        addMarket(market, 1.0);
        // addMarket(market, 0.3);
        String farMarketSymbol = ("OKCOIN_THISWEEK:LTC.USD.THISWEEK");

        farMarket = (Market) farMarket.forSymbol(farMarketSymbol);
        if (farMarket == null) {
            Exchange exchange = Exchange.forSymbol("OKCOIN_THISWEEK");
            Listing listing = Listing.forSymbol("LTC.USD.NEXTWEEK");
            Market.findOrCreate(exchange, listing);
            farMarket = (Market) farMarket.forSymbol(farMarketSymbol);

        }
        //   addMarket(farMarket, 0.3);
        // addMarket(farMarket, 0.0);

        String market2Symbol = ("POLONIEX:ETH.BTC");

        Market market2 = (Market) Market.forSymbol(market2Symbol);
        if (market2 == null) {
            Exchange exchange = Exchange.forSymbol("POLONIEX");
            Listing listing = Listing.forSymbol("ETH.BTC");
            Market.findOrCreate(exchange, listing);
            market2 = (Market) market2.forSymbol(market2Symbol);

        }
        //    addMarket(market2, 0.4);

        //addMarket(market2, 0.0);
        //  context.getInjector().injectMembers(market.getExchange());
        //context.getInjector().injectMembers(market);

        //  if (market.getExchange().getBalances() == null || market.getExchange().getBalances().isEmpty())
        //    market.getExchange().loadBalances(portfolio);
        setLimit(limit);
        setTimeToLive(timeToLive);
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
        setPositionInertia(positionInertia);
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

    public static class DataSubscriber {

        private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        @SuppressWarnings("rawtypes")
        public synchronized void update(Portfolio portfolio) {
            //so we need to delte allthe transactoins
            // and create on trnation eaul to our oringal balance
            PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);
            Amount totalBal = portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).plus(
                    portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()));
            if (previousBal == null)
                previousBal = totalBal;
            else
                previousBal = (totalBal.compareTo(previousBal) > 0) ? totalBal : previousBal;

            if (!monthlyReset)
                return;
            OrderService orderService = portfolio.context.getInjector().getInstance(OrderService.class);
            TransactionFactory transactionFactory = portfolio.context.getInjector().getInstance(TransactionFactory.class);

            // cancell all trigger orders
            log.info("Cancelling All Orders");
            for (Tradeable tradeable : portfolio.getMarkets()) {
                if (tradeable instanceof Market) {
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
                    Amount RealisedPnL = portfolioService.getBaseUnrealisedPnL(tradedCurrency);
                    if (fills != null && !fills.isEmpty() && !RealisedPnL.isZero()) {
                        Fill lastFill = fills.get(fills.size() - 1);
                        Transaction unRealisedPnL = transactionFactory.create(lastFill, portfolio, market.getExchange(), tradedCurrency,
                                TransactionType.REALISED_PROFIT_LOSS, RealisedPnL, new DiscreteAmount(0, tradedCurrency.getBasis()));

                        portfolio.context.setPublishTime(unRealisedPnL);
                        log.info("Publishing unrealised PnL as realised PnL: " + unRealisedPnL);
                        unRealisedPnL.persit();
                        portfolio.context.publish(unRealisedPnL);
                    }
                }
            }

            log.debug("Resetting all positions : " + portfolioService.getPositions());
            portfolio.positionReset();

            log.debug("Resetting all blanaces : " + portfolioService.getAvailableBalances());
            portfolioService.reset();
            for (Tradeable tradeable : portfolio.getMarkets())
                if (tradeable instanceof Market) {
                    Market market = (Market) tradeable;
                    market.getExchange().removeBalances();
                }
            previousBal = startingOrignalBal;

            originalBaseNotionalBalance = startingBaseNotionalBalance;

            portfolioService.loadBalances();
            log.info(this.getClass().getSimpleName() + ":update - Reset Balances Total Value (" + portfolio.getBaseAsset() + "):"
                    + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).plus(portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset())));

            /*          for (Market market : portfolio.getMarkets()) {
                          Iterator<Asset> iter = market.getExchange().getBalances().keySet().iterator();
                          while (iter.hasNext()) {
                              Asset balanceAsset = iter.next();
                              Balance balance = market.getExchange().getBalances().get(balanceAsset);
                              iter.remove();
                              //               market.getExchange().removeBalance(balance);
                              balance.delete();

                          }

                          for (Asset balanceAsset : market.getExchange().getBalances().keySet()) {
                              Balance balance = market.getExchange().getBalances().get(balanceAsset);
                              //               market.getExchange().removeBalance(balance);
                              balance.delete();
                          }

                          // for (Balance balance: market.getExchange().getBalances().get(balanceAsset) )
                          // market.getExchange().removeBalance(balance)
                          //  log.debug("Resetting all positions : " + portfolioService.getNetPosition(getMarket().getBase(), getMarket().getExchange()));
                          //balanceReset.reset();

                          Transaction rebalanceTransfer = transactionFactory.create(portfolio, market.getExchange(), portfolio.getBaseAsset(),
                                  TransactionType.CREDIT, previousBal, price);
                          portfolio.context.setPublishTime(rebalanceTransfer);
                          portfolio.context.publish(rebalanceTransfer);
                      }

                      DiscreteAmount price = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());
                      // startingOrignalBal;
                      Transaction initialCredit = transactionFactory.create(portfolio, market.getExchange(), portfolio.getBaseAsset(), TransactionType.CREDIT,
                              startingOrignalBal, price);
                      portfolio.context.setPublishTime(initialCredit);
                      portfolio.context.publish(initialCredit);
                  }*/

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
        public synchronized void update(Map map) {
            //  for (Object entry : map.keySet())
            //    if (map.get(entry) != null)
            if (weights.get(map.get("interval")) != null && (double) map.get("lookback") == 24.0)
                log.info("Forecast for market " + map.get("market") + " interval " + map.get("interval") + " forecast " + map.get("forecast") + " lookback "
                        + map.get("lookback"));
        }

        @SuppressWarnings("rawtypes")
        public synchronized void update(Long timestamp, Portfolio portfolio) {
            if (monthlyReset || previousRestBal == null || previousRestBal.isZero())
                return;
            //   Amount cashBal = portfolioService.getAvailableBaseBalance(market.getTradedCurrency());
            if (originalBaseNotionalBalance != null) {

                PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);
                OrderService orderService = portfolio.context.getInjector().getInstance(OrderService.class);
                TransactionFactory transactionFactory = portfolio.context.getInjector().getInstance(TransactionFactory.class);
                Amount scalcingFactor = DecimalAmount.of(Double.toString(rebalancingFactor));

                // cancell all trigger orders
                /*                log.info("Cancelling All Orders");

                                orderService.handleCancelAllShortOpeningSpecificOrders(portfolio, getMarket());
                                orderService.handleCancelAllShortClosingSpecificOrders(portfolio, getMarket(), ExecutionInstruction.TAKER);
                                orderService.handleCancelAllShortOpeningGeneralOrders(portfolio, getMarket());

                                orderService.handleCancelAllShortStopOrders(portfolio, getMarket());
                                orderService.handleCancelAllLongOpeningSpecificOrders(portfolio, getMarket());
                                orderService.handleCancelAllLongClosingSpecificOrders(portfolio, getMarket(), ExecutionInstruction.TAKER);
                                orderService.handleCancelAllLongOpeningGeneralOrders(portfolio, getMarket());
                                orderService.handleCancelAllLongStopOrders(portfolio, getMarket());

                                portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset());
                                List<Fill> fills = new ArrayList<Fill>();
                                for (Position position : portfolioService.getPositions())
                                    fills.addAll(position.getFills());

                                Collections.sort(fills, timeOrderIdComparator);
                                Amount RealisedPnL = portfolioService.getBaseUnrealisedPnL(getMarket().getTradedCurrency());
                                if (fills != null && !fills.isEmpty()) {
                                    Fill lastFill = fills.get(fills.size() - 1);
                                    Transaction unRealisedPnL = transactionFactory.create(lastFill, portfolio, getMarket().getExchange(), getMarket().getTradedCurrency(),
                                            TransactionType.REALISED_PROFIT_LOSS, RealisedPnL, new DiscreteAmount(0, getMarket().getTradedCurrency().getBasis()));

                                    portfolio.context.setPublishTime(unRealisedPnL);
                                    log.info("Publishing unrealised PnL as realised PnL: " + unRealisedPnL);
                                    unRealisedPnL.persit();
                                    portfolio.context.publish(unRealisedPnL);
                                }*/

                // so it get's the cash base balance
                //set the notional Balance balance;
                // balance to 50% of profits
                // resets all balances
                // then just credits a tranfer for all the base balance

                Amount cashBal = ((portfolioService.getBaseCashBalance(portfolio.getBaseAsset())).minus(previousRestBal)).times(scalcingFactor,
                        Remainder.ROUND_EVEN);
                log.info(portfolio.context.getTime() + "Calculating rebalancing portfolio equity risked:" + percentEquityRisked
                        + "portfolioBaseNotionalBalance: " + portfolio.getBaseNotionalBalance() + " previousRestBal: " + previousRestBal
                        + " originalNotionalBalanceUSD: " + originalBaseNotionalBalance + " balanceScalingAmount: " + balanceScalingAmount
                        + " startingNotionalBalanceUSD: " + startingBaseNotionalBalance + " baseCashBalance:"
                        + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " baseRealisedPnL:"
                        + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()));
                if (cashBal.isPositive()) {

                    Amount percentProfit = cashBal.dividedBy(previousRestBal, Remainder.ROUND_EVEN);
                    //BigDecimal PercentProfitBd = PercentProfit.asBigDecimal().setScale(0, RoundingMode.DOWN);
                    // if it is less then zero set ti back to orignal balance, otherwise times it by the whole mutliplier.
                    //  Double.toString(d)
                    // if (PercentProfit.compareTo(DecimalAmount.ONE.times(rebalancingFactor, Remainder.ROUND_EVEN)) > 0) {
                    // System.out.println("positive");
                    if (percentProfit.compareTo(DecimalAmount.ZERO) > 0) {

                        // BigDecimal PercentProfitBd = PercentProfit.asBigDecimal().setScale(0, RoundingMode.DOWN);
                        log.info(portfolio.context.getTime() + " / " + timestamp + " :Rebalancing portfolio with scale factor " + scalcingFactor
                                + " percentage profit " + percentProfit + " equity risked:" + percentEquityRisked + " previousRestBal: " + previousRestBal
                                + " originalNotionalBalanceUSD: " + originalBaseNotionalBalance + " balanceScalingAmount: " + balanceScalingAmount
                                + " startingNotionalBalanceUSD: " + startingBaseNotionalBalance + " baseCashBalance:"
                                + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " baseRealisedPnL:"
                                + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()));

                        // portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset());
                        //for(Market market: getMarketAllocations().keySet()){
                        originalBaseNotionalBalance = originalBaseNotionalBalance.times(percentProfit.plus(DecimalAmount.ONE), Remainder.ROUND_EVEN);
                        //          balanceScalingAmount = portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()).times(scalcingFactor, Remainder.ROUND_EVEN);

                        //  .getBaseCashBalance(portfolio.getBaseAsset()).minus(previousBal));
                        //.times(scalcingFactor,
                        // Remainder.ROUND_EVEN);
                        previousRestBal = portfolioService.getBaseCashBalance(portfolio.getBaseAsset());
                        // Amount transferBal = (portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).minus(previousBal)).negate();
                        // DiscreteAmount price = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());
                        //  previousBal = portfolioService.getBaseCashBalance(portfolio.getBaseAsset());

                        //              previousBal.times(percentProfit.plus(DecimalAmount.ONE), Remainder.ROUND_EVEN);
                        //  notionalBalanceUSD = notionalBalanceUSD.times(percentProfit.plus(DecimalAmount.ONE), Remainder.ROUND_EVEN);
                        // for (Position position : portfolioService.getPositions())
                        //                            fills.addAll(position.getFills());
                        //
                        //                        Collections.sort(fills, timeOrderIdComparator);
                        //                        Amount RealisedPnL = portfolioService.getBaseUnrealisedPnL(getMarket().getTradedCurrency());
                        //                        if (fills != null && !fills.isEmpty()) {
                        //                            Fill lastFill = fills.get(fills.size() - 1);
                        //                            Transaction unRealisedPnL = transactionFactory.create(lastFill, portfolio, getMarket().getExchange(), getMarket()
                        //                                    .getTradedCurrency(), TransactionType.REALISED_PROFIT_LOSS, RealisedPnL, new DiscreteAmount(0, getMarket()
                        //                                    .getTradedCurrency().getBasis()));
                        //
                        //                            portfolio.context.setPublishTime(unRealisedPnL);
                        //                            log.info("Publishing unrealised PnL as realised PnL: " + unRealisedPnL);
                        //                            unRealisedPnL.persit();
                        //                            portfolio.context.publish(unRealisedPnL);
                        //                        }

                        //                        log.debug("Resetting all blanaces : " + portfolioService.getAvailableBalances());
                        //                      portfolio.balanceReset();
                        //                    log.debug("Resetting all blanaces in portfolio: " + portfolioService.getAvailableBalances());

                        //                  portfolioService.reset();

                        // Balance balance;
                        /*      for (Market market : portfolio.getMarkets()) {
                                  Iterator<Asset> iter = market.getExchange().getBalances().keySet().iterator();
                                  while (iter.hasNext()) {
                                      Asset balanceAsset = iter.next();
                                      Balance balance = market.getExchange().getBalances().get(balanceAsset);
                                      iter.remove();
                                      //               market.getExchange().removeBalance(balance);
                                      balance.delete();

                                  }

                                  for (Asset balanceAsset : market.getExchange().getBalances().keySet()) {
                                      Balance balance = market.getExchange().getBalances().get(balanceAsset);
                                      //               market.getExchange().removeBalance(balance);
                                      balance.delete();
                                  }

                                  // for (Balance balance: market.getExchange().getBalances().get(balanceAsset) )
                                  // market.getExchange().removeBalance(balance)
                                  //  log.debug("Resetting all positions : " + portfolioService.getNetPosition(getMarket().getBase(), getMarket().getExchange()));
                                  //balanceReset.reset();

                                  Transaction rebalanceTransfer = transactionFactory.create(portfolio, market.getExchange(), portfolio.getBaseAsset(),
                                          TransactionType.CREDIT, previousBal, price);
                                  portfolio.context.setPublishTime(rebalanceTransfer);
                                  portfolio.context.publish(rebalanceTransfer);
                              }
                        */
                        // notionalBalanceUSD
                        // notionalBalanceUSD = originalNotionalBalanceUSD;
                        //DiscreteAmount price = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());
                        //log.debug("Resetting all blanaces : " + portfolioService.getAvailableBalances());
                        //portfolioService.resetBalances();
                        //log.debug("Resetting all positions : " + portfolioService.getNetPosition(getMarket().getBase(), getMarket().getExchange()));
                        //portfolio.reset();

                        // startingOrignalBal;
                        //Transaction initialCredit = transactionFactory.create(portfolio, getMarket().getExchange(), portfolio.getBaseAsset(),
                        //       TransactionType.CREDIT, previousBal, price);
                        //portfolio.context.setPublishTime(initialCredit);
                        //portfolio.context.publish(initialCredit);

                        //                        originalNotionalBalanceUSD = originalNotionalBalanceUSD.times(percentProfit.plus(DecimalAmount.ONE), Remainder.ROUND_EVEN);
                        //                        //.toBasis(
                        //                        // portfolio.getBaseAsset().getBasis(), Remainder.ROUND_EVEN);
                        //                        BigDecimal cashBalanceBd = portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).asBigDecimal();
                        // balanceScalingAmount = balanceScalingAmount.plus(cashBal);
                        //                        previousBal = portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).minus(balanceScalingAmount);
                        // if (portfolio.getBaseNotionalBalance().compareTo(notionalBaseBalance) != 0) {
                        //   portfolio.setBaseNotionalBalanceCount(notionalBaseBalance.toBasis(portfolio.getBaseAsset().getBasis(), Remainder.ROUND_EVEN)

                        //         .getCount());
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

    @Override
    // @When("select * from Book(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    //@When("select * from Book")
    //@When("select talib(\"rsi\", askPriceAsDouble, 14) as value from Book")
    void handleMarketMaker(Market maket, Book b) {
        // super.handleBook(b);
    }

    //    @When("@Priority(2) select * from Book(Book.market=TestStrategy.getMarket(),Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    public void handleBook(Tradeable market, Book b) {

        if (getPositions((Market) b.getMarket()) != null && !getPositions((Market) b.getMarket()).isEmpty()
                && positionMap.get(b.getMarket()).getType() != PositionType.EXITING) {

            createExits((Market) market, b);
        }

        //  }

        // }

        //  }
        lastAsk = b.getBestAsk().getPrice();
        lastBid = b.getBestBid().getPrice();
        // makeMarket(b);

        //

        //service.submit(new handleMarketMakerRunnable(b));
    }

    public void handleLongEntry(Market market, Bar b) {
        //if long, the close>open i.e. b.getClose()- b.getOpen()>0
        double timeFactor = 86400 / b.getInterval();
        double rawForecast = (b.getClose() - b.getOpen()) / (getPricePointsVol(market, getVolatilityInterval()) / Math.sqrt(timeFactor));
        //     double forecastScalar = 1;
        //   double forecast = Math.min(rawForecast * forecastScalar, 20);
        // then we  need to do wentry with this forcast.
        // forcast* insutment sclaar is the order size.
        //service.submit(new handleMarketMakerRunnable(b));
    }

    public void handleShortEntry(Market market, Book b) {

        //service.submit(new handleMarketMakerRunnable(b));
    }

    // want to exit longs (sell) when best bid < 12 hour low
    // LastBookWindow
    // @When("@Priority(4) on LastTradeWindow as trigger select trigger.priceCountAsDouble from ShortLowTradeIndicator where trigger.priceCountAsDouble<ShortLowTradeIndicator.low")

    //  @When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble from LongLowTradeIndicator where trigger.priceCountAsDouble<LongLowTradeIndicator.low")
    //so either last book was not less then the lowest trade indicator.
    //so the bid<ask!

    //  @When("@Priority(4) select * from SmoothedForecastWindow")
    @When("@Priority(4) select * from ForecastWindow")
    synchronized void handleForecast(Map forecastMap) {
        if ((forecastMap.get("market") != null && getMarketAllocation((Market) forecastMap.get("market")) == null)
                || (forecastMap.get("market") != null && getMarketAllocation((Market) forecastMap.get("market")) == 0.0) || interval == 0.0
                || weights.get(forecastMap.get("interval")) == null
                || (weights.get(forecastMap.get("interval")) != null && (double) forecastMap.get("lookback") != 64.0))

            return;
        // log.info("Long High Indicator Recived with Long Moving Average (should be positive):" + getLongMov() + " CCI (should be >100) : " + getLongCci());
        // if (getLongCci() < -100) {
        Market market = (Market) forecastMap.get("market");
        double pricePointsVolatility = (getPricePointsVol((Market) forecastMap.get("market"), getVolatilityInterval()));

        if (pricePointsVolatility == 0) {
            log.info("Handle forecast with prevented as pricePointsVolatility is zero at: " + context.getTime());
            //  || (positionMap.get(market) != null && positionMap.get(market).getType() == PositionType.ENTERING))
            return;

        }
        //Market market = (Market) forecastMap.get("market");
        //  DiscreteAmount forecastDiscrete = (new DiscreteAmount((long) ((double) forecastMap.get("forecast")), market.getPriceBasis()));

        //   forecastDiscrete.dividedBy(o, remainderHandler)

        double forecast = ((double) forecastMap.get("forecast")) / pricePointsVolatility;
        // need to noremalsi forcast
        // need to sca

        //  forecast = (forecast < 0) ? Math.max(forecast, -20.0) : Math.min(forecast, 20.0);

        log.info("At " + context.getTime() + " Handle forecast with " + market + " interval " + forecastMap.get("interval") + " forecast " + forecast
                + " lookback " + forecastMap.get("lookback"));
        //  public void handleEnterOrder(Market market, double entryPrice, double scaleFactor, double interval, ExecutionInstruction execInst, double forecast) {

        //  super.handleEnterOrder(market, (double) forecastMap.get("close"), weights.get(forecastMap.get("interval")), (double) forecastMap.get("interval"),
        //        ExecutionInstruction.TAKER, forecast);

    }

    public synchronized void update(Map map) {
        //  for (Object entry : map.keySet())
        //    if (map.get(entry) != null)
        if (weights.get(map.get("interval")) != null && (double) map.get("lookback") == 24.0)
            log.info("Forecast for market " + map.get("market") + " interval " + map.get("interval") + " forecast " + map.get("forecast") + " lookback "
                    + map.get("lookback"));
    }

    //@When("@Priority(4) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortLowTradeIndicator h where trigger.priceCountAsDouble<ShortLowTradeIndicator.low and trigger.market=ShortLowTradeIndicator.market")
    synchronized void handleShortLowIndicator(double d, Market market, double interval) {
        // exit high so I will sell at 
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0 || interval != getTrendInterval())
            return;
        log.info("Short Low Indicator Recived with price " + d + " with interval " + interval);

        double tragetPrice = d * atrTarget;
        // double tragetPrice = bestBid.getPriceCount() - (tragetAmount * priceScaling);
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());

        super.handleShortLowIndicator(interval, targetDiscrete.getCount(), ExecutionInstruction.TAKER, market);
    }

    // want to exit short (buy) when best ask > 12 hour low
    //  @When("@Priority(4) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from ShortHighTradeIndicator h where trigger.priceCountAsDouble>ShortHighTradeIndicator.high and trigger.market=ShortHighTradeIndicator.market")
    synchronized void handleShortHighIndicator(double d, Market market, double interval) {
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0 || interval == 0.0)
            return;
        log.info("Short High Indicator Recived with price " + d + " and interval " + interval);

        double tragetPrice = d * atrTarget;
        // double tragetPrice = bestBid.getPriceCount() - (tragetAmount * priceScaling);
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());

        super.handleShortHighIndicator(interval, targetDiscrete.getCount(), ExecutionInstruction.TAKER, market);
    }

    //  @When("@Priority(3) on LastBarWindow as trigger select trigger.high, trigger.market, h.interval from LongHighTradeIndicator h where trigger.interval=TrendStrategy.getTriggerTrendInterval() and trigger.high>LongHighTradeIndicator.high and trigger.market=LongHighTradeIndicator.market")
    // @When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongHighTradeIndicator h where trigger.priceCountAsDouble>LongHighTradeIndicator.high and trigger.market=LongHighTradeIndicator.market")
    synchronized void handleLongHighIndicator(double d, Market market, double interval) {
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0 || interval == 0.0)
            return;
        log.info("Long High Indicator Recived with price " + d + " interval " + interval + " Long Moving Average (should be positive):" + getLongMov()
                + " CCI (should be >100) : " + getLongCci());
        //if (getLongCci() > 100) {

        /*        if (!orderService.getPendingShortOpenOrders(portfolio, market).isEmpty()
                        || (positionMap.get(market) != null && positionMap.get(market).getPosition() != null && getShortPosition(market).getOpenVolume().isNegative())) {

                    log.info(this.getClass().getSimpleName() + ": handleLongHighIndicator - Already have short position to exit first in positionMap "
                            + positionMap.get(market).getPosition() + " with short position: " + getShortPosition(market) + " and open orders: "
                            + orderService.getPendingShortOpenOrders(portfolio, market));

                    handleShortHighIndicator(d, ExecutionInstruction.TAKER, market);
                    revertPositionMap(market);
                    //   handleLongHighIndicator(d, market);
                    ///  return;
                }*/
        // if (getLongMov() > 0) {
        //> 0 && getLongCci() > 100) {
        // log.info("Long High Indicator Recived with Long Moving Average (should be positive):" + getLongMov() + " CCI (should be >100) : " + getLongCci());
        double tragetPrice = d * atrTarget;
        // double tragetPrice = bestBid.getPriceCount() - (tragetAmount * priceScaling);
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());
        //log.info("Adjusting long stops");

        //super.updateLongStops(quotes.getLastAskForMarket(market));
        //  super.handleLongHighIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, market, null);
        // }
        // }
    }

    // select prev(1, low) from LatestBarWindow as longlow

    // on last trade if the price < min of min trades window || ohlc bar.

    //  @When("@Priority(3) on LastBarWindow as trigger select trigger.low, trigger.market, h.interval from LongLowTradeIndicator h where trigger.interval=TrendStrategy.getTriggerTrendInterval() and LongLowTradeIndicator.market=trigger.market and trigger.low<LongLowTradeIndicator.low")
    // @When("@Priority(3) on LastTradeWindow as trigger select trigger.priceCountAsDouble, trigger.market, h.interval from LongLowTradeIndicator h where LongLowTradeIndicator.market=trigger.market and trigger.priceCountAsDouble<LongLowTradeIndicator.low")
    synchronized void handleLongLowIndicator(double d, Market market, double interval) {
        if (getMarketAllocation(market) == null || getMarketAllocation(market) == 0.0 || interval == 0.0)
            return;
        // log.info("Long High Indicator Recived with Long Moving Average (should be positive):" + getLongMov() + " CCI (should be >100) : " + getLongCci());
        // if (getLongCci() < -100) {
        log.info("Long Low Indicator Recived with price " + d + " interval " + interval + " Long Moving Average (should be positive):" + getShortMov()
                + " CCI (should be <100) : " + getLongCci());

        /* if (!orderService.getPendingLongOpenOrders(portfolio, market).isEmpty()
                 || (positionMap.get(market) != null && positionMap.get(market).getPosition() != null && getLongPosition(market).getOpenVolume().isPositive())) {

             log.info(this.getClass().getSimpleName() + ": handleLongLowIndicator - Already have long position map " + positionMap.get(market).getPosition() + " long position: "
                     + getLongPosition(market) + " and open orders " + orderService.getPendingLongOpenOrders(portfolio, market));

             handleShortLowIndicator(d, ExecutionInstruction.TAKER, market);

             revertPositionMap(market);
             //          handleLongLowIndicator(d, market);

            // return;
         }*/
        //  if (getShortMov() > 0) {
        //&& getLongCci() < -100) {

        // 
        //  log.info("Long Low Indicator Recived with Long Moving Average (should be positive):" + getShortMov() + " CCI (should be <100) : " + getLongCci());
        double tragetPrice = d * atrTarget;
        // double tragetPrice = bestBid.getPriceCount() - (tragetAmount * priceScaling);
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());
        // short I am selling at the lowest price
        //log.info("Adjusting long stops");
        //  super.updateShortStops(quotes.getLastBidForMarket(market));

        //  Long Stop Order with Target PriceLong Stop Order with Target Price        
        super.handleLongLowIndicator(interval, weights.get(interval), targetDiscrete.getCount(), exeuctionMode, market, null);
        // }

    }

    //far price  < near price  exit longs and go short
    // so we are expecting prices to fall, so the price we can buy at is falling.
    //@When("@Priority(3) on FarBuyWindow as trigger select trigger.priceCountAsDouble from NearBuyWindow where trigger.priceCountAsDouble<NearBuyWindow.priceCountAsDouble")
    //  @When("@Priority(3) select entry from EnterShortCarryIndicator")
    synchronized void handleEnterShortCarryIndicator(double d, Market market) {
        Collection<Market> markets = new ArrayList<Market>();
        markets.add(market);
        markets.add(getFarMarket());
        log.info("Enter short carry Indicator Recived with price " + d + " last book for " + market + ":" + quotes.getLastBook(market) + "last near trade:"
                + quotes.getLastTrade(market) + " last far trade:" + quotes.getLastTrade(farMarket));
        for (Market tradedMarket : markets) {

            if (positionMap.get(tradedMarket) != null && getLongPosition(tradedMarket, interval).getOpenVolume().isPositive()) {

                log.info("Short Entry prevented as already have long position " + positionMap.get(tradedMarket).getPosition());

                handleShortLowIndicator(d, d, ExecutionInstruction.TAKER, tradedMarket);
                revertPositionMap(tradedMarket);

                return;
            }
        }
        double tragetPrice = d * atrTarget;
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());
        super.handleLongLowIndicator(1.0, 1.0, targetDiscrete.negate().getCount(), ExecutionInstruction.MAKER, market, getFarMarket());
    }

    // far price > near price exit shorts and go long if buying, price can buy for is always > price I can sell for
    // we are buy so expecting prices to rise, so the price we can sell at is rising
    //  @When("@Priority(3) on FarSellWindow as trigger select trigger.priceCountAsDouble from NearSellWindow where trigger.priceCountAsDouble>NearSellWindow.priceCountAsDouble")
    // @When("@Priority(3) select entry from EnterLongCarryIndicator")
    synchronized void handleEnterLongCarryIndicator(double d, Market market) {
        log.info("EnterLongEnterLong carry Indicator Recived with price " + d + " last book for " + market + ":" + quotes.getLastBook(market)
                + "last near trade:" + quotes.getLastTrade(market) + " last far trade:" + quotes.getLastTrade(farMarket));
        Collection<Market> markets = new ArrayList<Market>();
        markets.add(market);
        markets.add(getFarMarket());
        for (Market tradedMarket : markets) {

            if (positionMap.get(tradedMarket) != null && getShortPosition(tradedMarket, interval).getOpenVolume().isNegative()) {

                log.info("Long Entry prevented as already have short position " + positionMap.get(tradedMarket).getPosition());

                handleShortHighIndicator(d, d, ExecutionInstruction.TAKER, tradedMarket);
                revertPositionMap(tradedMarket);
                return;
            }
        }
        double tragetPrice = d * atrTarget;
        DiscreteAmount targetDiscrete = new DiscreteAmount((long) (tragetPrice), market.getPriceBasis());
        //super.handleLongHighIndicator(1.0, 1.0, targetDiscrete.getCount(), ExecutionInstruction.MAKER, market, getFarMarket());
    }

    @Transient
    public static Double getTrendInterval() {
        return trendInterval * shortIntervalFactor;
    }

    @Transient
    public static Double getForecastScalar() {
        return forecastScalar;
    }

    @Transient
    public static Double getLongTrendInterval() {
        return trendInterval * longIntervalFactor;
    }

    @Transient
    public static Double getTriggerTrendInterval() {
        return trendInterval * triggerIntervalFactor;
    }

    @Transient
    public static Double getMedTrendInterval() {
        return trendInterval * mediumIntervalFactor;
    }

    @Override
    @Transient
    public double getBidATR(Market market) {
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
                    if (attibutes.get("market").equals(market)) {
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
    public double getTradeATR(Tradeable market) {
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
                    if (attibutes.get("market").equals(market)) {
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
    public double getAskATR(Market market) {
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
                    if (attibutes.get("market").equals(market)) {
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

    @Transient
    public double getLongMov() {
        List<Object> events = null;
        double mov = 0;
        try {
            events = context.loadStatementByName("GET_MOV_LONG_HIGH");
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
    public double getLongCci() {
        List<Object> events = null;
        double mov = 0;
        try {
            events = context.loadStatementByName("GET_CCI");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("cci") != null) {
                    mov = (double) value.get("cci");
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
            events = context.loadStatementByName("GET_MOV_LONG_LOW");
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

}
