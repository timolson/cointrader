package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import com.google.inject.Injector;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;

import javax.inject.Inject;


/**
 * Each subclass of RunMode must be annotated with the JCommander @Parameters tag.  Main will
 * create an instance of the RunMode using injection, and JCommander will populate any fields annotated with @Parameter.
 *
 * @author Tim Olson
 * @see Parameters
 */
@Parameters(commandNames = "example", commandDescription = "This is an example of how to annotate your subclasses")
public abstract class RunMode implements Runnable {

//  @Parameter(names = {"-x","-X","-example"}, description = "this is an example")
//  public boolean exampleSwitch;

//  public void run() {
//      System.err.println("<unimplemented>");
//      System.exit(404);
//  }


    @Inject
    protected Injector injector;
    @Inject
    protected Configuration config;
    @Inject
    protected Logger log;
}
