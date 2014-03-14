package com.cryptocoinpartners.service;


import com.cryptocoinpartners.schema.Esper;
import com.cryptocoinpartners.schema.Event;
import com.cryptocoinpartners.schema.Security;

import java.util.*;


/**
 * Associates a Security with types of MarketData that should be collected
 *
 * @author Tim Olson
 */
public class Subscription {


    public static Subscription subscribe( MarketDataService marketDataService, Security security,
                                          SubscriptionType subscriptionType, Esper... espers ) {
        Subscription subscription = new Subscription(marketDataService, security, subscriptionType, espers);
        marketDataService.subscribe(subscription);
        return subscription;
    }


    public void unsubscribe() {
        // todo
    }


    private Subscription( MarketDataService marketDataService, Security security,
                          SubscriptionType subscriptionType, Esper... espers) {
        this.marketDataService = marketDataService;
        this.espers = new HashSet<Esper>(Arrays.asList(espers));
        this.security = security;
        this.subscriptionType = subscriptionType;
    }


    public MarketDataService getMarketDataService() { return marketDataService; }
    public Security getSecurity() { return security; }
    public SubscriptionType getSubscriptionType() { return subscriptionType; }


    public void publish( Event e ) {
        for( Esper esper : espers )
            esper.publish(e);
    }


    public void addEsper( Esper e ) { espers.add(e); }
    public void removeEsper( Esper e ) { espers.remove(e); }
    public boolean hasAnyEspers() { return espers.isEmpty(); }


    public String toString() {
        return subscriptionType.toString()+' '+security;
    }


    private Set<Esper> espers = new HashSet<Esper>();
    private Security security;
    private SubscriptionType subscriptionType;
    private final MarketDataService marketDataService;
}
