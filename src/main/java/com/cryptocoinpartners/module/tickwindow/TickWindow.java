package com.cryptocoinpartners.module.tickwindow;

import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.module.When;
import com.cryptocoinpartners.schema.*;
import com.cryptocoinpartners.module.Esper;
import org.apache.commons.configuration.Configuration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
public class TickWindow extends ModuleListenerBase {

    /*
    @When("select items.lastOf().price, sum(amount), items.lastOf().listing from Trade.win:time_batch(60 sec) group by listing")
    public void doSomethingWithEvery(BigDecimal price, BigDecimal amount, MarketListing listing) {
        Tick tick = new Tick( listing, null, Instant.now(), price, amount);
        esper.publish(tick);
        log.debug("published tick "+tick);
    }
    */


    private static Logger log = LoggerFactory.getLogger(TickWindow.class);
}
