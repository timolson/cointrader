package org.cryptocoinpartners.module;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.Transaction;
import org.joda.time.Instant;
import org.junit.Test;

public class FeeUtilTest {

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

		Market eosMarket = createMarket("OKCOIN_THISWEEK", eos, usd, new Prompt("THIS_WEEK", 1, 0.01, eos, 1, 0.001, 20, FeeMethod.PercentagePerUnit, 0.0002,
				0.0003, FeeMethod.PercentagePerUnit, FeeMethod.PercentagePerUnit), 0.001, 1);

		Market btcMarket = createMarket("OKCOIN_THISWEEK", btc, usd, new Prompt("THIS_WEEK", 1, 0.01, btc, 1, 0.01, 20, FeeMethod.PercentagePerUnit, 0.0002,
				0.0003, FeeMethod.PercentagePerUnit, FeeMethod.PercentagePerUnit), 0.01, 1);

		Market eosCashMarket = createMarket("BITFINEX", eos, usd, 0.01, 0.00000001, 0.002, 0.003, FeeMethod.PercentagePerUnit, 0.03);
		eosCashMarket.getExchange().setMargin(3);
		Market btcCashMarket = createMarket("BITFINEX", btc, usd, 0.01, 0.00000001, 0.002, 0.003, FeeMethod.PercentagePerUnit, 0.03);
		btcCashMarket.getExchange().setMargin(3);
		Market eosbtcCashMarket = createMarket("BITFINEX", eos, btc, 0.00000001, 0.00000001, 0.002, 0.003, FeeMethod.PercentagePerUnit, 0.03);
		eosbtcCashMarket.getExchange().setMargin(3);
		// Market market = Market.forSymbol(marketSymbol);
		SpecificOrder eosTestOrder = (new SpecificOrder(new Instant(System.currentTimeMillis() - 2000), new Portfolio(), eosMarket, BigDecimal.valueOf(67),
				"test order 1"));
		eosTestOrder.withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(5.310));
		Fill eosTestFill = new Fill(eosTestOrder, new Instant(System.currentTimeMillis() - 2000), new Instant(System.currentTimeMillis() - 2000), eosMarket,
				eosTestOrder.getLimitPriceCount(), eosTestOrder.getOpenVolumeCount(), "test");
		BigDecimal eosTragetCommsbd = BigDecimal.valueOf(0.02523541);
		DecimalAmount eosTragetComms = new DecimalAmount(eosTragetCommsbd).negate();
		BigDecimal eosTragetMarginbd = BigDecimal.valueOf(6.30885123);
		DecimalAmount eosTragetMargin = new DecimalAmount(eosTragetMarginbd).negate();
		Transaction eostransaction = new Transaction(eosTestFill, eosTestFill.getTime());

		SpecificOrder btcTestOrder = (new SpecificOrder(new Instant(System.currentTimeMillis() - 2000), new Portfolio(), btcMarket, BigDecimal.valueOf(13),
				"test order 1"));
		btcTestOrder.withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(6320.05));
		Fill btcTestFill = new Fill(btcTestOrder, new Instant(System.currentTimeMillis() - 2000), new Instant(System.currentTimeMillis() - 2000), btcMarket,
				btcTestOrder.getLimitPriceCount(), btcTestOrder.getOpenVolumeCount(), "test");
		BigDecimal btcTragetCommsbd = BigDecimal.valueOf(0.00004114);
		DecimalAmount btcTragetComms = new DecimalAmount(btcTragetCommsbd).negate();
		BigDecimal btcTragetMarginbd = BigDecimal.valueOf(0.01028473);
		DecimalAmount btcTragetMargin = new DecimalAmount(btcTragetMarginbd).negate();
		Transaction btctransaction = new Transaction(btcTestFill, btcTestFill.getTime());

		SpecificOrder btcCashTestOrder = (new SpecificOrder(new Instant(System.currentTimeMillis() - 2000), new Portfolio(), btcCashMarket,
				BigDecimal.valueOf(13), "test order 1"));
		btcCashTestOrder.withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER)
				.withLimitPrice(BigDecimal.valueOf(6320.05));
		Fill btcCashTestFill = new Fill(btcCashTestOrder, new Instant(System.currentTimeMillis() - 2000), new Instant(System.currentTimeMillis() - 2000),
				btcCashMarket, btcCashTestOrder.getLimitPriceCount(), btcCashTestOrder.getOpenVolumeCount(), "test");
		BigDecimal btcCashTragetCommsbd = BigDecimal.valueOf(164.33);
		DecimalAmount btcCashTragetComms = new DecimalAmount(btcCashTragetCommsbd).negate();
		BigDecimal btcCashTragetMarginbd = BigDecimal.valueOf(27386.89);
		DecimalAmount btcCashTragetMargin = new DecimalAmount(btcCashTragetMarginbd).negate();
		Transaction btctCashransaction = new Transaction(btcCashTestFill, btcCashTestFill.getTime());

		SpecificOrder eosbtcCashTestOrder = (new SpecificOrder(new Instant(System.currentTimeMillis() - 2000), new Portfolio(), eosbtcCashMarket,
				BigDecimal.valueOf(13), "test order 1"));
		eosbtcCashTestOrder.withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER)
				.withLimitPrice(BigDecimal.valueOf(0.000925));
		Fill eosbtcCashTestFill = new Fill(eosbtcCashTestOrder, new Instant(System.currentTimeMillis() - 2000), new Instant(System.currentTimeMillis() - 2000),
				eosbtcCashMarket, eosbtcCashTestOrder.getLimitPriceCount(), eosbtcCashTestOrder.getOpenVolumeCount(), "test");
		BigDecimal eosbtcCashTragetCommsbd = BigDecimal.valueOf(0.00002405);
		DecimalAmount eosbtcCashTragetComms = new DecimalAmount(eosbtcCashTragetCommsbd).negate();
		BigDecimal eosbtcCashTragetMarginbd = BigDecimal.valueOf(0.00400834);
		DecimalAmount eosbtcCashTragetMargin = new DecimalAmount(eosbtcCashTragetMarginbd).negate();
		Transaction eosbtctCashransaction = new Transaction(eosbtcCashTestFill, eosbtcCashTestFill.getTime());

		assertEquals(eostransaction.getCommission(), eosTragetComms);
		assertEquals(eostransaction.getCommissionDecimal(), eosTragetCommsbd.negate());
		assertEquals(eostransaction.getMargin(), eosTragetMargin);
		assertEquals(eostransaction.getMarginDecimal(), eosTragetMarginbd.negate());
		assertEquals(eostransaction.getCommissionCurrency(), eos);

		assertEquals(btctransaction.getCommission(), btcTragetComms);
		assertEquals(btctransaction.getCommissionDecimal(), btcTragetCommsbd.negate());
		assertEquals(btctransaction.getMargin(), btcTragetMargin);
		assertEquals(btctransaction.getMarginDecimal(), btcTragetMarginbd.negate());
		assertEquals(btctransaction.getCommissionCurrency(), btc);

		assertEquals(btctCashransaction.getCommission(), btcCashTragetComms);
		assertEquals(btctCashransaction.getCommissionDecimal(), btcCashTragetCommsbd.negate());
		assertEquals(btctCashransaction.getMargin(), btcCashTragetMargin);
		assertEquals(btctCashransaction.getMarginDecimal(), btcCashTragetMarginbd.negate());
		assertEquals(btctCashransaction.getCommissionCurrency(), usd);

		assertEquals(eosbtctCashransaction.getCommission(), eosbtcCashTragetComms);
		assertEquals(eosbtctCashransaction.getCommissionDecimal(), eosbtcCashTragetCommsbd.negate());
		assertEquals(eosbtctCashransaction.getMargin(), eosbtcCashTragetMargin);
		assertEquals(eosbtctCashransaction.getMarginDecimal(), eosbtcCashTragetMarginbd.negate());
		assertEquals(eosbtctCashransaction.getCommissionCurrency(), btc);

	}

	private Market createMarket(String exchangestr, Asset base, Asset quote, Prompt prompt, double priceBasis, double volumeBasis) {
		Exchange exchange = new Exchange(exchangestr);
		Listing listing = new Listing(base, quote, prompt);
		Market market = new Market(exchange, listing, priceBasis, volumeBasis);
		return market;

	}

	private Market createMarket(String exchangestr, Asset base, Asset quote, double priceBasis, double volumeBasis, double makerFeeRate, double takerFeeRate,
			FeeMethod feeMethod, double marginFeeRate) {

		Exchange exchange = new Exchange(exchangestr, 3, makerFeeRate, takerFeeRate, priceBasis, volumeBasis, feeMethod, marginFeeRate, feeMethod, volumeBasis,
				false);

		Listing listing = new Listing(base, quote);
		Market market = new Market(exchange, listing, priceBasis, volumeBasis);
		return market;

	}

}
