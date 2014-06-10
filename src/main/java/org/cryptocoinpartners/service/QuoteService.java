package org.cryptocoinpartners.service;

import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.MarketListing;
import org.cryptocoinpartners.schema.Trade;

import javax.annotation.Nullable;
import java.util.Set;


/**
 * QuoteService provides the latest MarketData for a given Listing or MarketListing
 * 
 * @author Tim Olson
 */
public interface QuoteService {

    /** returns the most recent Trade of the specified MarketListing */
    public Trade getLastTrade( MarketListing marketListing );
    
    /** returns the most recent trade of the specified Listing on any Market */
    public Trade getLastTrade( Listing listing );

    /** returns the most recent Book of the specified MarketListing */
    public Book getLastBook( MarketListing marketListing );
    
    /** returns the most recent trade of the specified Listing on any Market */
    public Book getLastBook( Listing listing );


    /** returns all the MarketListings which have reported data for the given Listing.  This is a way to discover
     *  which Markets are trading a Listing */
    public Set<MarketListing> getMarketListingsForListing( Listing listing );


    /** returns the Book which offers the best bid price.  Could be null if no data for listing has been received yet. */
    public @Nullable Book getBestBidForListing( Listing listing );


    /** returns the Book which offers the best ask price.  Could be null if no data for listing has been received yet. */
    public @Nullable Book getBestAskForListing( Listing listing );


}
