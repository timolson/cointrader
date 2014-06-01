package com.cryptocoinpartners.module.circuitbreaker;

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
public class CircuitBreaker extends ModuleListenerBase {

    @When("select NULL from Trade.win:time(60 sec) where listing=?listing having (max(priceAsDouble) - min(priceAsDouble))/min(priceAsDouble) > 0.1 ")
    public void deactivateListing() {
        // todo
    }

}
