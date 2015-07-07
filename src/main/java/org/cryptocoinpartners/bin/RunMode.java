package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.util.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameters;

/**
 * Each subclass of RunMode must be annotated with the JCommander @Parameters tag.  Main will
 * create an instance of the RunMode using injection, and JCommander will populate any fields annotated with @Parameter.
 *
 * @author Tim Olson
 * @see Parameters
 */
@Parameters(commandNames = "example", commandDescription = "This is an example of how to annotate your subclasses")
public abstract class RunMode implements Runnable {

    public abstract void run(Semaphore semaphore);

    //  @Parameter(names = {"-x","-X","-example"}, description = "this is an example")
    //  public boolean exampleSwitch;

    //  public void run() {
    //      System.err.println("<unimplemented>");
    //      System.exit(404);
    //  }

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.runMode");

    @Inject
    protected Injector injector;
    @Inject
    protected Configuration config;

}
