package org.cryptocoinpartners.module;

import java.math.BigDecimal;

import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Exchanges;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.MarketData;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.dao.BarJpaDao;
import org.cryptocoinpartners.schema.dao.BookJpaDao;
import org.cryptocoinpartners.schema.dao.MarketDataJpaDao;
import org.cryptocoinpartners.schema.dao.TradeJpaDao;
import org.joda.time.Instant;

import com.google.inject.Inject;

class saveDataRunnableTest implements Runnable {
    MarketData m;
    @Inject
    protected BookJpaDao bookDao;

    @Inject
    protected TradeJpaDao tradeDao;

    @Inject
    protected MarketDataJpaDao marketDataDao;

    @Inject
    protected BarJpaDao barDao;
    @Inject
    Market market;

    @Override
    public void run() {
        // saveData();

        if (m instanceof Trade) {
            Trade trade = (Trade) m;
            final Trade duplicate = tradeDao.queryZeroOne(Trade.class, "select t from Trade t where market=?1 and remoteKey=?2 and time=?3", trade.getMarket(),
                    trade.getRemoteKey(), trade.getTime());
            if (duplicate == null)
                tradeDao.persist(trade);
            //  PersistUtil.persist(trade);
            //else
            //log.warn("dropped duplicate Trade " + trade);
            //  } else if (m instanceof Book) {

        }

        else if (m instanceof Book) {
            Book book = (Book) m;

            bookDao.persist(book);

            Book.Builder b = new Book.Builder();

            b.start(Instant.now(), null, market.findOrCreate(Exchanges.BITSTAMP, Listing.forSymbol("BTC.USD")));
            b.addBid(new BigDecimal("2.1"), new BigDecimal("1.04"));
            b.addBid(new BigDecimal("2.2"), new BigDecimal("1.03"));
            b.addBid(new BigDecimal("2.3"), new BigDecimal("1.02"));
            b.addBid(new BigDecimal("2.4"), new BigDecimal("1.01"));
            b.addAsk(new BigDecimal("2.5"), new BigDecimal("1.01"));
            b.addAsk(new BigDecimal("2.6"), new BigDecimal("1.02"));
            b.addAsk(new BigDecimal("2.7"), new BigDecimal("1.03"));
            b.addAsk(new BigDecimal("2.8"), new BigDecimal("1.04"));
            Book parent = b.build();

            // PersistUtil.insert(parent);

            //    Book duplicate = PersistUtil.queryZeroOne(Book.class, "select b from Book b where b=?1", book);
            // if (duplicate == null) {
            //PersistUtil.persist(book);

            //  BookDao.persist(book);
            //}
            bookDao.persist(parent);
            //if (book.getParent() != null)
            //  PersistUtil.merge(book.getParent());
            //  book.setParent(null);
            // PersistUtil.insert(book);

        } else if (m instanceof Bar) {
            Bar bar = (Bar) m;

            final Bar duplicate = barDao.queryZeroOne(Bar.class, "select b from Bar b where b=?1", bar);
            if (duplicate == null)
                barDao.persist(bar);

            //if (book.getParent() != null)
            //  PersistUtil.merge(book.getParent());
            //  book.setParent(null);
            // PersistUtil.insert(book);

        } else { //// if not a Trade, persist unconditionally
            try {
                barDao.persist(m);
            } catch (Throwable e) {
                throw new Error("Could not insert " + m, e);
            }
        }

    }

    public saveDataRunnableTest(MarketData m) {
        this.m = m;
    }

    public void saveData() {

        if (m instanceof Trade) {
            Trade trade = (Trade) m;
            final Trade duplicate = tradeDao.queryZeroOne(Trade.class, "select t from Trade t where market=?1 and remoteKey=?2 and time=?3", trade.getMarket(),
                    trade.getRemoteKey(), trade.getTime());
            if (duplicate == null)
                tradeDao.persist(trade);
            //else
            //log.warn("dropped duplicate Trade " + trade);
            //  } else if (m instanceof Book) {

        }

        else if (m instanceof Book) {
            Book book = (Book) m;

            Book duplicate = bookDao.queryZeroOne(Book.class, "select b from Book b where b=?1", book);
            if (duplicate == null) {
                // bookDao.persist(book);

                bookDao.persist(book);
            }

            //if (book.getParent() != null)
            //  PersistUtil.merge(book.getParent());
            //  book.setParent(null);
            // PersistUtil.insert(book);

        } else if (m instanceof Bar) {
            Bar bar = (Bar) m;

            final Bar duplicate = barDao.queryZeroOne(Bar.class, "select b from Bar b where b=?1", bar);
            if (duplicate == null)
                barDao.persist(bar);

            //if (book.getParent() != null)
            //  PersistUtil.merge(book.getParent());
            //  book.setParent(null);
            // PersistUtil.insert(book);

        } else { //// if not a Trade, persist unconditionally
            try {
                marketDataDao.persist(m);
            } catch (Throwable e) {
                throw new Error("Could not insert " + m, e);
            }
        }
    }
}
