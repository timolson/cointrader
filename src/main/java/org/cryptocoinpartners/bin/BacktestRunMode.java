package org.cryptocoinpartners.bin;

import java.util.Arrays;
import java.util.List;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Holding;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.StrategyInstance;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.util.PersistUtil;
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
	private final Instant start = new DateTime(2013, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
	private final Instant end = new DateTime(2014, 1, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
	//private final Instant start = new DateTime(2014, 9, 9, 23, 0, 0, 0, DateTimeZone.UTC).toInstant();
	//private final Instant end = new DateTime(2014, 9, 10, 6, 0, 0, 0, DateTimeZone.UTC).toInstant();

	@Parameter(names = { "-p", "--position" }, arity = 2, description = "specify initial portfolio positions as {Exchange}:{Asset} {Amount} e.g. BITFINEX:BTC 1.0")
	public List<String> positions = Arrays.asList("BITFINEX:USD", "10000");

	@Parameter(names = { "-" }, description = "No-op switch used to end list of positions before supplying the strategy name")
	boolean noop = false;

	@Override
	public void run() {
		//Replay replay = Replay.between(start, end, true);
		PersistUtil.purgeTransactions();
		Replay replay = Replay.all(true);
		context = replay.getContext();
		context.attach(XchangeAccountService.class);
		context.attach(BasicQuoteService.class);
		context.attach(MockOrderService.class);
		for (String strategyName : strategyNames) {
			StrategyInstance strategyInstance = new StrategyInstance(strategyName);
			context.attachInstance(strategyInstance);
			setUpInitialPortfolio(strategyInstance);
			// context.getInjector().getInstance(cls)

		}

		replay.run();
		//System.exit(0);
		// todo report P&L, etc.
	}

	private void setUpInitialPortfolio(StrategyInstance strategyInstance) {
		Portfolio portfolio = strategyInstance.getPortfolio();
		if (positions.size() % 2 != 0) {
			System.err.println("You must supply an even number of arguments to the position switch. " + positions);
		}
		for (int i = 0; i < positions.size() - 1;) {
			Holding holding = Holding.forSymbol(positions.get(i++));
			//	Long str = (positions.get(i++));
			DiscreteAmount amount = new DiscreteAmount(Long.parseLong(positions.get(i++)), 0.01);
			DiscreteAmount price = new DiscreteAmount(0, 0.01);
			Transaction initialCredit = new Transaction(portfolio, holding.getExchange(), holding.getAsset(), TransactionType.CREDIT, amount, price);
			context.publish(initialCredit);

		}
	}

}
