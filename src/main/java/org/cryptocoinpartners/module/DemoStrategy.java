package org.cryptocoinpartners.module;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.schema.*;

import java.math.BigDecimal;


/**
 * This simple Strategy first waits for Book data to arrive about the target Market, then it places a buy order
 * at demostrategy.spread below the current bestAsk.  Once it enters the trade, it places a sell order at
 * demostrategy.spread above the current bestBid.
 * This strategy ignores the available Positions in the Fund and always trades the amount set by demostrategy.volume on
 * the Market specified by demostrategy.market
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class DemoStrategy extends SimpleStatefulStrategy {


    public void initModule(Context context, Configuration config) {
        String marketSymbol = config.getString("demostrategy.market","BITFINEX:BTC.USD");
        market = Market.forSymbol(marketSymbol);
        if( market == null )
            throw new Error("Could not find Market for symbol "+marketSymbol);
        BigDecimal volumeBD = config.getBigDecimal("demostrategy.volume",
                                                   new BigDecimal("0.00000100"));// 100 satoshis
        volumeCount = DiscreteAmount.countForValueRounded(volumeBD, market.getVolumeBasis());
        // set our orders $2.00 above/below the current best offers
        BigDecimal spreadBD = config.getBigDecimal("demostrategy.spread", new BigDecimal("2.00"));
        spreadCount = DiscreteAmount.countForValueRounded(spreadBD, market.getPriceBasis());
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
        return order.create(market,volumeCount).withLimitPriceCount(bestAsk.getPriceCount()-spreadCount);
    }


    @SuppressWarnings("ConstantConditions")
    protected OrderBuilder.CommonOrderBuilder buildExitOrder(Order entryOrder) {
        if( bestBid == null )
            return null;
        return order.create(market,-volumeCount).withLimitPriceCount(bestBid.getPriceCount() + spreadCount);
    }


    private Offer bestBid;
    private Offer bestAsk;
    private Market market;
    private long volumeCount;
    private long spreadCount;
}
