package org.cryptocoinpartners.bin;

import java.util.Arrays;
import java.util.List;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.module.xchange.XchangeData;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Holding;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.StrategyInstance;
import org.cryptocoinpartners.schema.Transaction;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = { "paper" }, commandDescription = "Run strategies against live streaming data but use the mock order system instead of live trades")
public class PaperTradeRunMode extends RunMode {
	public List<String> positions = Arrays.asList("BITFINEX:BTC", "1.0");

	@Override
	public void run() {
		context = Context.create();
		context.attach(XchangeAccountService.class);
		context.attach(BasicQuoteService.class);
		context.attach(MockOrderService.class);
		context.attach(XchangeData.class);
		for (String strategyName : strategyNames) {
			StrategyInstance strategyInstance = new StrategyInstance(strategyName);
			context.attachInstance(strategyInstance);
			setUpInitialPortfolio(strategyInstance);
			// context.getInjector().getInstance(cls)

		}

	}

	private void setUpInitialPortfolio(StrategyInstance strategyInstance) {
		Portfolio portfolio = strategyInstance.getPortfolio();
		if (positions.size() % 2 != 0) {
			System.err.println("You must supply an even number of arguments to the position switch. " + positions);
		}
		for (int i = 0; i < positions.size() - 1;) {
			Holding holding = Holding.forSymbol(positions.get(i++));
			DiscreteAmount amount = new DiscreteAmount(0, 0.000001);
			DiscreteAmount price = new DiscreteAmount(0, 0.000001);
			Transaction initialCredit = new Transaction(portfolio, holding.getExchange(), holding.getAsset(), TransactionType.CREDIT, amount, price);
			context.publish(initialCredit);

		}
	}

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	@Parameter(description = "Strategy name to load", arity = 1, required = true)
	public List<String> strategyNames;
	private Context context;
}
