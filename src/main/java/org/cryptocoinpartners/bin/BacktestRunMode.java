package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import org.cryptocoinpartners.module.BasicPortfolioService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.util.Replay;

import java.util.Arrays;
import java.util.List;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "backtest", commandDescription = "backtest a strategy (not functional)")
public class BacktestRunMode extends RunMode {

    @Parameter(description = "Strategy name to load", arity = 1, required = true)
    public List<String> strategyNames;


    @Parameter(names = {"-p","--position"}, arity = 2, description = "specify initial portfolio positions as {Exchange}:{Asset} {Amount} e.g. BITFINEX:BTC 1.0")
    public List<String> positions = Arrays.asList("BITFINEX:BTC","1.0");


    @Parameter(names = {"-"}, description = "No-op switch used to end list of positions before supplying the strategy name")
    boolean noop = false;


    public void run() {
        Replay replay = Replay.all(true);
        Context context = replay.getContext();
        context.attach(XchangeAccountService.class);
        context.attach(BasicQuoteService.class);
        context.attach(MockOrderService.class);
        context.attach(BasicPortfolioService.class);
        for( String strategyName : strategyNames ) {
        	 StrategyInstance strategyInstance = new StrategyInstance(strategyName);
             setUpInitialPortfolio(strategyInstance);
             context.attachInstance(strategyInstance);  
           
        }
        replay.run();
        // todo report P&L, etc.
    }


    private void setUpInitialPortfolio(StrategyInstance strategyInstance) {
        Portfolio portfolio = strategyInstance.getPortfolio();
        if( positions.size() % 2 != 0 ) {
            System.err.println("You must supply an even number of arguments to the position switch. "+positions);
        }
        for( int i = 0; i < positions.size(); ) {
            Holding holding = Holding.forSymbol(positions.get(i++));
            Amount amount = DecimalAmount.of(positions.get(i++));
            Amount price=DecimalAmount.ZERO;
            Position position = new Position(holding.getExchange(), holding.getAsset(), amount, price);
            Balance balance = new Balance(holding.getExchange(),holding.getAsset(), amount, Balance.BalanceType.ACTUAL);
            portfolio.modifyPosition( position, new Authorization("initial position") );
            portfolio.modifyBalance( balance, new Authorization("initial position") );
        }
    }

}
