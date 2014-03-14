package com.cryptocoinpartners.service;

import com.cryptocoinpartners.schema.Market;


/**
 * Instances of this class are used to indicate that a particular MarketDataService is able to capture a given
 * SubscriptionType for a given Market
 *
 * @author Tim Olson
 */
public class SubscriptionCapability {
    public SubscriptionCapability(Market market, SubscriptionType subscriptionType) {
        this.market = market;
        this.subscriptionType = subscriptionType;
    }


    public Market getMarket() {
        return market;
    }


    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }


    private Market market;
    private SubscriptionType subscriptionType;
}
