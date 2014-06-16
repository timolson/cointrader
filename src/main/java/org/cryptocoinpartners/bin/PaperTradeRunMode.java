package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.BasicAccountService;
import org.cryptocoinpartners.module.MockOrderService;
import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.xchangedata.XchangeData;
import org.cryptocoinpartners.schema.StrategyFundManager;

import java.util.List;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = {"paper"}, commandDescription = "Run strategies against live streaming data but use the mock order system instead of live trades")
public class PaperTradeRunMode extends RunMode {

    public void run() {
        Context context = new Context();
        context.attach(BasicAccountService.class);
        context.attach(BasicQuoteService.class);
        context.attach(MockOrderService.class);
        context.attach(XchangeData.class);
        for( String moduleName : moduleNames ) {
            StrategyFundManager strategyFundManager = new StrategyFundManager(moduleName);
            context.loadStrategyFundManager(strategyFundManager);
        }
    }


    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(description = "a list of module names which have Strategy classes in them")
    private List<String> moduleNames;
}
