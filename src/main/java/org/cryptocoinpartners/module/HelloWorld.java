package org.cryptocoinpartners.module;

import org.cryptocoinpartners.schema.Event;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * @author Tim Olson
 */
@Singleton
public class HelloWorld {

    @When("select * from Event")
    public void doSomethingWithEvery(Event e) {
        log.info("Hello, Event "+(++count)+" "+e);
    }

    static int count = 0;
    /** see HelloWorld.epl */
    public void setAvgTrade(double avg) {
        avgTrade = avg;
    }


    private double avgTrade;
    @Inject
    private Logger log;

}
