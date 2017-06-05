package org.cryptocoinpartners.module.xchange;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.BookFactory;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.schema.TradeFactory;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.RateLimiter;
import org.cryptocoinpartners.util.XchangeUtil;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.okcoin.FuturesContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
@Singleton
public class XchangeData {

    @Inject
    public XchangeData(Context context, Configuration config, BookFactory bookFactory, TradeFactory tradeFactory) {
        instanceExists = true;
        this.context = context;
        this.bookFactory = bookFactory;
        this.tradeFactory = tradeFactory;
        final String configPrefix = "xchange";
        Set<String> exchangeTags = XchangeUtil.getExchangeTags();

        // now we have all the exchange tags.  process each config group
        for (String tag : exchangeTags) {
            // three configs required:
            // .class the full classname of the Xchange implementation
            // .rate.queries rate limit the number of queries to this many (default: 1)
            // .rate.period rate limit the number of queries during this period of time (default: 1 second)
            // .listings identifies which Listings should be fetched from this exchange
            Exchange exchange = XchangeUtil.getExchangeForTag(tag);
            String prefix = configPrefix + "." + tag + '.';
            if (exchange != null) {

                final String helperClassName = config.getString(prefix + "helper.class", null);
                final String streamingConfigClassName = config.getString(prefix + "streaming.config.class", null);
                int queries = config.getInt(prefix + "rate.queries", 1);
                int retryCount = config.getInt(prefix + "retry", 10);
                Duration period = Duration.millis((long) (1000 * config.getDouble(prefix + "rate.period", 1))); // rate.period in seconds
                final List listings = config.getList(prefix + "listings");
                initExchange(helperClassName, streamingConfigClassName, queries, period, exchange, listings, retryCount);
            } else {
                log.warn("Could not find Exchange for property \"xchange." + tag + ".*\"");
            }
        }
    }

    /** You may implement this interface to customize the interaction with the Xchange library for each exchange.
        Set the class name of your Helper in the module configuration using the key:<br/>
        xchange.<marketname>.helper.class=com.foo.bar.MyHelper<br/>
        if you leave out the package name it is assumed to be the same as the XchangeData class (i.e. the xchange
        module package).
     */
    public interface Helper {
        Object[] getTradesParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId);

        Object[] getOrderBookParameters(CurrencyPair pair);

        void handleTrades(Trades tradeSpec);

