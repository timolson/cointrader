package org.cryptocoinpartners.module.xchangedata;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.module.Esper;
import org.cryptocoinpartners.module.ModuleListenerBase;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.RateLimiter;
import org.cryptocoinpartners.util.XchangeUtil;
import org.joda.time.Duration;
import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


/**
 * @author Tim Olson
 */
public class XchangeData extends ModuleListenerBase {


    public void initModule(final Esper esper, Configuration config) {
        super.initModule(esper, config);

        final String configPrefix = "xchange";
        Set<String> exchangeTags = XchangeUtil.getExchangeTags();


        // now we have all the exchange tags.  process each config group
        for( String tag : exchangeTags ) {
            // three configs required:
            // .class the full classname of the Xchange implementation
            // .rate.queries rate limit the number of queries to this many (default: 1)
            // .rate.period rate limit the number of queries during this period of time (default: 1 second)
            // .listings identifies which Listings should be fetched from this exchange
            Market market = XchangeUtil.getMarketForExchangeTag(tag);
            if( market != null ) {
                String prefix = configPrefix+"." + tag + '.';
                final String helperClassName = config.getString(prefix + "helper.class", null);
                int queries = config.getInt(prefix + "rate.queries", 1);
                Duration period = Duration.millis((long) (1000 * config.getDouble(prefix + "rate.period", 1))); // rate.period in seconds
                final List listings = config.getList(prefix + "listings");
                initExchange(helperClassName, queries, period, market, listings);
            }
            else {
                log.warn("Could not find Market for property \"xchange." + tag + ".*\"");
            }
        }
    }


    public void destroyModule() {
        super.destroyModule();
    }


    /** You may implement this interface to customize the interaction with the Xchange library for each exchange.
        Set the class name of your Helper in the module configuration using the key:<br/>
        xchange.<marketname>.helper.class=com.foo.bar.MyHelper<br/>
        if you leave out the package name it is assumed to be the same as the XchangeData class (i.e. the xchange
        module package).
     */
    public interface Helper {
        Object[] getTradesParameters( CurrencyPair pair, long lastTradeTime, long lastTradeId );
        Object[] getOrderBookParameters( CurrencyPair pair );
        void handleTrades( Trades tradeSpec );
        void handleOrderBook( OrderBook orderBook );
    }


    private void initExchange( @Nullable String helperClassName, int queries, Duration per,
                               Market market, List listings )
    {
        Exchange exchange = XchangeUtil.getExchangeForMarket(market);
        Helper helper = null;
        if( helperClassName != null && !helperClassName.isEmpty() ) {
            if( helperClassName.indexOf('.') == -1 )
                helperClassName = XchangeData.class.getPackage().getName()+'.'+helperClassName;
            try {
                final Class<?> helperClass = getClass().getClassLoader().loadClass(helperClassName);
                try {
                    helper = (Helper) helperClass.newInstance();
                }
                catch( InstantiationException | IllegalAccessException e ) {
                    log.error("Could not initialize XchangeData because helper class "+helperClassName+" could not be instantiated ",e);
                    return;
                }
                catch( ClassCastException e ) {
                    log.error("Could not initialize XchangeData because helper class "+helperClassName+" does not implement "+Helper.class);
                    return;
                }
            }
            catch( ClassNotFoundException e ) {
                log.error("Could not initialize XchangeData because helper class "+helperClassName+" was not found");
                return;
            }
        }
        PollingMarketDataService dataService = exchange.getPollingMarketDataService();
        RateLimiter rateLimiter = new RateLimiter(queries, per);
        Collection<MarketListing> marketListings = new ArrayList<>(listings.size());
        for( Object listingSymbol : listings ) {
            Listing listing = Listing.forSymbol(listingSymbol.toString().toUpperCase());
            final MarketListing marketListing = MarketListing.findOrCreate(market, listing);
            marketListings.add(marketListing);
        }
        for( final MarketListing marketListing : marketListings ) {
            rateLimiter.execute(new FetchTradesRunnable(esper, marketListing, rateLimiter, dataService, helper));
        }
    }


    private class FetchTradesRunnable implements Runnable {


        private final Helper helper;


