package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;

public interface TransactionFactory {

    //SpecificOrder create(Instant time, Market market, BigDecimal volume, String comment);
    //   generalOrder.getTime(), market, volume, generalOrder, generalOrder.getComment());
    Transaction create(Portfolio portfolio, Exchange exchange, Asset currency, TransactionType type, @Assisted("transactionAmount") Amount amount,
            @Assisted("transactionPrice") Amount price);

    Transaction create(Order order, Instant creationTime);

    Transaction create(Fill fill, Instant creationTime);

    Transaction create(Fill fill, Portfolio portfolio, Exchange exchange, Asset currency, TransactionType type, @Assisted("transactionAmount") Amount amount,
            @Assisted("transactionPrice") Amount price);

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
