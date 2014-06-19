package org.cryptocoinpartners.module;

/**
 * The idea here is to trigger on disaster conditions and set a Panic mode.  A better approach is to declare
 * the safe conditions and fail on anything outside that.
 *
 * @author Tim Olson
 */
public class CircuitBreaker {

    @When("select NULL from Trade.win:time(60 sec) where listing=?listing having (max(priceAsDouble) - min(priceAsDouble))/min(priceAsDouble) > 0.1 ")
    public void deactivateListing() {
        // todo
    }

}
