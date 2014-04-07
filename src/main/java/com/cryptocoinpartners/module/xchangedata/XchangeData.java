package com.cryptocoinpartners.module.xchangedata;

import com.cryptocoinpartners.module.Esper;
import com.cryptocoinpartners.module.ModuleListenerBase;
import com.cryptocoinpartners.schema.*;
import com.cryptocoinpartners.util.PersistUtil;
import com.cryptocoinpartners.util.RateLimiter;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.bitfinex.v1.BitfinexExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.apache.commons.configuration.Configuration;
import org.joda.time.Duration;
import org.joda.time.Instant;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.util.Collection;
import java.util.List;


/**
 * @author Tim Olson
 */
public class XchangeData extends ModuleListenerBase {

    // rate limit
    // one query per second
    public int queries = 1;
    public Duration per = Duration.standardSeconds(1);


    public void initModule(final Esper esper, Configuration config) {
        super.initModule(esper, config);
        Exchange bitfinex = ExchangeFactory.INSTANCE.createExchange(BitfinexExchange.class.getName());
        dataService = bitfinex.getPollingMarketDataService();
        rateLimiter = new RateLimiter(queries, per);

        Collection<Listing> listings = Listing.forMarket(Market.BITFINEX);
        for( final Listing listing : listings ) {
            rateLimiter.execute(new FetchTradesRunnable(esper, listing));
        }
    }

    /**
     * @param esper
     * @param config
     * @param exchangeId  the exchagne ID from ExchangeNames
     */
    public void initModule(final Esper esper, Configuration config, int exchangeId) {
        super.initModule(esper, config);
 
        Exchange currentExchange = ExchangeFactory.INSTANCE.createExchange(ExchangeNames.findExchangeName(exchangeId));
        dataService = currentExchange.getPollingMarketDataService();       
        rateLimiter = new RateLimiter(queries, per);
        
        Market curMarket = ExchangeMarketMapping.getMarketByExchangeId(exchangeId);
        Collection<Listing> listings = Listing.forMarket( curMarket);
        for( final Listing listing : listings ) {
            rateLimiter.execute(new FetchTradesRunnable(esper, listing));
        }
    }

    public void destroyModule() {
        super.destroyModule();
    }


    private class FetchTradesRunnable implements Runnable {

        public FetchTradesRunnable(Esper esper, Listing listing) {
            this.esper = esper;
            this.listing = listing;
            pair = new CurrencyPair(listing.getBase().getSymbol(), listing.getQuote().getSymbol());
            lastTradeTime = 0;
            lastTradeId = 0;
            EntityManager entityManager = PersistUtil.createEntityManager();
            try {
                TypedQuery<Trade> query = entityManager.createQuery("select t from Trade t where listing=?1 and time=(select max(time) from Trade where listing=?1)",
                                                                    Trade.class);
                query.setParameter(1, listing);
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
                Trades tradeSpec = dataService.getTrades(pair, lastTradeTime);
                List<com.xeiam.xchange.dto.marketdata.Trade> trades = tradeSpec.getTrades();
                for( com.xeiam.xchange.dto.marketdata.Trade trade : trades ) {
                    long remoteId = Long.valueOf(trade.getId());
                    if( remoteId > lastTradeId ) {
                        Instant tradeInstant = new Instant(trade.getTimestamp());
                        Trade ourTrade =
                                new Trade(listing, tradeInstant, trade.getId(),
                                          trade.getPrice(), trade.getTradableAmount());
                        esper.publish(ourTrade);
                        lastTradeTime = tradeInstant.getMillis();
                        lastTradeId = remoteId;
                    }
                }
            }
            catch( IOException e ) {
                log.warn("Could not get Bitfinex trades for "+ listing,e);
                esper.publish(new MarketDataError(listing, e));
            }
            finally {
                // requeue
                rateLimiter.execute(this);
            }
        }


        private final Esper esper;
        private final Listing listing;
        private CurrencyPair pair;
        private long lastTradeTime;
        private long lastTradeId;
    }


    private PollingMarketDataService dataService;
    private RateLimiter rateLimiter;
}
