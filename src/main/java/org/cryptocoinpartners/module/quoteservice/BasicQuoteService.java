package org.cryptocoinpartners.module.quoteservice;

import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.service.BaseService;
import org.cryptocoinpartners.service.QuoteService;

import javax.annotation.Nullable;
import java.util.*;


/**
 * This service listens to the Esper and caches the most recent Trades and Books
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class BasicQuoteService extends BaseService implements QuoteService {
    
    public Trade getLastTrade(Market market) {
        return null;
    }


    public Trade getLastTrade(Listing listing) {
        return null;
    }


    public Book getLastBook(Market market) {
        return null;
    }


    public Book getLastBook(Listing listing) {
        return null;
    }


    public Set<Market> getMarketsForListing(Listing listing) {
        Set<Market> result = marketsByListing.get(listing.getSymbol());
        return result == null ? Collections.<Market>emptySet() : result;
    }


    /**
     * @return null if no Books for the given listing have been received yet
     */
    public @Nullable Book getBestBidForListing( Listing listing ) {
        return bestBidByMarket.get(listing.getSymbol());
    }
    
    
    /**
     * @return null if no Books for the given listing have been received yet
     */
    public @Nullable Book getBestAskForListing( Listing listing ) {
        return bestAskByMarket.get(listing.getSymbol());
    }
    
    
    @When("select * from Book")
    void recordBook( Book b ) {
        Market market = b.getMarket();
        
        String listingSymbol = market.getListing().getSymbol();
        Book lastBookForListing = lastBookByListing.get(listingSymbol);
        if( lastBookForListing == null || lastBookForListing.getTime().isBefore(b.getTime()) )
            lastBookByListing.put(listingSymbol,b);

        String marketSymbol = market.getSymbol();
        Book lastBookForMarket = lastBookByMarket.get(marketSymbol);
        if( lastBookForMarket == null || lastBookForMarket.getTime().isBefore(b.getTime()) )
            lastBookByMarket.put(marketSymbol,b);

        Bid bestBid = b.getBestBid();
        Book lastBestBidBook = bestBidByMarket.get(marketSymbol);
        //noinspection ConstantConditions
        if( bestBid != null &&
                ( lastBestBidBook == null || bestBid.getPrice().compareTo(lastBestBidBook.getBestBid().getPrice()) > 0 )
          )
            bestBidByMarket.put(marketSymbol,b);

        Ask bestAsk = b.getBestAsk();
        Book lastBestAskBook = bestAskByMarket.get(marketSymbol);
        //noinspection ConstantConditions
        if( bestAsk != null &&
                ( lastBestAskBook == null || bestAsk.getPrice().compareTo(lastBestAskBook.getBestAsk().getPrice()) < 0 )
          )
            bestAskByMarket.put(marketSymbol,b);
    }


    @When("select * from Trade")
    void recordTrade( Trade t ) {
        Market market = t.getMarket();
        handleMarket(market);
        
        String listingSymbol = market.getListing().getSymbol();
        Trade lastTradeForListing = lastTradeByListing.get(listingSymbol);
        if( lastTradeForListing == null || lastTradeForListing.getTime().isBefore(t.getTime()) )
            lastTradeByListing.put(listingSymbol,t);

        String marketSymbol = market.getSymbol();
        Trade lastTradeForMarket = lastTradeByMarket.get(marketSymbol);
        if( lastTradeForMarket == null || lastTradeForMarket.getTime().isBefore(t.getTime()) )
            lastTradeByMarket.put(marketSymbol,t);
    }


    private void handleMarket(Market market) {
        final Listing listing = market.getListing();
        final String listingSymbol = listing.getSymbol();
        Set<Market> markets = marketsByListing.get(listingSymbol);
        if( markets == null ) {
            markets = new HashSet<>();
            markets.add(market);
            marketsByListing.put(listingSymbol, markets);
        }
        else
            markets.add(market);
    }


    private Map<String,Trade> lastTradeByListing = new HashMap<>();
    private Map<String,Book> lastBookByListing = new HashMap<>();
    private Map<String,Trade> lastTradeByMarket = new HashMap<>();
    private Map<String,Book> lastBookByMarket = new HashMap<>();
    private Map<String,Book> bestBidByMarket = new HashMap<>();
    private Map<String,Book> bestAskByMarket = new HashMap<>();
    private Map<String,Set<Market>> marketsByListing = new HashMap<>();
}
