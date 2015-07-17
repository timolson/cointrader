package org.cryptocoinpartners.bin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Holding;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.StrategyInstance;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
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

    @Parameter(description = "Strategy name to load", arity = 1, required = true)
    public List<String> strategyNames;
    private Context context;
    private static ExecutorService service;
    Semaphore backTestSemaphore = new Semaphore(0);

    private final Instant start = new DateTime(2015, 02, 15, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant start = new DateTime(2015, 2, , 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    // private final Instant start = new DateTime(2014, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //private final Instant end = new DateTime(2015, 02, 23, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    private final Instant end = new DateTime(2015, 02, 27, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    //private final Instant start = new DateTime(2014, 9, 9, 23, 0, 0, 0, DateTimeZone.UTC).toInstant();
    //private final Instant end = new DateTime(2014, 9, 10, 6, 0, 0, 0, DateTimeZone.UTC).toInstant();

    @Parameter(names = { "-p", "--position" }, arity = 2, description = "specify initial portfolio positions as {Exchange}:{Asset} {Amount} e.g. BITFINEX:BTC 1.0")
    // public List<String> positions = Arrays.asList("BITSTAMP:USD", "1000000");
    public List<String> positions = Arrays.asList("OKCOIN_THISWEEK:USD", "1000000");

    @Parameter(names = { "-" }, description = "No-op switch used to end list of positions before supplying the strategy name")
    boolean noop = false;

    @Override
    public void run(Semaphore semaphore) {
        //PersistUtil.purgeTransactions();
        //Replay replay = Replay.all(true);
        //Replay replay = Replay.between(start, end, true);
        Replay replay = Replay.between(start, end, true, backTestSemaphore);
        context = replay.getContext();
        context.attach(XchangeAccountService.class);
        context.attach(BasicQuoteService.class);
        context.attach(BasicPortfolioService.class);
        context.attach(MockOrderService.class);
        OrderService orderService = context.getInjector().getInstance(OrderService.class);
        orderService.setTradingEnabled(true);

        for (String strategyName : strategyNames) {
            StrategyInstance strategyInstance = new StrategyInstance(strategyName);
            context.attachInstance(strategyInstance);

            setUpInitialPortfolio(strategyInstance);

            // context.getInjector().getInstance(cls)

        }
        // this should be run on seperate thread
        //service = Executors.newSingleThreadExecutor();
        //	Replay replayThread = new Replay();
        //service.submit(replay);

        replay.run();
        if (semaphore != null)
            semaphore.release();
        //System.exit(0);
        // todo report P&L, etc.
    }

    private void setUpInitialPortfolio(StrategyInstance strategyInstance) {
        // @Inject
        // Portfolio portfolio;
        // ;= context.getInjector().getInstance(Portfolio.class);
        Portfolio portfolio = strategyInstance.getPortfolio();
        if (positions.size() % 2 != 0) {
            System.err.println("You must supply an even number of arguments to the position switch. " + positions);
        }
        for (int i = 0; i < positions.size() - 1;) {
            Holding holding = Holding.forSymbol(positions.get(i++));
            //	Long str = (positions.get(i++));
            DiscreteAmount amount = new DiscreteAmount(Long.parseLong(positions.get(i++)), holding.getAsset().getBasis());
            DiscreteAmount price = new DiscreteAmount(0, holding.getAsset().getBasis());
            Transaction initialCredit = new Transaction(portfolio, holding.getExchange(), holding.getAsset(), TransactionType.CREDIT, amount, price);
            context.publish(initialCredit);
            initialCredit.persit();

            strategyInstance.getStrategy().init();

        }
    }

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }
}
