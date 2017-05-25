package org.cryptocoinpartners.module;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Exchanges;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.ListingsMatrix;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * This service listens to the Context and caches the most recent Trades and Books
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public class BasicQuoteService implements QuoteService {

    @Override
    public Trade getLastTrade(Tradeable market) {
        if (market == null)
            return null;
        /*if (XchangeData.exists()) {
            XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
            try {

                for (Trade trade : xchangeData.getTrades(market, market.getExchange()))
                    recordTrade(trade);
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                log.error(this.getClass().getSimpleName() + ": getLastTrade - Unable to retrive latest trades for market", e);
            }
        }*/
        return lastTradeByMarket.get(market.getSymbol());
    }

    @Override
    public Bar getLastBar(Tradeable market, double interval) {
        if (market == null)
            return null;
        /*if (XchangeData.exists()) {
            XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
            try {

                for (Trade trade : xchangeData.getTrades(market, market.getExchange()))
                    recordTrade(trade);
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                log.error(this.getClass().getSimpleName() + ": getLastTrade - Unable to retrive latest trades for market", e);
            }
        }*/
        return lastBarByMarket.get(market.getSymbol()).get(interval);
    }

    @Override
    public Trade getLastTrade(Listing listing) {
        if (listing == null)
            return null;

        /*        if (XchangeData.exists()) {
                    XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
                    try {
                        for (Market market : getMarketsForListing(listing))
                            for (Trade trade : xchangeData.getTrades(market, market.getExchange()))
                                recordTrade(trade);
                    } catch (Throwable e) {
                        // TODO Auto-generated catch block
                        log.error(this.getClass().getSimpleName() + ": getLastTrade - Unable to retrive latest trades for market", e);
                    }
                }*/
        return lastTradeByListing.get(listing.getSymbol());
    }

    @Override
    public Book getLastBook(Tradeable market) {
        if (market == null)
            return null;
        /*        if (XchangeData.exists()) {
                    XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
                    try {
                        recordBook(xchangeData.getBook(market, market.getExchange()));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        log.error(this.getClass().getSimpleName() + ": getLastBidForMarket - Unable to retrive latest book for market", e);
                    }
                }*/
        return lastBookByMarket.get(market.getSymbol());
    }

    @Override
    public Book getLastBook(Listing listing) {
        if (listing == null)
            return null;
        /*        if (XchangeData.exists()) {
                    XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
                    try {
                        for (Market market : getMarketsForListing(listing))
                            recordBook(xchangeData.getBook(market, market.getExchange()));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        log.error(this.getClass().getSimpleName() + ": getLastBidForMarket - Unable to retrive latest book for market", e);
                    }
                }*/
        return lastBookByListing.get(listing.getSymbol());
    }

    @Override
    public Set<Market> getMarketsForListing(Listing listing) {
        if (listing == null)
            return null;
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
            if (bestBid == null || bestBid.getVolumeCount() == 0 || bestBid.getPriceCount() == 0
                    || (testBestBid != null && testBestBid.getPrice().compareTo(bestBid.getPrice()) > 0))
                bestBid = testBestBid;
        }
        bestBid = ((bestBid == null) ? getImpliedBestAskForListing(listing) : bestBid);

        return bestBid;
    }

    @Override
    public @Nullable
    Offer getLastBidForMarket(Tradeable market) {
        if (market == null)
            return null;
        Offer bestBid = null;
        Offer testBestBid = null;
        /*        if (XchangeData.exists()) {
                    XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
                    try {
                        recordBook(xchangeData.getBook(market, market.getExchange()));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        log.error(this.getClass().getSimpleName() + ": getLastBidForMarket - Unable to retrive latest book for market", e);
                    }
                }*/

        //XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
        // for( Market market : marketsByListing.get(listing.getSymbol()) ) {
        Book book = lastBookByMarket.get(market.getSymbol());
        if (book != null)
            testBestBid = book.getBestBid();
        //noinspection ConstantConditions

        if (bestBid == null || bestBid.getVolumeCount() == 0 || bestBid.getPriceCount() == 0
                || (testBestBid != null && testBestBid.getPrice().compareTo(bestBid.getPrice()) > 0))
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
                if (bestAsk == null || bestAsk.getVolumeCount() == 0 || bestAsk.getPriceCount() == 0
                        || (testBestAsk != null && testBestAsk.getPrice().compareTo(bestAsk.getPrice()) < 0))
                    bestAsk = testBestAsk;

            }

        }
        bestAsk = ((bestAsk == null) ? getImpliedBestAskForListing(listing) : bestAsk);

        return bestAsk;

    }

    @Override
    //TODO keep a map of markets so we don't hit the db each time.
    public @Nullable
    Offer getImpliedBestAskForListing(Listing listing) {
        if (listing == null)
            return null;
        try {
            long bestImpliedAsk = impliedAskMatrix.getRate(listing.getBase(), listing.getQuote());
            Market market = context.getInjector().getInstance(Market.class).findOrCreate(Exchanges.SELF, listing);
            return new Offer(market, Instant.now(), Instant.now(), bestImpliedAsk, 0L);
        } catch (java.lang.IllegalArgumentException e) {

            return null;
        }
    }

    @Override
    public @Nullable
    Offer getImpliedBestBidForListing(Listing listing) {
        if (listing == null)
            return null;
        try {
            long bestImpliedBid = impliedBidMatrix.getRate(listing.getBase(), listing.getQuote());
            Market market = context.getInjector().getInstance(Market.class).findOrCreate(Exchanges.SELF, listing);
            return new Offer(market, Instant.now(), Instant.now(), bestImpliedBid, 0L);
        } catch (java.lang.IllegalArgumentException e) {

            return null;
        }

    }

    @Override
    public @Nullable
    Offer getLastAskForMarket(Tradeable market) {
        if (market == null)
            return null;
        Offer bestAsk = null;
        Offer testBestAsk = null;
        /*        if (XchangeData.exists()) {
                    XchangeData xchangeData = context.getInjector().getInstance(XchangeData.class);
                    try {
                        recordBook(xchangeData.getBook(market, market.getExchange()));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        log.error(this.getClass().getSimpleName() + ": getLastAskForMarket - Unable to retrive latest book for market", e);
                    }
                }*/
        Book book = lastBookByMarket.get(market.getSymbol());
        if (book != null)
            testBestAsk = book.getBestAsk();
        //noinspection ConstantConditions
        if (bestAsk == null || bestAsk.getVolumeCount() == 0 || bestAsk.getPriceCount() == 0
                || (testBestAsk != null && testBestAsk.getPrice().compareTo(bestAsk.getPrice()) < 0))
            bestAsk = testBestAsk;

        return bestAsk;
    }

    //@Priority(10)
    @When("@Priority(9) select * from Book(Book.bidVolumeAsDouble>0, Book.askVolumeAsDouble<0)")
    private synchronized void recordBook(Book b) {
        Tradeable market = b.getMarket();
        if (!market.isSynthetic()) {
            Market marketToHandel = (Market) market;

            handleMarket(marketToHandel);

            String listingSymbol = marketToHandel.getListing().getSymbol();
            Book lastBookForListing = lastBookByListing.get(listingSymbol);
            if (lastBookForListing == null || lastBookForListing.getTime().isBefore(b.getTime()))
                lastBookByListing.put(listingSymbol, b);
            try {
                impliedBidMatrix.updateRates(marketToHandel.getBase(), marketToHandel.getQuote(), b.getBidPrice().getCount());
            } catch (java.lang.IllegalArgumentException e) {
                try {
                    impliedBidMatrix.addAsset(marketToHandel.getBase(), marketToHandel.getQuote(), b.getBidPrice().getCount());
                } catch (java.lang.IllegalArgumentException e2) {
                }
            }
            try {
                impliedAskMatrix.updateRates(marketToHandel.getBase(), marketToHandel.getQuote(), b.getAskPrice().getCount());
            } catch (java.lang.IllegalArgumentException e) {
                try {
                    impliedAskMatrix.addAsset(marketToHandel.getBase(), marketToHandel.getQuote(), b.getAskPrice().getCount());
                } catch (java.lang.IllegalArgumentException e2) {
                    log.error("Threw a Execption, full stack trace follows:", e2);

                }

            }

        }

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

    @When("@Priority(9) select * from Trade (Trade.volumeCount!=0)")
    private synchronized void recordTrade(Trade t) {
        Tradeable market = t.getMarket();
        if (!market.isSynthetic()) {
            Market marketToHandle = (Market) market;

            handleMarket(marketToHandle);

            String listingSymbol = marketToHandle.getListing().getSymbol();
            Trade lastTradeForListing = lastTradeByListing.get(listingSymbol);
            if (lastTradeForListing == null || lastTradeForListing.getTime().isBefore(t.getTime()))
                lastTradeByListing.put(listingSymbol, t);
        }

        String marketSymbol = market.getSymbol();
        Trade lastTradeForMarket = lastTradeByMarket.get(marketSymbol);
        if (lastTradeForMarket == null || lastTradeForMarket.getTime().isBefore(t.getTime()))
            lastTradeByMarket.put(marketSymbol, t);
    }

    @When("@Priority(9) select * from LastBarWindow group by market,interval")
    private synchronized void recordBar(Bar b) {
        Tradeable market = b.getMarket();
        double interval = b.getInterval();

        if (!market.isSynthetic()) {
            Market marketToHandle = (Market) b.getMarket();
            handleMarket(marketToHandle);

            String listingSymbol = marketToHandle.getListing().getSymbol();
            Bar lastBarForListing = lastBarByListing.get(listingSymbol) == null || lastBarByListing.get(listingSymbol).isEmpty() ? null : lastBarByListing.get(
                    listingSymbol).get(interval);

            if (lastBarForListing == null || lastBarForListing.getTime().isBefore(b.getTime())) {
                ConcurrentHashMap<Double, Bar> barInterval = new ConcurrentHashMap<Double, Bar>();
                if (lastBarByListing.get(listingSymbol) == null) {
                    barInterval.put(interval, b);

                    lastBarByListing.put(listingSymbol, barInterval);
                } else
                    lastBarByListing.get(listingSymbol).put(interval, b);

            }
        }

        String marketSymbol = market.getSymbol();
        Bar lastBarForMarket = lastBarByMarket.get(marketSymbol) == null || lastBarByMarket.get(marketSymbol).isEmpty() ? null : lastBarByMarket.get(
                marketSymbol).get(interval);

        // Bar lastBarForMarket = lastBarByMarket.get(marketSymbol).get(interval);
        if (lastBarForMarket == null || lastBarForMarket.getTime().isBefore(b.getTime())) {

            ConcurrentHashMap<Double, Bar> barInterval = new ConcurrentHashMap<Double, Bar>();
            if (lastBarByMarket.get(marketSymbol) == null) {
                barInterval.put(interval, b);

                lastBarByMarket.put(marketSymbol, barInterval);
            } else
                lastBarByMarket.get(marketSymbol).put(interval, b);

        }

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
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.quoteService");
    @Inject
    protected Context context;

    private final Map<String, Trade> lastTradeByListing = new ConcurrentHashMap<>();
    private final Map<String, Book> lastBookByListing = new ConcurrentHashMap<>();
    private final Map<String, Trade> lastTradeByMarket = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentHashMap<Double, Bar>> lastBarByMarket = new ConcurrentHashMap<String, ConcurrentHashMap<Double, Bar>>();
    private final Map<String, ConcurrentHashMap<Double, Bar>> lastBarByListing = new ConcurrentHashMap<String, ConcurrentHashMap<Double, Bar>>();

    private final Map<String, Book> lastBookByMarket = new ConcurrentHashMap<>();
    private final Map<String, Book> bestBidByMarket = new ConcurrentHashMap<>();
    private final Map<String, Book> bestAskByMarket = new ConcurrentHashMap<>();
    private final Map<String, Set<Market>> marketsByListing = new ConcurrentHashMap<>();

}
