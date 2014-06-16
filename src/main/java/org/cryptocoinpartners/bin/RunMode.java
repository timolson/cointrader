package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Each subclass of RunMode must be annotated with the JCommander @Parameters tag.  Main will
 * create a parameter object then give it to an instance of the subclass via run(Object param)
 *
 * will be instantiated with its default constructor, then getName() and get will be called followed
 * by
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

    protected Logger log = LoggerFactory.getLogger(getClass());
}
