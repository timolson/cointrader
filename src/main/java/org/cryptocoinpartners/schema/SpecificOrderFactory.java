package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;

import org.joda.time.Instant;

public interface SpecificOrderFactory {

    //SpecificOrder create(Instant time, Market market, BigDecimal volume, String comment);
    //   generalOrder.getTime(), market, volume, generalOrder, generalOrder.getComment());
    SpecificOrder create(SpecificOrder specificOrder);

    //
    //    SpecificOrder create(Instant time, Market market, Amount volume, String comment);
    //

    SpecificOrder create(Instant time, Portfolio portfolio, Market market, Amount volume, Order parentOrder, @Nullable String comment);

    //
    //    SpecificOrder create(Instant time, Market market, long volumeCount);
    //
    //    SpecificOrder create(Instant time, Market market, long volumeCount, String comment);
    //
    //    SpecificOrder create(Instant time, Market market, long volumeCount, Order parentOrder, String comment);
    //
    //    SpecificOrder create(LimitOrder limitOrder, com.xeiam.xchange.Exchange xchangeExchange, Portfolio portfolio, Date date);
    //
    //    SpecificOrder create(Instant time, Portfolio portfolio, Market market, Amount volume, Order parentOrder, String comment);
    //
    //    SpecificOrder create(Instant time, Portfolio portfolio, Market market, BigDecimal volume, String comment);
    //
    //    SpecificOrder create(Instant time, Portfolio portfolio, Market market, double volume, String comment);

    SpecificOrder create(Instant time, Portfolio portfolio, Market market, Amount negate, @Nullable String string);

}
