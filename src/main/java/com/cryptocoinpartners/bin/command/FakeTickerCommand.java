package com.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;
import com.cryptocoinpartners.module.Esper;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "faketicker", commandDescription = "Launch a test ticker and save bogus data to the database")
public class FakeTickerCommand extends Command {
    public void run() {
        Esper esper = new Esper();
        esper.loadModule("faketicker","savedata");
        //esper.loadModule("xchangedata","savedata");
    }

}
