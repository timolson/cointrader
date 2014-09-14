package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.Collection;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.Remainder;
import org.joda.time.Instant;

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

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, long volumeCount) {
		super(time);
		this.market = market;
		this.volumeCount = volumeCount;
		//super(time);
		super.setPortfolio(portfolio);

	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, long volumeCount, String comment) {
		super(time);
		this.market = market;
		this.volumeCount = volumeCount;
		super.setComment(comment);
		//super(time);
		super.setPortfolio(portfolio);

	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, long volumeCount, long stopPriceCount, String comment) {
		super(time);
		this.market = market;
		this.volumeCount = volumeCount;
		this.stopPriceCount = stopPriceCount;
		super.setComment(comment);
		super.setPortfolio(portfolio);

	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, long volumeCount, long stopPriceCount, long trailingStopPriceCount, String comment) {
		super(time);
		this.market = market;
		this.volumeCount = volumeCount;
		this.stopPriceCount = stopPriceCount;
		this.trailingStopPriceCount = trailingStopPriceCount;
		super.setComment(comment);
		super.setPortfolio(portfolio);

	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, Amount volume, String comment) {
		super(time);
		this.market = market;
		this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
		super.setComment(comment);
		super.setPortfolio(portfolio);
	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, Amount volume, Amount stopPrice, String comment) {
		super(time);
		this.market = market;
		this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
		this.stopPriceCount = stopPrice.toBasis(market.getPriceBasis(), Remainder.DISCARD).getCount();
		super.setComment(comment);
		super.setPortfolio(portfolio);
	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, Amount volume, Amount stopPrice, Amount trailingStopPrice, String comment) {
		super(time);
		this.market = market;
		this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
		this.stopPriceCount = stopPrice.toBasis(market.getPriceBasis(), Remainder.DISCARD).getCount();
		this.trailingStopPriceCount = trailingStopPrice.toBasis(market.getPriceBasis(), Remainder.DISCARD).getCount();
		super.setComment(comment);
		super.setPortfolio(portfolio);
	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, BigDecimal volume, String comment) {
		this(time, portfolio, market, new DecimalAmount(volume), comment);
	}

	public SpecificOrder(Instant time, Portfolio portfolio, Market market, double volume, String comment) {
		this(time, portfolio, market, new DecimalAmount(new BigDecimal(volume)), comment);
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
	@Nullable
	public DiscreteAmount getTrailingStopPrice() {
		if (trailingStopPriceCount == 0)
			return null;
		if (trailingStopPrice == null)
			trailingStopPrice = amount().fromPriceCount(trailingStopPriceCount);
		return trailingStopPrice;
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

		return "SpecificOrder{ time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "id=" + getId() + SEPARATOR + "parentOrder="
				+ (getParentOrder() == null ? "null" : getParentOrder().getId()) + SEPARATOR + "portfolio=" + getPortfolio() + SEPARATOR + "market=" + market
				+ SEPARATOR + "volumeCount=" + getVolume() + (limitPriceCount != 0 ? (SEPARATOR + "limitPriceCount=" + getLimitPrice()) : "")
				+ (stopPriceCount != 0 ? (SEPARATOR + "stopPriceCount=" + getStopPrice()) : "")
				+ (trailingStopPriceCount != 0 ? (SEPARATOR + "trailingStopPriceCount=" + getTrailingStopPrice()) : "")
				+ (hasFills() ? (SEPARATOR + "averageFillPrice=" + averageFillPrice()) : "") + "}";
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
		return forcastedFees;

	}

	/** 0 if no limit is set */
	protected long getStopPriceCount() {
		return stopPriceCount;
	}

	protected long getTrailingStopPriceCount() {
		return trailingStopPriceCount;
	}

	protected SpecificOrder(Instant time) {
		super(time);
	}

	protected void setMarket(Market market) {
		this.market = market;
	}

	public void setForcastedCommission(Amount forcastedFees) {
		this.forcastedFees = forcastedFees;
	}

	protected void setVolumeCount(long volumeCount) {
		this.volumeCount = volumeCount;
		volume = null;
	}

	public void setLimitPriceCount(long limitPriceCount) {
		this.limitPriceCount = limitPriceCount;
		limitPrice = null;
	}

	public void setStopPriceCount(long stopPriceCount) {
		this.stopPriceCount = stopPriceCount;
		stopPrice = null;
	}

	public void removeStopPriceCount() {
		this.stopPriceCount = 0;
		stopPrice = null;
	}

	protected void setTrailingStopPriceCount(long trailingStopPriceCount) {
		this.trailingStopPriceCount = trailingStopPriceCount;
		trailingStopPrice = null;
	}

	public void removeTrailingStopPriceCount() {
		this.trailingStopPriceCount = 0;
		trailingStopPrice = null;
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
	private DiscreteAmount trailingStopPrice;
	private Amount forcastedFees;
	private long volumeCount;
	private long limitPriceCount;
	private long stopPriceCount;
	private long trailingStopPriceCount;
	private Market.MarketAmountBuilder amountBuilder;

}
