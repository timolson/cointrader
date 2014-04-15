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
public class CircuitBreaker extends ModuleListenerBase {

    @When("select NULL from Trade.win:time(60 sec) where listing=?listing having (max(price) - min(price))/min(price) > 0.1 ")
    public void deactivateListing() {
        // todo
    }

    public void setAvgTrade(BigDecimal avg) {
        avgTrade = avg;
    }

    private BigDecimal avgTrade;
}
