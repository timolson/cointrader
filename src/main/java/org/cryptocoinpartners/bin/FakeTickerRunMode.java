package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockTicker;
import org.cryptocoinpartners.module.SaveMarketData;

import com.beust.jcommander.Parameters;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "mockticker", commandDescription = "Launch a test ticker and save bogus data to the database")
public class FakeTickerRunMode extends RunMode {

    @Override
    public void run(Semaphore semaphore) {
        Context context = Context.create();
        context.attach(MockTicker.class);
        context.attach(SaveMarketData.class);
        if (semaphore != null)
            semaphore.release();
    }

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }

}
