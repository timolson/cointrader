package org.cryptocoinpartners.command;

import org.cryptocoinpartners.util.PersistUtil;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ExitCommand extends CommandBase {

    @Override
    public String getUsageHelp() {
        return "exit";
    }

    @Override
    public String getExtraHelp() {
        return "exits the cointrader console and stops the node running";
    }

    @Override
    public void parse(String commandArguments) {
    }

    @Override
    public Object call() {
    	try {
    		PersistUtil.shutdown();
    	} catch  (Exception e) {
    		e.printStackTrace();
    	} finally {
            System.exit(0);
            return true;
		}
    }

}
