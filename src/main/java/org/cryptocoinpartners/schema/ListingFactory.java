package org.cryptocoinpartners.schema;

import com.google.inject.assistedinject.Assisted;

public interface ListingFactory {

	Listing create(@Assisted("base") Asset base, @Assisted("quote") Asset quote);

	Listing create(@Assisted("base") Asset base, @Assisted("quote") Asset quote, Prompt prompt);

}
