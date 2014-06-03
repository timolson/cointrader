package org.cryptocoinpartners.util;

import org.cryptocoinpartners.module.Esper;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.RemoteEvent;
import org.cryptocoinpartners.schema.Trade;
import org.joda.time.*;

import java.util.*;


/**
 Manages a Esper into which Trades and Books from the database are replayed.  The Esper time is also managed by this
 class as it advances through the events.
 */
public class Replay
{

    public static Replay all() {
        return during(new Interval(getEventsStart(),getEventsEnd()));
    }


    public static Replay since( Instant start )
    {
        return during(new Interval(start,getEventsEnd()));
    }


    public static Replay until( Instant end ) {
        return during(new Interval(getEventsStart(),end));
    }


    public static Replay between( Instant start, Instant end ) {
        return during(new Interval(start,end));
    }


    public static Replay during( Interval interval ) {
        return new Replay(interval);
    }


    public Replay( Interval replayTimeInterval ) {
        this.replayTimeInterval = replayTimeInterval;
        this.esper = new Esper(new EventTimeManager());
    }


    public Esper getEsper() { return esper; }


    /**
     queries the database for all Books and Trades which have start <= time <= stop, then publishes those
     Events in order of time to this Replay's Esper
     @param start
     @param stop
     */
    public void run() {
        final Instant start = replayTimeInterval.getStart().toInstant();
        final Instant end = replayTimeInterval.getEnd().toInstant();
        esper.advanceTime(start);
        if( replayTimeInterval.toDuration().isLongerThan(timeStep) ) {
            for( Instant now = start; !now.isAfter(end); ) {
                final Instant stepEnd = now.plus(timeStep);
                replayStep(now, stepEnd);
                now = stepEnd;
            }
        }
        else
            replayStep(start,end);
    }


    private void replayStep( Instant start, Instant stop )
    {
        for( RemoteEvent event : queryEvents(start, stop) )
            esper.publish(event);
        esper.advanceTime(stop);
    }


    private List<RemoteEvent> queryEvents( Instant start, Instant stop ) {
        final String tradeQuery = "select t from Trade t where time >= ?1 and time <= ?2";
        final String bookQuery = "select b from Book b where time >= ?1 and time <= ?2";
        final List<RemoteEvent> events = new ArrayList<RemoteEvent>();
        events.addAll(PersistUtil.queryList(Trade.class, tradeQuery, start, stop));
        events.addAll(PersistUtil.queryList(Book.class, bookQuery, start, stop));
        Collections.sort(events, timeComparator);
        return events;
    }
    
    
    private static Instant getEventsStart() {
        Instant bookStart = PersistUtil.queryOne(Instant.class,"select min(time) from Book");
        Instant tradeStart = PersistUtil.queryOne(Instant.class,"select min(time) from Trade");
        if( bookStart == null && tradeStart == null )
            return null;
        if( bookStart == null )
            return tradeStart;
        if( tradeStart == null )
            return bookStart;
        return tradeStart.isBefore(bookStart) ? tradeStart : bookStart;
    }


    private static Instant getEventsEnd() {
        // queries use max(time)+1 because the end of a range is exclusive, and we want to include the last event
        Instant bookEnd = PersistUtil.queryOne(Instant.class,"select max(time) from Book");
        Instant tradeEnd = PersistUtil.queryOne(Instant.class,"select max(time) from Trade");
        if( bookEnd == null && tradeEnd == null )
            return null;
        if( bookEnd == null )
            return tradeEnd;
        if( tradeEnd == null )
            return bookEnd;
        return tradeEnd.isAfter(bookEnd) ? tradeEnd : bookEnd;
    }


    private static final Comparator<RemoteEvent> timeReceivedComparator = new Comparator<RemoteEvent>()
    {
        public int compare( RemoteEvent event, RemoteEvent event2 )
        {
            return event.getTimeReceived().compareTo(event2.getTimeReceived());
        }
    };


    private static final Comparator<RemoteEvent> timeComparator = new Comparator<RemoteEvent>()
    {
        public int compare( RemoteEvent event, RemoteEvent event2 )
        {
            return event.getTime().compareTo(event2.getTime());
        }
    };


    public class EventTimeManager implements Esper.TimeProvider
    {

        public Instant getInitialTime()
        {
            return replayTimeInterval.getStart().toInstant();
        }


        public Instant nextTime( Event event )
        {
            return event.getTime();
        }
    }


    private final Interval replayTimeInterval;
    private final Esper esper;
    private static final Duration timeStep = Duration.standardMinutes(1); // how many rows from the DB to gather in one batch
}
