package org.cryptocoinpartners.module;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.util.Remainder;
import org.junit.Test;

public class DiscreteAmountTest {

  // Replay replay = new Replay(false);

  //   Context context = Context.create(new EventTimeManager());
  /*
   * protected Injector injector = Guice.createInjector(new AbstractModule() {
   * @Override protected void configure() { bind(MockOrderService.class); } }); // @Before // public void setup() { // injector.injectMembers(this);
   * // }
   * @Inject BaseOrderService orderSerivce;
   */
  @Test
  public final void test() {

    Exchange exchange = new Exchange("OKCOIN_THISWEEK");
    exchange.setFeeMethod(FeeMethod.PercentagePerUnit);
    Asset usd = new Currency(false, "USD", 0.01);
    Asset eos = new Currency(false, "EOS", 0.00000001);
    Asset btc = new Currency(false, "BTC", 0.00000001);

    Market eosMarket =
        createMarket(
            "OKCOIN_THISWEEK",
            eos,
            usd,
            new Prompt(
                "THIS_WEEK",
                1,
                0.01,
                eos,
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

    Market btcMarket =
        createMarket(
            "OKCOIN_THISWEEK",
            btc,
            usd,
            new Prompt(
                "THIS_WEEK",
                1,
                0.01,
                btc,
                1,
                0.01,
                20,
                FeeMethod.PercentagePerUnit,
                0.0002,
                0.0003,
                FeeMethod.PercentagePerUnit,
                FeeMethod.PercentagePerUnit),
            0.01,
            1);

    double ratioQuantity = -0.3277311;
    DiscreteAmount filledAmount = new DiscreteAmount(-119, btcMarket.getVolumeBasis());
    DiscreteAmount ratioQuantityDiscrete = new DiscreteAmount((long) -3277311, 0.0000001);

    // 39.0000009
    Amount negativeRoundCeling =
        filledAmount
            .times(ratioQuantity, Remainder.ROUND_CEILING)
            .toBasis(btcMarket.getVolumeBasis(), Remainder.ROUND_CEILING);
    // expected 40

    Amount negativeRoundFloor =
        filledAmount
            .times(ratioQuantity, Remainder.ROUND_FLOOR)
            .toBasis(btcMarket.getVolumeBasis(), Remainder.ROUND_FLOOR);
    /// expected 39

    filledAmount = new DiscreteAmount(119, btcMarket.getVolumeBasis());
    // -39.0000009
    Amount positiveRoundCeling =
        filledAmount
            .times(ratioQuantity, Remainder.ROUND_CEILING)
            .toBasis(btcMarket.getVolumeBasis(), Remainder.ROUND_CEILING);
    /// expected -39

    Amount positiveRoundFloor =
        filledAmount
            .times(ratioQuantity, Remainder.ROUND_FLOOR)
            .toBasis(btcMarket.getVolumeBasis(), Remainder.ROUND_FLOOR);
    /// expected -40

    System.out.println("test");
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

  private Market createMarket(
      String exchangestr,
      Asset base,
      Asset quote,
      double priceBasis,
      double volumeBasis,
      double makerFeeRate,
      double takerFeeRate,
      FeeMethod feeMethod,
      double marginFeeRate) {

    Exchange exchange =
        new Exchange(
            exchangestr,
            3,
            makerFeeRate,
            takerFeeRate,
            priceBasis,
            volumeBasis,
            feeMethod,
            marginFeeRate,
            feeMethod,
            volumeBasis,
            false);

    Listing listing = new Listing(base, quote);
    Market market = new Market(exchange, listing, priceBasis, volumeBasis);
    return market;
  }
}
