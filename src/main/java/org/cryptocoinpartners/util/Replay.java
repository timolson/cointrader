package org.cryptocoinpartners.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.RemoteEvent;
import org.cryptocoinpartners.schema.Trade;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.espertech.esper.client.EPRuntime;

/**
 Manages a Context into which Trades and Books from the database are replayed.  The Context time is also managed by this
 class as it advances through the events.
 */
public class Replay implements Runnable {

    public static Replay all(boolean orderByTimeReceived) {
        return during(new Interval(getEventsStart(orderByTimeReceived), getEventsEnd(orderByTimeReceived)), orderByTimeReceived);
    }

    public static Replay all(boolean orderByTimeReceived, Semaphore semaphore) {
        return during(new Interval(getEventsStart(orderByTimeReceived), getEventsEnd(orderByTimeReceived)), orderByTimeReceived, semaphore);
    }

    public static Replay since(Instant start, boolean orderByTimeReceived) {
        return during(new Interval(start, getEventsEnd(orderByTimeReceived)), orderByTimeReceived);
    }

    public static Replay since(Instant start, boolean orderByTimeReceived, Semaphore semaphore) {
        return during(new Interval(start, getEventsEnd(orderByTimeReceived)), orderByTimeReceived, semaphore);
    }

    public static Replay until(Instant end, boolean orderByTimeReceived) {
        return during(new Interval(getEventsStart(orderByTimeReceived), end), orderByTimeReceived);
    }

    public static Replay until(Instant end, boolean orderByTimeReceived, Semaphore semaphore) {
        return during(new Interval(getEventsStart(orderByTimeReceived), end), orderByTimeReceived, semaphore);
    }

    public static Replay between(Instant start, Instant end, boolean orderByTimeReceived) {
        return during(new Interval(start, end), orderByTimeReceived);
    }

    public static Replay between(Instant start, Instant end, boolean orderByTimeReceived, Semaphore semaphore) {
        return during(new Interval(start, end), orderByTimeReceived, semaphore);
    }

    public static Replay during(Interval interval, boolean orderByTimeReceived) {
        return new Replay(interval, orderByTimeReceived);
    }

    public static Replay during(Interval interval, boolean orderByTimeReceived, Semaphore semaphore) {
        return new Replay(interval, orderByTimeReceived, semaphore);
    }

    public Replay(Interval replayTimeInterval, boolean orderByTimeReceived) {
        this.replayTimeInterval = replayTimeInterval; // set this before creating EventTimeManager
        this.semaphore = null;
        this.context = Context.create(new EventTimeManager());
        this.orderByTimeReceived = orderByTimeReceived;
    }

    public Replay(Interval replayTimeInterval, boolean orderByTimeReceived, Semaphore semaphore) {
        this.replayTimeInterval = replayTimeInterval; // set this before creating EventTimeManager
        this.semaphore = semaphore;
        this.context = Context.create(new EventTimeManager());
        this.orderByTimeReceived = orderByTimeReceived;
    }

    public Context getContext() {
        return context;
    }

    /**
     queries the database for all Books and Trades which have start <= time <= stop, then publishes those
     Events in order of time to this Replay's Context
     */

    @Override
    public void run() {

        final Instant start = replayTimeInterval.getStart().toInstant();
        final Instant end = replayTimeInterval.getEnd().toInstant();
        int threadCount = 0;

        service = Executors.newFixedThreadPool(2);
        if (replayTimeInterval.toDuration().isLongerThan(timeStep)) {
            // Start two threads, but ensure the first thread publish first, then reuse it

            for (Instant now = start; !now.isAfter(end);) {
                final Instant stepEnd = now.plus(timeStep);
                ReplayStepRunnable replayStep = new ReplayStepRunnable(now, stepEnd, context.getRunTime(), semaphore);
                service.submit(replayStep);
                threadCount++;
                now = stepEnd;

            }

        } else
            replayStep(start, end);

        if (semaphore != null)
            try {
                semaphore.acquire(threadCount);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                //  e.printStackTrace();
            }

    }

    private class ReplayStepRunnable implements Runnable {

        private final Instant start;
        private final Instant stop;
        private final EPRuntime runtime;
        private final Semaphore semaphore;

        public ReplayStepRunnable(Instant start, Instant stop, EPRuntime runtime, Semaphore semaphore) {
            this.semaphore = semaphore;
            this.start = start;
            this.stop = stop;
            this.runtime = runtime;

        }