        void handleOrderBook(OrderBook orderBook);
    }

    private void initExchange(@Nullable String helperClassName, @Nullable String streamingConfigClassName, int queries, Duration per,
            Exchange coinTraderExchange, List listings, int retryCount) {
        org.knowm.xchange.Exchange xchangeExchange = XchangeUtil.getExchangeForMarket(coinTraderExchange);
        Helper helper = null;
        if (helperClassName != null && !helperClassName.isEmpty()) {
            if (helperClassName.indexOf('.') == -1)
                helperClassName = XchangeData.class.getPackage().getName() + '.' + helperClassName;
            try {
                final Class<?> helperClass = getClass().getClassLoader().loadClass(helperClassName);
                try {
                    helper = (Helper) helperClass.newInstance();
                    exchangeHelpers.put(coinTraderExchange, helper);
                } catch (InstantiationException | IllegalAccessException e) {
                    log.error("Could not initialize XchangeData because helper class " + helperClassName + " could not be instantiated ", e);
                    return;
                } catch (ClassCastException e) {
                    log.error("Could not initialize XchangeData because helper class " + helperClassName + " does not implement " + Helper.class);
                    return;
                }
            } catch (ClassNotFoundException e) {
                log.error("Could not initialize XchangeData because helper class " + helperClassName + " was not found");
                return;
            }
        }

        List<Market> markets = new ArrayList<>(listings.size());
        Market market;
        //  ExchangeStreamingConfiguration streamingConfiguration = new OkCoinExchangeStreamingConfiguration();
        for (Iterator<List> il = listings.iterator(); il.hasNext();) {
            Object listingSymbol = il.next();
            Listing listing = Listing.forSymbol(listingSymbol.toString().toUpperCase());
            market = context.getInjector().getInstance(Market.class).findOrCreate(coinTraderExchange, listing);
            markets.add(market);
        }

        RateLimiter rateLimiter = new RateLimiter(queries, per);

        //   initExchange(helperClassName, streamingConfigClassName, queries, period, exchange, listings);

        //   (@Nullable String helperClassName, @Nullable String streamingConfigClassName, int queries, Duration per,
        //         Exchange coinTraderExchange, List listings
        //       for (Iterator<Market> im = markets.iterator(); im.hasNext(); rateLimiter.execute(new FetchTradesRunnable(context, helperClassName,
        //             streamingConfigClassName, queries, per, coinTraderExchange, retryCount, xchangeExchange, market, rateLimiter, helper)))
        //       market = im.next();

        //TODO need to inialise data maps
        for (Market cointraderMarket : markets) {
            // add to various shared mapps
            lastTradeIds.put(cointraderMarket, 0L);
            lastTradeTimes.put(cointraderMarket, 0L);
            failedTradeCounts.put(cointraderMarket, 0);
            failedBookCounts.put(cointraderMarket, 0);
            retryCounts.put(cointraderMarket, retryCount);

            rateLimiter.execute(new FetchTradesRunnable(context, coinTraderExchange, cointraderMarket, rateLimiter));
        }

        // for (Iterator<Market> im = markets.iterator(); im.hasNext(); )
        //   market = im.next();

        //TODO need to inialise data maps

        // for (Iterator<Market> im = markets.iterator(); im.hasNext(); )
        //   market = im.next();

        return;

    }

    public synchronized Collection<org.cryptocoinpartners.schema.Trade> getTrades(Market market, Exchange coinTraderExchange) throws Throwable {
        Prompt prompt = market.getListing().getPrompt();
        ArrayList<org.cryptocoinpartners.schema.Trade> ourTrades = new ArrayList<org.cryptocoinpartners.schema.Trade>();
        CurrencyPair pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
        FuturesContract contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());
        if (lastTradeTimes.get(market) == null || lastTradeTimes.get(market) == 0 || lastTradeIds.get(market) == null || lastTradeIds.get(market) == 0) {
            try {

                List<org.cryptocoinpartners.schema.Trade> results = EM.queryList(org.cryptocoinpartners.schema.Trade.class,
                        "select t from Trade t where market=?1 and time=(select max(time) from Trade where market=?1)", market);

                for (org.cryptocoinpartners.schema.Trade trade : results) {
                    // org.cryptocoinpartners.schema.Trade trade = query.getSingleResult();
                    //long millis = Math.round(trade.getTime().getMillis() / 86400000);

                    if (trade.getTime().getMillis() > lastTradeTimes.get(market))
                        lastTradeTimes.put(market, trade.getTime().getMillis());
                    /*  Calendar cal = GregorianCalendar.getInstance();
                      cal.setTimeInMillis(lastTradeTime);
                      cal.set(Calendar.MILLISECOND, 0);
                      cal.set(Calendar.SECOND, 0);
                      cal.set(Calendar.MINUTE, 0);
                      cal.set(Calendar.HOUR_OF_DAY, 0);
                    */// long millis = cal.getTime().getTime();
                      //  trade.getTime().get
                      // todo this is broken and assumes an increasing integer remote key
                      // Long remoteId = Long.valueOf(trade.getRemoteKey().concat(String.valueOf(trade.getTimestamp())));
                      //Long remoteId = Long.valueOf(trade.getRemoteKey());
                      //lastTradeTime
                      //Long remoteId = cal.getTime().getTime() + Long.valueOf(trade.getRemoteKey());
                    Long remoteId = Long.valueOf(trade.getRemoteKey());
                    // Long remoteId=trade.getTime().getMillis();
                    //Long remoteId = millis+ Long.valueOf(trade.getRemoteKey());

                    // Long remoteId = Long.valueOf(String.valueOf(millis)+ Long.valueOf(trade.getRemoteKey()));

                    //   Long.valueOf(String.valueOf(lastTradeTime).concat(trade.getRemoteKey())).longValue();
                    if (remoteId > lastTradeIds.get(market))
                        lastTradeIds.put(market, remoteId);

                }
            } catch (Exception | Error e) {
                log.error(this.getClass().getSimpleName() + ":FetchTradesRunnable Unabel to query last trade id", e);
            }
        }

        try {
            Object params[];
            if (exchangeHelpers.get(coinTraderExchange) != null)
                params = exchangeHelpers.get(coinTraderExchange).getTradesParameters(pair, lastTradeTimes.get(market), lastTradeIds.get(market));
            else {
                if (contract == null)
                    params = new Object[] {};
                else
                    params = new Object[] { contract };

            }
            log.trace("Attempting to get trades from data service");
            Trades tradeSpec = XchangeUtil.getExchangeForMarket(coinTraderExchange).getMarketDataService().getTrades(pair, params);
            if (exchangeHelpers.get(coinTraderExchange) != null)
                exchangeHelpers.get(coinTraderExchange).handleTrades(tradeSpec);
            List<org.knowm.xchange.dto.marketdata.Trade> trades = tradeSpec.getTrades();
            log.trace("sorting trades by oldest first : " + trades);

            Collections.sort(trades, timeOrderIdComparator);

            //   Iterator<Trade> ilt = trades.iterator();
            //   log.trace("itterating over sorted trades: " + trades.size());

            for (Trade trade : trades) {
                // do {
                //   if (!ilt.hasNext())
                //     break;
                // org.knowm.xchange.dto.marketdata.Trade trade = ilt.next();
                /* Calendar cal = GregorianCalendar.getInstance();
                 cal.setTimeInMillis(trade.getTimestamp().getTime());
                 cal.set(Calendar.MILLISECOND, 0);
                 cal.set(Calendar.SECOND, 0);
                 cal.set(Calendar.MINUTE, 0);
                 cal.set(Calendar.HOUR_OF_DAY, 0);
                 // long remoteId = cal.getTime().getTime() + Long.valueOf(trade.getId());
                */long remoteId = Long.valueOf(trade.getId());
                //   long remoteId = trade.getTimestamp().getTime() + Long.valueOf(trade.getId());

                //  trade.getId().longValue();
                if ((trade.getTimestamp().getTime() > lastTradeTimes.get(market))
                        || (trade.getTimestamp().getTime() == lastTradeTimes.get(market) && remoteId > lastTradeTimes.get(market))) {
                    Instant tradeInstant = new Instant(trade.getTimestamp());
                    BigDecimal volume = (trade.getType() == OrderType.ASK) ? trade.getTradableAmount().negate() : trade.getTradableAmount();
                    log.trace("Creating new cointrader trades from: " + trade);

                    org.cryptocoinpartners.schema.Trade ourTrade = tradeFactory.create(market, tradeInstant, trade.getId(), trade.getPrice(), volume);
                    ourTrades.add(ourTrade);
                    // context.publish(ourTrade);
                    if (ourTrade.getDao() == null)
                        System.out.println("duffer");
                    lastTradeTimes.put(market, tradeInstant.getMillis());
                    lastTradeIds.put(market, remoteId);

                }

            }
            failedTradeCounts.put(market, 0);
            return ourTrades;

        } catch (Exception | Error e) {
            Integer tradeFailureCount = failedTradeCounts.get(market);
            tradeFailureCount++;
            failedTradeCounts.put(market, tradeFailureCount);

            log.info(this.getClass().getSimpleName() + ":getTrades Unabel to get trade for market " + market + " pair " + pair + ".  Failure "
                    + tradeFailureCount + " of " + retryCounts.get(market) + ".");
            if (tradeFailureCount >= retryCounts.get(market)) {
                //try {
                //  if (rateLimiter.getRunnables() == null || rateLimiter.getRunnables().isEmpty() || rateLimiter.remove(this)) {

                log.error(this.getClass().getSimpleName() + ":getTrades Unabel to get trade for market " + market + " pair " + pair + " for "
                        + tradeFailureCount + " of " + retryCounts.get(market) + " time. Resetting Data Service Connection.", e);
                XchangeUtil.resetExchange(coinTraderExchange);
                // dataService = xchangeExchange.getPollingMarketDataService();
                failedTradeCounts.put(market, 0);
                // .// tradeFailureCount = 0;
                failedBookCounts.put(market, 0);
                throw e;
                //}
                // } catch (Throwable e) {
                // TODO Auto-generated catch block
                //       throw e;
                // }
                //Thread.currentThread().

                // dataService = xchangeExchange.getPollingMarketDataService();

            }
            return null;
        }

    }

    public static boolean exists() {
        return instanceExists;

    }

    public synchronized Book getBook(Market market, Exchange coinTraderExchange) throws Exception {
        Prompt prompt = market.getListing().getPrompt();
        CurrencyPair pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
        FuturesContract contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());

        try {
            Object params[];
            if (exchangeHelpers.get(coinTraderExchange) != null)
                params = exchangeHelpers.get(coinTraderExchange).getOrderBookParameters(pair);
            else {
                if (contract == null)
                    params = new Object[] {};
                else
                    params = new Object[] { contract };

            }
            log.trace("Attempting to get book from data service");

            OrderBook orderBook = XchangeUtil.getExchangeForMarket(coinTraderExchange).getMarketDataService().getOrderBook(pair, params);
            if (exchangeHelpers.get(coinTraderExchange) != null)
                exchangeHelpers.get(coinTraderExchange).handleOrderBook(orderBook);
            log.trace("Attempting create book from: " + orderBook);
            Book book = bookFactory.create(new Instant(orderBook.getTimeStamp()), market);
            LimitOrder limitOrder;
            // sort lowerst to highest limit price
            List<LimitOrder> asks = orderBook.getAsks();
            log.trace("Attempting to sort asks: " + asks);

            Collections.sort(asks, limitPriceComparator);
            // sort highest to lowest limit price
            List<LimitOrder> bids = orderBook.getBids();
            log.trace("Attempting to sort bids: " + bids);

            Collections.sort(bids, Collections.reverseOrder(limitPriceComparator));
            // need to sort bids
            for (Iterator<LimitOrder> itb = bids.iterator(); itb.hasNext(); book.addBid(limitOrder.getLimitPrice(), limitOrder.getTradableAmount()))
                limitOrder = itb.next();

            // neet to sort asks
            for (Iterator<LimitOrder> ita = asks.iterator(); ita.hasNext(); book.addAsk(limitOrder.getLimitPrice(), limitOrder.getTradableAmount()))
                limitOrder = ita.next();

            book.build();
            //        log.debug("publish book:" + book.getId());

            failedBookCounts.put(market, 0);
            return book;
            //

        } catch (Exception | Error e) {
            Integer bookFailureCount = failedTradeCounts.get(market);
            bookFailureCount++;
            failedTradeCounts.put(market, bookFailureCount);
            log.info(this.getClass().getSimpleName() + ":getTrades Unabel to get trade for market " + market + " pair " + pair + ".  Failure "
                    + bookFailureCount + " of " + retryCounts.get(market) + ".");

            if ((bookFailureCount >= retryCounts.get(market))) {
                log.error(this.getClass().getSimpleName() + ":getBook Unabel to get book for market " + market + " pair " + pair + ". Failure "
                        + bookFailureCount + " of " + retryCounts.get(market) + " . Resetting Data Service Connection.", e);
                XchangeUtil.resetExchange(coinTraderExchange);
                failedTradeCounts.put(market, 0);
                // .// tradeFailureCount = 0;
                failedBookCounts.put(market, 0);
                //   failureCount=0;
                throw e;
                //}
                //  } catch (Throwable e) {
                // TODO Auto-generated catch block
                //     e.printStackTrace();
                //   }

            }
            return null;
        }

    }

    private class FetchTradesRunnable implements Runnable {

        //TODO Need to make this a callabale with a time out, as sometimes it takes longer the 5 secs for get trades and get book to respond

        DateFormat dateFormat = new SimpleDateFormat("ddMMyy");

        //    @Nullable String helperClassName, @Nullable String streamingConfigClassName, int queries, Duration per,
        //  Exchange coinTraderExchange, List listings

        public FetchTradesRunnable(Context context, Exchange coinTraderExchange, Market market, RateLimiter rateLimiter) {

            this.coinTraderExchange = coinTraderExchange;
            this.context = context;
            this.market = market;
            this.rateLimiter = rateLimiter;

        }

        @Override
        public void run() {
            try {
                rateLimiter.execute(this); // run again. requeue in case we die!
                Book book = getBook(market, coinTraderExchange);
                if (book != null && !book.getBids().isEmpty() && !book.getAsks().isEmpty())
                    context.publish(book);
                Collection<org.cryptocoinpartners.schema.Trade> trades = getTrades(market, coinTraderExchange);
                if (trades != null && !trades.isEmpty()) {

                    for (org.cryptocoinpartners.schema.Trade trade : trades)
                        context.publish(trade);
                }

            } catch (Throwable e) {
                log.error(this.getClass().getSimpleName() + ":run. Unable to retrive book or trades for market:" + market, e);
                //Thread.currentThread().
                // throw e;
                //Thread.currentThread().interrupt();
                // throw e;
            }
        }

        // private final Book.Builder bookBuilder = new Book.Builder();

        private final Exchange coinTraderExchange;

        private final RateLimiter rateLimiter;
        private final Context context;
        private final Market market;

    }

    private static final Comparator<LimitOrder> limitPriceComparator = new Comparator<LimitOrder>() {
        @Override
        public int compare(LimitOrder event, LimitOrder event2) {
            return event.getLimitPrice().compareTo(event2.getLimitPrice());
        }
    };

    private static final Comparator<Trade> timeOrderIdComparator = new Comparator<Trade>() {
        @Override
        public int compare(Trade event, Trade event2) {
            int sComp = event.getTimestamp().compareTo(event2.getTimestamp());
            if (sComp != 0) {
                return sComp;
            } else {
                return (event.getId().compareTo(event2.getId()));

            }
        }
    };
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.xchangeData");
    private final BookFactory bookFactory;
    private final boolean orderByTime = true;
    private final TradeFactory tradeFactory;
    //  @Inject
    //protected EntityManager entityManager;
    private final HashMap<Exchange, Helper> exchangeHelpers = new HashMap<Exchange, Helper>();
    private final HashMap<Market, Long> lastTradeIds = new HashMap<Market, Long>();
    private final HashMap<Market, Long> lastTradeTimes = new HashMap<Market, Long>();
    private final HashMap<Market, Integer> failedTradeCounts = new HashMap<Market, Integer>();
    private final HashMap<Market, Integer> failedBookCounts = new HashMap<Market, Integer>();
    private final HashMap<Market, Integer> retryCounts = new HashMap<Market, Integer>();
    private final Context context;
    private static boolean instanceExists = false;
}
