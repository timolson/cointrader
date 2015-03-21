package org.cryptocoinpartners.module.xchange;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.MarketDataError;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.RateLimiter;
import org.cryptocoinpartners.util.XchangeUtil;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.okcoin.FuturesContract;
import com.xeiam.xchange.service.polling.marketdata.PollingMarketDataService;

/**
 * @author Tim Olson
 */
@Singleton
public class XchangeData {

    @Inject
    public XchangeData(Context context, Configuration config) {
        this.context = context;
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
            if (exchange != null) {
                String prefix = configPrefix + "." + tag + '.';
                final String helperClassName = config.getString(prefix + "helper.class", null);
                int queries = config.getInt(prefix + "rate.queries", 1);
                Duration period = Duration.millis((long) (1000 * config.getDouble(prefix + "rate.period", 1))); // rate.period in seconds
                final List listings = config.getList(prefix + "listings");
                initExchange(helperClassName, queries, period, exchange, listings);
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

    private void initExchange(@Nullable String helperClassName, int queries, Duration per, Exchange coinTraderExchange, List listings) {
        com.xeiam.xchange.Exchange xchangeExchange = XchangeUtil.getExchangeForMarket(coinTraderExchange);
        Helper helper = null;
        if (helperClassName != null && !helperClassName.isEmpty()) {
            if (helperClassName.indexOf('.') == -1)
                helperClassName = XchangeData.class.getPackage().getName() + '.' + helperClassName;
            try {
                final Class<?> helperClass = getClass().getClassLoader().loadClass(helperClassName);
                try {
                    helper = (Helper) helperClass.newInstance();
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
        PollingMarketDataService dataService = xchangeExchange.getPollingMarketDataService();
        RateLimiter rateLimiter = new RateLimiter(queries, per);
        Collection<Market> markets = new ArrayList<>(listings.size());
        Market market;

        for (Iterator<List> il = listings.iterator(); il.hasNext(); markets.add(market)) {
            Object listingSymbol = il.next();
            Listing listing = Listing.forSymbol(listingSymbol.toString().toUpperCase());
            market = Market.findOrCreate(coinTraderExchange, listing);
        }

        for (Iterator<Market> im = markets.iterator(); im.hasNext(); rateLimiter.execute(new FetchTradesRunnable(context, market, rateLimiter, dataService,
                helper)))
            market = im.next();

        return;
    }

    private class FetchTradesRunnable implements Runnable {

        private final Helper helper;
        DateFormat dateFormat = new SimpleDateFormat("ddMMyy");

        public FetchTradesRunnable(Context context, Market market, RateLimiter rateLimiter, PollingMarketDataService dataService, @Nullable Helper helper) {
            this.context = context;
            this.market = market;
            this.rateLimiter = rateLimiter;
            this.dataService = dataService;
            this.helper = helper;
            this.prompt = market.getListing().getPrompt();
            pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
            contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());

            lastTradeTime = 0;
            lastTradeId = 0;
            EntityManager entityManager = PersistUtil.createEntityManager();
            try {

                TypedQuery<org.cryptocoinpartners.schema.Trade> query = entityManager.createQuery(
                        "select t from Trade t where market=?1 and time=(select max(time) from Trade where market=?1)",
                        org.cryptocoinpartners.schema.Trade.class);
                query.setParameter(1, market);
                for (org.cryptocoinpartners.schema.Trade trade : query.getResultList()) {
                    // org.cryptocoinpartners.schema.Trade trade = query.getSingleResult();
                    long millis = trade.getTime().getMillis();
                    if (millis > lastTradeTime)
                        lastTradeTime = millis;
                    // todo this is broken and assumes an increasing integer remote key
                    // Long remoteId = Long.valueOf(trade.getRemoteKey().concat(String.valueOf(trade.getTimestamp())));
                    Long remoteId = Long.valueOf(trade.getRemoteKey());
                    if (remoteId > lastTradeId)
                        lastTradeId = remoteId;
                }
            } finally {
                entityManager.close();
            }
        }

        @Override
        public void run() {
            try {
                if (getTradesNext)
                    getTrades();
                else
                    getBook();
            } finally {
                getTradesNext = !getTradesNext;
                rateLimiter.execute(this); // run again. requeue
            }
        }

        protected void getTrades() {
            try {
                Object params[];
                if (helper != null)
                    params = helper.getTradesParameters(pair, lastTradeTime, lastTradeId);
                else {
                    if (contract == null)
                        params = new Object[] {};
                    else
                        params = new Object[] { contract };

                }

                Trades tradeSpec = dataService.getTrades(pair, params);
                if (helper != null)
                    helper.handleTrades(tradeSpec);
                List trades = tradeSpec.getTrades();
                Iterator<Trade> ilt = trades.iterator();
                do {
                    if (!ilt.hasNext())
                        break;
                    com.xeiam.xchange.dto.marketdata.Trade trade = ilt.next();
                    long remoteId = Long.valueOf(String.valueOf(dateFormat.format(trade.getTimestamp()).concat(trade.getId()))).longValue();
                    if (remoteId > lastTradeId) {
                        Instant tradeInstant = new Instant(trade.getTimestamp());
                        org.cryptocoinpartners.schema.Trade ourTrade = new org.cryptocoinpartners.schema.Trade(market, tradeInstant, trade.getId(),
                                trade.getPrice(), trade.getTradableAmount());
                        context.publish(ourTrade);
                        lastTradeTime = tradeInstant.getMillis();
                        lastTradeId = remoteId;
                    }

                } while (true);
            } catch (IOException e) {
                log.warn("Could not get trades for " + market, e);
                context.publish(new MarketDataError(market, e));
            }
            return;
        }

        protected void getBook() {
            try {
                Object params[];
                if (helper != null)
                    params = helper.getOrderBookParameters(pair);
                else {
                    if (contract == null)
                        params = new Object[] {};
                    else
                        params = new Object[] { contract };

                }

                OrderBook orderBook = dataService.getOrderBook(pair, params);
                if (helper != null)
                    helper.handleOrderBook(orderBook);
                bookBuilder.start(new Instant(orderBook.getTimeStamp()), null, market);
                LimitOrder limitOrder;
                for (Iterator<LimitOrder> itb = orderBook.getBids().iterator(); itb.hasNext(); bookBuilder.addBid(limitOrder.getLimitPrice(),
                        limitOrder.getTradableAmount()))
                    limitOrder = itb.next();

                for (Iterator<LimitOrder> ita = orderBook.getAsks().iterator(); ita.hasNext(); bookBuilder.addAsk(limitOrder.getLimitPrice(),
                        limitOrder.getTradableAmount()))
                    limitOrder = ita.next();

                Book book = bookBuilder.build();
                context.publish(book);

            } catch (IOException e) {
                log.warn("Could not get book for " + market, e);
                context.publish(new MarketDataError(market, e));
            }
        }

        private final Book.Builder bookBuilder = new Book.Builder();
        private boolean getTradesNext = true;
        private final PollingMarketDataService dataService;
        private final RateLimiter rateLimiter;
        private final Context context;
        private final Market market;
        private final CurrencyPair pair;
        private final FuturesContract contract;
        private long lastTradeTime;
        private final Prompt prompt;
        private long lastTradeId;
    }

    @Inject
    private Logger log;
    private final Context context;
}
