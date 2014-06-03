package org.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "reset-database", commandDescription = "DROPS and recreates the database with default data")
public class ResetDatabaseCommand extends Command {
    public void run() {
        PersistUtil.resetDatabase();
        System.exit(0);
    }
}
