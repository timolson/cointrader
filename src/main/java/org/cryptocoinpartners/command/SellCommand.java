package org.cryptocoinpartners.command;

/**
 * @author Tim Olson
 */
@CommandName("sell")
public class SellCommand extends OrderCommand {
    public SellCommand() {
        super(true);
    }
}
