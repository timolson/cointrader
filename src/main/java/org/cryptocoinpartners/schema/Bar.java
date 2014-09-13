package org.cryptocoinpartners.schema;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Bar extends Event {
	private final long minuteValue;
	private final Double first;
	private final Double last;
	private final Double max;
	private final Double min;
	private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	private static final String SEPARATOR = ",";

	public Bar(long minuteValue, Double first, Double last, Double max, Double min) {
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

	@Override
	public String toString() {

		return "Bar Start=" + (getMinuteValue() != 0 ? (FORMAT.print(getMinuteValue())) : "") + SEPARATOR + "Open=" + getFirst() + SEPARATOR + "High="
				+ getMax() + SEPARATOR + "Low=" + getMin() + SEPARATOR + "Close=" + getLast();
	}
}
