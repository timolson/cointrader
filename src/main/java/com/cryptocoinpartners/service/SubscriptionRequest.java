package com.cryptocoinpartners.service;


import com.cryptocoinpartners.schema.Event;


/**
 * @author Tim Olson
 */
public class SubscriptionRequest extends Event {
    public enum SubscriptionRequestType { SUBSCRIBE, UNSUBSCRIBE }


    public SubscriptionRequest(SubscriptionRequestType subscriptionRequestType, Subscription subscription) {
        this.subscriptionRequestType = subscriptionRequestType;
        this.subscription = subscription;
    }


    public SubscriptionRequestType getSubscriptionRequestType() {
        return subscriptionRequestType;
    }


    public Subscription getSubscription() {
        return subscription;
    }


    public String toString() {
        return subscriptionRequestType.toString() + ' ' + subscription;
    }


    private SubscriptionRequestType subscriptionRequestType;
    private Subscription subscription;
}
