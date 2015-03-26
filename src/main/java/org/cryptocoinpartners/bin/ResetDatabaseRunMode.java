package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.util.PersistUtil;

import com.beust.jcommander.Parameters;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "reset-database", commandDescription = "DROPS and recreates the database with default data")
public class ResetDatabaseRunMode extends RunMode {

    @Override
    public void run(Semaphore semaphore) {
        PersistUtil.resetDatabase();
        if (semaphore != null)
            semaphore.release();
        System.exit(0);
    }

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }
}
