package org.cryptocoinpartners.service;

import java.util.Set;

import javax.annotation.Nullable;

import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Trade;

/**
 * QuoteService provides the latest MarketData for a given Listing or Market
 * 
 * @author Tim Olson
 */
@Service
public interface QuoteService {

	/** returns the most recent Trade of the specified Market */
	@Nullable
	public Trade getLastTrade(Market market);

	/** returns the most recent trade of the specified Listing on any Exchange */
	@Nullable
	public Trade getLastTrade(Listing listing);

	/** returns the most recent Book of the specified Market */
	@Nullable
	public Book getLastBook(Market market);

	/** returns the most recent trade of the specified Listing on any Exchange */
	@Nullable
	public Book getLastBook(Listing listing);

	/** returns all the Markets which have reported data for the given Listing.  This is a way to discover
	 *  which Markets are trading a Listing */
	public Set<Market> getMarketsForListing(Listing listing);

	/** returns the Book which offers the best bid price.  Could be null if no data for listing has been received yet. */
	public @Nullable
	org.cryptocoinpartners.schema.Offer getBestBidForListing(Listing listing);

	/** returns the Book which offers the best ask price.  Could be null if no data for listing has been received yet. */
	public @Nullable
	org.cryptocoinpartners.schema.Offer getBestAskForListing(Listing listing);

	public @Nullable
	org.cryptocoinpartners.schema.Offer getLastBidForMarket(Market market);

	public @Nullable
	org.cryptocoinpartners.schema.Offer getLastAskForMarket(Market market);

	Offer getImpliedBestAskForListing(Listing listing);

	Offer getImpliedBestBidForListing(Listing listing);

}
