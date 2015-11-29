package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface BookFactory {

    Book create(Instant time, Market market);

    Book create(Instant time, String remoteKey, Market market);

    Book create(@Assisted("bookTime") Instant time, @Assisted("bookTimeReceived") Instant timeReceived, String remoteKey, Market market);

}
