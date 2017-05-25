package org.cryptocoinpartners.bin;

import java.util.List;
import java.util.concurrent.Semaphore;

import javax.inject.Inject;

import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.JMXManager;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
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

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "backtest", commandDescription = "backtest a strategy (not functional)")
public class BacktestRunMode extends RunMode {

    @Inject
    protected transient ReplayFactory replayFactory;

    @Parameter(description = "Strategy name to load", arity = 1, required = true)
    public List<String> strategyNames;
    private Context context;
    Semaphore backTestSemaphore = new Semaphore(0);
    // List<Market> markets = new ArrayList<Market>();

    //
    //private final Instant start = new DateTime(2015, 02, 15, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant start = new DateTime(2015, 8, 30, 16, 50, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 2, , 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // 
    //private final Instant end = new DateTime(2014, 06, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    //  private final Instant start = new DateTime(2014, 05, 15, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //prod replay
    //

    // private final Instant start = new DateTime(2016, 07, 03, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2016, 05, 03, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant end = new DateTime(2016, 07, 18, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//
    // aws replay 
    private final Instant start = new DateTime(2016, 05, 16, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    private final Instant end = new DateTime(2017, 01, 26, 0, 0, 0, DateTimeZone.UTC).toInstant();//
    //
    //  private final Instant start = new DateTime(2016, 8, 19, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant start = new DateTime(2016, 8, 7, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //  private final Instant start = new DateTime(2016, 07, 25, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // private final Instant start = new DateTime(2016, 07, 4, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant end = new DateTime(2016, 8, 9, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    // private final Instant end = new DateTime(2016, 9, 15, 0, 0, 0, DateTimeZone.UTC).toInstant();//
    //  private final Instant start = new DateTime(2014, 05, 4, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant end = new DateTime(2014, 05, 8, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    //long r
    //

    //  private final Instant end = new DateTime(2015, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //

    //     private final Instant start = new DateTime(2014, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//
    //   private final Instant end = new DateTime(2015, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    //  private final Instant end = new DateTime(2015, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();;//
    // private final Instant end = new DateTime(2016, 03, 06, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //

    // private final Instant start = new DateTime(2014, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // private final Instant start = new DateTime(2014, 8, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant end = start.toDateTime().plus(Months.ONE).toInstant();
    //out of seq
    //  private final Instant start = new DateTime(2015, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//
    // private final Instant end = new DateTime(2016, 06, 8, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //rolling
    // private final Instant start = new DateTime(2014, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant end = start.toDateTime().plus(rollingWindow).toInstant();

    // private final Instant end = new DateTime(2015, 02, 24, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//
    // private final Instant start = new DateTime(2015, 02, 17, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //private final Instant end = new DateTime(2016, 04, 18, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    // private final Instant end = new DateTime(2016, 04, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    //   private final Instant start = new DateTime(2014, 04, 13, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2016, 02, 24, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //LongRun
    //private final Instant start = new DateTime(2014, 04, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // private final Instant start = new DateTime(2016, 4, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 2, 24, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant start = new DateTime(2014, 03, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2014, 04, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2014, 05, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2014, 06, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //

    // private final Instant start = new DateTime(2014, 8, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant start = new DateTime(2014, 9, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2014, 10, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2014, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant start = new DateTime(2014, 12, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 02, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 03, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 04, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 05, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant start = new DateTime(2015, 06, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //
    // private final Instant start = new DateTime(2015, 7, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 8, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 9, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 10, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //  private final Instant start = new DateTime(2015, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant end = start.toDateTime().plus(Months.ONE).toInstant();
    // private final Instant end = new DateTime(2014, 03, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant end = new DateTime(2016, 03, 29, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    //private final Instant end = new DateTime(2016, 02, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

    //private final Instant start = new DateTime(2015, 12, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //  private final Instant start = new DateTime(2015, 03, 11, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant end = new DateTime(2015, 03, 15, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // private final Instant start = new DateTime(2015, 06, 19, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //  private final Instant end = new DateTime(2016, 01, 12, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // private final Instant start = new DateTime(2015, 12, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //private final Instant start = new DateTime(2015, 12, 22, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // private final Instant end = new DateTime(2015, 8, 31, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //private final Instant start = new DateTime(2014, 9, 9, 23, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant end = new DateTime(2014, 9, 10, 6, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //
    // private final Instant end = new DateTime(2015, 02, 27, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // // private final Instant end = new DateTime(2015, 8, 7, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //  @Parameter(names = { "-p", "--position" }, arity = 2, description = "specify initial portfolio positions as {Exchange}:{Asset} {Amount} e.g. BITFINEX:BTC 1.0")
    // public List<String> positions = Arrays.asList("BITSTAMP:USD", "1000000");
    // public List<String> positions = Arrays.asList("OKCOIN_THISWEEK:USD", "3734375");
    //public List<String> positions = Arrays.asList("OKCOIN_THISWEEK:USD", "1000000");
    //  public List<String> positions = Arrays.asList("OKCOIN_THISWEEK:USD", "40000");
    // Long bal = ConfigUtil.combined().getLong("base.notional.balance", 100000) / Asset.forSymbol(ConfigUtil.combined().getString("base.symbol", "USD"));
    // new DiscreteAmount((long) 
    // long balance = (ConfigUtil.combined().getLong("xchange.okcoin_thisweek.balance", 10000) / (getMarket().getQuote().getBasis()));

    //    , getMarket()
    //  .getQuote().getBasis());
    // public List<String> positions = Arrays.asList("OKCOIN_THISWEEK:USD", "1000000");

    // strategy.rawticks

    @Parameter(names = { "-" }, description = "No-op switch used to end list of positions before supplying the strategy name")
    boolean noop = false;

    @Override
    public void run(Semaphore semaphore) {
        //PersistUtil.purgeTransactions();
        //Replay replay = Replay.all(true);
        //Replay replay = Replay.between(start, end, true);  
        //  Replay replay;
        // if (ConfigUtil.combined().getBoolean("randomticker", false))
        Replay replay = replayFactory.between(start, end, false, backTestSemaphore, ConfigUtil.combined().getBoolean("randomticker", false));

        //else
        //  replay = replayFactory.between(start, end, false, backTestSemaphore);
        //  while (backTestSemaphore.availablePermits())
        //backTestSemaphore.availablePermits()
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

            //setUpInitialPortfolio(strategyInstance);

            // context.getInjector().getInstance(cls)

        }
        // this should be run on seperate thread
        //service = Executors.newSingleThreadExecutor();
        //	Replay replayThread = new Replay();
        //service.submit(replay);
        //replay.getContext()
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
        // todo report P&L, etc.
    }

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }
}
