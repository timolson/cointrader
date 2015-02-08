package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockTicker;
import org.cryptocoinpartners.module.SaveMarketData;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "mockticker", commandDescription = "Launch a test ticker and save bogus data to the database")
public class FakeTickerRunMode extends RunMode {

    @Override
    public void run() {
        Context context = Context.create();
        context.attach(MockTicker.class);
        context.attach(SaveMarketData.class);
    }

}
