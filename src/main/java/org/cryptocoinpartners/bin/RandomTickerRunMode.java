package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.JMXManager;
import org.cryptocoinpartners.module.MockTicker;

import com.beust.jcommander.Parameters;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "randomticker", commandDescription = "Launch a random ticker generater and save into strategy")
public class RandomTickerRunMode extends RunMode {

    @Override
    public void run(Semaphore semaphore) {
        Context context = Context.create();
        context.attach(MockTicker.class);
        //  context.attach(SaveMarketData.class);
        context.attach(JMXManager.class);
        if (semaphore != null)
            semaphore.release();
    }

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }

}
