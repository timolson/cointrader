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
    
    public Trade getLastTrade(MarketListing marketListing) {
        return null;
    }


    public Trade getLastTrade(Listing listing) {
        return null;
    }


    public Book getLastBook(MarketListing marketListing) {
        return null;
    }


    public Book getLastBook(Listing listing) {
        return null;
    }


    public Set<MarketListing> getMarketListingsForListing( Listing listing ) {
        Set<MarketListing> result = marketListingsByListing.get(listing.getSymbol());
        return result == null ? Collections.<MarketListing>emptySet() : result;
    }


    /**
     * @return null if no Books for the given listing have been received yet
     */
    public @Nullable Book getBestBidForListing( Listing listing ) {
        return bestBidByMarketListing.get(listing.getSymbol());
    }
    
    
    /**
     * @return null if no Books for the given listing have been received yet
     */
    public @Nullable Book getBestAskForListing( Listing listing ) {
        return bestAskByMarketListing.get(listing.getSymbol());
    }
    
    
    @When("select * from Book")
    void recordBook( Book b ) {
        MarketListing marketListing = b.getMarketListing();
        
        String listingSymbol = marketListing.getListing().getSymbol();
        Book lastBookForListing = lastBookByListing.get(listingSymbol);
        if( lastBookForListing == null || lastBookForListing.getTime().isBefore(b.getTime()) )
            lastBookByListing.put(listingSymbol,b);

        String marketListingSymbol = marketListing.getSymbol();
        Book lastBookForMarketListing = lastBookByMarketListing.get(marketListingSymbol);
        if( lastBookForMarketListing == null || lastBookForMarketListing.getTime().isBefore(b.getTime()) )
            lastBookByMarketListing.put(marketListingSymbol,b);

        Bid bestBid = b.getBestBid();
        Book lastBestBidBook = bestBidByMarketListing.get(marketListingSymbol);
        //noinspection ConstantConditions
        if( bestBid != null &&
                ( lastBestBidBook == null || bestBid.getPrice().compareTo(lastBestBidBook.getBestBid().getPrice()) > 0 )
          )
            bestBidByMarketListing.put(marketListingSymbol,b);

        Ask bestAsk = b.getBestAsk();
        Book lastBestAskBook = bestAskByMarketListing.get(marketListingSymbol);
        //noinspection ConstantConditions
        if( bestAsk != null &&
                ( lastBestAskBook == null || bestAsk.getPrice().compareTo(lastBestAskBook.getBestAsk().getPrice()) < 0 )
          )
            bestAskByMarketListing.put(marketListingSymbol,b);
    }


    @When("select * from Trade")
    void recordTrade( Trade t ) {
        MarketListing marketListing = t.getMarketListing();
        handleMarketListing(marketListing);
        
        String listingSymbol = marketListing.getListing().getSymbol();
        Trade lastTradeForListing = lastTradeByListing.get(listingSymbol);
        if( lastTradeForListing == null || lastTradeForListing.getTime().isBefore(t.getTime()) )
            lastTradeByListing.put(listingSymbol,t);

        String marketListingSymbol = marketListing.getSymbol();
        Trade lastTradeForMarketListing = lastTradeByMarketListing.get(marketListingSymbol);
        if( lastTradeForMarketListing == null || lastTradeForMarketListing.getTime().isBefore(t.getTime()) )
            lastTradeByMarketListing.put(marketListingSymbol,t);
    }


    private void handleMarketListing(MarketListing marketListing) {
        final Listing listing = marketListing.getListing();
        final String listingSymbol = listing.getSymbol();
        Set<MarketListing> marketListings = marketListingsByListing.get(listingSymbol);
        if( marketListings == null ) {
            marketListings = new HashSet<>();
            marketListings.add(marketListing);
            marketListingsByListing.put(listingSymbol, marketListings);
        }
        else
            marketListings.add(marketListing);
    }


    private Map<String,Trade> lastTradeByListing = new HashMap<>();
    private Map<String,Book> lastBookByListing = new HashMap<>();
    private Map<String,Trade> lastTradeByMarketListing = new HashMap<>();
    private Map<String,Book> lastBookByMarketListing = new HashMap<>();
    private Map<String,Book> bestBidByMarketListing = new HashMap<>();
    private Map<String,Book> bestAskByMarketListing = new HashMap<>();
    private Map<String,Set<MarketListing>> marketListingsByListing = new HashMap<>();
}
