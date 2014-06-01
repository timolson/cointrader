package com.cryptocoinpartners.module.helloworld;

import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.module.When;
import com.cryptocoinpartners.schema.Event;
import com.cryptocoinpartners.module.Esper;
import com.cryptocoinpartners.schema.Fill;
import org.apache.commons.configuration.Configuration;

import java.math.BigDecimal;


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
