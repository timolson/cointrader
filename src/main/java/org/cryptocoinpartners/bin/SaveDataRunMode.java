package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.SaveMarketData;
import org.cryptocoinpartners.module.xchangedata.XchangeData;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = {"save-data","ticker"}, commandDescription = "Launch a data gathering node")
public class SaveDataRunMode extends RunMode {
    public void run() {
        Context context = Context.create();
        context.attach(XchangeData.class);
        context.attach(SaveMarketData.class);
    }
}
