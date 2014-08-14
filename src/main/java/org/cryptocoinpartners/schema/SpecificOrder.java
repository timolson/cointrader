package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.Collection;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.Remainder;

/**
 * SpecificOrders are bound to a Market and express their prices and volumes in DiscreteAmounts with the correct
 * basis for the Market.  A SpecificOrder may be immediately passed to a Exchange for execution without any further
 * reduction or processing.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
public class SpecificOrder extends Order {

	public SpecificOrder(Portfolio portfolio, Market market, long volumeCount) {
		this.market = market;
		this.volumeCount = volumeCount;
		super.setPortfolio(portfolio);

	}

	public SpecificOrder(Portfolio portfolio, Market market, Amount volume) {
		this.market = market;
		this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
		super.setPortfolio(portfolio);
	}

	public SpecificOrder(Portfolio portfolio, Market market, BigDecimal volume) {
		this(portfolio, market, new DecimalAmount(volume));
	}

	public SpecificOrder(Portfolio portfolio, Market market, double volume) {
		this(portfolio, market, new DecimalAmount(new BigDecimal(volume)));
	}

	@ManyToOne(optional = false)
	public Market getMarket() {
		return market;
	}

	@Transient
	public DiscreteAmount getVolume() {
		if (volume == null)
			volume = amount().fromVolumeCount(volumeCount);
		return volume;
	}

	@Transient
	@Nullable
	public DiscreteAmount getLimitPrice() {
		if (limitPriceCount == 0)
			return null;
		if (limitPrice == null)
			limitPrice = amount().fromPriceCount(limitPriceCount);
		return limitPrice;
	}

	@Transient
	@Nullable
	public DiscreteAmount getStopPrice() {
		if (stopPriceCount == 0)
			return null;
		if (stopPrice == null)
			stopPrice = amount().fromPriceCount(stopPriceCount);
		return stopPrice;
	}

	@Transient
	public DiscreteAmount getUnfilledVolume() {
		return new DiscreteAmount(getUnfilledVolumeCount(), market.getVolumeBasis());
	}

	@Transient
	public long getUnfilledVolumeCount() {
		long filled = 0;
		Collection<Fill> fills = getFills();
		if (fills == null)
			return volumeCount;
		for (Fill fill : fills)
			filled += fill.getVolumeCount();
		return volumeCount - filled;
	}

	@Override
	@Transient
	public boolean isFilled() {
		return getUnfilledVolumeCount() == 0;
	}

	@Override
	@Transient
	public boolean isBid() {
		return volumeCount > 0;
	}

	public void copyCommonOrderProperties(GeneralOrder generalOrder) {
		setTime(generalOrder.getTime());
		setEmulation(generalOrder.isEmulation());
		setExpiration(generalOrder.getExpiration());
		setPortfolio(generalOrder.getPortfolio());
		setMarginType(generalOrder.getMarginType());
		setPanicForce(generalOrder.getPanicForce());
	}

	@Override
	public String toString() {
		String s = "SpecificOrder{" + "id=" + getId() + ", parentOrder=" + (getParentOrder() == null ? "null" : getParentOrder().getId()) + ", portfolio="
				+ getPortfolio() + ", market=" + market + ", volumeCount=" + getVolume();
		if (limitPriceCount != 0)
			s += ", limitPriceCount=" + getLimitPrice();
		if (stopPriceCount != 0)
			s += ", stopPriceCount=" + getStopPrice();
		if (hasFills())
			s += ", averageFillPrice=" + averageFillPrice();
		s += '}';
		return s;
	}

	// JPA
	protected long getVolumeCount() {
		return volumeCount;
	}

	/** 0 if no limit is set */
	protected long getLimitPriceCount() {
		return limitPriceCount;
	}

	@Transient
	public Amount getForcastedCommission() {
		// TODO Auto-generated method stub
		return null;
	}

	/** 0 if no limit is set */
	protected long getStopPriceCount() {
		return stopPriceCount;
	}

	protected SpecificOrder() {
	}

	protected void setMarket(Market market) {
		this.market = market;
	}

	protected void setVolumeCount(long volumeCount) {
		this.volumeCount = volumeCount;
		volume = null;
	}

	protected void setLimitPriceCount(long limitPriceCount) {
		this.limitPriceCount = limitPriceCount;
		limitPrice = null;
	}

	protected void setStopPriceCount(long stopPriceCount) {
		this.stopPriceCount = stopPriceCount;
		stopPrice = null;
	}

	private Market.MarketAmountBuilder amount() {
		if (amountBuilder == null)
			amountBuilder = market.buildAmount();
		return amountBuilder;
	}

	private Market market;
	private DiscreteAmount volume;
	private DiscreteAmount limitPrice;
	private DiscreteAmount stopPrice;
	private long volumeCount;
	private long limitPriceCount;
	private long stopPriceCount;
	private Market.MarketAmountBuilder amountBuilder;

}
