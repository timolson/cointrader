package org.cryptocoinpartners.module.mockticker;


import org.cryptocoinpartners.module.ConfigurationError;
import org.cryptocoinpartners.module.Esper;
import org.cryptocoinpartners.module.ModuleListenerBase;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.util.MathUtil;
import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.schema.Trade;
import org.joda.time.Instant;

import java.util.Random;


/**
 * @author Tim Olson
 */
@SuppressWarnings("FieldCanBeLocal")
public class MockTicker extends ModuleListenerBase {

    public void initModule(Esper esper, Configuration config) {
        super.initModule(esper, config);
        String marketStr = config.getString("faketicker.exchange");
        if( marketStr == null )
            throw new ConfigurationError("MockTicker must be configured with the \"mockticker.exchange\" property");
        for( String marketName : marketStr.toUpperCase().split(",") ) {
            String upperMarket = marketName.toUpperCase();
            Exchange exchange = Exchange.forSymbol(upperMarket);
            if( exchange == null )
                throw new ConfigurationError("Could not find Exchange with symbol \""+ upperMarket +"\"");
            for( Market market : Market.find(exchange) ) {
                new PoissonTickerThread(market).start();
            }
        }
    }


    public void stop() { running = false; }


    private double nextVolume() {
        return volumeBasis*MathUtil.getPoissonRandom(averageVolume);
    }


    private double nextPrice() {
        double delta = random.nextGaussian()*priceMovementStdDev;
        double multiple;
        if( delta < 0 )
            multiple = 1/(1 - delta);
        else
            multiple = 1 + delta;
        currentPrice *= multiple;
        return currentPrice;
    }


    private class PoissonTickerThread extends Thread {
        public void run() {
            running = true;
            while( running ) {
                try {
                    double lambda = 1/averageTimeBetweenTrades;
                    double poissonSleep = -Math.log(1d - random.nextDouble())/lambda;
                    sleep((long) (1000*poissonSleep));
                }
                catch( InterruptedException e ) {
                    break;
                }
                if( !running )
                    break;
                Trade trade = Trade.fromDoubles(market, Instant.now(), null, nextPrice(), nextVolume());
                esper.publish(trade);
            }
        }


        private PoissonTickerThread(Market market) {
            setDaemon(true);
            this.market = market;
        }


        private final Market market;
    }


    private double averageTimeBetweenTrades = 2;
    private double priceMovementStdDev = 0.0001;
    private double averageVolume = 100.0;
    private double volumeBasis = 1/1000.0;


    private Random random = new Random();
    private double currentPrice = 100;
    private volatile boolean running;

}
