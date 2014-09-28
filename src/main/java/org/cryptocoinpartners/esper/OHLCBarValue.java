package org.cryptocoinpartners.esper;

public class OHLCBarValue {
	private final long minuteValue;
	private final Double first;
	private final Double last;
	private final Double max;
	private final Double min;

	public OHLCBarValue(long minuteValue, Double first, Double last, Double max, Double min) {
		this.minuteValue = minuteValue;
		this.first = first;
		this.last = last;
		this.max = max;
		this.min = min;
	}

	public long getMinuteValue() {
		return minuteValue;
	}

	public Double getFirst() {
		return first;
	}

	public Double getLast() {
		return last;
	}

	public Double getMax() {
		return max;
	}

	public Double getMin() {
		return min;
	}
}
