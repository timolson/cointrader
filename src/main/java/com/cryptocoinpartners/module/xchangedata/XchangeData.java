package com.cryptocoinpartners.module.xchangedata;

import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.module.Esper;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.bitfinex.v1.BitfinexExchange;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.apache.commons.configuration.Configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;


/**
 * @author Tim Olson
 */
public class XchangeData extends ModuleListenerBase {

    public void initModule(Esper esper, Configuration config) {
        super.initModule(esper, config);
        // start the ticker plant
        Exchange bitfinex = ExchangeFactory.INSTANCE.createExchange(BitfinexExchange.class.getName());
        dataService = bitfinex.getPollingMarketDataService();
        executor = new ScheduledThreadPoolExecutor(5);
    }


    public void destroyModule() {
        executor.shutdownNow();
        super.destroyModule();
    }


    private class PollingThread implements Runnable {
        public void run() {
            running = true;
            while(running) {
                // todo
            }
        }

        private volatile boolean running;
    }


    private PollingMarketDataService dataService;
    private ScheduledThreadPoolExecutor executor;
}
