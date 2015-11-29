package org.cryptocoinpartners.module;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.BarFactory;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.MarketData;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.dao.BarJpaDao;
import org.cryptocoinpartners.schema.dao.BookJpaDao;
import org.cryptocoinpartners.schema.dao.MarketDataJpaDao;
import org.cryptocoinpartners.schema.dao.TradeJpaDao;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.EM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * @author Tim Olson
 */
@Singleton
public class SaveMarketData {

    private static ExecutorService tradeService;
    private static ExecutorService bookService;
    private static ExecutorService barService;
    static Future future;
    private static MarketData lastMktData = null;

    @Inject
    protected BarFactory barFactory;

    @Inject
    protected BookJpaDao bookDao;

    @Inject
    protected TradeJpaDao tradeDao;

    @Inject
    protected MarketDataJpaDao marketDataDao;

    @Inject
    protected BarJpaDao barDao;
    static {
        tradeService = Executors.newFixedThreadPool(ConfigUtil.combined().getInt("db.trade.writer.threads"));
        bookService = Executors.newFixedThreadPool(ConfigUtil.combined().getInt("db.book.writer.threads"));

        barService = Executors.newFixedThreadPool(ConfigUtil.combined().getInt("db.bar.writer.threads"));
    }

    //@When("select * from MarketData")
    // @When("select * from MarketData")
    @When("@Priority(1) select * from Book")
    public void handleBook(Book m) {

        //  if (future == null || future.isDone()) {
        //Future future = 
        log.trace("book recieved: " + m.getId() + " thread: " + Thread.currentThread().getName());
        bookService.submit(new saveBookRunnable(m));

    }

    @When("@Priority(1) select * from Trade")
    public void handleTrade(Trade m) {

        //  if (future == null || future.isDone()) {
        //Future future = 
        // log.debug("trade recieved: " + m.getId() + " thread: " + Thread.currentThread().getName());
        log.trace("Trade recieved: " + m.getId() + " thread: " + Thread.currentThread().getName());

        tradeService.submit(new saveTradeRunnable(m));

    }

    @When("@Priority(1) select * from Bar")
    public void handleBar(Bar m) {

        //  if (future == null || future.isDone()) {
        //Future future = 
        //  log.debug("bar recieved: " + m.getId() + " thread: " + Thread.currentThread().getName());
        log.trace("Bar recieved: " + m.getId() + " thread: " + Thread.currentThread().getName());

        barService.submit(new saveBarRunnable(m));

    }

    public class saveTradeRunnable implements Runnable {
        Trade trade;

        @Override
        public void run() {
            saveData();

        }

        public saveTradeRunnable(Trade m) {
            this.trade = m;
        }

        public void saveData() {
            // issues is when we have mutlipe thread persisting , we have persitance contect per thread that is not updated.

            Trade duplicate = (trade.getDao() == null) ? EM.queryZeroOne(Trade.class, "select t from Trade t where market=?1 and remoteKey=?2 and time=?3",
                    trade.getMarket(), trade.getRemoteKey(), trade.getTime()) : trade.queryZeroOne(Trade.class,
                    "select t from Trade t where market=?1 and remoteKey=?2 and time=?3", trade.getMarket(), trade.getRemoteKey(), trade.getTime());

            if (duplicate == null)
                trade.persit();

        }
    }

    public class saveBookRunnable implements Runnable {
        Book book;

        @Override
        public void run() {
            saveData();

        }

        public saveBookRunnable(Book m) {
            this.book = m;
        }

        public void saveData() {

            UUID duplicate = null;
            if (book.getId() != null)
                duplicate = (book.getDao() == null) ? (EM.queryZeroOne(UUID.class, "select b.id from Book b where b.id=?1", book.getId())) : (book
                        .queryZeroOne(UUID.class, "select b.id from Book b where b.id=?1", book.getId()));

            if (duplicate == null) {
                //try {
                //      log.info("persiting book: " + book.getId());
                book.persit();
            }
        }
    }

    public class saveBarRunnable implements Runnable {
        Bar rawBar;

        @Override
        public void run() {
            saveData();

        }

        public saveBarRunnable(Bar m) {
            this.rawBar = m;
        }

        public void saveData() {
            Bar bar = barFactory.create(rawBar);
            Bar duplicate = barDao.queryZeroOne(Bar.class, "select b from Bar b where b=?1", bar);
            if (duplicate == null)
                bar.persit();

        }
    }

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.saveMarketData");

}
