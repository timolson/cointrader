package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class WatchCommand extends WatchCommandBase {

    @Override
    public Object call() {
        notifications.watch(listing);
        return true;
    }
}
