package org.cryptocoinpartners.module;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.service.QuoteService;

/**
 * This service listens to the Context and caches the most recent Trades and Books
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public class BasicQuoteService implements QuoteService {

	@Override
	public Trade getLastTrade(Market market) {
		return lastTradeByMarket.get(market.getSymbol());
	}

	@Override
	public Trade getLastTrade(Listing listing) {
		return lastTradeByListing.get(listing.getSymbol());
	}

	@Override
	public Book getLastBook(Market market) {
		return lastBookByMarket.get(market.getSymbol());
	}

	@Override
	public Book getLastBook(Listing listing) {
		return lastBookByListing.get(listing.getSymbol());
	}

	@Override
	public Set<Market> getMarketsForListing(Listing listing) {
		Set<Market> result = marketsByListing.get(listing.getSymbol());
		return result == null ? Collections.<Market> emptySet() : result;
	}

	/**
	 * @return null if no Books for the given listing have been received yet
	 */
	@Override
	public @Nullable
	Offer getBestBidForListing(Listing listing) {
		Offer bestBid = null;
		for (Market market : marketsByListing.get(listing.getSymbol())) {
			Book book = bestBidByMarket.get(market.getSymbol());
			Offer testBestBid = book.getBestBid();
			//noinspection ConstantConditions
			if (bestBid == null || (testBestBid != null && testBestBid.getPrice().compareTo(bestBid.getPrice()) > 0))
				bestBid = testBestBid;
		}
		return bestBid;
	}

	@Override
	public @Nullable
	Offer getLastBidForMarket(Market market) {
		Offer bestBid = null;
		// for( Market market : marketsByListing.get(listing.getSymbol()) ) {
		Book book = lastBookByMarket.get(market.getSymbol());
		Offer testBestBid = book.getBestBid();
		//noinspection ConstantConditions
		if (bestBid == null || (testBestBid != null && testBestBid.getPrice().compareTo(bestBid.getPrice()) > 0))
			bestBid = testBestBid;

		return bestBid;
	}

	/**
	 * @return null if no Books for the given listing have been received yet
	 */
	@Override
	public @Nullable
	Offer getBestAskForListing(Listing listing) {
		Offer bestAsk = null;
		for (Market market : marketsByListing.get(listing.getSymbol())) {
			Book book = bestAskByMarket.get(market.getSymbol());
			Offer testBestAsk = book.getBestAsk();
			//noinspection ConstantConditions
			if (bestAsk == null || (testBestAsk != null && testBestAsk.getPrice().compareTo(bestAsk.getPrice()) < 0))
				bestAsk = testBestAsk;
		}
		return bestAsk;
	}

	@Override
	public @Nullable
	Offer getLastAskForMarket(Market market) {
		Offer bestAsk = null;

		Book book = lastBookByMarket.get(market.getSymbol());
		Offer testBestAsk = book.getBestAsk();
		//noinspection ConstantConditions
		if (bestAsk == null || (testBestAsk != null && testBestAsk.getPrice().compareTo(bestAsk.getPrice()) < 0))
			bestAsk = testBestAsk;

		return bestAsk;
	}

	@When("select * from Book")
	private void recordBook(Book b) {
		Market market = b.getMarket();

		String listingSymbol = market.getListing().getSymbol();
		Book lastBookForListing = lastBookByListing.get(listingSymbol);
		if (lastBookForListing == null || lastBookForListing.getTime().isBefore(b.getTime()))
			lastBookByListing.put(listingSymbol, b);

		String marketSymbol = market.getSymbol();
		Book lastBookForMarket = lastBookByMarket.get(marketSymbol);
		if (lastBookForMarket == null || lastBookForMarket.getTime().isBefore(b.getTime()))
			lastBookByMarket.put(marketSymbol, b);

		Offer bestBid = b.getBestBid();
		Book lastBestBidBook = bestBidByMarket.get(marketSymbol);
		//noinspection ConstantConditions
		if (bestBid != null && (lastBestBidBook == null || bestBid.getPrice().compareTo(lastBestBidBook.getBestBid().getPrice()) > 0))
			bestBidByMarket.put(marketSymbol, b);

		Offer bestAsk = b.getBestAsk();
		Book lastBestAskBook = bestAskByMarket.get(marketSymbol);
		//noinspection ConstantConditions
		if (bestAsk != null && (lastBestAskBook == null || bestAsk.getPrice().compareTo(lastBestAskBook.getBestAsk().getPrice()) < 0))
			bestAskByMarket.put(marketSymbol, b);
	}

	@When("select * from Trade")
	private void recordTrade(Trade t) {
		Market market = t.getMarket();
		handleMarket(market);

		String listingSymbol = market.getListing().getSymbol();
		Trade lastTradeForListing = lastTradeByListing.get(listingSymbol);
		if (lastTradeForListing == null || lastTradeForListing.getTime().isBefore(t.getTime()))
			lastTradeByListing.put(listingSymbol, t);

		String marketSymbol = market.getSymbol();
		Trade lastTradeForMarket = lastTradeByMarket.get(marketSymbol);
		if (lastTradeForMarket == null || lastTradeForMarket.getTime().isBefore(t.getTime()))
			lastTradeByMarket.put(marketSymbol, t);
	}

	private void handleMarket(Market market) {
		final Listing listing = market.getListing();
		final String listingSymbol = listing.getSymbol();
		Set<Market> markets = marketsByListing.get(listingSymbol);
		if (markets == null) {
			markets = new HashSet<>();
			markets.add(market);
			marketsByListing.put(listingSymbol, markets);
		} else
			markets.add(market);
	}

	private final Map<String, Trade> lastTradeByListing = new HashMap<>();
	private final Map<String, Book> lastBookByListing = new HashMap<>();
	private final Map<String, Trade> lastTradeByMarket = new HashMap<>();
	private final Map<String, Book> lastBookByMarket = new HashMap<>();
	private final Map<String, Book> bestBidByMarket = new HashMap<>();
	private final Map<String, Book> bestAskByMarket = new HashMap<>();
	private final Map<String, Set<Market>> marketsByListing = new HashMap<>();
}
