package org.cryptocoinpartners.schema;

import java.util.Collection;

public interface PositionFactory {

    //SpecificOrder create(Instant time, Market market, BigDecimal volume, String comment);
    //   generalOrder.getTime(), market, volume, generalOrder, generalOrder.getComment());

    Position create(Collection<Fill> fills, Market market);

    Position create(Fill fill, Market market);

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

}
