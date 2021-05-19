package org.cryptocoinpartners.module;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.apache.commons.configuration.ConfigurationException;
import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.TargetStrategy;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.util.Injector;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.RemainderHandler;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;

public class BaseOrderServiceTest {
  // @Mock MockOrderService orderService;
  // @/Mock TradeFactory tradeFactory;

  static Injector rootInjector;
  @Inject Context context;

  @Before
  public void setup() {}

  public static void start(String[] args)
      throws ConfigurationException, IllegalAccessException, InstantiationException {}

  static class MainParams {}

  @Test
  public void testService() throws Exception {}

  @Test
  public final void setUpTest() {}

  // Replay replay = new Replay(false);

  //   Context context = Context.create(new EventTimeManager());
  /*
   * protected Injector injector = Guice.createInjector(new AbstractModule() {
   * @Override protected void configure() { bind(MockOrderService.class); } }); // @Before // public void setup() { // injector.injectMembers(this);
   * // }
   * @Inject BaseOrderService orderSerivce;
   */

  public class MyModule extends AbstractModule {
    @Override
    protected void configure() {
      // bind(OrderService.class).to(BaseOrderService.class);
    }
  }

  @Test
  public final void test() {}

  @Test
  public final void pairOrderPlacedOnFillTest() {
    Exchange exchange = new Exchange("OKCOIN_THISWEEK");
    Asset base = new Currency(false, "USD", 0.01);
    Asset quote = new Currency(false, "BTC", 0.01);

    Listing listing = new Listing(base, quote);
    Market market = new Market(exchange, listing, 0.01, 0.01);
    // Market market = Market.forSymbol(marketSymbol);
    GeneralOrder targetOrder1 =
        (new GeneralOrder(
            new Instant(System.currentTimeMillis() - 2000),
            new Portfolio(),
            market,
            BigDecimal.ONE,
            FillType.TRAILING_STOP_LOSS));
    // Injector injector = Guice.createInjector(new MyModule()); // modules are the next section
    // OrderService orderService = injector.getInstance(OrderService.class);

    /*    try {
      orderService.placeOrder(targetOrder1);
      Collection<Order> pendingOrders = orderService.getPendingOrders();
    } catch (Throwable e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }*/
  }

  @Test
  public final void POVTest() {
    Exchange exchange = new Exchange("OKCOIN_THISWEEK");
    Asset base = new Currency(false, "USD", 0.01);
    Asset quote = new Currency(false, "LTC", 0.00000001);
    Prompt prompt =
        new Prompt(
            "QUARTER",
            1d,
            0.1,
            quote,
            1d,
            0.001,
            20,
            FeeMethod.PercentagePerUnit,
            0.003,
            0.003,
            FeeMethod.PercentagePerUnit,
            FeeMethod.PercentagePerUnit);

    final RemainderHandler buyHandler =
        new RemainderHandler() {
          @Override
          public RoundingMode getRoundingMode() {
            return RoundingMode.FLOOR;
          }
        };

    final RemainderHandler sellHandler =
        new RemainderHandler() {
          @Override
          public RoundingMode getRoundingMode() {
            return RoundingMode.CEILING;
          }
        };

    Listing listing = new Listing(base, quote, prompt);
    Market market = new Market(exchange, listing, 0.001, 1);
    // Market market = Market.forSymbol(marketSymbol);
    GeneralOrder generalOrder =
        (new GeneralOrder(
            new Instant(System.currentTimeMillis() - 2000),
            new Portfolio(),
            market,
            BigDecimal.ONE.negate(),
            FillType.LIMIT));
    DiscreteAmount discreteLimitPrice =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(BigDecimal.valueOf(136.07), market.getPriceBasis()),
            market.getPriceBasis());

    generalOrder
        .withLimitPrice(discreteLimitPrice)
        .withPercentageOfVolume(0.0001)
        .withPercentageOfVolumeInterval(86400.0)
        .withTargetStrategy(TargetStrategy.PERCENTAGEOFVOLUME);

