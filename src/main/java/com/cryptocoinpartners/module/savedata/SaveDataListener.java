package com.cryptocoinpartners.module.savedata;

import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.module.When;
import com.cryptocoinpartners.schema.MarketData;
import com.cryptocoinpartners.util.PersistUtil;


/**
 * @author Tim Olson
 */
public class SaveDataListener extends ModuleListenerBase {

    @When("select * from MarketData")
    public void handleMarketData( MarketData m ) {
        PersistUtil.insert(m);
    }

}
