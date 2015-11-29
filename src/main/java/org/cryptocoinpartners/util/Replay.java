package org.cryptocoinpartners.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 Manages a Context into which Trades and Books from the database are replayed.  The Context time is also managed by this
 class as it advances through the events.
 */
public class Replay implements Runnable {

    @AssistedInject
    public Replay(@Assisted boolean orderByTimeReceived) {
        // new Interval(this.getEventsStart(orderByTimeReceived), this.getEventsEnd(orderByTimeReceived));

        this(new Interval(getEventsStart(orderByTimeReceived), getEventsEnd(orderByTimeReceived)), orderByTimeReceived);
    }

    //
    @AssistedInject
    public Replay(@Assisted boolean orderByTimeReceived, @Assisted Semaphore semaphore) {
        this(new Interval(getEventsStart(orderByTimeReceived), getEventsEnd(orderByTimeReceived)), orderByTimeReceived, semaphore);
    }

    //
    @AssistedInject
    public Replay(@Assisted("startTime") Instant start, @Assisted boolean orderByTimeReceived) {
        this(new Interval(start, getEventsEnd(orderByTimeReceived)), orderByTimeReceived);
    }

    //
    @AssistedInject
    public Replay(@Assisted("startTime") Instant start, @Assisted boolean orderByTimeReceived, @Assisted Semaphore semaphore) {
        this(new Interval(start, getEventsEnd(orderByTimeReceived)), orderByTimeReceived, semaphore);
    }

    //

    @AssistedInject
    public Replay(@Assisted("endTime") Instant end, @Assisted boolean orderByTimeReceived, @Assisted("until") boolean until) {
        this(new Interval(getEventsStart(orderByTimeReceived), end), orderByTimeReceived);
    }

    //
    @AssistedInject
    public Replay(@Assisted("endTime") Instant end, @Assisted boolean orderByTimeReceived, @Assisted Semaphore semaphore, @Assisted("until") boolean until) {
        this(new Interval(getEventsStart(orderByTimeReceived), end), orderByTimeReceived, semaphore);
    }

    //
    @AssistedInject
    public Replay(@Assisted("startTime") Instant start, @Assisted("endTime") Instant end, @Assisted boolean orderByTimeReceived) {
        this(new Interval(start, end), orderByTimeReceived);
    }

    //
    @AssistedInject
    public Replay(@Assisted("startTime") Instant start, @Assisted("endTime") Instant end, @Assisted boolean orderByTimeReceived, @Assisted Semaphore semaphore) {
        this(new Interval(start, end), orderByTimeReceived, semaphore);
    }

    //
    // @AssistedInject
    // public Replay (Interval interval, boolean orderByTimeReceived) {
    //  return new Replay(interval, orderByTimeReceived);
    // }
    //
    //    @AssistedInject
    //    public Replay during(Interval interval, boolean orderByTimeReceived, Semaphore semaphore) {
    //        return new Replay(interval, orderByTimeReceived, semaphore);
    //    }

    @AssistedInject
    public Replay(@Assisted Interval replayTimeInterval, @Assisted boolean orderByTimeReceived) {
        this.replayTimeInterval = replayTimeInterval; // set this before creating EventTimeManager
        this.semaphore = null;
        this.context = Context.create(new EventTimeManager());
        this.orderByTimeReceived = orderByTimeReceived;
    }

