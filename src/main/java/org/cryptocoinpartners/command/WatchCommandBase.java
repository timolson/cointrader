package org.cryptocoinpartners.command;

import org.cryptocoinpartners.bin.ConsoleNotifications;
import org.cryptocoinpartners.schema.Listing;

import javax.inject.Inject;


/**
 * @author Tim Olson
 */
public abstract class WatchCommandBase extends CommandBase {

    public String getUsageHelp() {
        return "watch {listing}";
    }


    public void parse(String commandArguments) {
        try {
            listing = Listing.forSymbol(commandArguments);
        }
        catch( IllegalArgumentException e ) {
            console.println("Unknown listing "+commandArguments);
        }
    }


    public String getExtraHelp() {
        return "Prints out market data for the given listing as soon as the data arrives.";
    }


    @Inject
    private ConsoleWriter console;
    @Inject
    protected ConsoleNotifications notifications;
    protected Listing listing;
}
