package org.cryptocoinpartners.esper;

import org.cryptocoinpartners.schema.Market;

public class HeikinAshiBarValue extends OHLCBarValue {

  public HeikinAshiBarValue(long minuteValue, Double first, Double last, Double max, Double min) {
    super(minuteValue, first, last, max, min);
  }

  public HeikinAshiBarValue(
      long minuteValue, Double first, Double last, Double max, Double min, Market market) {
    super(minuteValue, first, last, max, min, market);
  }
}
