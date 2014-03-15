package com.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;
import com.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
@Parameters(commandNames = "reset-database", commandDescription = "DROPS and recreates the database with default data")
public class ResetDatabaseCommand extends Command {
    public void run() {
        PersistUtil.resetDatabase();
        System.exit(0);
    }
}
