package com.cryptocoinpartners.schema;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;


/**
 * Our wrapper around the Esper engine
 *
 * @author Tim Olson
 */
public class Esper {


    public Esper() {
        Configuration config = new Configuration();
        epService = EPServiceProviderManager.getDefaultProvider(config);
        epRuntime = epService.getEPRuntime();
    }


    /**
     * This will load all the
     * @param moduleName
     */
    public void loadModule( String moduleName ) {
        // todo tim
    }


    public void publish(Event e) {
        epRuntime.sendEvent(e);
    }


    public void destroy() {
        epService.destroy();
    }


    private final EPServiceProvider epService;
    private final EPRuntime epRuntime;
}
