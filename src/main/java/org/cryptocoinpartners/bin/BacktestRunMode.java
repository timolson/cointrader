package org.cryptocoinpartners.bin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.inject.Inject;

import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.JMXManager;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.ReplayFactory;
import org.cryptocoinpartners.schema.StrategyInstance;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.Replay;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/** @author Tim Olson */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "backtest", commandDescription = "backtest a strategy (not functional)")
public class BacktestRunMode extends RunMode {

  @Inject protected transient ReplayFactory replayFactory;

  @Parameter(description = "Strategy name to load", arity = 1, required = true)
  public List<String> strategyNames;

  private Context context;
  Semaphore backTestSemaphore = new Semaphore(0);

  // aws replay (LTC/BTC)
  // private final Instant start = new DateTime(2017, 7, 06, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // aws replay (AllMarkets)
  // private final Instant start = new DateTime(2017, 7, 04, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // private final Instant end = new DateTime(2017, 11, 30, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // out of sample
  //	private final Instant start = new DateTime(2018, 04, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  // private final Instant end = new DateTime(2018, 07, 02, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // private final Instant start = new DateTime(2017, 12, 11, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  //	private final Instant end = new DateTime(2017, 12, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // devdata
  // private final Instant start = new DateTime(2017, 12, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  // private final Instant end = new DateTime(2019, 04, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // backtester
  //

  // private final Instant start = new DateTime(2018, 3, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  private final Instant start = new DateTime(2019, 7, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

  //

  private final Instant end = new DateTime(2021, 05, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
  // backtesterparis

  // private final Instant start = new DateTime(2019, 9, 04, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // SWAPS
  // private final Instant start = new DateTime(2020, 3, 26, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant start =
  //    new DateTime(2020, 4, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant(); //
  // SWAPS
  // private final Instant start =
  //   new DateTime(2020, 03, 19, 0, 0, 0, 0, DateTimeZone.UTC).toInstant(); //
  // private final Instant start = new DateTime(2019, 9, 16, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant start = new DateTime(2019, 8, 28, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant start =
  //   new DateTime(2020, 04, 14, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
  // private final Instant start =
  //  new DateTime(2019, 10, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

  // private final Instant start = new DateTime(2019, 9, 06, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant end = new DateTime(2021, 04, 20, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  //  private final Instant start =
  //    new DateTime(2021, 03, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
  // private final Instant start =
  //  new DateTime(2021, 03, 02, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

  /*private final Instant start =
        new DateTime(2020, 06, 04, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    private final Instant end = new DateTime(2021, 04, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
  */
  // private final Instant start =
  //   new DateTime(2019, 8, 12, 0, 0, 0, 0, DateTimeZone.UTC).toInstant(); //
  //
  // private final Instant end = new DateTime(2020, 05, 9, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // private final Instant start = new DateTime(2018, 12, 07, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant end = new DateTime(2018, 12, 12, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // private final Instant start = new DateTime(2019, 01, 23, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant end = new DateTime(2019, 01, 25, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // prodbacktester trend
  // private final Instant start = new DateTime(2016, 04, 22, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  // private final Instant end = new DateTime(2018, 11, 28, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // EWC/EWA backtest
  // private final Instant start = new DateTime(2018, 10, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  // private final Instant end = new DateTime(2018, 11, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // prodbacktester pairs
  // private final Instant start = new DateTime(2019, 8, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//

  // private final Instant start = new DateTime(2018, 11, 15, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  //

  // private final Instant start = new DateTime(2019, 05, 15, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//

  //// private final Instant start = new DateTime(2019, 04, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  //
  //	private final Instant start = new DateTime(2019, 02, 05, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  //
  //
  // private final Instant start = new DateTime(2018, 10, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // btc/btc
  // private final Instant start = new DateTime(2019, 10, 01, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant end = new DateTime(2022, 11, 9, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  // btc/btc
  // private final Instant start = new DateTime(2019, 9, 16, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();
  // private final Instant end = new DateTime(2022, 11, 9, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();

  private final Set<StrategyInstance> strategyInstances = new HashSet<StrategyInstance>();
  //
  // out of sample tester (from 2014-01-07 to 2017-06-05)
  // private final Instant start = new DateTime(2016, 06, 10, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//
  // private final Instant end = new DateTime(2017, 11, 15, 0, 0, 0, 0,
  // DateTimeZone.UTC).toInstant();//

  @Parameter(
      names = {"-"},
      description = "No-op switch used to end list of positions before supplying the strategy name")
  boolean noop = false;

  @Override
  public void run(Semaphore semaphore) {
    // PersistUtil.purgeTransactions();
    // Replay replay = Replay.all(true);
    // Replay replay = Replay.between(start, end, true);
    //  Replay replay;
    // if (ConfigUtil.combined().getBoolean("randomticker", false))
    Replay replay =
        replayFactory.between(
            start,
            end,
            false,
            backTestSemaphore,
            ConfigUtil.combined().getBoolean("randomticker", false),
            ConfigUtil.combined().getBoolean("strategy.prefeed.books", true),
            false,
            null);

    // else
    //  replay = replayFactory.between(start, end, false, backTestSemaphore);
    //  while (backTestSemaphore.availablePermits())
    // backTestSemaphore.availablePermits()
    //  rootInjector.createChildInjector(new PersistanceModule());

    context = replay.getContext();

    context.attach(XchangeAccountService.class);
    context.attach(BasicQuoteService.class);
    context.attach(BasicPortfolioService.class);
    context.attach(MockOrderService.class);
    //  context.attach(JMXManager.class);
    //

    OrderService orderService = context.getInjector().getInstance(OrderService.class);
    context.attach(JMXManager.class);
    //  Manager.manage("org.cryptocoinpartners.cointrader", context.getInjector().getInjector());
    //  context.attach(JMXManager.class);
    orderService.setTradingEnabled(true);
    // context.get
    for (String strategyName : strategyNames) {
      StrategyInstance strategyInstance = new StrategyInstance(strategyName);
      context.attachInstance(strategyInstance);
      strategyInstance.getStrategy().init();
      strategyInstances.add(strategyInstance);

      // strategyInstance.getP
      // setUpInitialPortfolio(strategyInstance);

      // context.getInjector().getInstance(cls)

    }
    // this should be run on seperate thread
    // service = Executors.newSingleThreadExecutor();
    //	Replay replayThread = new Replay();
    // service.submit(replay);
    // replay.getContext()
    replay.run();
    while (backTestSemaphore.availablePermits() > 0) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        log.debug(this.getClass().getSimpleName() + ": replaying historic prices", e);
      }
    }

