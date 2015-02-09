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
import org.cryptocoinpartners.schema.Exchanges;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.ListingsMatrix;
import org.joda.time.Instant;

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
        bestBid = ((bestBid == null) ? getImpliedBestAskForListing(listing) : bestBid);

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
        if (marketsByListing.get(listing.getSymbol()) != null) {
            for (Market market : marketsByListing.get(listing.getSymbol())) {
                Book book = bestAskByMarket.get(market.getSymbol());
                Offer testBestAsk = book.getBestAsk();
                //noinspection ConstantConditions
                if (bestAsk == null || (testBestAsk != null && testBestAsk.getPrice().compareTo(bestAsk.getPrice()) < 0))
                    bestAsk = testBestAsk;

            }

        }
        bestAsk = ((bestAsk == null) ? getImpliedBestAskForListing(listing) : bestAsk);

        return bestAsk;

    }

    @Override
    public @Nullable
    Offer getImpliedBestAskForListing(Listing listing) {
        try {
            long bestImpliedAsk = impliedAskMatrix.getRate(listing.getBase(), listing.getQuote());
            Market market = Market.findOrCreate(Exchanges.SELF, listing);
            return new Offer(market, Instant.now(), Instant.now(), bestImpliedAsk, 0L);
        } catch (java.lang.IllegalArgumentException e) {

            return null;
        }
    }

    @Override
    public @Nullable
    Offer getImpliedBestBidForListing(Listing listing) {
        try {
            long bestImpliedBid = impliedBidMatrix.getRate(listing.getBase(), listing.getQuote());
            Market market = Market.findOrCreate(Exchanges.SELF, listing);
            return new Offer(market, Instant.now(), Instant.now(), bestImpliedBid, 0L);
        } catch (java.lang.IllegalArgumentException e) {

            return null;
        }

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

    //@Priority(10)
    @When("@Priority(9) select * from Book")
    private void recordBook(Book b) {
        Market market = b.getMarket();
        handleMarket(market);

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
        try {
            impliedBidMatrix.updateRates(market.getBase(), market.getQuote(), b.getBidPrice().getCount());
        } catch (java.lang.IllegalArgumentException e) {
            try {
                impliedBidMatrix.addAsset(market.getBase(), market.getQuote(), b.getBidPrice().getCount());
            } catch (java.lang.IllegalArgumentException e2) {
                //              //we not recived enouhg trades to create a link to other currecny
                //              BigDecimal inverseRateBD = (((BigDecimal.valueOf(1 / (bestBid.getMarket().getQuote().getBasis()))).divide(
                //                      BigDecimal.valueOf(bestBid.getPriceCount()), bestBid.getMarket().getBase().getScale(), RoundingMode.HALF_EVEN)).divide(BigDecimal
                //                      .valueOf(bestBid.getMarket().getBase().getBasis())));
                //              long inverseCrossRate = inverseRateBD.longValue();
                //              impliedBidMatrix.addAsset(bestBid.getMarket().getQuote(), bestBid.getMarket().getBase(), inverseCrossRate);
            }
        }

        Offer bestAsk = b.getBestAsk();
        Book lastBestAskBook = bestAskByMarket.get(marketSymbol);
        //noinspection ConstantConditions
        if (bestAsk != null && (lastBestAskBook == null || bestAsk.getPrice().compareTo(lastBestAskBook.getBestAsk().getPrice()) < 0))
            bestAskByMarket.put(marketSymbol, b);
        try {
            impliedAskMatrix.updateRates(market.getBase(), market.getQuote(), b.getAskPrice().getCount());
        } catch (java.lang.IllegalArgumentException e) {
            try {
                impliedAskMatrix.addAsset(market.getBase(), market.getQuote(), b.getAskPrice().getCount());
            } catch (java.lang.IllegalArgumentException e2) {
                e2.printStackTrace();
                //we not recived enouhg trades to create a link to other currecny
                //we not recived enouhg trades to create a link to other currecny
                //              BigDecimal inverseRateBD = (((BigDecimal.valueOf(1 / (bestAsk.getMarket().getQuote().getBasis()))).divide(
                //                      BigDecimal.valueOf(bestAsk.getPriceCount()), bestAsk.getMarket().getBase().getScale(), RoundingMode.HALF_EVEN)).divide(BigDecimal
                //                      .valueOf(bestAsk.getMarket().getBase().getBasis())));
                //              long inverseCrossRate = inverseRateBD.longValue();
                //              impliedBidMatrix.addAsset(bestAsk.getMarket().getQuote(), bestAsk.getMarket().getBase(), inverseCrossRate);

            }

        }

    }

    @When("@Priority(9) select * from Trade")
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

    private final ListingsMatrix impliedBidMatrix = new ListingsMatrix();
    private final ListingsMatrix impliedAskMatrix = new ListingsMatrix();

    private final Map<String, Trade> lastTradeByListing = new HashMap<>();
    private final Map<String, Book> lastBookByListing = new HashMap<>();
    private final Map<String, Trade> lastTradeByMarket = new HashMap<>();
    private final Map<String, Book> lastBookByMarket = new HashMap<>();
    private final Map<String, Book> bestBidByMarket = new HashMap<>();
    private final Map<String, Book> bestAskByMarket = new HashMap<>();
    private final Map<String, Set<Market>> marketsByListing = new HashMap<>();

}
