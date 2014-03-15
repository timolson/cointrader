package com.cryptocoinpartners.service.xchange;

import com.cryptocoinpartners.schema.*;
import com.cryptocoinpartners.service.MarketDataServiceBase;
import com.cryptocoinpartners.service.Subscription;
import com.cryptocoinpartners.service.SubscriptionCapability;
import com.cryptocoinpartners.service.SubscriptionType;

import java.util.HashMap;
import java.util.Map;


/**
 * @author Tim Olson
 */
public class XchangeMarketDataService extends MarketDataServiceBase {


    /** Must be public for auto-instantiation */
    public XchangeMarketDataService() {
        super(
                new SubscriptionCapability(Market.BITFINEX, SubscriptionType.TRADE),
                new SubscriptionCapability(Market.BITFINEX, SubscriptionType.BOOK)
             );
    }


    public void subscribe(Subscription subscription) {
        super.subscribe(subscription);
        fakeTickers.put(subscription,new FakeTicker(subscription));
    }


    public void unsubscribe(Subscription subscription) {
        super.unsubscribe(subscription);
        FakeTicker fakeTicker = fakeTickers.remove(subscription);
        if( fakeTicker != null )
            fakeTicker.stop();
    }


    private Map<Subscription,FakeTicker> fakeTickers = new HashMap<Subscription, FakeTicker>();
}
