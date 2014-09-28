package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

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

	public GeneralOrder(Instant time, Listing listing, BigDecimal volume) {
		super(time);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
	}

	public GeneralOrder(Instant time, Listing listing, String volume) {
		super(time);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
	}

	@ManyToOne(optional = false)
	public Listing getListing() {
		return listing;
	}

	@Embedded
	@AttributeOverride(name = "bd", column = @Column(name = "volume"))
	@SuppressWarnings("JpaDataSourceORMInspection")
	public DecimalAmount getVolume() {
		return volume;
	}

	@Nullable
	@Embedded
	@AttributeOverride(name = "bd", column = @Column(name = "limitPrice"))
	@SuppressWarnings("JpaDataSourceORMInspection")
	public DecimalAmount getLimitPrice() {
		return limitPrice;
	}

	@Nullable
	@Embedded
	@AttributeOverride(name = "bd", column = @Column(name = "stopPrice"))
	@SuppressWarnings("JpaDataSourceORMInspection")
	public DecimalAmount getStopPrice() {
		return stopPrice;
	}

	@Nullable
	@Embedded
	@AttributeOverride(name = "bd", column = @Column(name = "trailingStopPrice"))
	@SuppressWarnings("JpaDataSourceORMInspection")
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

	@OneToMany
	public List<SpecificOrder> getChildren() {
		return children;
	}

	@Override
	public String toString() {
		String s = "GeneralOrder{" + "id=" + getId() + ", parentOrder=" + (getParentOrder() == null ? "null" : getParentOrder().getId()) + ", listing="
				+ listing + ", volume=" + volume;
		if (limitPrice != null)
			s += ", limitPrice=" + limitPrice;
		if (stopPrice != null)
			s += ", stopPrice=" + stopPrice;
		if (trailingStopPrice != null)
			s += ", trailingStopPrice=" + trailingStopPrice;

		if (hasFills())
			s += ", averageFillPrice=" + averageFillPrice();
		s += '}';
		return s;
	}

	protected GeneralOrder(Instant time) {
		super(time);
	}

	protected void setVolume(DecimalAmount volume) {
		this.volume = volume;
	}

	protected void setLimitPrice(DecimalAmount limitPrice) {
		this.limitPrice = limitPrice;
	}

	protected void setStopPrice(DecimalAmount stopPrice) {
		this.stopPrice = stopPrice;
	}

	protected void setTrailingStopPrice(DecimalAmount trailingStopPrice) {
		this.trailingStopPrice = trailingStopPrice;
	}

	protected void setListing(Listing listing) {
		this.listing = listing;
	}

	protected void setChildren(List<SpecificOrder> children) {
		this.children = children;
	}

	private List<SpecificOrder> children;
	private Listing listing;
	private DecimalAmount volume;
	private DecimalAmount limitPrice;
	private DecimalAmount stopPrice;
	private DecimalAmount trailingStopPrice;

}
