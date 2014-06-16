package org.cryptocoinpartners.command;

import org.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ExitCommand extends CommandBase {

    public String getCommandName() { return "exit"; }

    public void parse(String commandArguments) { }


    public void printHelp() {
        out.println("exit");
        out.println();
        out.println("\texits the cointrader console and stops the node running");
    }


    public void run() {
        PersistUtil.shutdown();
        System.exit(0);
    }

}
