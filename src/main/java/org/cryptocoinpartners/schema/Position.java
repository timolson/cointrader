package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.Remainder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * A Position represents an amount of some Asset within an Exchange.  If the Position is related to an Order
 * then the Position is being held in reserve (not tradeable) to cover the costs of the open Order.
 *
 * @author Tim Olson
 */
@Entity
public class Position extends Holding {

	private Portfolio portfolio;
	protected static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	protected static final String SEPARATOR = ",";

	public Position(Portfolio portfolio, Exchange exchange, Market market, Asset asset, Amount volume, Amount price) {

		this.exchange = exchange;
		this.market = market;
		this.longVolume = volume.isPositive() ? volume : this.longVolume;
		this.longVolumeCount = volume.isPositive() ? volume.toBasis(asset.getBasis(), Remainder.ROUND_EVEN).getCount() : this.longVolumeCount;
		this.shortVolume = volume.isNegative() ? volume : this.shortVolume;
		this.shortVolumeCount = volume.isNegative() ? volume.toBasis(asset.getBasis(), Remainder.ROUND_EVEN).getCount() : this.shortVolumeCount;
		this.price = price;
		this.avgPrice = price;
		this.longAvgPrice = volume.isPositive() ? price : this.longAvgPrice;
		this.shortAvgPrice = volume.isNegative() ? price : this.shortAvgPrice;
		this.asset = asset;
		this.portfolio = portfolio;
	}

	public Position(Portfolio portfolio, Exchange exchange, Market market, Asset asset, Amount volume, Amount price, Amount exitPrice) {

		this.exchange = exchange;
		this.market = market;
		this.longVolume = volume.isPositive() ? volume : this.longVolume;
		this.longVolumeCount = volume.isPositive() ? volume.toBasis(asset.getBasis(), Remainder.ROUND_EVEN).getCount() : this.longVolumeCount;
		this.shortVolume = volume.isNegative() ? volume : this.shortVolume;
		this.shortVolumeCount = volume.isNegative() ? volume.toBasis(asset.getBasis(), Remainder.ROUND_EVEN).getCount() : this.shortVolumeCount;
		this.price = price;
		this.longExitPrice = volume.isPositive() ? exitPrice : this.longExitPrice;
		this.shortExitPrice = volume.isNegative() ? exitPrice : this.shortExitPrice;
		this.asset = asset;
		this.avgPrice = price;
		this.longAvgPrice = volume.isPositive() ? price : this.longAvgPrice;
		this.shortAvgPrice = volume.isNegative() ? price : this.shortAvgPrice;
		this.portfolio = portfolio;
	}

	@Transient
	public boolean isOpen() {

		return !getVolume().isZero();
	}

	@Transient
	public boolean isLong() {

		return getVolume().isPositive();
	}

	@Transient
	public boolean isShort() {

		return getVolume().isNegative();
	}

	@Transient
	public boolean isFlat() {

		return getVolume().isZero();
	}

	@Transient
	public Market getMarket() {

		return market;
	}

	@Transient
	public Portfolio getPortfolio() {

		return portfolio;
	}

	@Transient
	public Amount getMarginAmount() {

		if (isOpen() && marginAmount != null) {
			return marginAmount;
		} else {
			return DecimalAmount.ONE;
		}
	}

	@Transient
	public Amount getVolume() {

		if (getLongVolume() != null && getShortVolume() != null) {
			return getLongVolume().plus(getShortVolume());
		} else if (getLongVolume() != null) {
			return getLongVolume();
		} else {
			return getShortVolume();
		}

	}

	@Transient
	public Amount getLongVolume() {
		if (longVolume == null)
			longVolume = new DiscreteAmount(longVolumeCount, asset.getBasis());
		return longVolume;
	}

	@Transient
	public Amount getShortVolume() {
		if (shortVolume == null)
			shortVolume = new DiscreteAmount(shortVolumeCount, asset.getBasis());
		return shortVolume;
	}

	@Transient
	public Amount getPrice() {
		return price;
	}

	@Transient
	public Amount getAvgPrice() {
		return avgPrice;
	}

