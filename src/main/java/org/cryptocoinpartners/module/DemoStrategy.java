package org.cryptocoinpartners.module;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.schema.*;

import javax.inject.Inject;
import java.math.BigDecimal;


/**
 * This simple Strategy first waits for Book data to arrive about the target Market, then it places a buy order
 * at demostrategy.spread below the current bestAsk.  Once it enters the trade, it places a sell order at
 * demostrategy.spread above the current bestBid.
 * This strategy ignores the available Positions in the Portfolio and always trades the amount set by demostrategy.volume on
 * the Market specified by demostrategy.market
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class DemoStrategy extends SimpleStatefulStrategy {


    @Inject
    public DemoStrategy(Context context, Configuration config) {
       // String marketSymbol = config.getString("demostrategy.market","BITFINEX:BTC.USD");
        String marketSymbol = ("BITFINEX:BTC.USD");
        
        market = Market.forSymbol(marketSymbol);
        if( market == null )
            throw new Error("Could not find Market for symbol "+marketSymbol);
        BigDecimal volumeBD = 
                                                   new BigDecimal("0.00000100");// 100 satoshis
        volumeCount = DiscreteAmount.roundedCountForBasis(volumeBD, market.getVolumeBasis());
    }


    @When("select * from Book")
    void handleBook( Book b ) {
        if( b.getMarket().equals(market) ) {
        
            bestBid = b.getBestBid();
            bestAsk = b.getBestAsk();
            if( bestBid != null && bestAsk != null ) {
                ready();
                enterTrade();
                exitTrade();
         }
        }
    }


    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildEntryOrder() {
        if( bestAsk == null )
            return null;
        DiscreteAmount limitPrice = bestBid.getPrice().decrement();
        return order.create(market,volumeCount).withLimitPrice(limitPrice);
    }


    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildExitOrder(Order entryOrder) {
        if( bestBid == null )
            return null;
        DiscreteAmount limitPrice = bestAsk.getPrice().increment();
        return order.create(market,-volumeCount).withLimitPrice(limitPrice);
    }


    private Offer bestBid;
    private Offer bestAsk;
    private Market market;
    private long volumeCount;
}