        @Override
        // @Inject
        public void run() {
            boolean firstThread = false;
            if (startLatch == null) {
                //First thread to have started, so I will set the latch and count it donw once complete
                startLatch = new CountDownLatch(1);
                firstThread = true;
            }

            try {
                // perform interesting task

                // Log.debug(context.getInjector().toString());
                //PortfolioService port = context.getInjector().getInstance(PortfolioService.class);
                Iterator<RemoteEvent> ite = queryEvents(start, stop).iterator();
                // thread 1 starts, thread 2 finishes, want to wait till thread 1 is complete before processing 

                // we need to wait for current thread to finish.
                try {
                    if (!firstThread) {
                        startLatch.await();

                    }
                    while (ite.hasNext()) {
                        RemoteEvent event = ite.next();
                        //runtime.sendEvent(event);
                        context.publish(event);
                    }

                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // context.advanceTime(stop);

            } finally {
                if (semaphore != null)
                    semaphore.release();
                if (firstThread) {
                    startLatch.countDown();
                    startLatch = null;
                }

            }
        }

    }

    private void replayStep(Instant start, Instant stop) {
        Iterator<RemoteEvent> ite = queryEvents(start, stop).iterator();
        while (ite.hasNext()) {
            RemoteEvent event = ite.next();
            context.publish(event);
        }
        context.advanceTime(stop); // advance to the end of the time window to trigger any timer events
    }

    private List<RemoteEvent> queryEvents(Instant start, Instant stop) {
        final Market market = Market.forSymbol("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        final String timeField = timeFieldForOrdering(orderByTimeReceived);
        final String tradeQuery = "select t from Trade t where market=?1 and " + timeField + " >= ?2 and " + timeField + " <= ?3";
        final String bookQuery = "select b from Book b where market=?1 and " + timeField + " >= ?2 and " + timeField + " <= ?3";
        final List<RemoteEvent> events = new ArrayList<>();
        events.addAll(PersistUtil.queryList(Trade.class, tradeQuery, market, start, stop));
        events.addAll(PersistUtil.queryList(Book.class, bookQuery, market, start, stop));
        Collections.sort(events, orderByTimeReceived ? timeReceivedComparator : timeHappenedComparator);
        return events;
    }

    private static Instant getEventsStart(boolean orderByRemoteTime) {
        String timeField = timeFieldForOrdering(orderByRemoteTime);
        Instant bookStart = PersistUtil.queryOne(Instant.class, "select min(" + timeField + ") from Book");
        Instant tradeStart = PersistUtil.queryOne(Instant.class, "select min(" + timeField + ") from Trade");
        if (bookStart == null && tradeStart == null)
            return null;
        if (bookStart == null)
            return tradeStart;
        if (tradeStart == null)
            return bookStart;
        return tradeStart.isBefore(bookStart) ? tradeStart : bookStart;
    }

    private static Instant getEventsEnd(boolean orderByTimeReceived) {
        final String timeField = timeFieldForOrdering(orderByTimeReceived);
        // queries use max(time)+1 because the end of a range is exclusive, and we want to include the last event
        Instant bookEnd = PersistUtil.queryOne(Instant.class, "select max(" + timeField + ") from Book");
        Instant tradeEnd = PersistUtil.queryOne(Instant.class, "select max(" + timeField + ") from Trade");
        if (bookEnd == null && tradeEnd == null)
            return null;
        if (bookEnd == null)
            return tradeEnd;
        if (tradeEnd == null)
            return bookEnd;
        return tradeEnd.isAfter(bookEnd) ? tradeEnd : bookEnd;
    }

    private static String timeFieldForOrdering(boolean orderByTimeReceived) {
        return orderByTimeReceived ? "timeReceived" : "time";
    }

    private static final Comparator<RemoteEvent> timeReceivedComparator = new Comparator<RemoteEvent>() {
        @Override
        public int compare(RemoteEvent event, RemoteEvent event2) {
            return event.getTimeReceived().compareTo(event2.getTimeReceived());
        }
    };

    private static final Comparator<RemoteEvent> timeHappenedComparator = new Comparator<RemoteEvent>() {
        @Override
        public int compare(RemoteEvent event, RemoteEvent event2) {
            return event.getTime().compareTo(event2.getTime());
        }
    };

    public class EventTimeManager implements Context.TimeProvider {
        @Override
        public Instant getInitialTime() {
            return replayTimeInterval.getStart().toInstant();
        }

        @Override
        public Instant nextTime(Event event) {
            if (orderByTimeReceived && event instanceof RemoteEvent) {
                RemoteEvent remoteEvent = (RemoteEvent) event;
                return remoteEvent.getTimeReceived();
            } else
                return event.getTime();
        }
    }

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.replay");

    private final Interval replayTimeInterval;
    private final Semaphore semaphore;
    private static ExecutorService service;
    private final Context context;
    private static final Duration timeStep = Duration.standardDays(1); // how many rows from the DB to gather in one batch
    private final boolean orderByTimeReceived;
    private static CountDownLatch startLatch;

}