        public FetchTradesRunnable(Esper esper, MarketListing marketListing, RateLimiter rateLimiter,
                                   PollingMarketDataService dataService, @Nullable Helper helper ) {
            this.esper = esper;
            this.marketListing = marketListing;
            this.rateLimiter = rateLimiter;
            this.dataService = dataService;
            this.helper = helper;
            pair = XchangeUtil.getCurrencyPairForListing(marketListing.getListing());
            lastTradeTime = 0;
            lastTradeId = 0;
            EntityManager entityManager = PersistUtil.createEntityManager();
            try {
                TypedQuery<org.cryptocoinpartners.schema.Trade> query = entityManager.createQuery("select t from Trade t where marketListing=?1 and time=(select max(time) from Trade where marketListing=?1)",
                                                                    org.cryptocoinpartners.schema.Trade.class);
                query.setParameter(1, marketListing);
                for( org.cryptocoinpartners.schema.Trade trade : query.getResultList() ) {
                    long millis = trade.getTime().getMillis();
                    if( millis > lastTradeTime )
                        lastTradeTime = millis;
                    // todo this is broken and assumes an increasing integer remote key
                    Long remoteId = Long.valueOf(trade.getRemoteKey());
                    if( remoteId > lastTradeId )
                        lastTradeId = remoteId;
                }
            }
            finally {
                entityManager.close();
            }
        }


        public void run() {
            try {
                if( getTradesNext )
                    getTrades();
                else
                    getBook();
            }
            finally {
                getTradesNext = !getTradesNext;
                rateLimiter.execute(this); // run again. requeue
            }
        }


        protected void getTrades()
        {
            try {
                Object[] params;
                if( helper != null )
                    params = helper.getTradesParameters(pair, lastTradeTime, lastTradeId);
                else
                    params = new Object[] { };
                Trades tradeSpec = dataService.getTrades(pair, params);
                if( helper != null )
                    helper.handleTrades(tradeSpec);
                List<com.xeiam.xchange.dto.marketdata.Trade> trades = tradeSpec.getTrades();
                for( com.xeiam.xchange.dto.marketdata.Trade trade : trades ) {
                    long remoteId = Long.valueOf(trade.getId());
                    if( remoteId > lastTradeId ) {
                        Instant tradeInstant = new Instant(trade.getTimestamp());
                        org.cryptocoinpartners.schema.Trade ourTrade = new org.cryptocoinpartners.schema.Trade(marketListing, tradeInstant, trade.getId(),
                                                   trade.getPrice(), trade.getTradableAmount());
                        esper.publish(ourTrade);
                        lastTradeTime = tradeInstant.getMillis();
                        lastTradeId = remoteId;
                    }
                }
            }
            catch( IOException e ) {
                log.warn("Could not get trades for " + marketListing, e);
                esper.publish(new MarketDataError(marketListing, e));
            }
        }


        protected void getBook()
        {
            try {
                Object[] params;
                if( helper != null )
                    params = helper.getOrderBookParameters(pair);
                else
                    params = new Object[0];
                final OrderBook orderBook = dataService.getOrderBook(pair, params);
                if( helper != null )
                    helper.handleOrderBook(orderBook);
                bookBuilder.start(new Instant(orderBook.getTimeStamp()), null, marketListing);
                for( LimitOrder limitOrder : orderBook.getBids() )
                    bookBuilder.addBid(limitOrder.getLimitPrice(),limitOrder.getTradableAmount());
                for( LimitOrder limitOrder : orderBook.getAsks() )
                    bookBuilder.addAsk(limitOrder.getLimitPrice(), limitOrder.getTradableAmount());
                Book book = bookBuilder.build();
                esper.publish(book);
            }
            catch( IOException e ) {
                log.warn("Could not get book for " + marketListing, e);
                esper.publish(new MarketDataError(marketListing, e));
            }
        }


        private Book.Builder bookBuilder = new Book.Builder();
        private boolean getTradesNext = true;
        private PollingMarketDataService dataService;
        private RateLimiter rateLimiter;
        private final Esper esper;
        private final MarketListing marketListing;
        private CurrencyPair pair;
        private long lastTradeTime;
        private long lastTradeId;
    }


}
