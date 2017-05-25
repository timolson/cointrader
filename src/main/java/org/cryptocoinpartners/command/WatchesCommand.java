package org.cryptocoinpartners.command;

import javax.inject.Inject;

import org.cryptocoinpartners.bin.ConsoleNotifications;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class WatchesCommand extends CommandBase {

    @Override
    public Object call() {
        out.println("Watching:");
        out.printList(notifications.getWatchList());
        return true;
    }

    @Override
    public String getExtraHelp() {
        return "Displays all listings which are currently being watched";
    }

    @Inject
    protected ConsoleNotifications notifications;
}
