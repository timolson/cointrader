package org.cryptocoinpartners.module.helloworld;

import org.cryptocoinpartners.module.ModuleListenerBase;
import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.module.Esper;
import org.apache.commons.configuration.Configuration;


/**
 * @author Tim Olson
 */
public class HelloWorld extends ModuleListenerBase {

    public void initModule(Esper esper, Configuration config) {
        super.initModule(esper, config);
        log.info("Hello, world!!!!");
        if( config.containsKey("name") )
            log.info("And hello to you, too, "+config.getString("name")+"!");
    }

    @When("select * from Event")
    public void doSomethingWithEvery(Event e) {
        if( log.isTraceEnabled() )
            log.trace(e.toString());
    }

    /** see HelloWorld.epl */
    public void setAvgTrade(double avg) {
        avgTrade = avg;
    }

    private double avgTrade;
}
