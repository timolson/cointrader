package org.cryptocoinpartners.module;

/**
 * @author Tim Olson
 */
public class CircuitBreaker {

    @When("select NULL from Trade.win:time(60 sec) where listing=?listing having (max(priceAsDouble) - min(priceAsDouble))/min(priceAsDouble) > 0.1 ")
    public void deactivateListing() {
        // todo
    }

}
