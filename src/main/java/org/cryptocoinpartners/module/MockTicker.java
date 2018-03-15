package org.cryptocoinpartners.module;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FilenameUtils;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.BookFactory;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.MathUtil;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jep.Jep;

/**
 * @author Tim Olson
 */
@SuppressWarnings("FieldCanBeLocal")
public class MockTicker {

	// private final Instant end = new DateTime(2015, 11, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
	//

	// private final Instant start = new DateTime(2014, 01, 01, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();//

	@Inject
	public MockTicker(Context context, Configuration config, Instant start, Instant end, BookFactory bookFactory, QuoteService quotes) {
		this.context = context;
		this.quotes = quotes;
		List<Market> markets = new ArrayList<Market>();
		List marketSymbols = config.getList("randomticker.market");
		if (marketSymbols == null)
			throw new ConfigurationError("MockTicker must be configured with the \"randomticker.market\" property");

		for (Iterator<List> il = marketSymbols.iterator(); il.hasNext();) {
			Object marketElement = il.next();
			String marketStr = marketElement.toString().toUpperCase();

			//    String marketStr = config.getString("randomticker.market");
			Market market = (Market) Market.forSymbol(marketStr);
			if (market == null) {
				String[] exchangeAndLising = marketStr.toUpperCase().split(":");
				String upperMarket = exchangeAndLising[0].toUpperCase();
				Exchange exchange = Exchange.forSymbolOrCreate(upperMarket);
				if (exchange == null)
					throw new ConfigurationError("Could not find Exchange with symbol \"" + upperMarket + "\"");
				String upperListing = exchangeAndLising[1].toUpperCase();
				Listing listing = Listing.forSymbol(upperListing);
				market = Market.findOrCreate(exchange, listing);
				if (market == null)
					throw new ConfigurationError("MockTicker must be configured with the \"randomticker.market\" property");

			}
			markets.add(market);
		}

		long interval = config.getInt("randomticker.interval", 60);
		int trend = config.getInt("randomticker.trend", 172800);
		double high = config.getDouble("randomticker.high", 928.00);
		double low = config.getDouble("randomticker.low", 230.00);
		double noise = config.getDouble("randomticker.noise", 0.05);
		long secondsDuration = ((end.minus(start.getMillis())).getMillis() / 1000);
		int length = Math.round(secondsDuration / interval);
		double range = high - low;
		int trendLength = Math.round(trend / interval);

		double offset = low * (1 - noise) + (((high * (1 + noise)) - (low * (1 - noise))) / 2);
		if (config.getBoolean("randomticker", false))

			new Thread(new PoissonTickerThread(markets, start, length, trendLength, range, noise, offset, interval, bookFactory)).start();
		else
			throw new ConfigurationError("RandomTicker must be enabled with the  \"randomticker\" property");

	}

	public void stop() {
		running = false;
	}

	private double nextVolume(Market market) {
		// double meanVolume = averageVolumeCount;

		int volume = MathUtil.getPoissonRandom(averageVolumeCount);
		double volumeAsDouble = (market.getVolumeBasis() < 1) ? (Double.parseDouble("1.0") / volume) * averageVolumeCount
				: (Double.parseDouble("1.0") / volume) * averageVolumeCount * averageVolumeCount;
		DiscreteAmount volumeDiscrete = new DiscreteAmount((long) (volumeAsDouble / market.getVolumeBasis()), market.getVolumeBasis());
		volumeDiscrete.asDouble();
		return volumeDiscrete.asDouble();
	}

	private double nextPrice() {
		double delta = random.nextGaussian() * priceMovementStdDev;
		double multiple;
		if (delta < 0)
			multiple = 1 / (1 - delta);
		else
			multiple = 1 + delta;
		currentPrice *= multiple;
		return currentPrice;
	}

	private class PoissonTickerThread extends Thread {

		private final int length;
		private final double range;
		private final double volatility;
		private final int trendLength;
		private final double offset;
		private final long interval;
		private final Instant start;
		private int priceCount;
		private final BookFactory bookFactory;
		private final List<Market> markets;
		private final Map<Market, ArrayList<Double>> marketPrices = new HashMap<Market, ArrayList<Double>>();;

