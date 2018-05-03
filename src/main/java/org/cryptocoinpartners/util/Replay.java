package org.cryptocoinpartners.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import javax.annotation.Nullable;

import org.cryptocoinpartners.module.BasicQuoteService;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.module.MockTicker;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.BookFactory;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.RemoteEvent;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.service.PortfolioService;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.espertech.esper.client.EPRuntime;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * Manages a Context into which Trades and Books from the database are replayed. The Context time is also managed by this class as it advances through
 * the events.
 */
public class Replay implements Runnable {

	private Map<Double, Instant> latestBars = new HashMap<Double, Instant>();

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
	public Replay(@Assisted("startTime") Instant start, @Assisted("endTime") Instant end, @Assisted("orderByTimeReceived") boolean orderByTimeReceived,
			@Assisted Semaphore semaphore, @Assisted("useRandomData") boolean useRandomData, @Assisted("replayBooks") boolean replayBooks,
			@Assisted("replayBars") boolean replayBars, @Assisted("barIntervals") @Nullable List<String> barIntervals) {
		this(new Interval(start, end), orderByTimeReceived, semaphore, useRandomData, replayBooks, replayBars, barIntervals);
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
		this.useRandomData = false;

	}

	@AssistedInject
	public Replay(@Assisted Interval replayTimeInterval, @Assisted boolean orderByTimeReceived, @Assisted Semaphore semaphore) {
		this.replayTimeInterval = replayTimeInterval; // set this before creating EventTimeManager
		this.semaphore = semaphore;
		this.context = Context.create(new EventTimeManager());
		this.orderByTimeReceived = orderByTimeReceived;
		this.useRandomData = false;
		// this.useRandomData=useRandomData;
	}

	public Replay(Interval replayTimeInterval, boolean orderByTimeReceived, @Assisted Semaphore semaphore, boolean useRandomData, boolean replayBooks,
			boolean replayBars, @Nullable List<String> barIntervals) {
		this.replayTimeInterval = replayTimeInterval; // set this before creating EventTimeManager
		this.semaphore = semaphore;
		this.context = Context.create(new EventTimeManager());
		this.orderByTimeReceived = orderByTimeReceived;
		this.useRandomData = useRandomData;
		this.replayBooks = replayBooks;
		this.replayBars = replayBars;
		this.barIntervals = barIntervals;
		// this.useRandomData=useRandomData;
	}

	public Context getContext() {
		return context;
	}

	/**
	 * queries the database for all Books (optional) and Trades which have start <= time <= stop, then publishes those Events in order of time to this
	 * Replay's Context
	 */

