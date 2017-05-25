package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.enumeration.OrderState;
import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface OrderUpdateFactory {

    //SpecificOrder create(Instant time, Market market, BigDecimal volume, String comment);
    //   generalOrder.getTime(), market, volume, generalOrder, generalOrder.getComment());
    OrderUpdate create(Instant time, Order order, @Assisted("orderUpdateLastState") OrderState lastState, @Assisted("orderUpdateState") OrderState state);

    //
    //    SpecificOrder create(Instant time, Market market, Amount volume, String comment);
    //

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
