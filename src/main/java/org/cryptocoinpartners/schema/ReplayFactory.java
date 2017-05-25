package org.cryptocoinpartners.schema;

import java.util.concurrent.Semaphore;

import org.cryptocoinpartners.util.Replay;
import org.joda.time.Instant;
import org.joda.time.Interval;

import com.google.inject.assistedinject.Assisted;

public interface ReplayFactory {

    Replay all(boolean orderByTimeReceived);

    Replay all(boolean orderByTimeReceived, Semaphore semaphore);

    Replay since(@Assisted("startTime") Instant start, boolean orderByTimeReceived);

    Replay since(@Assisted("startTime") Instant start, boolean orderByTimeReceived, Semaphore semaphore);

    Replay until(@Assisted("endTime") Instant end, boolean orderByTimeReceived, @Assisted("until") boolean until);

    Replay until(@Assisted("endTime") Instant end, boolean orderByTimeReceived, Semaphore semaphore, @Assisted("until") boolean until);

    Replay between(@Assisted("startTime") Instant start, @Assisted("endTime") Instant end, boolean orderByTimeReceived);

    Replay between(@Assisted("startTime") Instant start, @Assisted("endTime") Instant end, @Assisted("orderByTimeReceived") boolean orderByTimeReceived,
            Semaphore semaphore, @Assisted("useRandomData") boolean useRandomData);

    Replay during(Interval interval, boolean orderByTimeReceived);

    Replay during(Interval interval, boolean orderByTimeReceived, Semaphore semaphore);

    Replay create(Interval replayTimeInterval, boolean orderByTimeReceived);

    Replay create(Interval replayTimeInterval, boolean orderByTimeReceived, Semaphore semaphore);

}
