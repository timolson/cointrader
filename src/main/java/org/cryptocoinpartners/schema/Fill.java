package org.cryptocoinpartners.schema;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * A Fill represents some completion of an Order.  The volume of the Fill might be less than the requested volume of the
 * Order
 *
 * @author Tim Olson
 */
@Entity
public class Fill extends RemoteEvent {
	private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	private static final String SEPARATOR = ",";

	public Fill(SpecificOrder order, Instant time, Market market, long priceCount, long volumeCount) {
		this.order = order;
		this.market = market;
		this.priceCount = priceCount;
		this.volumeCount = volumeCount;
	}

	public Fill(SpecificOrder order, Instant time, Market market, long priceCount, long volumeCount, Amount commission) {
		this.order = order;
		this.market = market;
		this.priceCount = priceCount;
		this.volumeCount = volumeCount;
		this.commission = commission;
	}

	public @ManyToOne(cascade = { CascadeType.ALL })
	//(cascade = { CascadeType.ALL })
	@JoinColumn(name = "`order`")
	SpecificOrder getOrder() {
		return order;
	}

	@ManyToOne
	public Market getMarket() {
		return market;
	}

	@Transient
	public Amount getPrice() {
		return new DiscreteAmount(priceCount, market.getPriceBasis());
	}

	public long getPriceCount() {
		return priceCount;
	}

	@Transient
	public Amount getVolume() {
		return new DiscreteAmount(volumeCount, market.getVolumeBasis());
	}

	public long getVolumeCount() {
		return volumeCount;
	}

	@Transient
	public Amount getCommission() {
		return commission;
	}

	@Transient
	public FillType getFillType() {
		return getOrder().getFillType();
	}

	@Override
	public String toString() {

		return "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "OrderID=" + order.getId() + SEPARATOR + "Type=" + getFillType()
				+ SEPARATOR + "Market=" + market + SEPARATOR + "Price=" + getPrice() + SEPARATOR + "Volume=" + getVolume();
	}

	// JPA
	protected Fill() {
	}

	protected void setOrder(SpecificOrder order) {
		this.order = order;
	}

	protected void setMarket(Market market) {
		this.market = market;
	}

	protected void setPriceCount(long priceCount) {
		this.priceCount = priceCount;
	}

	protected void setVolumeCount(long volumeCount) {
		this.volumeCount = volumeCount;
	}

	public void setCommission(Amount commission) {
		this.commission = commission;
	}

	private SpecificOrder order;
	private Market market;
	private long priceCount;
	private long volumeCount;
	private Amount commission;

}
