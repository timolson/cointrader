package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.enumeration.FeeMethod;

public class Exchanges {
  /** An imaginary Exchange used for testing */
  public static final Exchange MOCK = Exchange.forSymbolOrCreate("MOCK", 1, 0.0015, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);

  /** Our own "Exchange". Internal Accounts have this as their Exchange */
  public static final Exchange SELF = Exchange.forSymbolOrCreate("SELF", 1, 0.0015, 0.002, 0, 0.01, FeeMethod.PerUnit, true);

  public static final Exchange BITFINEX = Exchange.forSymbolOrCreate("BITFINEX", 3, 0.001, 0.002, 0.00000001, 0.01, FeeMethod.PercentagePerUnit,
      0.0003, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange POLONIEX = Exchange.forSymbolOrCreate("POLONIEX", 3, 0.0015, 0.0025, 0, 0.01, FeeMethod.PercentagePerUnit, 0.25, 0,
      FeeMethod.PercentagePerUnit, true);
  public static final Exchange BITSTAMP = Exchange.forSymbolOrCreate("BITSTAMP", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange BTCCHINA = Exchange.forSymbolOrCreate("BTCCHINA", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange BTCE = Exchange.forSymbolOrCreate("BTCE", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange BTER = Exchange.forSymbolOrCreate("BTER", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange COINBASE = Exchange.forSymbolOrCreate("COINBASE", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange CRYPTSY = Exchange.forSymbolOrCreate("CRYPTSY", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange CAMPBX = Exchange.forSymbolOrCreate("CAMPBX", 2, 0.002, 0.0025, 0, 0.01, FeeMethod.PercentagePerUnit, 0.25, 0,
      FeeMethod.PercentagePerUnit, true);
  public static final Exchange BITTREX = Exchange.forSymbolOrCreate("BITTREX", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, true);
  public static final Exchange OKCOIN = Exchange.forSymbolOrCreate("OKCOIN", 1, 0.002, 0.002, 0, 0.01, FeeMethod.PercentagePerUnit, 0.25, 0,
      FeeMethod.PercentagePerUnit, false);
  public static final Exchange OKCOIN_THISWEEK = Exchange.forSymbolOrCreate("OKCOIN_THISWEEK", 1, 0.002, 0.002, 0, 0, FeeMethod.PercentagePerUnit,
      0.25, 0, FeeMethod.PercentagePerUnit, false);
  public static final Exchange OKCOIN_QUARTER = Exchange.forSymbolOrCreate("OKCOIN_QUARTER", 1, 0.002, 0.002, 0, 0, FeeMethod.PercentagePerUnit,
      0.25, 0, FeeMethod.PercentagePerUnit, false);
  public static final Exchange OKCOIN_NEXTWEEK = Exchange.forSymbolOrCreate("OKCOIN_NEXTWEEK", 1, 0.002, 0.002, 0, 0, FeeMethod.PercentagePerUnit,
      0.25, 0, FeeMethod.PercentagePerUnit, false);
  public static final Exchange KRAKEN = Exchange.forSymbolOrCreate("KRAKEN", 3, 0.0016, 0.0026, 0.00000001, 0.01, FeeMethod.PercentagePerUnit,
      0.0003, 0, FeeMethod.PercentagePerUnit, true);
}
