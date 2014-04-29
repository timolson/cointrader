package com.cryptocoinpartners.bin.command;

import com.beust.jcommander.Parameters;
import com.cryptocoinpartners.module.Esper;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "hello",commandDescription = "Example command which says hello to the world and to you")
public class HelloCommand extends Command {
    public void run() {
        Esper esper = new Esper();
        esper.loadModule("helloworld");
    }
}
