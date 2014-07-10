package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;

import javax.inject.Inject;


/**
 * Implements the typical fee structure where the exchange keeps a fixed ratio amount of the trade volume, taken from
 * the quote currency
 *
 * @author Tim Olson
 */
public class RatioFeeStructure implements FeeStructure {


    public Position fee(SpecificOrder o) {
        throw new Error("unimplemented"); // todo
        /*
        // todo improve this estimate by using the quote service
        // currently we just add 1% to the estimated order cost and use that amount as the reserve amount
        Amount price = order.getLimitPrice();
        if( price == null )
            price = order.getStopPrice();
        // todo the book could be empty... how to estimate a price??
        if( price == null ) {
            Offer offer = order.isBid() ? quotes.getBestAskForListing(order.getMarket().getListing())
                                        : quotes.getBestBidForListing(order.getMarket().getListing());
            if( offer != null )
                price = offer.getPrice();
        }
        if( price == null ) {
            Trade lastTrade = quotes.getLastTrade(order.getMarket());
            if( lastTrade != null )
                price = lastTrade.getPrice();
        }
        if( price == null ) {
            // there is no data on which to base a price estimate.  reserve ALL Positions in the quote fungible
            long count = Long.MAX_VALUE;
            return new DiscreteAmount(count,order.getMarket().getQuote().getBasis());
        }
        else {

        }
        // todo calculate fees
        */
    }


    public DiscreteAmount maximumPurchaseOf(Market m, Portfolio tradeablePortfolio) {
        return null;
    }



    @Inject
    private QuoteService quoteService;

}
