package org.cryptocoinpartners.bin;

import java.util.ArrayList;
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
import org.cryptocoinpartners.module.SaveMarketData;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.module.xchange.XchangeData;
import org.cryptocoinpartners.schema.ReplayFactory;
import org.cryptocoinpartners.schema.StrategyInstance;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.util.ConfigUtil;
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
	@Inject
	protected transient TransactionFactory transactionFactory;

	@Inject
	protected transient ReplayFactory replayFactory;

	//  public List<String> positions = Arrays.asList("OKCOIN:USD", "1000000"); //private final Instant start = new DateTime(2014, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
	final ExecutorService service = Executors.newSingleThreadExecutor();
	private final Instant end = new DateTime(DateTime.now()).toInstant();
	Semaphore paperSemaphore = new Semaphore(0);
	Long prefeedPeriod = ConfigUtil.combined().getLong("strategy.prefeed.period", 25);
	Boolean replayBooks = ConfigUtil.combined().getBoolean("strategy.prefeed.books", false);
	Boolean replayBars = ConfigUtil.combined().getBoolean("strategy.prefeed.bars", false);
	List<String> barIntervals = ConfigUtil.combined().getList("strategy.prefeed.bar.intervals", new ArrayList<String>());

	//		.getBoolean("strategy.prefeed.bars", false);
	//strate//gy.prefeed.bar.intervals

	private final Instant start = end.minus(Duration.standardHours(prefeedPeriod)).toInstant();

	// new DateTime(2013, 12, 20, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

	@Override
	public void run(Semaphore semaphore) {
		//context = Context.create();

		Replay replay = replayFactory.between(start, end, false, paperSemaphore, false, replayBooks, replayBars, barIntervals);
		context = replay.getContext();
		context.attach(XchangeAccountService.class);
		context.attach(BasicQuoteService.class);
		context.attach(BasicPortfolioService.class);
		context.attach(MockOrderService.class);
		context.attach(JMXManager.class);

		for (String strategyName : strategyNames) {
			StrategyInstance strategyInstance = new StrategyInstance(strategyName);
			context.attachInstance(strategyInstance);
			strategyInstance.getStrategy().init();

			//setUpInitialPortfolio(strategyInstance);

			// context.getInjector().getInstance(cls)

		}
		log.debug(this.getClass().getSimpleName() + ": replaying historic prices");
		replay.run();

		log.debug(this.getClass().getSimpleName() + ": replayed historic prices");
		//context.esperConfig.getEngineDefaults().getThreading().setInternalTimerEnabled(false);
		context.setTimeProvider(null);
		context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));
		// context.
		//  context.setTimeProvider(null);
		OrderService orderService = context.getInjector().getInstance(OrderService.class);
		// context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));

		//  context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));
		if (ConfigUtil.combined().getBoolean("save.marketdata", true))

			context.attach(SaveMarketData.class);
		log.debug(this.getClass().getSimpleName() + ": enableing trading");

		context.attach(XchangeData.class);
		log.debug(this.getClass().getSimpleName() + ": enableing trading");

		orderService.setTradingEnabled(true); //  context.publish(new TimerControlEvent(TimerControlEvent.ClockType.CLOCK_INTERNAL));

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
