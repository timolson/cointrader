package org.cryptocoinpartners.schema;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Bar extends Event {
	private long timestamp;
	private Double open;
	private Double close;
	private Double high;
	private Double low;
	private Market market;
	private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	private static final String SEPARATOR = ",";

	public Bar(long timestamp, Double open, Double close, Double high, Double low, Market market) {
		this.timestamp = timestamp;
		this.open = open;
		this.close = close;
		this.high = high;
		this.low = low;
		this.market = market;
	}

	@Override
	public long getTimestamp() {
		return timestamp;
	}

	public Double getOpen() {
		return open;
	}

	public Double getClose() {
		return close;
	}

	public Double getHigh() {
		return high;
	}

	public Double getLow() {
		return low;
	}

	public Market getMarket() {
		return market;
	}

	protected void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	protected void setOpen(Double open) {
		this.open = open;
	}

	protected void setHigh(Double high) {
		this.high = high;
	}

	protected void setLow(Double low) {
		this.low = low;
	}

	protected void setClose(Double close) {
		this.close = close;
	}

	protected void setMarket(Market market) {
		this.market = market;
	}

	@Override
	public String toString() {

		return "Bar Start=" + (getTimestamp() != 0 ? (FORMAT.print(getTimestamp())) : "") + SEPARATOR + "Market=" + getMarket() + SEPARATOR + "Open="
				+ getOpen() + SEPARATOR + "High=" + getHigh() + SEPARATOR + "Low=" + getLow() + SEPARATOR + "Close=" + getClose();
	}
}
