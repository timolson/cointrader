package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class UnwatchCommand extends WatchCommandBase {
    public void run() { notifications.unwatch(listing); }
}
