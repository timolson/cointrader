package org.cryptocoinpartners.esper;

import java.util.ArrayList;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.util.Precision;

import com.espertech.esper.epl.agg.aggregator.AggregationMethod;

import jline.internal.Log;

public class Skew implements AggregationMethod {

  ArrayList<Double> values = new ArrayList<Double>();

  @Override
  public void enter(Object value) {

    values.add((double) value);
  }

  @Override
  public void leave(Object value) {
    values.remove(value);
  }

  @Override
  public Object getValue() {
    if (values == null || values.size() == 0) {
      return 0;
    }
    Double a[] = values.toArray(new Double[0]);
    Skewness skewness = new Skewness();
    double skew = skewness.evaluate(ArrayUtils.toPrimitive(a));
    if (!Double.isNaN(skew)) Log.debug(" skew");
    else Log.debug("nan skew");
    return Precision.round(skew, 4);
  }

  @Override
  public Class getValueType() {
    return double.class;
  }

  @Override
  public void clear() {
    values = new ArrayList<>();
  }
}