	@Override
	public void run() {

		final Instant start = replayTimeInterval.getStart().toInstant();
		final Instant end = replayTimeInterval.getEnd().toInstant();
		List<Double> intervals = new ArrayList<Double>();
		Double maxInterval = 0d;
		Bar lastBar = null;
		Map<String, Tradeable> markets = new HashMap<String, Tradeable>();

		PortfolioService portfolioService = context.getInjector().getInstance(PortfolioService.class);
		for (Portfolio portfolio : portfolioService.getPortfolios()) {
			for (Tradeable tradeable : portfolio.getMarkets())
				if (tradeable != null)
					markets.put(tradeable.getSymbol(), tradeable);

		}
		if (replayBars) {
			for (String interval : barIntervals) {
				Double intervalAsDouble = Double.parseDouble(interval);
				if (!intervals.contains(intervalAsDouble))
					intervals.add(intervalAsDouble);
				if (intervalAsDouble > maxInterval)
					maxInterval = intervalAsDouble;
			}

			final String maxBarTimeQuery = "select r from Bar r where  market in (?1) and interval= ?2  and volume<>0 order by time desc";
			try {
				lastBar = EM.queryLimitOne(Bar.class, maxBarTimeQuery, new ArrayList(markets.values()), maxInterval);
			} catch (Exception | Error ex) {
				lastBar = null;
			}
		}
		//got the start time of the 115200 which would have been  2018-03-20 08:00:00 and run time was 2018-03-21 07:36:49 
		final List<RemoteEvent> events = new ArrayList<>();
		final List<Book> books = new ArrayList<>();
		final List<Trade> trades = new ArrayList<>();
		final List<Bar> bars = new ArrayList<>();
		//   ArrayList marketsArray = new ArrayList(markets.values());
		//we could kick these off the book and trade on seperate threads then let them come back before continuing.
		//trades.addAll(EM.queryList(Trade.class, tradeQuery, new ArrayList(markets.values()), start, stop));

		if (!useRandomData) {
			int threadCount = 0;
			CountDownLatch startLatch = null;
			CountDownLatch stopLatch = null;
			service = Executors.newFixedThreadPool(dbReaderThreads);

			//   engines = Executors.newFixedThreadPool(1);
			//    replayTimeInterval.toDuration().
			// engines.submit(new PublisherRunnable());
			/*
			 * if (replayTimeInterval.toDuration().isLongerThan(timeStep)) { // Start two threads, but ensure the first thread publish first, then reuse it
			 * for (Instant now = start; !now.isAfter(end);) { final Instant stepEnd = now.plus(timeStep); threadCount++; now = stepEnd; } } endLatch = new
			 * CountDownLatch(threadCount);
			 *///  semaphore.release(threadCount);

			if (replayTimeInterval.toDuration().isLongerThan(timeStep)) {
				// Start two threads, but ensure the first thread publish first, then reuse it

				for (Instant now = start; !now.isAfter(end);) {
					final Instant stepEnd = now.plus(timeStep);
					Instant lastBarEnd = lastBar == null ? null : lastBar.getTime().toDateTime().plusSeconds(lastBar.getInterval().intValue()).toInstant();
					log.debug("Replay: Run replaying from " + now + " to " + stepEnd + (lastBarEnd == null ? "" : "with last bar start time of " + lastBarEnd));
					stopLatch = new CountDownLatch(1);
					log.debug("Replay: ReplayStepRunnable created with: " + now + ", " + context.getRunTime() + ", " + semaphore + ", " + startLatch + ", "
							+ stopLatch + ", " + threadCount);

					ReplayStepRunnable replayStep = new ReplayStepRunnable(now, stepEnd, context.getRunTime(), semaphore, startLatch, stopLatch, threadCount,
							replayBooks, replayBars, (lastBarEnd == null ? null : lastBarEnd), markets, intervals);
					startLatch = stopLatch;
					service.submit(replayStep);
					// if (threadCount != 0)
					threadCount++;
					now = stepEnd;

				}
				semaphore.release(threadCount);
				while (semaphore.availablePermits() > 0) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						log.debug(this.getClass().getSimpleName() + ": replaying historic prices", e);
					}
				}
				if (replayBars) {
					threadCount = 0;
					//context = replay.getContext();

					//	latestBars
					double lastBarInterval = 0d;
					for (Double interval : latestBars.keySet())
						if (interval > lastBarInterval)
							lastBarInterval = interval;
					Instant endInstant = new Instant(System.currentTimeMillis());
					Instant startInstant = (latestBars.get(lastBarInterval) != null)
							? latestBars.get(lastBarInterval).toDateTime().plusSeconds((int) lastBarInterval).toInstant()
							: endInstant.toDateTime().minusSeconds(maxInterval.intValue()).toInstant();

					for (Instant now = startInstant; !now.isAfter(endInstant);) {
						final Instant stepEnd = now.plus(timeStep);
						log.debug("Replay: Run replaying from " + now + " to " + stepEnd);
						stopLatch = new CountDownLatch(1);
						log.debug("Replay: ReplayStepRunnable created with: " + now + ", " + context.getRunTime() + ", " + semaphore + ", " + startLatch + ", "
								+ stopLatch + ", " + threadCount);

						ReplayStepRunnable replayStep = new ReplayStepRunnable(now, stepEnd, context.getRunTime(), semaphore, startLatch, stopLatch,
								threadCount, false, false, null, markets, intervals);
						startLatch = stopLatch;
						service.submit(replayStep);
						// if (threadCount != 0)
						threadCount++;
						now = stepEnd;

					}
					semaphore.release(threadCount);

				}
				while (semaphore.availablePermits() > 0) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						log.debug(this.getClass().getSimpleName() + ": replaying historic prices", e);
					}
				}
				log.debug("completed");

			} else
				replayStep(start, end, replayBooks, replayBars, null, markets, intervals);
		} else {
			new MockTicker(context, ConfigUtil.combined(), start, end, context.getInjector().getInstance(BookFactory.class),
					context.getInjector().getInstance(BasicQuoteService.class));

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
		private final boolean replayBooks;
		private final boolean replayBars;
		private final Instant barEnd;
		private Map<String, Tradeable> markets;
		private List<Double> intervals;

		private final EPRuntime runtime;
		private final Semaphore semaphore;
		//  private final CountDownLatch startLatch;
		private final CountDownLatch stopLatch;
		private final int threadCount;
		private final CountDownLatch startLatch;

		public ReplayStepRunnable(Instant start, Instant stop, EPRuntime runtime, Semaphore semaphore, final CountDownLatch startLatch,
				CountDownLatch stopLatch, int threadCount, boolean replayBooks, boolean replayBars, Instant barEnd, Map<String, Tradeable> markets,
				List<Double> intervals) {
			this.semaphore = semaphore;
			this.start = start;
			this.stop = stop;
			this.runtime = runtime;
			this.startLatch = startLatch;
			this.stopLatch = stopLatch;
			this.threadCount = threadCount;
			this.replayBooks = replayBooks;
			this.replayBars = replayBars;
			this.barEnd = barEnd;
			this.markets = markets;
			this.intervals = intervals;
		}

		@Override
		// @Inject
		public void run() {

			try {
				// perform interesting task
				log.debug("ReplayStepRunnable: Run querying events from " + start + " to " + stop + " with latch " + startLatch);
				List<RemoteEvent> events = queryEvents(start, stop, replayBooks, replayBars, barEnd, markets, intervals);

				// Log.debug(context.getInjector().toString());
				//PortfolioService port = context.getInjector().getInstance(PortfolioService.class);
				//   Iterator<RemoteEvent> ite = queryEvents(start, stop).iterator();
				// thread 1 starts, thread 2 finishes, want to wait till thread 1 is complete before processing 

				// we need to wait for current thread to finish.
				//  try {
				if (startLatch != null) {
					log.debug("ReplayStepRunnable: Run Waiting for start latch " + startLatch);

					startLatch.await();
				}
				log.debug("ReplayStepRunnable: Run publishing events from " + start + " to " + stop);

				for (RemoteEvent event : events) {
					context.publish(event);
					EM.detach(event);
				}
				System.gc();
				log.debug("ReplayStepRunnable: Published events from " + start + " to " + stop);

				//  } catch (Error | Exception e) {
				// TODO Auto-generated catch block
				//     log.debug("ReplayStepRunnable: Unable to query events between " + start + " and stop " + stop + ", full stack trace follows:", e);

				// }

				// context.advanceTime(stop);

			} catch (Error | Exception e) {
				log.debug("ReplayStepRunnable: Unable to query events between " + start + " and stop " + stop + ", full stack trace follows:", e);

				// TODO Auto-generated catch block
				//  e.printStackTrace();
			} finally {
				if (startLatch != null) {
					log.debug("ReplayStepRunnable: Run Waiting for start latch " + startLatch);

					try {
						startLatch.await();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						log.debug("ReplayStepRunnable: Unable to query events between " + start + " and stop " + stop + ", full stack trace follows:", e);

					}
				}

				log.debug("ReplayStepRunnable: Run Counting down stop latch " + stopLatch);

				stopLatch.countDown();
				if (semaphore != null) {

					try {
						log.debug("ReplayStepRunnable: removing permit from to pool for semaphore avaiable permits " + semaphore.availablePermits());

						semaphore.acquire();
					} catch (InterruptedException e) {
						log.debug("ReplayStepRunnable: unable to remove permit from to pool for semaphore avaiable permits " + semaphore.availablePermits(), e);

					}
				}

			}

		}

	}

	private void replayStep(Instant start, Instant stop, boolean replayBooks, boolean replayBars, Instant barEnd, Map<String, Tradeable> markets,
			List<Double> intervals) {
		Iterator<RemoteEvent> ite = queryEvents(start, stop, replayBooks, replayBars, barEnd, markets, intervals).iterator();
		while (ite.hasNext()) {
			RemoteEvent event = ite.next();

			context.publish(event);
			event.detach();

		}
		System.gc();
		context.advanceTime(stop); // advance to the end of the time window to trigger any timer events

	}

	private List<RemoteEvent> queryEvents(Instant start, Instant stop, boolean replayBooks, boolean replayBars, Instant barEnd, Map<String, Tradeable> markets,
			List<Double> intervals) {
		final String timeField = timeFieldForOrdering(orderByTimeReceived);
		//  order in (?1)
		final String tradeQuery = "select t from Trade t where  market in (?1) and " + timeField + " >= ?2 and " + timeField + " <= ?3";
		final String bookQuery = "select b from Book b where  market in (?1) and " + timeField + " >= ?2 and " + timeField + " <= ?3";
		final String barQuery = "select r from Bar r where interval in (?1) and market in (?2) and " + timeField + " >= ?3 and " + timeField + " <= ?4";

		final List<RemoteEvent> events = new ArrayList<>();
		final List<Book> books = new ArrayList<>();
		final List<Trade> trades = new ArrayList<>();
		final List<Bar> bars = new ArrayList<>();
		//   ArrayList marketsArray = new ArrayList(markets.values());
		//we could kick these off the book and trade on seperate threads then let them come back before continuing.
		//trades.addAll(EM.queryList(Trade.class, tradeQuery, new ArrayList(markets.values()), start, stop));

		if (replayBooks) {
			books.addAll(EM.queryList(Book.class, bookQuery, new ArrayList(markets.values()), start, stop));
			Iterator<Book> itb = books.iterator();

			while (itb.hasNext()) {
				Book book = itb.next();
				book.sortBook();

				//book.
				if (!markets.containsKey(book.getMarket().getSymbol())) {
					itb.remove();
					continue;
				}
				book.setMarket(markets.get(book.getMarket().getSymbol()));
				book.setPersisted(true);
			}

			events.addAll(books);
		}
		//got the start time of the 115200 which would have been  2018-03-20 08:00:00 and run time was 2018-03-21 07:36:49 
		//now -32 => 19/03/2018 23:36
		//2135:2018-03-21 07:25:06 [pool-27-thread-2] DEBUG org.cryptocoinpartners.replay - ReplayStepRunnable: Run querying events from 2018-03-16T21:24:45.910Z to 2018-03-17T21:24:45.910Z with latch java.util.concurrent.CountDownLatch@54a24078[Count = 1]
		//		2905:2018-03-21 07:25:07 [pool-27-thread-3] DEBUG org.cryptocoinpartners.replay - ReplayStepRunnable: Run querying events from 2018-03-17T21:24:45.910Z to 2018-03-18T21:24:45.910Z with latch java.util.concurrent.CountDownLatch@7f09c683[Count = 1]
		//		3683:2018-03-21 07:25:07 [pool-27-thread-4] DEBUG org.cryptocoinpartners.replay - ReplayStepRunnable: Run querying events from 2018-03-18T21:24:45.910Z to 2018-03-19T21:24:45.910Z with latch java.util.concurrent.CountDownLatch@2a05469c[Count = 1]
		//		4453:2018-03-21 07:25:08 [pool-27-thread-5] DEBUG org.cryptocoinpartners.replay - ReplayStepRunnable: Run querying events from 2018-03-19T21:24:45.910Z to 2018-03-20T21:24:45.910Z with latch java.util.concurrent.CountDownLatch@5260231b[Count = 1]			

		//	so between 2018-03-17T21:24:45.910Z to 2018-03-18T21:24:45.910 at 2018-03-21 07:25:29 no envents?

		if (replayBars && start.isBefore(barEnd)) {
			if (stop.isAfter(barEnd))
				stop = barEnd;

			bars.addAll(EM.queryList(Bar.class, barQuery, intervals, new ArrayList(markets.values()), start, stop));
			Iterator<Bar> itb = bars.iterator();

			while (itb.hasNext()) {
				Bar bar = itb.next();
				if (latestBars == null || latestBars.isEmpty() || !latestBars.containsKey(bar.getInterval()))
					latestBars.put(bar.getInterval(), bar.getTime());
				else if (latestBars.containsKey(bar.getInterval()) && latestBars.get(bar.getInterval()).isBefore(bar.getTime()))
					latestBars.put(bar.getInterval(), bar.getTime());

				if (!markets.containsKey(bar.getMarket().getSymbol())) {
					itb.remove();
					continue;
				}
				bar.setMarket(markets.get(bar.getMarket().getSymbol()));
				bar.setPersisted(true);
			}
			//I need to publish the trade updates then 
			events.addAll(bars);
		} else if (!replayBars || (replayBars && barEnd == null)) {
			//TODO we need to replay trades for any bars we don't have.
			trades.addAll(EM.queryList(Trade.class, tradeQuery, new ArrayList(markets.values()), start, stop));

			Iterator<Trade> itt = trades.iterator();

			while (itt.hasNext()) {
				Trade trade = itt.next();
				if (!markets.containsKey(trade.getMarket().getSymbol())) {
					itt.remove();
					continue;
				}
				trade.setMarket(markets.get(trade.getMarket().getSymbol()));
				trade.setPersisted(true);
			}
			events.addAll(trades);

		}

		Collections.sort(events, orderByTimeReceived ? timeReceivedComparator : timeHappenedComparator);
		System.gc();
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
			int sComp = event.getTimeReceived().compareTo(event2.getTimeReceived());
			if (sComp != 0) {
				return sComp;
			} else if (event.getRemoteKey() != null && event.getRemoteKey() != null) {
				return (event.getId().compareTo(event2.getId()));
			} else
				return event.getTimeReceived().compareTo(event2.getTimeReceived());

			//   return event.getRemoteKey().compareTo(event.getRemoteKey());

		}

	};

	private static final Comparator<RemoteEvent> timeHappenedComparator = new Comparator<RemoteEvent>() {
		// @Override
		//  public int compare(RemoteEvent event, RemoteEvent event2) {
		//    return event.getTime().compareTo(event2.getTime());
		// }
		@Override
		public int compare(RemoteEvent event, RemoteEvent event2) {
			// if (event.getRemoteKey() != null && event.getRemoteKey() != null)
			//   return event.getRemoteKey().compareTo(event.getRemoteKey());

			//else
			return (event.getTime().compareTo(event2.getTime()));

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
	private static CountDownLatch endLatch;

	private final Context context;
	private static final Duration timeStep = Duration.standardHours(24); // how many rows from the DB to gather in one batch
	private final boolean orderByTimeReceived;
	private final boolean useRandomData;
	private boolean replayBooks = true;
	private List<String> barIntervals = new ArrayList<String>();
	private boolean replayBars = false;
	private Instant barEnd;

}
