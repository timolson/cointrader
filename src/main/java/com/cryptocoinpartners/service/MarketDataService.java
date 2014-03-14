package com.cryptocoinpartners.service;

import com.cryptocoinpartners.schema.*;
import com.cryptocoinpartners.util.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;


/**
 * Any subclasses of MarketDataService will be automatically instantiated (default constructor)
 *
 * @author Tim Olson
 */
public abstract class MarketDataService {


    public static void subscribeAll( Esper esper ) {
        for( MarketDataService marketDataService : getAll() ) {
            for( MarketDataService dataService : all ) {
                for( SubscriptionCapability capability : dataService.getSubscriptionCapabilities() ) {
                    for( Security security : capability.getMarket().getSecurities() ) {
                        Subscription subscription = Subscription.subscribe(marketDataService, security,
                                                                           capability.getSubscriptionType(), esper);
                    }
                }
            }
        }
    }


    public static Collection<MarketDataService> getAll() {
        if( all == null ) {
            all = new ArrayList<MarketDataService>();
            Set<Class<? extends MarketDataService>> subtypes = ReflectionUtil.getSubtypesOf(MarketDataService.class);
            for( Class<? extends MarketDataService> subtype : subtypes ) {
                try {
                    if( !Modifier.isAbstract(subtype.getModifiers()) ) {
                        MarketDataService marketDataService = subtype.newInstance();
                        all.add(marketDataService);
                    }
                }
                catch( InstantiationException e ) {
                    log.error("Could not instantiate MarketDataService " + subtype, e);
                }
                catch( IllegalAccessException e ) {
                    log.error("Could not instantiate MarketDataService " + subtype, e);
                }
            }
        }
        return all;
    }


    abstract public Collection<SubscriptionCapability> getSubscriptionCapabilities();


    /**
     * The public method for subscribing is Subscription.subscribe()
     *
     * After this is called, any information about the given Security should be posted to the esper instance
     * @param subscription
     * @see Subscription#subscribe(MarketDataService, com.cryptocoinpartners.schema.Security, SubscriptionType, com.cryptocoinpartners.schema.Esper...)()
     */
    abstract void subscribe(Subscription subscription);


    /**
     * The public method for unsubscribing is Subscription.subscribe()
     *
     * After this is called, any market data about the given Security should be ceased.
     * @param subscription
     */
    abstract void unsubscribe(Subscription subscription);


    private static Collection<MarketDataService> all;
    protected static Logger log = LoggerFactory.getLogger(MarketDataService.class);
}
