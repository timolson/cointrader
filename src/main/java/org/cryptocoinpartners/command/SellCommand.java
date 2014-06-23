package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
@CommandName("sell")
@SuppressWarnings("UnusedDeclaration")
public class SellCommand extends OrderCommand {
    public SellCommand() {
        super(true);
    }
}
