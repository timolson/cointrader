package org.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.module.Esper;
import org.cryptocoinpartners.util.ModuleLoaderError;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "backtest", commandDescription = "backtest a strategy (not functional)")
public class BacktestCommand extends Command {

    @Parameter(description = "Strategy name to load", arity = 1, required = true)
    public String strategyName;

    public void run() {
        Esper esper = new Esper();
        // todo set time manager
        // todo load data producing module
        try {
            esper.loadModule(strategyName);
        }
        catch( ModuleLoaderError e ) {
            throw new Error("Could not load strategy "+strategyName,e);
        }
    }

}
