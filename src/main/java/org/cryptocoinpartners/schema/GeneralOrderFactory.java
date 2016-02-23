package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import org.cryptocoinpartners.enumeration.FillType;
import org.joda.time.Instant;

public interface GeneralOrderFactory {

    GeneralOrder create(Instant time, Portfolio portfolio, Market market, BigDecimal volume, FillType type);

    GeneralOrder create(Instant time, Portfolio portfolio, Listing listing, BigDecimal volume, FillType type);

    GeneralOrder create(Instant time, Portfolio portfolio, Order parentOrder, Market market, BigDecimal volume, FillType type);

    GeneralOrder create(Instant time, Fill parentFill, Market market, BigDecimal volume, FillType type);

    GeneralOrder create(Instant time, Portfolio portfolio, Listing listing, BigDecimal volume);

    GeneralOrder create(Instant time, Portfolio portfolio, Order parentOrder, Listing listing, BigDecimal volume);

    GeneralOrder create(Instant time, Portfolio portfolio, Listing listing, String volume);

}
