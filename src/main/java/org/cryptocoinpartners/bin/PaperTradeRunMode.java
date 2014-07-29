package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.xchange.XchangeAccountService;
import org.cryptocoinpartners.module.xchange.XchangeData;
import org.cryptocoinpartners.schema.StrategyInstance;

import java.util.List;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = {"paper"}, commandDescription = "Run strategies against live streaming data but use the mock order system instead of live trades")
public class PaperTradeRunMode extends RunMode {

    public void run() {
        Context context = Context.create();
        context.attach(XchangeAccountService.class);
        context.attach(BasicQuoteService.class);
        context.attach(MockOrderService.class);
        context.attach(XchangeData.class);
        for( String strategyName : strategyNames ) {
            StrategyInstance strategyInstance = new StrategyInstance(strategyName);
            context.attachInstance(strategyInstance);
        }
    }


    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(description = "a list of strategy names")
    private List<String> strategyNames;
}