    Bar bar =
        new Bar(
            new Instant(System.currentTimeMillis() - 2000),
            new Instant(System.currentTimeMillis() - 2000),
            "bar",
            86400d,
            70.126d,
            72.845d,
            75.233d,
            69.635d,
            648014.0,
            28157d,
            -28157d,
            market);
    //  com.google.inject.Injector injector =
    //    Guice.createInjector(new MyModule()); // modules are the next section
    // BaseOrderService orderService = injector.getInstance(BaseOrderService.class);

    DiscreteAmount volume = BaseOrderService.getPercentageOfVolume(generalOrder, market, bar);
    // assertEquals(volume, discreteLimitPrice);
  }

  @Test
  public final void outstandingVolumeTest() {
    double ratioQuantity = -7.5000459;
    DecimalAmount workngLinkedVolume = DecimalAmount.ZERO;
    DecimalAmount filledLinkedVolume = DecimalAmount.ZERO;

    Exchange exchange = new Exchange("OKCOIN_SWAP");
    Asset base = new Currency(false, "USD", 0.01);
    Asset quote = new Currency(false, "LTC", 0.00000001);
    Prompt prompt =
        new Prompt(
            "SWAP",
            1d,
            0.1,
            quote,
            1d,
            0.001,
            20,
            FeeMethod.PercentagePerUnit,
            0.003,
            0.003,
            FeeMethod.PercentagePerUnit,
            FeeMethod.PercentagePerUnit);
    Listing listing = new Listing(base, quote, prompt);
    Market pairMarket = new Market(exchange, listing, 0.001, 1);
    long filledVolumeCount =
        DiscreteAmount.roundedCountForBasis(BigDecimal.valueOf(0.0749), 0.00000001);
    DiscreteAmount filledVolume = new DiscreteAmount(filledVolumeCount, 0.00000001);
    DecimalAmount outstandingFilled =
        filledVolume.times(BigDecimal.valueOf(ratioQuantity), Remainder.ROUND_UP);
    DiscreteAmount outstandingVolume =
        ((outstandingFilled).minus(workngLinkedVolume).minus(filledLinkedVolume))
            .toBasis(pairMarket.getVolumeBasis(), Remainder.ROUND_DOWN);
  }

  @Test
  public final void orderConverstionTest() {
    Exchange exchange = new Exchange("OKCOIN_THISWEEK");
    Asset base = new Currency(false, "USD", 0.01);
    Asset quote = new Currency(false, "LTC", 0.00000001);

    final RemainderHandler buyHandler =
        new RemainderHandler() {
          @Override
          public RoundingMode getRoundingMode() {
            return RoundingMode.FLOOR;
          }
        };

    final RemainderHandler sellHandler =
        new RemainderHandler() {
          @Override
          public RoundingMode getRoundingMode() {
            return RoundingMode.CEILING;
          }
        };

    Listing listing = new Listing(base, quote);
    Market market = new Market(exchange, listing, 0.001, 1);
    // Market market = Market.forSymbol(marketSymbol);
    GeneralOrder generalOrder =
        (new GeneralOrder(
            new Instant(System.currentTimeMillis() - 2000),
            new Portfolio(),
            market,
            BigDecimal.ONE.negate(),
            FillType.LIMIT));
    DiscreteAmount discreteLimitPrice =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(BigDecimal.valueOf(136.07), market.getPriceBasis()),
            market.getPriceBasis());

    generalOrder.withLimitPrice(discreteLimitPrice);
    DiscreteAmount volumeDiscrete =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(BigDecimal.valueOf(-1), market.getPriceBasis()),
            market.getPriceBasis());

    SpecificOrder specificOrder =
        new SpecificOrder(
            new Instant(System.currentTimeMillis() - 2000),
            new Portfolio(),
            market,
            volumeDiscrete.getCount());
    // limitPrice = generalOrder.getLimitPrice();

    DecimalAmount limitPrice =
        DecimalAmount.of(
            specificOrder.getLegNumber() == 1
                ? generalOrder.getLimitPrice().plus(specificOrder.getDifferentialPrice())
                : generalOrder.getLimitPrice().minus(specificOrder.getDifferentialPrice()));

    DiscreteAmount discreteLimit = limitPrice.toBasis(market.getPriceBasis(), buyHandler);
    specificOrder.withLimitPrice(discreteLimit);
    assertEquals(specificOrder.getLimitPrice(), discreteLimitPrice);
  }
}
