package org.cryptocoinpartners.module.xchangedata;

import com.xeiam.xchange.bitfinex.v1.BitfinexExchange;
import com.xeiam.xchange.bitstamp.BitstampExchange;

/**
 * @author Philip Chen
 * 
 * This class returns the names for each exchange in order to get trade information given
 * exchange ID.
 */
public class ExchangeNames {

	//TODO 
	public static final int Bitfinex = 100;
	public static final int BitstampExchange = 101;

	private static String exchangeName = null;

	public static String findExchangeName(int exchangeId) {
		switch (exchangeId) {
		case Bitfinex:
			exchangeName = BitfinexExchange.class.getName();
			break;
		case BitstampExchange:
			exchangeName = BitstampExchange.class.getName();
			break;
		}

		return exchangeName;
	}

}
