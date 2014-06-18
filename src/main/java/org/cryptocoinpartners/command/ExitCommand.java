package org.cryptocoinpartners.command;

import org.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ExitCommand extends CommandBase {

    public String getUsageHelp() {
        return "exit";
    }


    public String getExtraHelp() {
        return "exits the cointrader console and stops the node running";
    }


    public void parse(String commandArguments) { }


    public void run() {
        PersistUtil.shutdown();
        System.exit(0);
    }

}