	@Transient
	public Amount getLongAvgPrice() {
		if (longAvgPrice == null)
			longAvgPrice = DecimalAmount.ZERO;
		return longAvgPrice;
	}

	@Transient
	public Amount getShortAvgPrice() {
		if (shortAvgPrice == null)
			shortAvgPrice = DecimalAmount.ZERO;
		return shortAvgPrice;
	}

	@Transient
	public Amount getShortExitPrice() {
		return shortExitPrice;
	}

	@Transient
	public Amount getLongExitPrice() {
		return longExitPrice;
	}

	/** If the SpecificOrder is not null, then this Position is being held in reserve as payment for that Order */

	@Transient
	protected boolean isReserved() {
		return order != null;
	}

	/**
	 * Modifies this Position in-place by the amount of the position argument.
	 * @param position a Position to add to this one.
	 * @return true iff the positions both have the same Asset and the same Exchange, in which case this Position
	 * has modified its volume by the amount in the position argument.
	 */
	protected boolean merge(Position position) {
		if (!exchange.equals(position.exchange) || !asset.equals(position.asset))
			return false;

		longVolumeCount += position.longVolumeCount;
		shortVolumeCount += position.shortVolumeCount;
		setLongVolumeCount(longVolumeCount);
		setShortVolumeCount(shortVolumeCount);
		return true;
	}

	@Override
	public String toString() {
		return "Position=[Exchange=" + exchange + ", Price=" + price + ", Average Price=" + avgPrice
				+ (getShortVolume() != null ? (SEPARATOR + ", Short Qty=" + getShortVolume()) : "")
				+ (getShortAvgPrice() != null ? (SEPARATOR + ", Short Avg Price=" + getShortAvgPrice()) : "")
				+ (getLongVolume() != null ? (SEPARATOR + "Long Qty=" + getLongVolume()) : "")
				+ (getLongAvgPrice() != null ? (SEPARATOR + "Long Avg Price=" + getLongAvgPrice()) : "") + ", Net Qty=" + getVolume().toString()
				+ ",  Entry Date=" + ", Instrument=" + asset + (longExitPrice != null ? (SEPARATOR + " Long Exit Price=" + getLongExitPrice()) : "")
				+ (shortExitPrice != null ? (SEPARATOR + " Short Exit Price=" + getShortExitPrice()) : "") + "]";
	}

	// JPA
	protected Position() {
	}

	@Transient
	protected long getVolumeCount() {
		return longVolumeCount + shortVolumeCount;
	}

	protected long getLongVolumeCount() {
		return longVolumeCount;
	}

	protected long getShortVolumeCount() {
		return shortVolumeCount;
	}

	protected void setLongVolumeCount(long longVolumeCount) {
		this.longVolumeCount = longVolumeCount;
		this.longVolume = null;
	}

	protected void setShortVolumeCount(long shortVolumeCount) {
		this.shortVolumeCount = shortVolumeCount;
		this.shortVolume = null;
	}

	public void setLongExitPriceCount(long longExitPriceCount) {
		this.longExitPriceCount = longExitPriceCount;
		this.longExitPrice = null;
	}

	public void setAvgPrice(Amount avgPrice) {
		this.avgPrice = avgPrice;
	}

	public void setLongAvgPrice(Amount longAvgPrice) {
		this.longAvgPrice = longAvgPrice;
	}

	public void setShortAvgPrice(Amount shortAvgPrice) {
		this.shortAvgPrice = shortAvgPrice;
	}

	public void setShortExitPriceCount(long shortExitPriceCount) {
		this.shortExitPriceCount = shortExitPriceCount;
		this.shortExitPrice = null;
	}

	private Amount longVolume;
	private Amount shortVolume;
	private Market market;
	private Amount price;
	private Amount avgPrice;
	private Amount longAvgPrice;
	private Amount shortAvgPrice;
	private Amount shortExitPrice;
	private Amount longExitPrice;
	private Amount marginAmount;
	private long longVolumeCount;
	private long shortVolumeCount;
	private long priceCount;
	private long shortExitPriceCount;
	private long longExitPriceCount;
	private SpecificOrder order;

}
