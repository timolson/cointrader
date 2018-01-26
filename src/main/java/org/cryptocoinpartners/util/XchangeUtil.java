package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.CombinedConfiguration;
import org.cryptocoinpartners.module.xchange.XchangeData.Helper;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;

import com.google.common.collect.HashBiMap;

/**
 * @author Tim Olson
 */
public class XchangeUtil {

	public static Exchange getExchangeForTag(String tag) {
		return Exchange.forSymbolOrCreate(tag.toUpperCase());
	}

	public static Set<String> getExchangeTags() {

		return exchangeTags;
	}

	public static org.knowm.xchange.Exchange getExchangeForMarket(Exchange coinTraderExchange) {
		org.knowm.xchange.Exchange xchangeExchange = exchangesByMarket.get(coinTraderExchange);
		if (xchangeExchange == null)
			throw new Error("Could not get XChange Exchange for Coin Trader Exchange " + coinTraderExchange);
		return xchangeExchange;
	}

	public static Exchange getExchangeForMarket(org.knowm.xchange.Exchange xeiamExchange) {
		Exchange coinTraderExchange = exchangesByMarket.inverse().get(xeiamExchange);
		if (coinTraderExchange == null)
			throw new Error("Could not get XChange Exchange for Coin Trader Exchange " + coinTraderExchange);
		return coinTraderExchange;

	}

	public static Helper getHelperForExchange(Exchange exchnage) {
		return exchangeHelpers.get(exchnage);

	}

	public static void addHelperForExchange(Exchange exchnage, Helper helper) {
		exchangeHelpers.put(exchnage, helper);

	}

	public static org.knowm.xchange.Exchange resetExchange(Exchange coinTraderExchange) throws InterruptedException {
		ExchangeSpecification spec = getExchangeForMarket(coinTraderExchange).getExchangeSpecification();

		if (spec != null) {
			//Required for nonces to sync
			try {
				Thread.sleep(1000);

				org.knowm.xchange.Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
				//Required for nonces to sync
				// Thread.sleep(1000);
				// exchange.getNonceFactory().createValue();
				exchangesByMarket.put(coinTraderExchange, exchange);
				return exchange;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				throw e;
			}
		} else
			throw new Error("Could not get XChange Exchange for Coin Trader Exchange " + coinTraderExchange);

	}

	public static CurrencyPair getCurrencyPairForListing(Listing listing) {
		return new CurrencyPair(listing.getBase().getSymbol(), listing.getQuote().getSymbol());
	}

	private static HashBiMap<Exchange, org.knowm.xchange.Exchange> exchangesByMarket;
	private final static HashMap<Exchange, Helper> exchangeHelpers = new HashMap<Exchange, Helper>();

	private static Set<String> exchangeTags;

	static {
		// find all the config keys starting with "xchange." and collect their second groups after the dot
		final String configPrefix = "xchange";
		CombinedConfiguration config = ConfigUtil.combined();
		final Iterator xchangeConfigKeys = config.getKeys(configPrefix);
		exchangeTags = new HashSet<>();
		final Pattern configPattern = Pattern.compile(configPrefix + "\\.([^\\.]+)\\..+");
		while (xchangeConfigKeys.hasNext()) {
			String key = (String) xchangeConfigKeys.next();
			final Matcher matcher = configPattern.matcher(key);
			if (matcher.matches())
				exchangeTags.add(matcher.group(1));
		}

		exchangesByMarket = HashBiMap.create();

		//= new HashMap<>();
		Iterator ite = exchangeTags.iterator();
		while (ite.hasNext()) {
			// for (String exchangeTag : exchangeTags) {
			String exchangeTag = (String) ite.next();
			String baseKey = "xchange." + exchangeTag + ".";
			String key = baseKey + "class";
			String exchangeClassName = config.getString(key);
			if (exchangeClassName == null)
				throw new Error("Property " + key + " is not set.  Please edit cointrader-default.properties to specify the correct XChange adapter class.");
			ExchangeSpecification spec = new ExchangeSpecification(exchangeClassName);
			spec.setUserName(config.getString(baseKey + "username", null));
			spec.setPassword(config.getString(baseKey + "password", null));
			spec.setApiKey(config.getString(baseKey + "apikey", null));
			spec.setSecretKey(config.getString(baseKey + "apisecret", null));
			spec.setExchangeSpecificParametersItem("Use_Intl", Boolean.valueOf(config.getString(baseKey + "exchangeSpecificParameters.intl", "false")));
			spec.setExchangeSpecificParametersItem("Use_Futures", Boolean.valueOf(config.getString(baseKey + "exchangeSpecificParameters.futures", "false")));
			spec.setSslUri(config.getString(baseKey + "ssluri", null));
			spec.setHost(config.getString(baseKey + "host", null));
			List<String> listings = config.getList(baseKey + "listings");
			if (listings == null || listings.isEmpty()) {
				ite.remove();
				// break;
			} else {

				for (String listingSymbol : listings) {
					Listing listing = Listing.forSymbol(listingSymbol.toUpperCase());
					if (listing.getPrompt() != null) {
						spec.setExchangeSpecificParametersItem("Futures_Contract_String", listing.getPrompt().getSymbol());
						spec.setExchangeSpecificParametersItem("Futures_Leverage",
								String.valueOf(config.getString(baseKey + "exchangeSpecificParameters.futures.leverage", "10")));

					}

				}
				if (!exchangesByMarket.containsKey(getExchangeForTag(exchangeTag))) {
					org.knowm.xchange.Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
					exchangesByMarket.put(getExchangeForTag(exchangeTag), exchange);
				}
			}

		}
	}
}
