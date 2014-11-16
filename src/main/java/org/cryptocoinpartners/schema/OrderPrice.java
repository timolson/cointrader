package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.annotation.Nullable;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.joda.time.Instant;

/**
 * Superclass for Orders and Fills which have a price and volume
 *
 * @author Tim Olson
 */
@MappedSuperclass
public abstract class OrderPrice extends Temporal {

	/**
	 * @param time when the pricing event originally occured
	 * @param market which Market this pricing is for
	 * @param priceCount relative to the Market's quoteBasis
	 * @param volumeCount relative to the Market's volumeBasis
	 */
	public OrderPrice(Instant time, Market market, @Nullable Long priceCount, @Nullable Long volumeCount) {
		super(time);
		this.priceCount = priceCount;
		this.volumeCount = volumeCount;
	}

	public OrderPrice(Instant time, @Nullable String remoteKey, Market market, @Nullable BigDecimal price, @Nullable BigDecimal volume) {
		super(time);
		this.priceCount = DiscreteAmount.roundedCountForBasis(price, market.getPriceBasis());
	}

	@ManyToOne
	public Market getMarket() {
		return market;
	}

	public @Nullable
	Long getPriceCount() {
		return priceCount;
	}

	public @Nullable
	Long getVolumeCount() {
		return volumeCount;
	}

	@Transient
	@Nullable
	public Amount getPrice() {
		if (priceCount == null)
			return null;
		if (price == null)
			price = new DiscreteAmount(priceCount, getMarket().getPriceBasis());
		return price;
	}

	@Transient
	@Nullable
	public Double getPriceAsDouble() {
		Amount price = getPrice();
		return price == null ? null : price.asDouble();
	}

	@Transient
	@Nullable
	public Amount getVolume() {
		if (volumeCount == null)
			return null;
		if (volume == null)
			volume = new DiscreteAmount(volumeCount, getMarket().getVolumeBasis());
		return price;
	}

	@Transient
	@Nullable
	public Double getVolumeAsDouble() {
		Amount volume = getVolume();
		return volume == null ? null : volume.asDouble();
	}

	// JPA
	protected OrderPrice() {
		super();
	}

	protected void setPriceCount(Long priceCount) {
		this.priceCount = priceCount;
	}

	protected void setVolumeCount(Long volumeCount) {
		this.volumeCount = volumeCount;
	}

	protected void setMarket(Market market) {
		this.market = market;
	}

	private Amount price;
	private Amount volume;
	private Long priceCount;
	private Long volumeCount;
	private Market market;
}
