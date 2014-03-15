package com.cryptocoinpartners.module.gatherdata;

import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.service.Esper;
import com.cryptocoinpartners.service.MarketDataService;
import com.cryptocoinpartners.service.Subscription;
import org.apache.commons.configuration.Configuration;

import java.util.Collection;


/**
 * @author Tim Olson
 */
public class GatherDataListener extends ModuleListenerBase {
    public void initModule(Esper esper, Configuration config) {
        super.initModule(esper, config);
        subscriptions = MarketDataService.subscribeAll(esper);
    }


    public void destroyModule() {
        for( Subscription subscription : subscriptions )
            subscription.unsubscribe();
        subscriptions.clear();
    }


    private Collection<Subscription> subscriptions;
}
