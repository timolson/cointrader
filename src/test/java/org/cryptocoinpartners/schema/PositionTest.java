package org.cryptocoinpartners.schema;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.Instant;
import org.junit.Test;

import jline.internal.Log;

/** This is not yet a real JUnit test case */
public class PositionTest {

  @Test
  public final void test() {
    Exchange exchange = new Exchange("OKCOIN");
    Asset base = new Currency(false, "BTC", 0.00000001);
    Asset quote = new Currency(false, "USDT", 0.01);

    Listing listing = new Listing(base, quote);
    Market market = new Market(exchange, listing, 0.01, 0.01);
    // Market market = Market.forSymbol(marketSymbol);

    DiscreteAmount volumeDiscrete =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(
                BigDecimal.valueOf(-0.97628217d), market.getVolumeBasis()),
            market.getVolumeBasis());

    SpecificOrder order =
        new SpecificOrder(
            new Instant(System.currentTimeMillis() - 2000),
            new Portfolio(),
            market,
            volumeDiscrete.getCount());

    Fill fill1 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(8040.4d), market.getPriceBasis()),
                    market.getPriceBasis())
                .getCount(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(-0.19362078d), market.getVolumeBasis()),
                    market.getVolumeBasis())
                .getCount(),
            Long.toString(order.getTime().getMillis()));
    System.out.println("fill1  " + fill1);
    Fill fill2 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(8039.9d), market.getPriceBasis()),
                    market.getPriceBasis())
                .getCount(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(-0.00248708d), market.getVolumeBasis()),
                    market.getVolumeBasis())
                .getCount(),
            Long.toString(order.getTime().getMillis()));
    System.out.println("fill2  " + fill2);
    Fill fill3 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(8039.6d), market.getPriceBasis()),
                    market.getPriceBasis())
                .getCount(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(-0.78017431d), market.getVolumeBasis()),
                    market.getVolumeBasis())
                .getCount(),
            Long.toString(order.getTime().getMillis()));
    System.out.println("fill3  " + fill3);
    Position position = new Position(fill1, fill1.getMarket());
    assertEquals(position.getShortAvgPrice(), fill1.getPrice());
    assertEquals(position.getShortVolume(), fill1.getVolume());
    assertEquals(position.getOpenVolume(), fill1.getVolume());
    assertEquals(
        position.getLongVolume(),
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(BigDecimal.ZERO, market.getVolumeBasis()),
            market.getVolumeBasis()));
    assertEquals(position.getLongAvgPrice(), DecimalAmount.ZERO);
    position.addFill(fill2);

    System.out.println("position child " + position);
  }

  @Test
  public final void test2() {
    Exchange exchange = new Exchange("OKCOIN");
    Asset base = new Currency(false, "BTC", 0.00000001);
    Asset quote = new Currency(false, "USDT", 0.00000001);

    Listing listing = new Listing(base, quote);
    Market market = new Market(exchange, listing, 0.00000001, 0.00000001);
    // Market market = Market.forSymbol(marketSymbol);

    DiscreteAmount volumeDiscrete =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(
                BigDecimal.valueOf(-0.97628217d), market.getVolumeBasis()),
            market.getVolumeBasis());

    SpecificOrder order =
        new SpecificOrder(
            new Instant(System.currentTimeMillis() - 2000),
            new Portfolio(),
            market,
            volumeDiscrete.getCount());

    Fill fill1 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            Long.valueOf("804040000000"),
            Long.valueOf("-19362078"),
            Long.toString(order.getTime().getMillis()));
    System.out.println("fill1  " + fill1);
    Fill fill2 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            Long.valueOf("803990000000"),
            Long.valueOf("-19610786"),
            Long.toString(order.getTime().getMillis()));
    System.out.println("fill2  " + fill2);
    Fill fill3 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            Long.valueOf("803960000000"),
            Long.valueOf("-97628220"),
            Long.toString(order.getTime().getMillis()));

    System.out.println("fill3  " + fill3);
    Position position = new Position(fill1, fill1.getMarket());
    System.out.println("position child " + position);
    assertEquals(position.getShortAvgPrice(), fill1.getPrice());
    assertEquals(position.getShortVolume(), fill1.getVolume());
    assertEquals(position.getOpenVolume(), fill1.getVolume());
    assertEquals(
        position.getLongVolume(),
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(BigDecimal.ZERO, market.getVolumeBasis()),
            market.getVolumeBasis()));
    assertEquals(position.getLongAvgPrice(), DecimalAmount.ZERO);
    position.addFill(fill2);
    System.out.println("position child " + position);
    position.addFill(fill3);
    System.out.println("position child " + position);
  }

  @Test
  public final void fromBookTest() {
    Exchange exchange = new Exchange("OKCOIN");
    Asset base = new Currency(false, "BTC", 0.00000001);
    Asset quote = new Currency(false, "USDT", 0.01);

    Listing listing = new Listing(base, quote);
    Market market = new Market(exchange, listing, 0.01, 0.01);
    // Market market = Market.forSymbol(marketSymbol);
    Book.Builder b = new Book.Builder();
    b.start(Instant.now(), null, market);
    b.addBid(new BigDecimal("8039.9"), new BigDecimal("0.00248708"));

    Book book1 = b.build();

    b.start(Instant.now(), null, market);
    b.addBid(new BigDecimal("2.1"), new BigDecimal("1.04"));
    b.addBid(new BigDecimal("2.2"), new BigDecimal("1.03"));
    Book book2 = b.build();

    b.start(Instant.now(), null, market);
    b.addBid(new BigDecimal("2.1"), new BigDecimal("1.04"));
    b.addBid(new BigDecimal("2.2"), new BigDecimal("1.03"));
    Book book3 = b.build();

    DiscreteAmount volumeDiscrete =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(
                BigDecimal.valueOf(-0.97628217d), market.getVolumeBasis()),
            market.getVolumeBasis());

    SpecificOrder order =
        new SpecificOrder(
            new Instant(System.currentTimeMillis() - 2000),
            new Portfolio(),
            market,
            volumeDiscrete.getCount());

    Fill fill1 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            book1.getBestBid().getPriceCount(),
            book1.getBestBid().getVolumeCount(),
            Long.toString(order.getTime().getMillis()));
    System.out.println("fill1  " + fill1);
    Fill fill2 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            Long.valueOf(803999),
            Long.valueOf(-2487088),
            Long.toString(order.getTime().getMillis()));
    System.out.println("fill2  " + fill2);
    Fill fill3 =
        new Fill(
            order,
            order.getTime(),
            order.getTime(),
            order.getMarket(),
            Long.valueOf(803933),
            Long.valueOf(-333333),
            Long.toString(order.getTime().getMillis()));

    System.out.println("fill3  " + fill3);
    Position position = new Position(fill1, fill1.getMarket());
    //	assertEquals(position.getShortAvgPrice(), fill1.getPrice());
    //	assertEquals(position.getShortVolume(), fill1.getVolume());
    //	assertEquals(position.getOpenVolume(), fill1.getVolume());
    //	assertEquals(position.getLongVolume(),
    //			new DiscreteAmount(DiscreteAmount.roundedCountForBasis(BigDecimal.ZERO,
    // market.getVolumeBasis()), market.getVolumeBasis()));
    //	assertEquals(position.getLongAvgPrice(), DecimalAmount.ZERO);
    position.addFill(fill2);
    position.addFill(fill3);
    System.out.println("position child " + position);
  }

  private Market createMarket(
      String exchangestr,
      Asset base,
      Asset quote,
      Prompt prompt,
      double priceBasis,
      double volumeBasis) {
    Exchange exchange = new Exchange(exchangestr);
    Listing listing = new Listing(base, quote, prompt);
    Market market = new Market(exchange, listing, priceBasis, volumeBasis);
    return market;
  }

  @Test
  public final void dpTest() {

    Exchange exchange = new Exchange("OKCOIN_QUARTER");
    exchange.setFeeMethod(FeeMethod.PercentagePerUnit);
    Asset usd = new Currency(false, "USD", 0.01);
    Asset ltc = new Currency(false, "LTC", 0.00000001);
    Asset btc = new Currency(false, "BTC", 0.00000001);

    Market market =
        createMarket(
            "OKCOIN_QUARTER",
            ltc,
            usd,
            new Prompt(
                "QUARTER",
                1,
                0.01,
                ltc,
                1,
                0.001,
                20,
                FeeMethod.PercentagePerUnit,
                0.0002,
                0.0003,
                FeeMethod.PercentagePerUnit,
                FeeMethod.PercentagePerUnit),
            0.001,
            1);

    DiscreteAmount price =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(
                BigDecimal.valueOf(45.173d), market.getPriceBasis()),
            market.getPriceBasis());
    double contractSize = (market.getContractSize(market));

    double increment = 1.0 / price.getBasis();
    Amount multiplier = market.getMultiplier(market, price, price.increment((long) increment));

    Amount dollarsPerPoint =
        multiplier.times(contractSize, Remainder.ROUND_EVEN).times(price, Remainder.ROUND_EVEN);
    Log.debug(dollarsPerPoint);
  }

  @Test
  public final void test4() {
    int mod = 225 % 20;

    System.out.println(mod);

    int mod1 = 245 % 20;

    System.out.println(mod);

    int mod3 = 220 % 20;
    System.out.println(mod3);
  }
}