    @AssistedInject
    public Replay(@Assisted Interval replayTimeInterval, @Assisted boolean orderByTimeReceived, @Assisted Semaphore semaphore) {
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
        CountDownLatch startLatch = null;
        CountDownLatch stopLatch = null;
        service = Executors.newFixedThreadPool(dbReaderThreads);
        //   engines = Executors.newFixedThreadPool(1);

        // engines.submit(new PublisherRunnable());
        if (replayTimeInterval.toDuration().isLongerThan(timeStep)) {
            // Start two threads, but ensure the first thread publish first, then reuse it

            for (Instant now = start; !now.isAfter(end);) {
                final Instant stepEnd = now.plus(timeStep);
                stopLatch = new CountDownLatch(1);
                ReplayStepRunnable replayStep = new ReplayStepRunnable(now, stepEnd, context.getRunTime(), semaphore, startLatch, stopLatch, threadCount);
                service.submit(replayStep);
                startLatch = stopLatch;
                // if (threadCount != 0)

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

    private class PublisherRunnable implements Runnable {

        public PublisherRunnable() {

        }

        @Override
        // @Inject
        public void run() {
            while (true)
                try {

                    RemoteEvent event = queue.take();
                    //runtime.sendEvent(event);
                    context.publish(event);

                } catch (Exception | Error e) {
                    e.printStackTrace();
                }

        }

    }

    private class ReplayStepRunnable implements Runnable {

        private final Instant start;
        private final Instant stop;
        private final EPRuntime runtime;
        private final Semaphore semaphore;
        private final CountDownLatch startLatch;
        private final CountDownLatch stopLatch;
        private final int threadCount;

        public ReplayStepRunnable(Instant start, Instant stop, EPRuntime runtime, Semaphore semaphore, CountDownLatch startLatch, CountDownLatch stopLatch,
                int threadCount) {
            this.semaphore = semaphore;
            this.start = start;
            this.stop = stop;
            this.runtime = runtime;
            this.startLatch = startLatch;
            this.stopLatch = stopLatch;
            this.threadCount = threadCount;

        }

        @Override
        // @Inject
        public void run() {

            try {
                // perform interesting task

                // Log.debug(context.getInjector().toString());
                //PortfolioService port = context.getInjector().getInstance(PortfolioService.class);
                Iterator<RemoteEvent> ite = queryEvents(start, stop).iterator();
                // thread 1 starts, thread 2 finishes, want to wait till thread 1 is complete before processing 

                // we need to wait for current thread to finish.
                try {
                    if (startLatch != null)
                        startLatch.await();

                    while (ite.hasNext()) {
                        RemoteEvent event = ite.next();
                        //   queue.put(event);

                        context.publish(event);
                        EM.detach(event);

                    }

                } catch (Error | Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // context.advanceTime(stop);

            } catch (Error | Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            finally {
                if (semaphore != null)
                    semaphore.release();

                stopLatch.countDown();

            }
        }

    }

    private void replayStep(Instant start, Instant stop) {
        Iterator<RemoteEvent> ite = queryEvents(start, stop).iterator();
        while (ite.hasNext()) {
            RemoteEvent event = ite.next();
            context.publish(event);
            event.detach();
        }
        context.advanceTime(stop); // advance to the end of the time window to trigger any timer events
    }

    private List<RemoteEvent> queryEvents(Instant start, Instant stop) {
        final Market market = Market.forSymbol("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        final String timeField = timeFieldForOrdering(orderByTimeReceived);
        final String tradeQuery = "select t from Trade t where market=?1 and " + timeField + " >= ?2 and " + timeField + " <= ?3";
        final String bookQuery = "select b from Book b where market=?1 and " + timeField + " >= ?2 and " + timeField + " <= ?3";
        final List<RemoteEvent> events = new ArrayList<>();
        events.addAll(EM.queryList(Trade.class, tradeQuery, market, start, stop));
        events.addAll(EM.queryList(Book.class, bookQuery, market, start, stop));
        Collections.sort(events, orderByTimeReceived ? timeReceivedComparator : timeHappenedComparator);
        return events;
    }

    private static Instant getEventsStart(boolean orderByRemoteTime) {
        String timeField = timeFieldForOrdering(orderByRemoteTime);
        Instant bookStart = EM.queryOne(Instant.class, "select min(" + timeField + ") from Book");
        Instant tradeStart = EM.queryOne(Instant.class, "select min(" + timeField + ") from Trade");
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
        Instant bookEnd = EM.queryOne(Instant.class, "select max(" + timeField + ") from Book");
        Instant tradeEnd = EM.queryOne(Instant.class, "select max(" + timeField + ") from Trade");
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
    private final BlockingQueue<RemoteEvent> queue = new LinkedBlockingQueue<RemoteEvent>();
    private final Interval replayTimeInterval;
    private final Integer dbReaderThreads = ConfigUtil.combined().getInt("db.replay.reader.threads");
    private final Semaphore semaphore;
    private static ExecutorService service;
    private static ExecutorService engines;

    private final Context context;
    private static final Duration timeStep = Duration.standardDays(1); // how many rows from the DB to gather in one batch
    private final boolean orderByTimeReceived;

}
