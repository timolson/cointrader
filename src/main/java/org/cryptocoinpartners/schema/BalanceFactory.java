package org.cryptocoinpartners.schema;

public interface BalanceFactory {

    //SpecificOrder create(Instant time, Market market, BigDecimal volume, String comment);
    //   generalOrder.getTime(), market, volume, generalOrder, generalOrder.getComment());

    Balance create(Exchange exchange, Asset asset, long amountCount);

    Balance create(Exchange exchange, Asset asset);

    Balance create(Exchange exchange, Asset asset, long amountCount, String description);

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
