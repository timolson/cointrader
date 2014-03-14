package com.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;
import com.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
@Parameters(commandNames = "reset-database", commandDescription = "This DROPS the existing database and recreates the default data")
public class ResetDatabaseCommand extends Command {
    public void run() {
        PersistUtil.resetDatabase();
        System.exit(0);
    }
}
