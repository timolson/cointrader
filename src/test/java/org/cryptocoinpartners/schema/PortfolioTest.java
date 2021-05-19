package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import org.apache.commons.configuration.ConfigurationException;
import org.cryptocoinpartners.bin.Main.MainParamsOnly;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.Injector;
import org.joda.time.Instant;
import org.junit.Test;

public class PortfolioTest {

  // Replay replay = new Replay(false);

  //   Context context = Context.create(new EventTimeManager());
  /*
   * protected Injector injector = Guice.createInjector(new AbstractModule() {
   * @Override protected void configure() { bind(MockOrderService.class); } }); // @Before // public void setup() { // injector.injectMembers(this);
   * // }
   * @Inject BaseOrderService orderSerivce;
   */

  private Context context;

  // @Before
  public void setup() {
    MainParamsOnly mainParamsOnly = new MainParamsOnly();
    context = Context.create();
    context.setTimeProvider(null);
    try {
      ConfigUtil.init(mainParamsOnly.propertiesFilename, mainParamsOnly.definitions);
    } catch (ConfigurationException e) { // TODO Auto-generated catch block
      e.printStackTrace();
    }
    Injector rootInjector = Injector.root();
    rootInjector.getInstance(Portfolio.class);
  }

  @Test
  public final void test() {

    Exchange exchange = new Exchange("OKCOIN");
    Asset base = new Currency(false, "USDT", 0.01);
    Asset quote = new Currency(false, "BTC", 0.01);

    Listing listing = new Listing(base, quote);
    Market market = new Market(exchange, listing, 0.01, 0.01);
    // Market market = Market.forSymbol(marketSymbol);

    Portfolio portfolio = new Portfolio();
    portfolio.setBaseAsset(new Currency(false, "USD", 0.01));
    DiscreteAmount volumeDiscrete =
        new DiscreteAmount(
            DiscreteAmount.roundedCountForBasis(
                BigDecimal.valueOf(-0.97628217d), market.getPriceBasis()),
            market.getPriceBasis());

    SpecificOrder openOrder =
        new SpecificOrder(
            new Instant(System.currentTimeMillis() - 2000),
            portfolio,
            market,
            volumeDiscrete.getCount());
    openOrder.withPositionEffect(PositionEffect.OPEN);

    Fill openFill1 =
        new Fill(
            openOrder,
            openOrder.getTime(),
            openOrder.getTime(),
            openOrder.getMarket(),
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
            Long.toString(openOrder.getTime().getMillis()));
    System.out.println("fill1  " + openFill1);
    portfolio.merge(openFill1);
    Fill openFill2 =
        new Fill(
            openOrder,
            openOrder.getTime(),
            openOrder.getTime(),
            openOrder.getMarket(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(8039.9), market.getPriceBasis()),
                    market.getPriceBasis())
                .getCount(),
            new DiscreteAmount(
                    DiscreteAmount.roundedCountForBasis(
                        BigDecimal.valueOf(-0.00248708d), market.getVolumeBasis()),
                    market.getVolumeBasis())
                .getCount(),
            Long.toString(openOrder.getTime().getMillis()));
    System.out.println("fill2  " + openFill2);
    portfolio.merge(openFill2);
    Fill openFill3 =
        new Fill(
            openOrder,
            openOrder.getTime(),
            openOrder.getTime(),
            openOrder.getMarket(),
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
            Long.toString(openOrder.getTime().getMillis()));
    System.out.println("fill3  " + openFill3);
    portfolio.merge(openFill3);
  }
}
