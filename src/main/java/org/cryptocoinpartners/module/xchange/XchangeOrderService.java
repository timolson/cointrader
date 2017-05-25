package org.cryptocoinpartners.module.xchange;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.exceptions.OrderNotFoundException;
import org.cryptocoinpartners.exceptions.UnknownOrderStateException;
import org.cryptocoinpartners.module.BaseOrderService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.FillFactory;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.util.CompareUtils;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.RateLimiter;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.XchangeUtil;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.okcoin.FuturesContract;
import org.knowm.xchange.okcoin.service.polling.OkCoinFuturesTradeService.OkCoinFuturesTradeHistoryParams;
import org.knowm.xchange.service.polling.trade.PollingTradeService;
import org.knowm.xchange.service.polling.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.streaming.ExchangeEvent;
import org.knowm.xchange.service.streaming.ExchangeEventType;
import org.knowm.xchange.service.streaming.ExchangeStreamingConfiguration;
import org.knowm.xchange.service.streaming.StreamingExchangeService;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * This module routes SpecificOrders through Xchange
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class XchangeOrderService extends BaseOrderService {

    private final FillFactory fillFactory;
    //  @Inject
    //protected EntityManager entityManager;

    private final Context context;
    private final HashMap<Exchange, Helper> exchangeHelpers = new HashMap<Exchange, Helper>();

    @Inject
    public XchangeOrderService(Context context, Configuration config, FillFactory fillFactory) {
        this.context = context;
        this.fillFactory = fillFactory;
        final String configPrefix = "xchange";
        Set<String> exchangeTags = XchangeUtil.getExchangeTags();

        // now we have all the exchange tags.  process each config group
        for (String tag : exchangeTags) {
            // three configs required:
            // .class the full classname of the Xchange implementation
            // .rate.queries rate limit the number of queries to this many (default: 1)
            // .rate.period rate limit the number of queries during this period of time (default: 1 second)
            // .listings identifies which Listings should be fetched from this exchange
            org.cryptocoinpartners.schema.Exchange exchange = XchangeUtil.getExchangeForTag(tag);
            String prefix = configPrefix + "." + tag + '.';
            if (exchange != null && config.getString(prefix + "apikey", null) != null && config.getString(prefix + "apisecret", null) != null) {

                final String helperClassName = config.getString(prefix + "helper.class", null);
                final String streamingConfigClassName = config.getString(prefix + "streaming.config.class", null);
                int queries = config.getInt(prefix + "rate.queries", 1);
                int retryCount = config.getInt(prefix + "retry", 10);

                Duration period = Duration.millis((long) (2000 * config.getDouble(prefix + "rate.period", 1))); // rate.period in seconds
                final List listings = config.getList(prefix + "listings");

                initExchange(helperClassName, streamingConfigClassName, retryCount, queries, period, exchange, listings);
            } else {
                log.warn("Could not find Exchange for property \"xchange." + tag + ".*\"");
            }
        }
    }

    public interface Helper {
        Object[] getTradesParameters(CurrencyPair pair, long lastTradeTime, long lastTradeId);

        Object[] getOrderBookParameters(CurrencyPair pair);

        void handleTrades(Trades tradeSpec);

        void handleOrderBook(OrderBook orderBook);
    }

    private void initExchange(@Nullable String helperClassName, @Nullable String streamingConfigClassName, int retryCount, int queries, Duration per,
            Exchange coinTraderExchange, List listings) {
        org.knowm.xchange.Exchange xchangeExchange = XchangeUtil.getExchangeForMarket(coinTraderExchange);
        StreamingExchangeService streamingDataService;
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

        ExchangeStreamingConfiguration streamingConfiguration = null;
        List<Market> markets = new ArrayList<>(listings.size());
        Market market;
        //  ExchangeStreamingConfiguration streamingConfiguration = new OkCoinExchangeStreamingConfiguration();
        for (Iterator<List> il = listings.iterator(); il.hasNext();) {
            Object listingSymbol = il.next();
            Listing listing = Listing.forSymbol(listingSymbol.toString().toUpperCase());
            market = context.getInjector().getInstance(Market.class).findOrCreate(coinTraderExchange, listing);
            markets.add(market);
        }

        if (streamingConfigClassName != null && !streamingConfigClassName.isEmpty()) {
            RateLimiter rateLimiter = new RateLimiter(queries, per);
            // streamingDataService = xchangeExchange.getStreamingExchangeService(streamingConfiguration);
            for (Iterator<Market> im = markets.iterator(); im.hasNext();) {
                market = im.next();

                ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
                ListenableFuture<StreamOrdersRunnable> streamingTradesFuture = service.submit(new StreamOrdersRunnable(context, xchangeExchange, market,
                        rateLimiter, streamingConfigClassName, helper));

                Futures.addCallback(streamingTradesFuture, new FutureCallback<StreamOrdersRunnable>() {
                    // we want this handler to run immediately after we push the big red button!
                    @Override
                    public void onSuccess(StreamOrdersRunnable streamingTradesFuture) {
                        System.out.println("complete");

                        //walkAwayFrom(explosion);
                    }

                    @Override
                    public void onFailure(Throwable thrown) {
                        System.out.println("failed");
                        //battleArchNemesis(); // escaped the explosion!
                    }
                });

            }
            return;
        } else {
            //    PollingTradeService dataService = xchangeExchange.getPollingTradeService();
            // when 
            RateLimiter rateLimiter = new RateLimiter(queries, per);

            for (Iterator<Market> im = markets.iterator(); im.hasNext(); rateLimiter.execute(new FetchOrdersRunnable(context, market, rateLimiter,
                    coinTraderExchange, retryCount, helper)))
                market = im.next();

            return;
        }
    }

    private class StreamOrdersRunnable implements Callable {

        private final Helper helper;
        DateFormat dateFormat = new SimpleDateFormat("ddMMyy");
        private ExchangeStreamingConfiguration streamingConfiguration;
        private CurrencyPair[] pairs;

        public StreamOrdersRunnable(Context context, org.knowm.xchange.Exchange xchangeExchange, Market market, RateLimiter rateLimiter,
                String streamingConfigClassName, @Nullable Helper helper) {
            this.context = context;
            this.rateLimiter = rateLimiter;
            this.market = market;
            this.helper = helper;
            this.prompt = market.getListing().getPrompt();
            pairs = new CurrencyPair[] { XchangeUtil.getCurrencyPairForListing(market.getListing()) };
            contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());

            //            Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            //                @Override
            //                public void uncaughtException(Thread t, Throwable e) {
            //                    e.printStackTrace();
            //                }
            //            });

            if (streamingConfigClassName.indexOf('.') == -1)
                streamingConfigClassName = XchangeData.class.getPackage().getName() + '.' + streamingConfigClassName;
            try {
                final Class<?> streamingConfigClass = getClass().getClassLoader().loadClass(streamingConfigClassName);
                //  CurrencyPair[] ccy = new CurrencyPair[] { CurrencyPair.BTC_USD };
                try {
                    streamingConfiguration = (ExchangeStreamingConfiguration) CompareUtils.tryToCreateBestMatch(streamingConfigClass, new Object[] { pairs });
                    dataService = xchangeExchange.getStreamingExchangeService(streamingConfiguration);
                    dataService.connect();

                    String str = streamingConfigClass.getCanonicalName();
                } catch (InstantiationException e1) {
                    // TODO Auto-generated catch block
                    log.error("Threw a Execption, full stack trace follows:", e1);

                    e1.printStackTrace();
                } catch (IllegalAccessException e1) {
                    // TODO Auto-generated catch block
                    log.error("Threw a Execption, full stack trace follows:", e1);

                    e1.printStackTrace();
                } catch (InvocationTargetException e1) {
                    // TODO Auto-generated catch block
                    log.error("Threw a Execption, full stack trace follows:", e1);

                    e1.printStackTrace();
                } catch (Exception | Error e2) {
                    // TODO Auto-generated catch block
                    log.error("Threw a Execption, full stack trace follows:", e2);

                    e2.printStackTrace();
                }

                try {
                    streamingConfiguration = (ExchangeStreamingConfiguration) streamingConfigClass.newInstance();

                    //           StreamingExchangeService service = xchangeExchange.getStreamingExchangeService(new (ExchangeStreamingConfiguration) streamingConfigClass(new CurrencyPair[]{ CurrencyPair.BTC_USD }));

                } catch (InstantiationException | IllegalAccessException e) {
                    log.error("Could not initialize XchangeData because stremaing configuration class " + streamingConfigClassName
                            + " could not be instantiated ", e);
                    return;
                } catch (ClassCastException e) {
                    log.error("Could not initialize XchangeData because stremaing configuration class " + streamingConfigClassName + " does not implement "
                            + ExchangeStreamingConfiguration.class);
                    return;
                } catch (Exception | Error e2) {
                    // TODO Auto-generated catch block
                    log.error("Threw a Execption, full stack trace follows:", e2);

                    e2.printStackTrace();
                    return;
                }
            } catch (ClassNotFoundException e) {
                log.error("Could not initialize XchangeData because stremaing configuration class " + streamingConfigClassName + " was not found");
                return;
            } catch (Exception | Error e2) {
                log.error("Threw a Execption, full stack trace follows:", e2);

                // TODO Auto-generated catch block
                e2.printStackTrace();
            }

            Class<? extends ExchangeStreamingConfiguration> myclass = streamingConfiguration.getClass();
            Class<?> streamingConfigClass = null;
            try {
                streamingConfigClass = getClass().getClassLoader().loadClass(streamingConfigClassName);
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                log.error("Threw a Execption, full stack trace follows:", e);

                e.printStackTrace();
            } catch (Exception | Error e2) {
                // TODO Auto-generated catch block
                log.error("Threw a Execption, full stack trace follows:", e2);

                e2.printStackTrace();
            }

            try {
                streamingConfiguration = (ExchangeStreamingConfiguration) streamingConfigClass.newInstance();
            } catch (InstantiationException e) {
                // TODO Auto-generated catch block
                log.error("Threw a Execption, full stack trace follows:", e);

                e.printStackTrace();
            } catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                log.error("Threw a Execption, full stack trace follows:", e);

                e.printStackTrace();
            } catch (Exception | Error e2) {
                log.error("Threw a Execption, full stack trace follows:", e2);

                // TODO Auto-generated catch block
                e2.printStackTrace();
            }
            //   EntityManager entityManager = //.createEntityManager();
            try {

                List<org.cryptocoinpartners.schema.Trade> results = EM.queryList(org.cryptocoinpartners.schema.Trade.class,
                        "select t from Trade t where market=?1 and time=(select max(time) from Trade where market=?1)", market);

                for (org.cryptocoinpartners.schema.Trade trade : results) {
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
                //  EM.em().close();
            }

            //StreamingExchangeService streamingDataService = xchangeExchange.getStreamingExchangeService(streamingConfiguration,new CurrencyPair[]{ CurrencyPair.BTC_USD }));

            lastTradeTime = 0;
            lastTradeId = 0;

        }

        @Override
        public Object call() {
            try {
                while (true) {

                    ExchangeEvent event = dataService.getNextEvent();

                    if (event != null) {
                        //System.out.println("---> " + event.getPayload() + " " + event.getEventType());

                        if (event.getEventType().equals(ExchangeEventType.TRADE)) {
                            org.knowm.xchange.dto.marketdata.Trade trade = (org.knowm.xchange.dto.marketdata.Trade) event.getPayload();
                            long remoteId = Long.valueOf(String.valueOf(dateFormat.format(trade.getTimestamp()).concat(trade.getId()))).longValue();
                            if (remoteId > lastTradeId) {

                                Instant tradeInstant = new Instant(trade.getTimestamp());
                                BigDecimal volume = (trade.getType() == OrderType.ASK) ? trade.getTradableAmount().negate() : trade.getTradableAmount();

                                // Fill fill = fillFactory.create(order, context.getTime(), context.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume,
                                //       Long.toString(bid.getTime().getMillis()));

                                // context.publish(fill);
                                lastTradeTime = tradeInstant.getMillis();
                                lastTradeId = remoteId;
                            }

                        }
                        if (event.getEventType().equals(ExchangeEventType.DISCONNECT)) {
                            log.error(this.getClass().getSimpleName() + " Disconnected");
                            //Thread.currentThread().interrupt();
                            //dataService.
                            // dataService.disconnect();
                            //    dataService.connect();
                            //READYSTATE status = dataService.getWebSocketStatus();
                            //  dataService.connect();

                            // let's resubmit and connect
                        }

                    }
                }
            } catch (InterruptedException e) {
                log.error("Threw a Execption, full stack trace follows disconnecting:", e);

                //  Thread.currentThread().interrupt();
                dataService.disconnect();

                // Thread.currentThread().interrupt();

            } catch (RejectedExecutionException rej) {
                log.error("Threw a Execption, full stack trace follows:", rej);

                rej.printStackTrace();
            }

            catch (Exception | Error e2) {
                // TODO Auto-generated catch block
                log.error("Threw a Execption, full stack trace follows:", e2);

                e2.printStackTrace();

            }

            return Thread.currentThread();
        }

        //   private final Book.Builder bookBuilder = new Book.Builder();
        private final boolean getTradesNext = true;
        private StreamingExchangeService dataService = null;
        private final Context context;
        private final Market market;
        private final RateLimiter rateLimiter;
        private final FuturesContract contract;
        private long lastTradeTime;
        private final Prompt prompt;
        private long lastTradeId;
    }

    @Override
    protected void handleSpecificOrder(SpecificOrder specificOrder) throws Throwable {
        Order.OrderType orderType = null;
        org.knowm.xchange.Exchange exchange = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange());
        PollingTradeService tradeService = exchange.getPollingTradeService();
        if (specificOrder.getLimitPrice() != null && specificOrder.getStopPrice() != null) {
            specificOrder.persit();
            reject(specificOrder, "Stop-limit orders are not supported");
        }
        if (specificOrder.getPositionEffect() == null || specificOrder.getPositionEffect() == PositionEffect.OPEN)
            orderType = specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
        else if (specificOrder.getPositionEffect() == PositionEffect.CLOSE)
            // if order volume is < 0 && it is closing, then I am exiting Bid, else I am exit bid
            //  orderType = specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
            orderType = specificOrder.isAsk() ? Order.OrderType.EXIT_BID : Order.OrderType.EXIT_ASK;
        BigDecimal tradeableVolume = specificOrder.getVolume().abs().asBigDecimal();
        CurrencyPair currencyPair = XchangeUtil.getCurrencyPairForListing(specificOrder.getMarket().getListing());
        String id = specificOrder.getId().toString();
        Date timestamp = specificOrder.getTime().toDate();
        if (specificOrder.getLimitPrice() != null) {
            LimitOrder limitOrder = new LimitOrder(orderType, tradeableVolume, currencyPair, "", null, specificOrder.getLimitPrice().asBigDecimal());
            // todo put on a queue
            try {
                //TODO if this failed like we place a clsoing order it jsut get's rejected but we have not reverted teh stack correctly

                specificOrder.setRemoteKey(tradeService.placeLimitOrder(limitOrder));
                specificOrder.persit();
                updateOrderState(specificOrder, OrderState.PLACED, true);
            } catch (ExchangeException ex) {
                specificOrder.persit();
                reject(specificOrder, "handleSpecificOrder: unable to place order: " + specificOrder.getId() + " error " + ex);

                log.error(this.getClass().getSimpleName() + ":handleSpecificOrder Unable to place order " + specificOrder
                        + ". Threw a Execption, full stack trace follows:", ex);

                throw ex;
                // todo retry until expiration or reject as invalid
            } catch (Exception | Error e) {
                // Someexchange retrun a http execption even though order was placed, so let's raise an execption on this.
                specificOrder.persit();
                log.error(this.getClass().getSimpleName() + ":handleSpecificOrder Unable to place order " + specificOrder
                        + ". Threw a Execption, full stack trace follows:", e);
                if (specificOrder.getId().toString().equals(specificOrder.getRemoteKey()))

                    error(specificOrder, "handleSpecificOrder: unable to place order: " + specificOrder.getId() + " error " + e);

                throw new UnknownOrderStateException("Unknown state of order " + specificOrder.getId(), e);
                //  reject(specificOrder, "handleSpecificOrder: unable to place order: " + specificOrder.getId() + " error " + e);

            }
        } else {
            MarketOrder marketOrder = new MarketOrder(orderType, tradeableVolume, currencyPair, id, timestamp);
            // todo put on a queue
            try {
                specificOrder.setRemoteKey(tradeService.placeMarketOrder(marketOrder));
                specificOrder.persit();
                updateOrderState(specificOrder, OrderState.PLACED, true);
            } catch (NotYetImplementedForExchangeException e) {
                specificOrder.persit();
                log.warn("XChange adapter " + exchange + " does not support this order: " + specificOrder, e);
                reject(specificOrder, "XChange adapter " + exchange + " does not support this order");
                throw e;
            } catch (ExchangeException ex) {

                specificOrder.persit();
                log.error(this.getClass().getSimpleName() + ":handleSpecificOrder Unable to place order " + specificOrder
                        + ". Threw a Execption, full stack trace follows:", ex);
                reject(specificOrder, "handleSpecificOrder: unable to place order: " + specificOrder.getId() + " error " + ex);
                throw ex;
            } catch (Exception | Error e) {
                specificOrder.persit();
                log.error(this.getClass().getSimpleName() + ":handleSpecificOrder Unable to place order " + specificOrder
                        + ". Threw a Execption, full stack trace follows:", e);
                if (specificOrder.getId().toString().equals(specificOrder.getRemoteKey()))
                    error(specificOrder, "handleSpecificOrder: unable to place order: " + specificOrder.getId() + " error " + e);

                throw new UnknownOrderStateException("Unknown state of order " + specificOrder.getId(), e);
                // throw e;
                // todo retry until expiration or reject as invalid
            }
        }

    }

    @Transient
    protected Collection<SpecificOrder> getPendingXchangeOrders(Market market, Portfolio portfolio) {
        Collection<SpecificOrder> pendingOrders = new ArrayList<SpecificOrder>();
        org.knowm.xchange.Exchange exchange;
        try {

            exchange = XchangeUtil.getExchangeForMarket(market.getExchange());
        } catch (Error err) {
            log.info("market:" + market + " not found");
            return pendingOrders;
        }
        PollingTradeService tradeService = exchange.getPollingTradeService();
        SpecificOrder specificOrder;
        boolean exists = false;
        //TODO: need to check prompts to ensure they have the full OKCOIN_THISWEEK:BTC.USD.THISWEEK not just OKCOIN_THISWEEK:BTC.USD
        try {
            OpenOrders openOrders = tradeService.getOpenOrders();
            for (LimitOrder xchangeOrder : openOrders.getOpenOrders()) {
                for (org.cryptocoinpartners.schema.Order cointraderOrder : orderStateMap.keySet()) {
                    if (cointraderOrder instanceof SpecificOrder) {
                        specificOrder = (SpecificOrder) cointraderOrder;
                        if (xchangeOrder.getId().equals(specificOrder.getRemoteKey()) && specificOrder.getMarket().equals(market)) {
                            Fill fill = createFill(xchangeOrder, specificOrder);
                            if (fill != null)
                                handleFillProcessing(fill);

                            if (!adaptOrderState(xchangeOrder.getStatus()).equals(orderStateMap.get(specificOrder)))
                                if (adaptOrderState(xchangeOrder.getStatus()) != OrderState.FILLED
                                        || adaptOrderState(xchangeOrder.getStatus()) != OrderState.PARTFILLED)
                                    updateOrderState(specificOrder, adaptOrderState(xchangeOrder.getStatus()), true);

                            pendingOrders.add(specificOrder);
                            exists = true;
                            break;
                        }
                    }
                }

                /*                if (!exists) {
                                    Date time = (xchangeOrder.getTimestamp() != null) ? xchangeOrder.getTimestamp() : new Date();
                                    specificOrder = new SpecificOrder(xchangeOrder, exchange, portfolio, time);
                                    specificOrder.persit();
                                    updateOrderState(specificOrder, adaptOrderState(xchangeOrder.getStatus()), false);
                                    Fill fill = createFill(xchangeOrder, specificOrder);
                                    if (fill != null)
                                        handleFillProcessing(fill);
                                    // need to create fills if these are not the same
                                    pendingOrders.add(specificOrder);
                                }*/
            }

        } catch (IOException e) {
            log.error("Threw a Execption, full stack trace follows:", e);

            e.printStackTrace();

        }
        return pendingOrders;

    }

    @Transient
    private Collection<Trade> getFills(Portfolio portfolio) {

        Collection<Trade> fills = new ArrayList<Trade>();

        for (Tradeable market : portfolio.getContext().getInjector().getInstance(Market.class).findAll())
            if (market instanceof Market)
                fills.addAll(getXchangeFills((Market) market, portfolio));

        return fills;

    }

    protected synchronized void getOrders(@Nullable Helper helper, Market market, CurrencyPair pair, long lastTradeTime, long lastTradeId,
            FuturesContract contract, boolean firstRun, Exchange coinTraderExchange, int restartCount) throws Throwable {
        int tradeFailureCount = 0;
        Set<org.cryptocoinpartners.schema.Order> cointraderOpenOrders = new HashSet<org.cryptocoinpartners.schema.Order>();
        Set<org.knowm.xchange.dto.Order> xchangeOpenOrders = new HashSet<org.knowm.xchange.dto.Order>();
        Set<org.knowm.xchange.dto.Order> cointraderXchangeOrders = new HashSet<org.knowm.xchange.dto.Order>();
        PollingTradeService tradePollingService = XchangeUtil.getExchangeForMarket(coinTraderExchange).getPollingTradeService();
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
            log.trace("Attempting to get trades from data service");
            List<String> openOrdersXchangeIds = new ArrayList<String>();

            //    if (stateOrderMap.get(OrderState.NEW) != null)
            //      cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.NEW));
            if (stateOrderMap.get(OrderState.PLACED) != null)
                cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PLACED));
            if (stateOrderMap.get(OrderState.PARTFILLED) != null)
                cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.PARTFILLED));
            if (stateOrderMap.get(OrderState.ROUTED) != null)
                cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.ROUTED));
            if (stateOrderMap.get(OrderState.CANCELLING) != null)
                cointraderOpenOrders.addAll(stateOrderMap.get(OrderState.CANCELLING));
            for (org.cryptocoinpartners.schema.Order openOrder : cointraderOpenOrders) {
                SpecificOrder openSpecificOrder;
                if (openOrder instanceof SpecificOrder) {
                    openSpecificOrder = (SpecificOrder) openOrder;
                    openOrdersXchangeIds.add(openSpecificOrder.getRemoteKey());
                    //     Set<String> openMarketOrders;
                    //   if(cointraderOrders.get(openSpecificOrder.getMarket())==null){
                    //     openMarketOrders=new HashSet<String>();
                    //   openMarketOrders.add(openSpecificOrder.getRemoteKey());
                    // cointraderOrders.put(openSpecificOrder.getMarket(), openMarketOrders);

                    // } else
                    // {
                    //    cointraderOrders.get(openSpecificOrder.getMarket()).add(openSpecificOrder.getRemoteKey());
                    // }
                    //openOrders.add(openSpecificOrder.getRemoteKey());
                }
            }
            //TODO how to get the deliveries from the exchange with hte corresponding price
            if (firstRun) {
                // let's check all fills from exchange are in portfolio

            }
            // getPendingOrders();
            Collection<Order> exchangeOrders = null;

            //  TradeHistoryParams historyParams = new OkCoinFuturesTradeHistoryParams(50, 0, CurrencyPair.BTC_USD, FuturesContract.ThisWeek, "86751191");

            //tradeService.createTradeHistoryParams();
            //new TradeHistoryParamFuturesContract();
            //params.
            //params.setCurrencyPair(CurrencyPair.BTC_MXN);
            //params.setStartId("86751191");
            //   params.

            // Trades trades = XchangeUtil.getExchangeForMarket(coinTraderExchange).getPollingTradeService().getTradeHistory(historyParams);

            // exchangeOrders = XchangeUtil.getExchangeForMarket(coinTraderExchange).getPollingTradeService()
            //       .getOrder(openOrders.toArray(new String[openOrders.size()]));

            if (!openOrdersXchangeIds.isEmpty()) {

                // Trades tradeSpec = XchangeUtil.getExchangeForMarket(coinTraderExchange).

                exchangeOrders = tradePollingService.getOrder(openOrdersXchangeIds.toArray(new String[openOrdersXchangeIds.size()]));

                // OpenOrders tradeSpec = tradeService.getOpenOrders();
                // List<LimitOrder> openExchangeOrders = tradeSpec.getOpenOrders();
                Boolean orderFound = false;
                for (Order exchangeOrder : exchangeOrders) {
                    cointraderXchangeOrders.add(exchangeOrder);
                    // so let's check these are in the ordermap
                    for (org.cryptocoinpartners.schema.Order openOrder : cointraderOpenOrders) {

                        SpecificOrder specificOpenOrder = null;
                        if (openOrder instanceof SpecificOrder) {
                            specificOpenOrder = (SpecificOrder) openOrder;
                            if (specificOpenOrder.getRemoteKey().equals(exchangeOrder.getId())) {
                                orderFound = true;
                                Fill fill = createFill(exchangeOrder, specificOpenOrder);
                                if (fill != null)
                                    handleFillProcessing(fill);

                                if (!adaptOrderState(exchangeOrder.getStatus()).equals(orderStateMap.get(openOrder)))
                                    if (adaptOrderState(exchangeOrder.getStatus()) != OrderState.FILLED
                                            || adaptOrderState(exchangeOrder.getStatus()) != OrderState.PARTFILLED)
                                        updateOrderState(openOrder, adaptOrderState(exchangeOrder.getStatus()), true);

                                //lets see if we need to create some fills

                                // let's create any fills 

                            }
                        }
                    }
                    /*                    if (!orderFound) {
                                            String comment;
                                            if (exchangeOrder.getTradableAmount().compareTo(BigDecimal.ZERO) > 0)
                                                comment = "Long Order Entry";
                                            else
                                                comment = "Long Order Entry";
                                            DiscreteAmount volume = DecimalAmount.of(exchangeOrder.getTradableAmount()).toBasis(market.getVolumeBasis(), Remainder.DISCARD);

                                            SpecificOrder specificOrder = specificOrderFactory.create(context.getTime(), context.getInjector().getInstance(Portfolio.class),
                                                    market, volume, comment);
                                            Fill fill = createFill(exchangeOrder, specificOrder);
                                            if (fill != null)
                                                handleFillProcessing(fill);

                                            // Market markettest = specificOrder.getMarket();
                                            // specificOrder.withParentFill(generalOrder.getParentFill());
                                            specificOrder.withPositionEffect(PositionEffect.OPEN);
                                            specificOrder.withExecutionInstruction(ExecutionInstruction.TAKER);
                                            orderStateMap.put(specificOrder, adaptOrderState(exchangeOrder.getStatus()));
                                            // work out if we need any fills

                                        }*/
                }
            }

            OpenOrders openOrders = tradePollingService.getOpenOrders();
            for (Order xchangeOrder : openOrders.getOpenOrders()) {
                xchangeOpenOrders.add(xchangeOrder);
            }
            // if we have any order that are open at exchange but not known to cointrader, raise unknown order execption i.e orders in xchangeOpenOrders but not in cointraderXchangeOrders
            SetView<Order> unknownOrders = Sets.difference(xchangeOpenOrders, cointraderXchangeOrders);
            if (!ignoreUnknownOrders) {
                for (Order unknowOrder : unknownOrders) {
                    // we are going to log it and cancel them, but they might have been filled!
                    //let's create a specifc order for them
                    Collection<Portfolio> portfolios = context.getInjector().getInstance(PortfolioService.class).getPortfolios();
                    Portfolio lastPortfolio = null;
                    for (Portfolio porfolio : portfolios)
                        for (Tradeable tradeable : porfolio.getMarkets())
                            if (!tradeable.isSynthetic()) {
                                Market portfolioMarket = (Market) tradeable;
                                if (portfolioMarket.equals(market))
                                    lastPortfolio = porfolio;
                            }
                    if (lastPortfolio == null)
                        continue;
                    SpecificOrder specificOrder = specificOrderFactory.create(unknowOrder, market, lastPortfolio);

                    // add the order to the state cache as placed
                    specificOrder.persit();
                    updateOrderState(specificOrder, OrderState.PLACED, true);
                    // then cancel the order to check if it is filled.
                    try {
                        if (cancelUnknownOrders && handleCancelSpecificOrder(specificOrder))

                            log.warn(this.getClass().getSimpleName() + ":getOrders - Cancelled Unkown order" + specificOrder);
                    } catch (Throwable e) {
                        // TODO Auto-generated catch block
                        log.error("getOrders: called from class " + Thread.currentThread().getStackTrace()[2] + " unable to cancel order:" + specificOrder
                                + " " + e);
                        throw new OrderNotFoundException("Unknown exchnage order " + unknowOrder);
                    }

                }
            }
            tradeFailureCount = 0;
            return;
        } catch (Exception | Error e) {

            tradeFailureCount++;
            log.error(this.getClass().getSimpleName() + ":getOrders unable to get orders for market  " + market + " pair " + pair + ".  Failure "
                    + tradeFailureCount + " of " + restartCount + ". Full Stack Trace: " + e);
            if (tradeFailureCount >= restartCount) {
                //try {
                //  if (rateLimiter.getRunnables() == null || rateLimiter.getRunnables().isEmpty() || rateLimiter.remove(this)) {

                log.error(this.getClass().getSimpleName() + ":getOrders unable to get orders for " + market + " pair " + pair + " for " + tradeFailureCount
                        + " of " + restartCount + " time. Resetting Trade Service Connection.");
                org.knowm.xchange.Exchange xchangeExchange = XchangeUtil.resetExchange(coinTraderExchange);
                // dataService = xchangeExchange.getPollingMarketDataService();
                tradeFailureCount = 0;
                throw e;
                //}

            }
            return;
        }

    }

    //  FetchOrdersRunnable.

    private class FetchOrdersRunnable implements Runnable {

        private final Helper helper;
        DateFormat dateFormat = new SimpleDateFormat("ddMMyy");
        private boolean firstRun;

        public FetchOrdersRunnable(Context context, Market market, RateLimiter rateLimiter, Exchange coinTraderExchange, int restartCount,
                @Nullable Helper helper) {
            this.context = context;
            this.market = market;
            this.rateLimiter = rateLimiter;
            this.coinTraderExchange = coinTraderExchange;
            //   this.tradeService = tradeService;
            this.helper = helper;
            this.prompt = market.getListing().getPrompt();
            pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
            contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());
            this.restartCount = restartCount;
            lastTradeTime = 0;
            lastTradeId = 0;
            this.firstRun = true;
            ;
            // EntityManager entityManager = PersistUtil.createEntityManager();

        }

        public FetchOrdersRunnable(Context context, Market market, Exchange coinTraderExchange) {
            this.context = context;
            this.market = market;
            this.rateLimiter = null;
            this.coinTraderExchange = coinTraderExchange;
            //   this.tradeService = tradeService;
            this.helper = exchangeHelpers.get(coinTraderExchange);
            this.prompt = market.getListing().getPrompt();
            pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
            contract = prompt == null ? null : XchangeUtil.getContractForListing(market.getListing());
            this.restartCount = 0;
            lastTradeTime = 0;
            lastTradeId = 0;
            resubmitable = false;
            this.firstRun = false;
            ;
            // EntityManager entityManager = PersistUtil.createEntityManager();

        }

        @Override
        public void run() {
            try {
                if (resubmitable)
                    rateLimiter.execute(this); // requeue in case we die!

                getOrders(helper, market, pair, lastTradeTime, lastTradeId, contract, firstRun, coinTraderExchange, restartCount);
            } catch (Throwable e) {
                log.error(this.getClass().getSimpleName() + ":run. Unable to retrive order statuses for market:" + market);
                //Thread.currentThread().
                // throw e;
                //Thread.currentThread().interrupt();
                //throw e;

            } finally {
                // getTradesNext = !getTradesNext;
                firstRun = false;
                // run again. requeue

            }
        }

        /* protected Fill createFill(org.knowm.xchange.dto.Order exchangeOrder, SpecificOrder order) {
             Fill fill = null;
             // new DiscreteAmount(DiscreteAmount.roundedCountForBasis(stopPrice.asBigDecimal(), fill.getMarket().getPriceBasis()), fill
             //      .getMarket().getPriceBasis());
             DiscreteAmount exchangeVolume = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getTradableAmount(), order.getMarket()
                     .getVolumeBasis()), order.getMarket().getVolumeBasis());
             // DiscreteAmount exchnageVolume = DecimalAmount.of(exchangeOrder.getTradableAmount()).toBasis(order.getMarket().getVolumeBasis(), Remainder.DISCARD);
             DiscreteAmount exchangeFilledVolume = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getCumulativeAmount(), order.getMarket()
                     .getVolumeBasis()), order.getMarket().getVolumeBasis());
             DiscreteAmount exchangeUnfilledVolume = (DiscreteAmount) exchangeVolume.minus(exchangeFilledVolume);
             DecimalAmount averagePrice = new DecimalAmount(exchangeOrder.getAveragePrice());

             Amount fillVolume = (order.getUnfilledVolume().minus(exchangeUnfilledVolume));

             //  new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getAveragePrice(), order.getMarket().getPriceBasis());

             DecimalAmount fillPrice = DecimalAmount.ZERO;
             //exchangeOrder.getAveragePrice();
             //order.getAverageFillPrice()
             if (!fillVolume.isZero()) {
                 fillPrice = ((exchangeFilledVolume.times(averagePrice, Remainder.ROUND_EVEN)).minus((order.getVolume().minus(order.getUnfilledVolume()).times(
                         order.getAverageFillPrice(), Remainder.ROUND_EVEN)))).divide(fillVolume.asBigDecimal(), Remainder.ROUND_EVEN);
                 fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(),
                         fillPrice.toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount(),
                         fillVolume.toBasis(order.getMarket().getVolumeBasis(), Remainder.ROUND_EVEN).getCount(),
                         Long.toString(exchangeOrder.getTimestamp().getTime()));

                 // this.priceCount = DiscreteAmount.roundedCountForBasis(fillPrice, market.getPriceBasis());

             }

             return fill;
             // -(order.getAverageFillPrice().times(order.getVolume().minus(order.getUnfilledVolume()), Remainder.ROUND_EVEN));
             // DiscreteAmount volume;
             // If the unfilled volume of specifcOrder > tradedAmount-Cumalitve quanity, create fill
             // fill volume= (specficOrder.unfiledVolume) - (xchangeOrder.tradableAmount - xchangeOrder.cumlativeQuanity)

             //  price = (exchangeFilledVolumeCount*)

             //Case 2
             //Specfic Order is created after the xchange order is filled or part filled

             //     // specificOpenOrder.getVolume().compareTo(o)(exchnageVolume)!=0)
             //   if (order.getUnfilledVolume().compareTo(exchnageUnfilledVolume) == 0 || order.getVolume().compareTo(exchnageVolume) != 0) {
             //     Amount fillVolume = exchnageUnfilledVolume.minus(order.getUnfilledVolume());

             // Fill fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(), order.getPriceCount(), fillVolume,
             //       exchangeOrder.getTimestamp().getTime());

             //}
             // volume = exchnageUnfilledVolume;
             // we need to create a fill 
             //return null;

         }*/

        // private final Book.Builder bookBuilder = new Book.Builder();
        private final boolean getTradesNext = true;
        private final RateLimiter rateLimiter;
        private final Exchange coinTraderExchange;
        private final int restartCount;
        private final int tradeFailureCount = 0;
        boolean resubmitable = true;
        private final Context context;
        private final Market market;
        private final CurrencyPair pair;
        private final FuturesContract contract;
        private final long lastTradeTime;
        private final Prompt prompt;
        private final long lastTradeId;
    }

    protected OrderState adaptOrderState(OrderStatus state) {
        switch (state) {

            case PENDING_NEW:
                return OrderState.NEW;
            case NEW:
                return OrderState.PLACED;
            case PARTIALLY_FILLED:
                return OrderState.PARTFILLED;
            case FILLED:
                return OrderState.FILLED;

            case CANCELED:
                return OrderState.CANCELLED;

            case PENDING_REPLACE:
                return OrderState.NEW;
            case REPLACED:
                return OrderState.PLACED;
            case STOPPED:
                return OrderState.TRIGGER;
            case REJECTED:
                return OrderState.REJECTED;
            case EXPIRED:

                return OrderState.EXPIRED;

            default:
                return OrderState.PLACED;
        }
    }

    protected Fill createFill(org.knowm.xchange.dto.Order exchangeOrder, SpecificOrder order) {
        Fill fill = null;
        // new DiscreteAmount(DiscreteAmount.roundedCountForBasis(stopPrice.asBigDecimal(), fill.getMarket().getPriceBasis()), fill
        //      .getMarket().getPriceBasis());
        DiscreteAmount exchangeVolume = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getTradableAmount(), order.getMarket()
                .getVolumeBasis()), order.getMarket().getVolumeBasis());
        // DiscreteAmount exchnageVolume = DecimalAmount.of(exchangeOrder.getTradableAmount()).toBasis(order.getMarket().getVolumeBasis(), Remainder.DISCARD);
        DiscreteAmount exchangeFilledVolume = new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getCumulativeAmount(), order.getMarket()
                .getVolumeBasis()), order.getMarket().getVolumeBasis());
        DiscreteAmount exchangeUnfilledVolume = (DiscreteAmount) exchangeVolume.minus(exchangeFilledVolume);
        DecimalAmount averagePrice = new DecimalAmount(exchangeOrder.getAveragePrice());

        Amount fillVolume = (order.isAsk()) ? (order.getUnfilledVolume().abs().minus(exchangeUnfilledVolume)).negate() : (order.getUnfilledVolume().abs()
                .minus(exchangeUnfilledVolume));
        //  if (order.isAsk())

        //  new DiscreteAmount(DiscreteAmount.roundedCountForBasis(exchangeOrder.getAveragePrice(), order.getMarket().getPriceBasis());

        DecimalAmount fillPrice = DecimalAmount.ZERO;
        //exchangeOrder.getAveragePrice();
        //order.getAverageFillPrice()
        if (!fillVolume.isZero()) {
            fillPrice = ((exchangeFilledVolume.times(averagePrice, Remainder.ROUND_EVEN)).minus((order.getVolume().abs().minus(order.getUnfilledVolume().abs())
                    .times(order.getAverageFillPrice(), Remainder.ROUND_EVEN)))).divide(fillVolume.abs().asBigDecimal(), Remainder.ROUND_EVEN);
            fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(),
                    fillPrice.toBasis(order.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount(),
                    fillVolume.toBasis(order.getMarket().getVolumeBasis(), Remainder.ROUND_EVEN).getCount(),
                    Long.toString(exchangeOrder.getTimestamp().getTime()));

            // this.priceCount = DiscreteAmount.roundedCountForBasis(fillPrice, market.getPriceBasis());

        }

        return fill;
        // -(order.getAverageFillPrice().times(order.getVolume().minus(order.getUnfilledVolume()), Remainder.ROUND_EVEN));
        // DiscreteAmount volume;
        // If the unfilled volume of specifcOrder > tradedAmount-Cumalitve quanity, create fill
        // fill volume= (specficOrder.unfiledVolume) - (xchangeOrder.tradableAmount - xchangeOrder.cumlativeQuanity)

        //  price = (exchangeFilledVolumeCount*)

        //Case 2
        //Specfic Order is created after the xchange order is filled or part filled

        //     // specificOpenOrder.getVolume().compareTo(o)(exchnageVolume)!=0)
        //   if (order.getUnfilledVolume().compareTo(exchnageUnfilledVolume) == 0 || order.getVolume().compareTo(exchnageVolume) != 0) {
        //     Amount fillVolume = exchnageUnfilledVolume.minus(order.getUnfilledVolume());

        // Fill fill = fillFactory.create(order, context.getTime(), context.getTime(), order.getMarket(), order.getPriceCount(), fillVolume,
        //       exchangeOrder.getTimestamp().getTime());

        //}
        // volume = exchnageUnfilledVolume;
        // we need to create a fill 
        //return null;

    }

    protected OrderStatus adaptOrderState(OrderState state) {

        switch (state) {
            case NEW:
                return OrderStatus.PENDING_NEW;
            case PLACED:
                return OrderStatus.NEW;
            case FILLED:
                return OrderStatus.FILLED;
            case CANCELLING:
                return OrderStatus.PENDING_CANCEL;
            case CANCELLED:
                return OrderStatus.CANCELED;
            case REJECTED:
                return OrderStatus.REJECTED;
            case EXPIRED:
                return OrderStatus.EXPIRED;

            default:
                return OrderStatus.NEW;
        }

    }

    @Transient
    protected Collection<Trade> getXchangeFills(Market market, Portfolio portfolio) {
        org.knowm.xchange.Exchange exchange;
        Collection<Trade> fills = Collections.synchronizedList(new ArrayList<Trade>());

        try {
            exchange = XchangeUtil.getExchangeForMarket(market.getExchange());
        } catch (Error err) {
            log.info("market:" + market + " not found");
            return fills;
        }
        PollingTradeService tradeService = exchange.getPollingTradeService();

        try {
            //   TradeHistoryParamsAll params = new TradeHistoryParamsAll();
            // for (order workingOrder : orderStateMap.
            TradeHistoryParams params = new OkCoinFuturesTradeHistoryParams(50, 0, CurrencyPair.BTC_USD, FuturesContract.ThisWeek, "86751191");

            //tradeService.createTradeHistoryParams();
            //new TradeHistoryParamFuturesContract();
            //params.
            //params.setCurrencyPair(CurrencyPair.BTC_MXN);
            //params.setStartId("86751191");
            //   params.

            Trades trades = tradeService.getTradeHistory(params);
            //for (Trade trade : trades.getTrades())
            fills.addAll(trades.getTrades());
            return fills;
        } catch (ExchangeException e) {
            log.error("Unable to find orders of portfolio :" + portfolio.getId() + ". Full stack trace: ", e);
        } catch (NotAvailableFromExchangeException e) {
            log.error("Unable to cancel order :" + portfolio.getId() + ". Full stack trace: ", e);
        } catch (NotYetImplementedForExchangeException e) {
            log.error("Unable to cancel order :" + portfolio.getId() + ". Full stack trace: ", e);
        } catch (IOException e) {
            log.error("failed to cancel order " + portfolio.getId() + ". Full stack trace: ", e);
            e.printStackTrace();
        }
        return fills;

    }

    protected static final Collection<SpecificOrder> pendingOrders = new ArrayList<SpecificOrder>();
    protected static final HashBiMap<org.knowm.xchange.dto.Order, org.knowm.xchange.dto.Order> externalOrderMap = HashBiMap.create();

    @Override
    public void init() {
        super.init();
        // do we need to get teh status of orders from the xchange?
        // TODO Auto-generated method stub

    }

    private static final Comparator<LimitOrder> timeOrderIdComparator = new Comparator<LimitOrder>() {
        @Override
        public int compare(LimitOrder event, LimitOrder event2) {
            int sComp = event.getTimestamp().compareTo(event2.getTimestamp());
            if (sComp != 0) {
                return sComp;
            } else {
                return (event.getId().compareTo(event2.getId()));

            }
        }
    };

    @SuppressWarnings("finally")
    @Override
    protected synchronized boolean cancelSpecificOrder(SpecificOrder order) throws Throwable {
        org.knowm.xchange.Exchange exchange;
        boolean deleted = false;
        if (!orderStateMap.get(order).isOpen()) {
            log.error("Unable to cancel order as is " + orderStateMap.get(order) + " :" + order);
            deleted = true;
            return deleted;

        }

        try {
            exchange = XchangeUtil.getExchangeForMarket(order.getMarket().getExchange());
            PollingTradeService tradeService = exchange.getPollingTradeService();
            if (tradeService.cancelOrder(order.getRemoteKey())) {

                /*    this.context = context;
                    this.market = market;
                    this.rateLimiter = null;
                    this.coinTraderExchange = coinTraderExchange;
                    //   this.tradeService = tradeService;
                    this.helper = exchangeHelpers.get(coinTraderExchange);
                    this.prompt = market.getListing().getPrompt();
                    pair = XchangeUtil.getCurrencyPairForListing(market.getListing());
                    FuturesContract contract = order.getMarket().getListing().getPrompt() == null ? null : XchangeUtil.getContractForListing(order.getMarket().getListing());
                    this.restartCount = 0;
                    lastTradeTime = 0;
                    lastTradeId = 0;
                    resubmitable = false;
                    this.firstRun = false;    
                */
                CurrencyPair pair = XchangeUtil.getCurrencyPairForListing(order.getMarket().getListing());
                FuturesContract contract = order.getMarket().getListing().getPrompt() == null ? null : XchangeUtil.getContractForListing(order.getMarket()
                        .getListing());

                // lets kick of a new thread to check that it is canclled we need to wait for this to complete before returning
                getOrders(exchangeHelpers.get(order.getMarket().getExchange()), order.getMarket(), pair, 0, 0, contract, false,
                        order.getMarket().getExchange(), 0);
                if (orderStateMap.get(order) != null && orderStateMap.get(order).equals(OrderState.CANCELLED))
                    deleted = true;
            } else {
                /// if (!pendingOrders.contains(order)) {
                //TODO check if order is on exchange
                log.error("Unable to cancel order as not present in exchange order book. Order:" + order);
                // deleted = false;
                // }

            }
            return deleted;
        } catch (Error | Exception e) {
            //TODO we need to handel this better, do we add them to a qeueue and then keep retrying in the event of an execption and reset the exchange after x attempts
            log.error("Unable to cancel order :" + order + " due to " + e);
            throw e;

        }

    }

    @Override
    protected OrderState getOrderStateFromOrderService(org.cryptocoinpartners.schema.Order order) throws Throwable {
        OrderUpdate orderUpdate = null;
        // so let's get order from exchange    protected Collection<SpecificOrder> getPendingXchangeOrders(Market market, Portfolio portfolio) {
        List<String> openOrders = new ArrayList<String>();
        Boolean exchangeOrderFound = false;

        if (order instanceof SpecificOrder) {
            SpecificOrder specificOrder = (SpecificOrder) order;

            openOrders.add(specificOrder.getRemoteKey());
            Collection<Order> exchangeOrders = null;
            if (!openOrders.isEmpty()) {

                //TODO: need to check prompts to ensure they have the full OKCOIN_THISWEEK:BTC.USD.THISWEEK not just OKCOIN_THISWEEK:BTC.USD
                try {
                    exchangeOrders = XchangeUtil.getExchangeForMarket(specificOrder.getMarket().getExchange()).getPollingTradeService()
                            .getOrder(openOrders.toArray(new String[openOrders.size()]));
                    for (Order exchangeOrder : exchangeOrders) {
                        if (specificOrder.getRemoteKey().equals(exchangeOrder.getId())) {
                            exchangeOrderFound = true;
                            return adaptOrderState(exchangeOrder.getStatus());
                        }
                    }

                } catch (IllegalArgumentException iae) {
                    return OrderState.REJECTED;
                }

                catch (Exception | Error e) {
                    log.error(this.getClass().getSimpleName() + ":getOrderStateFromOrderService unable to get order: " + specificOrder + " from market: "
                            + specificOrder.getMarket() + ".  Failure. Full Stack Trace: ", e);
                    throw e;
                }
            }
        }
        if (!exchangeOrderFound) {
            // if this fails let's get it from DB
            orderUpdate = EM.namedQueryOne(OrderUpdate.class, "orderUpdate.findStateByOrder", order);
            if (orderUpdate != null)
                return orderUpdate.getState();

        }
        return null;

    }

    @Override
    public void updateWorkingOrderQuantity(org.cryptocoinpartners.schema.Order order, Amount quantity) {
        // TODO Auto-generated method stub

    }
}
