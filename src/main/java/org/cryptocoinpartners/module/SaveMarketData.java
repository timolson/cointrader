package org.cryptocoinpartners.module;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.MarketData;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.util.PersistUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
@Singleton
public class SaveMarketData {

    private static ExecutorService service = Executors.newFixedThreadPool(1);
    static Future future;
    private static MarketData lastMktData = null;

    //@When("select * from MarketData")
    @When("select * from MarketData.std:lastevent()")
    public static void handleMarketData(MarketData m) {

        if (future == null || future.isDone()) {
            Future future = service.submit(new saveDataRunnable(m));
            try {
                future.get();
            } catch (InterruptedException e) {
                log.error("Threw a Execption, full stack trace follows:", e);

                e.printStackTrace();
                Thread.currentThread().interrupt();

            } catch (ExecutionException ex) {
                log.error("Threw a Execption, full stack trace follows:", ex);

                ex.getCause().printStackTrace();

            }
        }
    }

    private static class saveDataRunnable implements Runnable {
        static MarketData m;

        @Override
        public void run() {
            saveData();

        }

        public saveDataRunnable(MarketData m) {
            this.m = m;
        }

        public static void saveData() {

            if (m instanceof Trade) {
                Trade trade = (Trade) m;
                final Trade duplicate = PersistUtil.queryZeroOne(Trade.class, "select t from Trade t where market=?1 and remoteKey=?2 and time=?3",
                        trade.getMarket(), trade.getRemoteKey(), trade.getTime());
                if (duplicate == null)
                    PersistUtil.persist(trade);
                //else
                //log.warn("dropped duplicate Trade " + trade);
                //  } else if (m instanceof Book) {

            }

            else if (m instanceof Book) {
                Book book = (Book) m;

                final Book duplicate = PersistUtil.queryZeroOne(Book.class, "select b from Book b where b=?1", book);
                if (duplicate == null)
                    PersistUtil.persist(book);

                //if (book.getParent() != null)
                //  PersistUtil.merge(book.getParent());
                //  book.setParent(null);
                // PersistUtil.insert(book);

            } else { // if not a Trade, persist unconditionally
                try {
                    PersistUtil.persist(m);
                } catch (Throwable e) {
                    throw new Error("Could not insert " + m, e);
                }
            }
        }
    }

    public SaveMarketData() {
        int myint = 1;
    }

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.saveMarketData");

}
