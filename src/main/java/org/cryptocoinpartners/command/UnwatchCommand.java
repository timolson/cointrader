package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class UnwatchCommand extends WatchCommandBase {

    @Override
    public Object call() {
        notifications.unwatch(listing);
        return true;
    }
}
