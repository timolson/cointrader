package org.cryptocoinpartners.module;

import static org.junit.Assert.assertEquals;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.util.PromptsUtil;
import org.junit.Test;

public class PromptUtilTest {

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

		Market thisWeekMarket = createMarket("OKCOIN_THISWEEK", eos, usd, new Prompt("THISWEEK", 1, 0.01, btc, 1, 0.001, 20, FeeMethod.PercentagePerUnit,
				0.0002, 0.0003, FeeMethod.PercentagePerUnit, FeeMethod.PercentagePerUnit), 0.001, 1);

		Market nextWeekMarket = createMarket("OKCOIN_NEXTWEEK", btc, usd, new Prompt("NEXTWEEK", 1, 0.01, btc, 1, 0.01, 20, FeeMethod.PercentagePerUnit, 0.0002,
				0.0003, FeeMethod.PercentagePerUnit, FeeMethod.PercentagePerUnit), 0.01, 1);

		Market quaterlyMarket = createMarket("OKCOIN_QUARTER", btc, usd, new Prompt("QUARTER", 1, 0.01, btc, 1, 0.01, 20, FeeMethod.PercentagePerUnit, 0.0002,
				0.0003, FeeMethod.PercentagePerUnit, FeeMethod.PercentagePerUnit), 0.01, 1);

		Market cashMarket = createMarket("BITFINEX", btc, usd, 0.01, 1, 0.0002, 0.0003, FeeMethod.PercentagePerUnit, 0.01);
		long diff = 0L;
		//when all before 2 weeks out
		ZonedDateTime date = ZonedDateTime.parse("2019-09-10T21:28:30+00:00", DateTimeFormatter.ISO_DATE_TIME);
		ZonedDateTime cashExpiry = date;
		ZonedDateTime weeklyExpiry = ZonedDateTime.parse("2019-09-13T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		ZonedDateTime nextWeekExpiry = ZonedDateTime.parse("2019-09-20T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		ZonedDateTime quaterExpiry = ZonedDateTime.parse("2019-09-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		ZonedDateTime cashResult = PromptsUtil.getExpiryDate(cashMarket, date);
		ZonedDateTime weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		ZonedDateTime nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		ZonedDateTime quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);
		int offset = 0;
		//when 1st day of year
		date = ZonedDateTime.parse("2019-01-01T18:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-01-04T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-01-11T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		//when all within 2 weeks out
		date = ZonedDateTime.parse("2019-09-18T21:28:30+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-09-20T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-09-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-12-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		//when all within 1 week
		date = ZonedDateTime.parse("2019-09-25T21:28:30+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-09-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-10-04T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-12-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		//expiry today
		date = ZonedDateTime.parse("2019-09-27T21:28:30+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-10-04T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-10-11T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-12-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		date = ZonedDateTime.parse("2019-09-27T00:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-09-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-10-04T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-12-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		date = ZonedDateTime.parse("2019-09-27T00:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-09-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-10-04T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-12-27T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		offset = -1;
		//when 1st day of year
		date = ZonedDateTime.parse("2019-01-01T18:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-01-04T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-01-11T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		offset = -1;
		//when 1st day of year
		date = ZonedDateTime.parse("2019-03-22T00:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-03-22T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-06-28T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		offset = -1;
		//when 1st day of year
		date = ZonedDateTime.parse("2019-03-15T00:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-03-15T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-03-22T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		offset = -1;
		//when 1st day of year
		date = ZonedDateTime.parse("2019-03-16T00:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-03-22T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-06-28T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);

		offset = 0;
		//when 1st day of year
		date = ZonedDateTime.parse("2019-03-15T07:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-03-15T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-03-22T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		cashResult = PromptsUtil.getExpiryDate(cashMarket, date);
		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);
		diff = ChronoUnit.DAYS.between(cashResult.toLocalDate(), quaterlyResult.toLocalDate());

		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);
		assertEquals(14L, diff);

		date = ZonedDateTime.parse("2019-03-15T09:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-03-22T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-06-28T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);
		diff = ChronoUnit.DAYS.between(cashResult.toLocalDate(), quaterlyResult.toLocalDate());
		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);
		assertEquals(105L, diff);

		date = ZonedDateTime.parse("2019-03-15T18:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2019-03-22T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-06-28T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		cashResult = PromptsUtil.getExpiryDate(cashMarket, date);
		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);
		diff = ChronoUnit.DAYS.between(cashResult.toLocalDate(), quaterlyResult.toLocalDate());
		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);
		assertEquals(105L, diff);

		date = ZonedDateTime.parse("2018-12-13T12:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2018-12-14T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2018-12-21T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2018-12-28T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		cashResult = PromptsUtil.getExpiryDate(cashMarket, date);
		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);
		diff = ChronoUnit.DAYS.between(cashResult.toLocalDate(), quaterlyResult.toLocalDate());
		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);
		assertEquals(15L, diff);

		date = ZonedDateTime.parse("2018-12-14T07:59:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2018-12-14T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2018-12-21T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2018-12-28T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		cashResult = PromptsUtil.getExpiryDate(cashMarket, date);
		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);
		diff = ChronoUnit.DAYS.between(cashResult.toLocalDate(), quaterlyResult.toLocalDate());
		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);
		assertEquals(14L, diff);

		date = ZonedDateTime.parse("2018-12-14T08:01:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		weeklyExpiry = ZonedDateTime.parse("2018-12-21T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		nextWeekExpiry = ZonedDateTime.parse("2018-12-28T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);
		quaterExpiry = ZonedDateTime.parse("2019-03-29T08:00:00+00:00", DateTimeFormatter.ISO_DATE_TIME);

		cashResult = PromptsUtil.getExpiryDate(cashMarket, date);
		weeklyResult = PromptsUtil.getExpiryDate(thisWeekMarket, date);
		nextWeekResult = PromptsUtil.getExpiryDate(nextWeekMarket, date);
		quaterlyResult = PromptsUtil.getExpiryDate(quaterlyMarket, date);
		diff = ChronoUnit.DAYS.between(cashResult.toLocalDate(), quaterlyResult.toLocalDate());
		assertEquals(weeklyResult, weeklyExpiry);
		assertEquals(nextWeekResult, nextWeekExpiry);
		assertEquals(quaterlyResult, quaterExpiry);
		assertEquals(105L, diff);

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
