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
public class TickWindow extends ModuleListenerBase {

    @When("select items.lastOf().price, sum(amount), items.lastOf().listing from Trade.win:time_batch(60 sec) group by listing")
    public void doSomethingWithEvery(BigDecimal price, BigDecimal amount, Listing listing) {
        Tick tick = new Tick( listing, null, Instant.now(), price, amount);
        esper.publish(tick);
    }

}
