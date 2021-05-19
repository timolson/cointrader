package org.cryptocoinpartners.esper;

import java.util.ArrayList;

import com.espertech.esper.epl.agg.aggregator.AggregationMethod;

public class LongestNegativeSequence implements AggregationMethod {

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

		int maxIdx = 0, maxLen = 0, currLen = 0, currIdx = 0;

		for (int k = 0; k < a.length; k++) {
			if (a[k] < 0) {
				currLen++;

				// New sequence, store 
				// beginning index. 
				if (currLen == 1)
					currIdx = k;
			} else {
				if (currLen > maxLen) {
					maxLen = currLen;
					maxIdx = currIdx;
				}
				currLen = 0;
			}

		}
		if (currLen > maxLen) {
			maxLen = currLen;
			maxIdx = currIdx;
		}
		return maxLen;

	}

	@Override
	public Class getValueType() {
		return Integer.class;
	}

	@Override
	public void clear() {
		values = new ArrayList<>();

	}

}
