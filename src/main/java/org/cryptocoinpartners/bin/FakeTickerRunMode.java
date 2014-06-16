package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockTicker;
import org.cryptocoinpartners.module.SaveData;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "mockticker", commandDescription = "Launch a test ticker and save bogus data to the database")
public class FakeTickerRunMode extends RunMode {
    public void run() {
        Context context = new Context();
        context.attach(MockTicker.class);
        context.attach(SaveData.class);
    }

}
