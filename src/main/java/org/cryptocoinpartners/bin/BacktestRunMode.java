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

	// aws replay (LTC/BTC)
	//private final Instant start = new DateTime(2017, 6, 10, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
	//private final Instant end = new DateTime(2017, 9, 18, 0, 0, 0, DateTimeZone.UTC).toInstant();//

	// aws replay (ALL)
	//	private final Instant start = new DateTime(2017, 9, 1, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
	//private final Instant end = new DateTime(2017, 11, 19, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

	///private final Instant start = new DateTime(2017, 07, 03, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
	//	private final Instant end = new DateTime(2017, 10, 19, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

	//private final Instant end = new DateTime(2017, 11, 16, 0, 0, 0, DateTimeZone.UTC).toInstant();

	//back tester (from 2014-01-07 to 2017-06-05)
	//private final Instant start = new DateTime(2014, 10, 25, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

	private final Instant start = new DateTime(2014, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//
	private final Instant end = new DateTime(2015, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//
	//out of sample tester (from 2014-01-07 to 2017-06-05)
	//	private final Instant start = new DateTime(2016, 06, 10, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//
	//private final Instant end = new DateTime(2017, 12, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

	@Parameter(names = { "-" }, description = "No-op switch used to end list of positions before supplying the strategy name")
	boolean noop = false;

	@Override
	public void run(Semaphore semaphore) {
		//PersistUtil.purgeTransactions();
		//Replay replay = Replay.all(true);
		//Replay replay = Replay.between(start, end, true);  
		//  Replay replay;
		// if (ConfigUtil.combined().getBoolean("randomticker", false))
		Replay replay = replayFactory.between(start, end, false, backTestSemaphore, ConfigUtil.combined().getBoolean("randomticker", false), true);

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
