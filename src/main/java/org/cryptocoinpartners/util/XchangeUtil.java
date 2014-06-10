package org.cryptocoinpartners.util;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.currency.CurrencyPair;
import org.apache.commons.configuration.CombinedConfiguration;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Tim Olson
 */
public class XchangeUtil {


    public static Market getMarketForExchangeTag(String tag) { return Market.forSymbol(tag.toUpperCase()); }


    public static Set<String> getExchangeTags() { return exchangeTags; }


    public static Exchange getExchangeForMarket(Market market) {
        Exchange exchange = exchangesByMarket.get(market);
        if( exchange == null )
            throw new Error("Could not get Exchange for Market "+market);
        return exchange;
    }


    public static CurrencyPair getCurrencyPairForListing(Listing listing)
    {
        return new CurrencyPair(listing.getBase().getSymbol(), listing.getQuote().getSymbol());
    }


    private static Map<Market,Exchange> exchangesByMarket;
    private static Set<String> exchangeTags;


    static {
        // find all the config keys starting with "xchange." and collect their second groups after the dot
        final String configPrefix = "xchange";
        CombinedConfiguration config = Config.combined();
        final Iterator xchangeConfigKeys = config.getKeys(configPrefix);
        exchangeTags = new HashSet<>();
        final Pattern configPattern = Pattern.compile(configPrefix+"\\.([^\\.]+)\\..+");
        while( xchangeConfigKeys.hasNext() ) {
            String key = (String) xchangeConfigKeys.next();
            final Matcher matcher = configPattern.matcher(key);
            if( matcher.matches() )
                exchangeTags.add(matcher.group(1));
        }

        exchangesByMarket = new HashMap<>();
        for( String exchangeTag : exchangeTags ) {
            String key = "xchange." + exchangeTag + ".class";
            String exchangeClassName = config.getString(key);
            if( exchangeClassName == null )
                throw new Error("Property "+key+" is not set.  Please edit trader-default.properties to specify the correct XChange adapter class.");
            Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exchangeClassName);
            exchangesByMarket.put(getMarketForExchangeTag(exchangeTag),exchange);
        }
    }

}
