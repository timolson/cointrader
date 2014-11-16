package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.joda.time.Instant;

/**
 * A GeneralOrder only specifies a Listing but not an Exchange.  The GeneralOrder must be processed and broken down into
 * a series of SpecificOrders before it can be placed on Markets.  GeneralOrders express their volumes and prices using
 * BigDecimal, since the trading basis at each Exchange may be different, thus a DiscreteAmount cannot be used.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
public class GeneralOrder extends Order {

	public GeneralOrder(Instant time, Portfolio portfolio, Listing listing, BigDecimal volume) {
		super(time);
		super.setPortfolio(portfolio);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
	}

	public GeneralOrder(Instant time, Portfolio portfolio, Order parentOrder, Listing listing, BigDecimal volume) {
		super(time);
		super.setPortfolio(portfolio);
		parentOrder.addChild(this);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
	}

	public GeneralOrder(Instant time, Portfolio portfolio, Market market, BigDecimal volume, FillType type) {
		super(time);
		super.setPortfolio(portfolio);
		this.market = market;
		this.listing = market.getListing();
		this.volume = DecimalAmount.of(volume);
		this.fillType = type;
	}

	public GeneralOrder(Instant time, Portfolio portfolio, Order parentOrder, Market market, BigDecimal volume, FillType type) {
		super(time);
		super.setPortfolio(portfolio);
		parentOrder.addChild(this);
		this.market = market;
		this.listing = market.getListing();
		this.volume = DecimalAmount.of(volume);
		this.fillType = type;
	}

	public GeneralOrder(Instant time, Portfolio portfolio, Listing listing, String volume) {
		super(time);
		super.setPortfolio(portfolio);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
	}

	@ManyToOne(optional = false, cascade = { CascadeType.MERGE, CascadeType.REMOVE })
	//@EmbeddedId
	public Listing getListing() {
		return listing;
	}

	@Override
	@ManyToOne(optional = true, cascade = { CascadeType.MERGE, CascadeType.REMOVE })
	public Market getMarket() {
		return market;
	}

	public BigDecimal getVolumeDecimal() {

		if (volume == null)
			volume = DecimalAmount.of(BigDecimal.ZERO);
		return volume.asBigDecimal();

	}

	public BigDecimal getLimitPriceDecimal() {
		if (limitPrice == null)
			return null;
		return limitPrice.asBigDecimal();

	}

	public BigDecimal getStopPriceDecimal() {
		if (stopPrice == null)
			return null;
		return stopPrice.asBigDecimal();

	}

	public BigDecimal getTrailingStopPriceDecimal() {
		if (trailingStopPrice == null)
			return null;
		return trailingStopPrice.asBigDecimal();
	}

	@Override
	@Transient
	public DecimalAmount getVolume() {
		return volume;
	}

	@Override
	@Transient
	public DecimalAmount getLimitPrice() {
		return limitPrice;
	}

	@Override
	@Transient
	public DecimalAmount getStopPrice() {
		return stopPrice;
	}

	@Override
	@Transient
	public DecimalAmount getTrailingStopPrice() {
		return trailingStopPrice;
	}

	@Transient
	BigDecimal getUnfilledVolume() {
		BigDecimal filled = BigDecimal.ZERO;
		for (Fill fill : getFills())
			filled = filled.add(fill.getVolume().asBigDecimal());
		return filled;
	}

	@Override
	@Transient
	public boolean isFilled() {
		return getUnfilledVolume().equals(BigDecimal.ZERO);
	}

	@Override
	@Transient
	public boolean isBid() {
		return !volume.isNegative();
	}

	public void copyCommonFillProperties(Fill fill) {
		setTime(fill.getTime());
		setEmulation(fill.getOrder().isEmulation());
		setExpiration(fill.getOrder().getExpiration());
		setPortfolio(fill.getOrder().getPortfolio());
		setMarginType(fill.getOrder().getMarginType());
		setPanicForce(fill.getOrder().getPanicForce());
	}

	@Override
	public String toString() {
		String s = "GeneralOrder{" + "id=" + getId() + ", parentOrder=" + (getParentOrder() == null ? "null" : getParentOrder().getId()) + ", listing="
				+ listing + ", volume=" + volume;
		if (limitPrice != null && limitPrice.asBigDecimal() != null)
			s += ", limitPrice=" + limitPrice;
		if (stopPrice != null && stopPrice.asBigDecimal() != null)
			s += ", stopPrice=" + stopPrice;
		if (trailingStopPrice != null && trailingStopPrice.asBigDecimal() != null)
			s += ", trailingStopPrice=" + trailingStopPrice;
		if (comment != null)
			s += ", comment=" + comment;
		if (fillType != null)
			s += ", type=" + fillType;
		if (hasFills())
			s += ", averageFillPrice=" + averageFillPrice();
		s += '}';
		return s;
	}

	protected GeneralOrder() {
	}

	protected GeneralOrder(Instant time) {
		super(time);
	}

	protected void setVolume(DecimalAmount volume) {
		this.volume = volume;
	}

	protected void setVolumeDecimal(BigDecimal volume) {
		this.volume = new DecimalAmount(volume);
	}

	protected void setLimitPrice(DecimalAmount limitPrice) {
		this.limitPrice = limitPrice;
	}

	protected void setLimitPriceDecimal(BigDecimal limitPrice) {
		if (limitPrice != null) {
			this.limitPrice = DecimalAmount.of(limitPrice);
		}
	}

	@Override
	public void setStopPrice(DecimalAmount stopPrice) {
		this.stopPrice = stopPrice;
	}

	public void setStopPriceDecimal(BigDecimal stopPrice) {
		if (stopPrice != null) {
			this.stopPrice = DecimalAmount.of(stopPrice);
		}
	}

	@Override
	public void setTrailingStopPrice(DecimalAmount trailingStopPrice) {
		this.trailingStopPrice = trailingStopPrice;
	}

	public void setTrailingStopPriceDecimal(BigDecimal trailingStopPrice) {
		if (trailingStopPrice != null) {
			this.trailingStopPrice = DecimalAmount.of(trailingStopPrice);
		}
	}

	protected void setListing(Listing listing) {
		this.listing = listing;
		this.market = null;
	}

	@Override
	public void setMarket(Market market) {
		this.market = market;
		this.listing = market.getListing();
	}

	private Listing listing;
	private Market market;
	private DecimalAmount volume;
	private DecimalAmount limitPrice;
	private DecimalAmount stopPrice;
	private DecimalAmount trailingStopPrice;
	private Amount forcastedFees;

}
