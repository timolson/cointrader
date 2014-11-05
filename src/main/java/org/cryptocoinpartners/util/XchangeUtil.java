package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.CombinedConfiguration;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;

import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;

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

	public static com.xeiam.xchange.Exchange getExchangeForMarket(Exchange coinTraderExchange) {
		com.xeiam.xchange.Exchange xchangeExchange = exchangesByMarket.get(coinTraderExchange);
		if (xchangeExchange == null)
			throw new Error("Could not get XChange Exchange for Coin Trader Exchange " + coinTraderExchange);
		return xchangeExchange;
	}

	public static CurrencyPair getCurrencyPairForListing(Listing listing) {
		return new CurrencyPair(listing.getBase().getSymbol(), listing.getQuote().getSymbol());
	}

	private static Map<Exchange, com.xeiam.xchange.Exchange> exchangesByMarket;
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

		exchangesByMarket = new HashMap<>();
		for (String exchangeTag : exchangeTags) {
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
			spec.setExchangeSpecificParametersItem("Use_Intl", true);
			com.xeiam.xchange.Exchange exchange = ExchangeFactory.INSTANCE.createExchange(spec);
			exchangesByMarket.put(getExchangeForTag(exchangeTag), exchange);
		}
	}

}
