package org.cryptocoinpartners.bin;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.HelloWorld;
import org.cryptocoinpartners.module.JMXManager;
import org.cryptocoinpartners.schema.Panic;

import com.beust.jcommander.Parameters;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Parameters(commandNames = "hello", commandDescription = "Example runmode which says hello to the world and to you")
public class HelloRunMode extends RunMode {

    @Override
    public void run(Semaphore semaphore) {
        Context context = Context.create();
        context.attach(HelloWorld.class);
        context.attach(JMXManager.class);
        context.publish(new Panic());
        if (semaphore != null)
            semaphore.release();
    }

    @Override
    public void run() {
        Semaphore semaphore = null;
        run(semaphore);

    }
}
