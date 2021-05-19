package org.cryptocoinpartners.bin;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import javax.inject.Inject;

import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.JMXManager;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.module.xchange.XchangeData;
import org.cryptocoinpartners.schema.ReplayFactory;
import org.cryptocoinpartners.schema.StrategyInstance;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.OrderService;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.espertech.esper.client.time.TimerControlEvent;

/** @author Tim Olson */
@SuppressWarnings("UnusedDeclaration")
@Parameters(
    commandNames = {"testing"},
    commandDescription =
        "Run strategies against live streaming data and exchange order book for live trades")
public class TestingRunMode extends RunMode {
  @Inject protected transient TransactionFactory transactionFactory;

  @Inject protected transient ReplayFactory replayFactory;

  // public List<String> positions = Arrays.asList("OKCOIN:USD", "1000000"); //private final Instant
  // start = new DateTime(2014, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
  // public List<String> positions = Arrays.asList("OKCOIN_THISWEEK:USD", "40000");

  final ExecutorService service = Executors.newSingleThreadExecutor();

  // new DateTime(2013, 12, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

  @Override
  public void run(Semaphore semaphore) {
    context = Context.create();

    context.attach(XchangeAccountService.class);
    context.attach(BasicQuoteService.class);
    context.attach(BasicPortfolioService.class);
    context.attach(MockOrderService.class);
    context.attach(JMXManager.class);

    // context.esperConfig.getEngineDefaults().getThreading().setInternalTimerEnabled(false);
    context.setTimeProvider(null);
    context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));
    OrderService orderService = context.getInjector().getInstance(OrderService.class);
    context.attach(XchangeData.class);
    log.debug(this.getClass().getSimpleName() + ": enableing trading");
    orderService.setTradingEnabled(true); //  context.publish(new
    for (String strategyName : strategyNames) {
      StrategyInstance strategyInstance = new StrategyInstance(strategyName);
      context.attachInstance(strategyInstance);
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
