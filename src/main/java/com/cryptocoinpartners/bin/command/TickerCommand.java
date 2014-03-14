package com.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;
import com.cryptocoinpartners.schema.Esper;
import com.cryptocoinpartners.service.MarketDataService;


/**
 * @author Tim Olson
 */
@Parameters(commandNames = "ticker", commandDescription = "Launch a data gathering node")
public class TickerCommand extends Command {

    public void run() {
        Esper esper = new Esper();
        MarketDataService.subscribeAll(esper);
    }

}