		@Override
		public void run() {

			log.debug("running mock ticker");
			running = true;
			// generate price data
			ClassLoader classLoader = getClass().getClassLoader();
			URL jepPath = classLoader.getResource("commonrandom.py");
			String file = jepPath.getFile();

			String path = jepPath.getPath();
			String baseUrl = "/" + FilenameUtils.getPath(path);
			//    .getFile();

			try (Jep jep = new Jep(false, baseUrl)) {
				jep.eval("from commonrandom import  generate_trendy_price");
				jep.eval("from commonrandom import  skew_returns_annualised");

				//   skew_returns_annualised
				// any of the following work, these are just pseudo-examples

				// using eval(String) to invoke methods
				jep.set("Nlength", length);
				jep.set("Tlength", trendLength);
				jep.set("Xamplitude", range);
				jep.set("Volscale", volatility);
				for (Market market : markets) {

					// jep.set("annualSR", annualSharpeRatio);
					//jep.set("want_skew", skew);
					//jep.set("size", length);

					//jep.set("Xamplitude", range);
					// jep.set("Volscale", volatility);

					jep.eval("ans=generate_trendy_price(Nlength, Tlength, Xamplitude, Volscale)");
					//jep.eval("ans=skew_returns_annualised(annualSR, want_skew, size)");
					ArrayList<Double> rawPrices = (ArrayList<Double>) jep.getValue("ans");
					priceCount = (priceCount < rawPrices.size()) ? rawPrices.size() : priceCount;

					marketPrices.put(market, rawPrices);
				}
			} catch (Error | Exception e) {
				//  String path= "java.library.path";

				log.error("unable to run PoissonTickerThread", e);

			}
			long tradeTime = 0;
			for (int index = 0; index < priceCount; index++) {
				tradeTime = (tradeTime == 0 ? start.getMillis() : tradeTime + (interval * 1000));

				for (Market market : markets) {
					double rawPrice = marketPrices.get(market).get(index);

					// we need to get same index value from all other markets

					//   if (tradeTime=)
					Book book = bookFactory.create(new Instant(tradeTime), market);
					// this needs to be aditive, we take the last book and add it
					int loopVal;
					int end_value = 20;
					for (loopVal = 1; loopVal < end_value; loopVal++) {
						book.addBid(BigDecimal.valueOf((rawPrice + offset) - (market.getPriceBasis() * loopVal)), BigDecimal.valueOf(nextVolume(market)));
						book.addAsk(BigDecimal.valueOf((rawPrice + offset) + (market.getPriceBasis() * loopVal)), BigDecimal.valueOf(nextVolume(market)));

					}
					book.build();
					//   context.publish(book);

					// log.info("publishing book " + book);
					Trade trade = Trade.fromDoubles(market, new Instant(tradeTime), null, (rawPrice + offset), nextVolume(market));
					// log.info("publishing trade " + trade);
					context.publish(book);
					context.publish(trade);
				}

				// double scaledPrice = rawPrice+offset;
			}

			// using getValue(String) to invoke methods

			// using runScript

			/*            while (running) {
			                try {
			                    double lambda = 1 / averageTimeBetweenTrades;
			                    double poissonSleep = -Math.log(1d - random.nextDouble()) / lambda;
			                    sleep((long) (1000 * poissonSleep));
			                } catch (InterruptedException e) {
			                    break;
			                }
			                if (!running)
			                    break;
			                Trade trade = Trade.fromDoubles(market, Instant.now(), null, nextPrice(), nextVolume());
			                context.publish(trade);
			            }*/
		}

		private PoissonTickerThread(List<Market> markets, Instant start, int length, int trendLength, double range, double volatility, double offset,
				long interval, BookFactory bookFactory) {
			setDaemon(true);
			this.markets = markets;

			this.length = length;
			this.range = range;
			this.volatility = volatility;
			this.trendLength = trendLength;
			this.offset = offset;
			this.interval = interval;
			this.start = start;
			this.bookFactory = bookFactory;

		}

	}

	private final double averageTimeBetweenTrades = 2;
	private final double priceMovementStdDev = 0.0001;
	private final double averageVolumeCount = 100;
	//  private final double volumeBasis = 1 / 1000.0;

	private final Random random = new Random();
	private double currentPrice = 100;
	private boolean running;

	private final Context context;
	protected final QuoteService quotes;
	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.mockTicker");

}
