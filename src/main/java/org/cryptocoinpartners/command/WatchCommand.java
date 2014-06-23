package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class WatchCommand extends WatchCommandBase {
    public void run() { notifications.watch(listing); }
}
