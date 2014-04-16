package com.cryptocoinpartners.module.xchangedata;

import com.cryptocoinpartners.module.Esper;
import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.schema.*;
import com.cryptocoinpartners.util.PersistUtil;
import com.cryptocoinpartners.util.RateLimiter;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.apache.commons.configuration.*;
import org.joda.time.Duration;
import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Tim Olson
 */
public class XchangeData extends ModuleListenerBase {


    public void initModule(final Esper esper, Configuration config) {
        super.initModule(esper, config);

        final String configPrefix = "xchange";

        // find all the config keys starting with "xchange." and collect their second groups after the dot
        final Iterator xchangeConfigKeys = config.getKeys(configPrefix);
        Set<String> exchangeTags = new HashSet<String>();
        final Pattern configPattern = Pattern.compile(configPrefix+"\\.([^\\.]+)\\..+");
        while( xchangeConfigKeys.hasNext() ) {
            String key = (String) xchangeConfigKeys.next();
            final Matcher matcher = configPattern.matcher(key);
            if( matcher.matches() )
                exchangeTags.add(matcher.group(1));
        }


        // now we have all the exchange tags.  process each config group
        for( String tag : exchangeTags ) {
            // three configs required:
            // .class the full classname of the Xchange implementation
            // .rate.queries rate limit the number of queries to this many (default: 1)
            // .rate.period rate limit the number of queries during this period of time (default: 1 second)
            Market market = Market.forSymbol(tag.toUpperCase());
            if( market != null ) {
                String prefix = configPrefix+"." + tag + '.';
                final String exchangeClassName = config.getString(prefix + "class");
                final String helperClassName = config.getString(prefix + "helper.class", null);
                int queries = config.getInt(prefix + "rate.queries", 1);
                Duration period = Duration.millis((long) (1000 * config.getDouble(prefix + "rate.period", 1))); // rate.period in seconds
                final List listings = config.getList(prefix + "listings");
                initExchange(exchangeClassName, helperClassName, queries, period, market, listings);
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
        Object[] getTradeParameters( CurrencyPair pair, long lastTradeTime, long lastTradeId );
        void handleTrades( Trades tradeSpec );
    }


    private void initExchange( String exchangeClassName, @Nullable String helperClassName, int queries, Duration per,
                               Market market, List listings )
    {
        Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exchangeClassName);
        Helper helper = null;
        if( helperClassName != null && !helperClassName.isEmpty() ) {
            if( helperClassName.indexOf('.') == -1 )
                helperClassName = XchangeData.class.getPackage().getName()+'.'+helperClassName;
            try {
                final Class<?> helperClass = getClass().getClassLoader().loadClass(helperClassName);
                try {
                    helper = (Helper) helperClass.newInstance();
                }
                catch( InstantiationException e ) {
                    log.error("Could not initialize XchanngeData "+exchangeClassName+" because helper class "+helperClassName+" could not be instantiated ",e);
                    return;
                }
                catch( IllegalAccessException e ) {
                    log.error("Could not initialize XchanngeData "+exchangeClassName+" because helper class "+helperClassName+" could not be instantiated ",e);
                    return;
                }
                catch( ClassCastException e ) {
                    log.error("Could not initialize XchanngeData "+exchangeClassName+" because helper class "+helperClassName+" does not implement "+Helper.class);
                    return;
                }
            }
            catch( ClassNotFoundException e ) {
                log.error("Could not initialize XchanngeData "+exchangeClassName+" because helper class "+helperClassName+" was not found");
                return;
            }
        }
        PollingMarketDataService dataService = exchange.getPollingMarketDataService();
        RateLimiter rateLimiter = new RateLimiter(queries, per);
        Collection<MarketListing> marketListings = new ArrayList<MarketListing>(listings.size());
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
            pair = new CurrencyPair(marketListing.getBase().getSymbol(), marketListing.getQuote().getSymbol());
            lastTradeTime = 0;
            lastTradeId = 0;
            EntityManager entityManager = PersistUtil.createEntityManager();
            try {
                TypedQuery<Trade> query = entityManager.createQuery("select t from Trade t where marketListing=?1 and time=(select max(time) from Trade where marketListing=?1)",
                                                                    Trade.class);
                query.setParameter(1, marketListing);
                for( Trade trade : query.getResultList() ) {
                    long millis = trade.getTime().getMillis();
                    if( millis > lastTradeTime )
                        lastTradeTime = millis;
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
                Object[] params;
                if( helper != null )
                    params = helper.getTradeParameters(pair,lastTradeTime,lastTradeId);
                else
                    params = new Object[] {};
                Trades tradeSpec = dataService.getTrades(pair, params);
                if( helper != null )
                    helper.handleTrades(tradeSpec);
                List<com.xeiam.xchange.dto.marketdata.Trade> trades = tradeSpec.getTrades();
                for( com.xeiam.xchange.dto.marketdata.Trade trade : trades ) {
                    long remoteId = Long.valueOf(trade.getId());
                    if( remoteId > lastTradeId ) {
                        Instant tradeInstant = new Instant(trade.getTimestamp());
                        Trade ourTrade =
                                new Trade(marketListing, tradeInstant, trade.getId(),
                                          trade.getPrice(), trade.getTradableAmount());
                        esper.publish(ourTrade);
                        lastTradeTime = tradeInstant.getMillis();
                        lastTradeId = remoteId;
                    }
                }
            }
            catch( IOException e ) {
                log.warn("Could not get Bitfinex trades for "+ marketListing,e);
                esper.publish(new MarketDataError(marketListing, e));
            }
            finally {
                // requeue
                rateLimiter.execute(this);
            }
        }


        private PollingMarketDataService dataService;
        private RateLimiter rateLimiter;
        private final Esper esper;
        private final MarketListing marketListing;
        private CurrencyPair pair;
        private long lastTradeTime;
        private long lastTradeId;
    }


}
