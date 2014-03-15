package com.cryptocoinpartners.service.xchange;


import com.cryptocoinpartners.service.Subscription;
import com.cryptocoinpartners.schema.Trade;
import com.cryptocoinpartners.util.MathUtil;
import org.joda.time.Instant;

import java.math.BigDecimal;
import java.util.Random;


/**
 * @author Tim Olson
 */
@SuppressWarnings("FieldCanBeLocal")
public class FakeTicker {

    public FakeTicker(Subscription subscription) {
        this.subscription = subscription;
        Thread thread = new Thread() {
            public void run() {
                running = true;
                while( running ) {
                    try {
                        double lambda = 1/averageTimeBetweenTrades;
                        double poissonSleep = -Math.log(1d-random.nextDouble())/lambda;
                        sleep((long)(1000*poissonSleep));
                    }
                    catch( InterruptedException e ) {
                        break;
                    }
                    if( !running )
                        break;
                    produceTick();
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }


    public void stop() { running = false; }


    private void produceTick() {
        Trade trade = new Trade(subscription.getSecurity(),Instant.now(),nextPrice(),nextVolume());
        subscription.publish(trade);
    }


    private BigDecimal nextVolume() {
        return BigDecimal.valueOf(volumeBasis * MathUtil.getPoissonRandom(averageVolume));
    }


    private BigDecimal nextPrice() {
        double delta = random.nextGaussian()*priceMovementStdDev;
        double multiple;
        if( delta < 0 )
            multiple = 1/(1-delta);
        else
            multiple = 1+delta;
        currentPrice = currentPrice.multiply(BigDecimal.valueOf(multiple));
        return currentPrice;
    }


    private double averageTimeBetweenTrades = 2;
    private double priceMovementStdDev = 0.0001;
    private double averageVolume = 100.0;
    private double volumeBasis = 1/1000.0;


    private Random random = new Random();
    private BigDecimal currentPrice = BigDecimal.valueOf(100);
    private volatile boolean running;
    private final Subscription subscription;

}
