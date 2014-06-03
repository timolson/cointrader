package org.cryptocoinpartners.module.circuitbreaker;

import org.cryptocoinpartners.module.ModuleListenerBase;
import org.cryptocoinpartners.module.When;


/**
 * @author Tim Olson
 */
public class CircuitBreaker extends ModuleListenerBase {

    @When("select NULL from Trade.win:time(60 sec) where listing=?listing having (max(priceAsDouble) - min(priceAsDouble))/min(priceAsDouble) > 0.1 ")
    public void deactivateListing() {
        // todo
    }

}
