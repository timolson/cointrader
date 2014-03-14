package com.cryptocoinpartners.service;

import com.cryptocoinpartners.schema.*;

import java.util.*;


/**
 * @author Tim Olson
 */
public abstract class MarketDataServiceBase extends MarketDataService {

    public Collection<SubscriptionCapability> getSubscriptionCapabilities() { return subscriptionCapabilities; }


    public void subscribe(Subscription subscription) {
        allSubscriptions.add(subscription);
        getSubscriptionsForMarket(subscription.getSecurity().getMarket()).add(subscription);
    }


    public void unsubscribe(Subscription subscription) {
        allSubscriptions.remove(subscription);
        getSubscriptionsForMarket(subscription.getSecurity().getMarket()).remove(subscription);
    }


    public Set<Subscription> getSubscriptionsForMarket( Market m ) {
        Set<Subscription> marketSubscriptions = subscriptionsByMarket.get(m);
        if( marketSubscriptions == null ) {
            marketSubscriptions = Collections.synchronizedSet(new HashSet<Subscription>());
            subscriptionsByMarket.put(m, marketSubscriptions);
        }
        return marketSubscriptions;
    }


    protected MarketDataServiceBase( SubscriptionCapability... subscriptionCapabilities ) {
        this.subscriptionCapabilities = Arrays.asList(subscriptionCapabilities);
    }


    private Collection<SubscriptionCapability> subscriptionCapabilities;
    private Map<Market,Set<Subscription>> subscriptionsByMarket = new HashMap<Market, Set<Subscription>>();
    private Set<Subscription> allSubscriptions = Collections.synchronizedSet(new HashSet<Subscription>());
}
