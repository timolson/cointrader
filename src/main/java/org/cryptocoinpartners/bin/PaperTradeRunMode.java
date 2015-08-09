package org.cryptocoinpartners.bin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.SaveMarketData;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.module.xchange.XchangeData;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Holding;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.StrategyInstance;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.util.Replay;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.espertech.esper.client.time.TimerControlEvent;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = { "paper" }, commandDescription = "Run strategies against live streaming data but use the mock order system instead of live trades")
public class PaperTradeRunMode extends RunMode {
    public List<String> positions = Arrays.asList("OKCOIN:USD", "1000000"); //private final Instant start = new DateTime(2014, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    final ExecutorService service = Executors.newSingleThreadExecutor();
    private final Instant end = new DateTime(DateTime.now()).toInstant();
    Semaphore paperSemaphore = new Semaphore(0);
    private final Instant start = end.minus(Duration.standardHours(2)).toInstant();

    // new DateTime(2013, 12, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    @Override
    public void run(Semaphore semaphore) {
        //context = Context.create();
        Replay replay = Replay.between(start, end, true, paperSemaphore);
        context = replay.getContext();
        // context = Context.create();

        context.attach(XchangeAccountService.class);
        context.attach(BasicQuoteService.class);
        context.attach(BasicPortfolioService.class);
        context.attach(MockOrderService.class);

        for (String strategyName : strategyNames) {
            StrategyInstance strategyInstance = new StrategyInstance(strategyName);
            context.attachInstance(strategyInstance);
            setUpInitialPortfolio(strategyInstance);
            // context.getInjector().getInstance(cls)

        }

        // Future future = service.submit(replay);
        //while (!future.isDone()) {

        //}

        replay.run();
        context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));
        context.setTimeProvider(null);
        OrderService orderService = context.getInjector().getInstance(OrderService.class);
        // context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));

        //  context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));
        context.attach(SaveMarketData.class);
        context.attach(XchangeData.class);
        orderService.setTradingEnabled(true); //  context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));

        //  if (semaphore != null)
        //    semaphore.release();

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
            //  Long str = (positions.get(i++));
            DiscreteAmount amount = new DiscreteAmount(Long.parseLong(positions.get(i++)), holding.getAsset().getBasis());
            DiscreteAmount price = new DiscreteAmount(0, holding.getAsset().getBasis());
            Transaction initialCredit = new Transaction(portfolio, holding.getExchange(), holding.getAsset(), TransactionType.CREDIT, amount, price);
            context.publish(initialCredit);
            initialCredit.persit();

            strategyInstance.getStrategy().init();

        }
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(description = "Strategy name to load", arity = 1, required = true)
    public List<String> strategyNames;
    private Context context;

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }
}
