package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class WatchCommand extends WatchCommandBase {

    @Override
    public void run() { notifications.watch(listing); }
}