    log.info("Back test completed");
    for (StrategyInstance strategyInstance : strategyInstances) {
      log.info(
          this.getClass().getSimpleName()
              + ":run - Portfolio: "
              + strategyInstance.getPortfolio()
              + " Total Cash Value ("
              + strategyInstance.getPortfolio().getBaseAsset()
              + "):"
              + strategyInstance
                  .getPortfolioService()
                  .getBaseCashBalance(strategyInstance.getPortfolio().getBaseAsset())
                  .plus(
                      strategyInstance
                          .getPortfolioService()
                          .getBaseUnrealisedPnL(strategyInstance.getPortfolio().getBaseAsset()))
              + ", Total Notional Value ("
              + strategyInstance.getPortfolio().getBaseAsset()
              + "):"
              + strategyInstance
                  .getPortfolio()
                  .getStartingBaseNotionalBalance()
                  .plus(
                      strategyInstance
                          .getPortfolioService()
                          .getBaseCashBalance(strategyInstance.getPortfolio().getBaseAsset()))
                  .plus(
                      strategyInstance
                          .getPortfolioService()
                          .getBaseUnrealisedPnL(strategyInstance.getPortfolio().getBaseAsset()))
                  .minus(strategyInstance.getPortfolio().getStartingBaseCashBalance())
              + " (Cash Balance:"
              + strategyInstance
                  .getPortfolioService()
                  .getBaseCashBalance(strategyInstance.getPortfolio().getBaseAsset())
              + " Realised PnL (M2M):"
              + strategyInstance
                  .getPortfolioService()
                  .getBaseRealisedPnL(strategyInstance.getPortfolio().getBaseAsset())
              + " Open Trade Equity:"
              + strategyInstance
                  .getPortfolioService()
                  .getBaseUnrealisedPnL(strategyInstance.getPortfolio().getBaseAsset())
              + " MarketValue:"
              + strategyInstance
                  .getPortfolioService()
                  .getBaseMarketValue(strategyInstance.getPortfolio().getBaseAsset())
              + ")");
      for (Position position : strategyInstance.getPortfolio().getNetPositions()) {
        log.info(
            this.getClass().getSimpleName()
                + ":run - Portfolio: "
                + strategyInstance.getPortfolio()
                + " Instrument: "
                + position.getAsset()
                + " Position: "
                + position.toString());
        log.info(
            this.getClass().getSimpleName()
                + ":run - Portfolio: "
                + strategyInstance.getPortfolio()
                + " Instrument: "
                + position.getAsset()
                + " position: "
                + position.getUuid()
                + " fills: "
                + position.getFills());
      }
    }

    // todo report P&L, etc.
  }

  @Override
  public void run() {
    Semaphore semaphore = null;
    run(semaphore);
  }
}
