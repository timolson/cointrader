package org.cryptocoinpartners.bin;

import com.beust.jcommander.Parameters;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.HelloWorld;
import org.cryptocoinpartners.schema.Panic;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "hello",commandDescription = "Example runmode which says hello to the world and to you")
public class HelloRunMode extends RunMode {
    public void run() {
        Context context = new Context();
        context.attach(HelloWorld.class);
        context.publish(new Panic());
    }
}
