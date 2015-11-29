package org.cryptocoinpartners.schema;

import com.google.inject.assistedinject.Assisted;

public interface CurrencyFactory {

    Currency create(boolean fiat, String symbol, double basis);

    Currency create(boolean fiat, String symbol, @Assisted("basis") double basis, @Assisted("multiplier") double multiplier);

}
