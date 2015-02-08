package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "reset-database", commandDescription = "DROPS and recreates the database with default data")
public class ResetDatabaseRunMode extends RunMode {

    @Override
    public void run() {
        PersistUtil.resetDatabase();
        System.exit(0);
    }
}
