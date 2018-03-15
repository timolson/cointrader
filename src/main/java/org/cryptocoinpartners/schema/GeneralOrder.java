package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * A GeneralOrder only specifies a Listing but not an Exchange. The GeneralOrder must be processed and broken down into a series of SpecificOrders
 * before it can be placed on Markets. GeneralOrders express their volumes and prices using BigDecimal, since the trading basis at each Exchange may
 * be different, thus a DiscreteAmount cannot be used.
 * 
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@DiscriminatorValue(value = "GeneralOrder")
// @Cacheable
@Table(name = "GeneralOrder", indexes = { @Index(columnList = "fillType"), @Index(columnList = "portfolio"), @Index(columnList = "parentFill"),
		@Index(columnList = "parentOrder"), @Index(columnList = "listing"), @Index(columnList = "version"), @Index(columnList = "revision") })
public class GeneralOrder extends Order {
	@AssistedInject
	public GeneralOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Listing listing, @Assisted BigDecimal volume) {
		super(time);
		this.getId();
		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		super.setPortfolio(portfolio);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
		this.fillType = FillType.MARKET;
		this.positionEffect = PositionEffect.OPEN;

	}

	// So we need a filed that tells us to trigger on open quanity?
	@AssistedInject
	public GeneralOrder(@Assisted GeneralOrder generalOrder) {
		super(generalOrder.getTime());

		this.getId();
		this.children = new CopyOnWriteArrayList<Order>();
		this.orderGroup = generalOrder.getOrderGroup();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		super.setPortfolio(generalOrder.getPortfolio());
		this.listing = generalOrder.getListing();
		this.market = generalOrder.getMarket();

		this.volume = generalOrder.getVolume();
		this.fillType = generalOrder.getFillType();
		this.positionEffect = generalOrder.getPositionEffect();
		if (generalOrder.getParentOrder() != null) {
			synchronized (generalOrder.getParentOrder()) {
				generalOrder.getParentOrder().addChildOrder(this);
			}
			this.setParentOrder(generalOrder.getParentOrder());
		}
		this.limitPrice = generalOrder.getLimitPrice();
		this.marketPrice = generalOrder.getMarketPrice();
		this.stopAmount = generalOrder.getStopAmount();
		this.stopPercentage = generalOrder.getStopPercentage();
		this.targetPercentage = generalOrder.getTargetPercentage();
		this.triggerInterval = generalOrder.getTriggerInterval();
		this.stopPrice = generalOrder.getStopPrice();
		this.lastBestPrice = generalOrder.getLastBestPrice();

		this.targetAmount = generalOrder.getTargetAmount();
		this.targetPrice = generalOrder.getTargetPrice();
		this.trailingStopAmount = generalOrder.getTrailingStopAmount();
		this.trailingStopPrice = generalOrder.getTrailingStopPrice();
		this.usePosition = generalOrder.getUsePosition();

		this.timeToLive = generalOrder.getTimeToLive();
		this.marginType = generalOrder.getMarginType();
		this.comment = generalOrder.getComment();
		this.expiration = generalOrder.getExpiration();
		this.executionInstruction = generalOrder.getExecutionInstruction();
		if (generalOrder.getParentFill() != null) {
			synchronized (generalOrder.getParentFill()) {
				generalOrder.getParentFill().addChildOrder(this);
			}
			this.setParentFill(generalOrder.getParentFill());
		}

	}

	@AssistedInject
	public GeneralOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Order parentOrder, @Assisted Listing listing,
			@Assisted BigDecimal volume) {
		super(time);
		this.getId();

		this.children = new CopyOnWriteArrayList<Order>();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		super.setPortfolio(portfolio);
		synchronized (parentOrder) {
			parentOrder.addChildOrder(this);
		}
		this.setParentOrder(parentOrder);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
		this.fillType = FillType.MARKET;
		this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public GeneralOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted BigDecimal volume, @Assisted FillType type) {
		super(time);
		this.getId();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		super.setPortfolio(portfolio);
		this.market = market;
		this.listing = market.getListing();
		double minimumOrderSize = volume.compareTo(BigDecimal.ZERO) < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//set it to the order size or the minumum size for the market.
		this.volume = (volume.compareTo(BigDecimal.ZERO) != 0 && volume.abs().compareTo(BigDecimal.valueOf(minimumOrderSize).abs()) < 0)
				? DecimalAmount.of(BigDecimal.valueOf(minimumOrderSize))
				: DecimalAmount.of(volume);

		this.fillType = type;
		this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public GeneralOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Listing listing, @Assisted BigDecimal volume,
			@Assisted FillType type) {
		super(time);
		this.getId();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		super.setPortfolio(portfolio);
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
		this.fillType = type;
		this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public GeneralOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Order parentOrder, @Assisted Market market,
			@Assisted BigDecimal volume, @Assisted FillType type) {
		super(time);
		this.getId();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		super.setPortfolio(portfolio);
		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		synchronized (parentOrder) {
			parentOrder.addChildOrder(this);
		}
		this.setParentOrder(parentOrder);
		this.market = market;
		this.listing = market.getListing();
		//set it to the order size or the minumum size for the market.
		double minimumOrderSize = volume.compareTo(BigDecimal.ZERO) < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		this.volume = (volume.compareTo(BigDecimal.ZERO) != 0 && volume.abs().compareTo(BigDecimal.valueOf(minimumOrderSize).abs()) < 0)
				? DecimalAmount.of(BigDecimal.valueOf(minimumOrderSize))
				: DecimalAmount.of(volume);
		this.fillType = type;
		this.positionEffect = PositionEffect.OPEN;
	}

	@AssistedInject
	public GeneralOrder(@Assisted Instant time, @Assisted Fill parentFill, @Assisted Market market, @Assisted BigDecimal volume, @Assisted FillType type) {
		super(time);
		this.getId();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		super.setPortfolio(parentFill.getPortfolio());
		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		parentFill.addChildOrder(this);

		this.setParentFill(parentFill);
		this.market = market;
		this.listing = market.getListing();
		//set it to the order size or the minumum size for the market.
		double minimumOrderSize = volume.compareTo(BigDecimal.ZERO) < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);

		this.volume = (volume.compareTo(BigDecimal.ZERO) != 0 && volume.abs().compareTo(BigDecimal.valueOf(minimumOrderSize).abs()) < 0)
				? DecimalAmount.of(BigDecimal.valueOf(minimumOrderSize))
				: DecimalAmount.of(volume);
		this.fillType = type;
		this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public GeneralOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Listing listing, @Assisted String volume) {
		super(time);
		this.getId();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		super.setPortfolio(portfolio);
		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.listing = listing;
		this.volume = DecimalAmount.of(volume);
		this.fillType = FillType.MARKET;
		this.positionEffect = PositionEffect.OPEN;

	}

	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(name = "listing")
	public Listing getListing() {
		return listing;
	}

	@Override
	public Order withLimitPrice(String price) {
		this.setLimitPrice(DecimalAmount.of(price));
		return this;
	}

	@Override
	public Order withLimitPrice(DiscreteAmount price) {
		this.setLimitPriceDecimal(price.asBigDecimal());
		return this;
	}

	@Override
	public Order withLimitPrice(BigDecimal price) {
		this.setLimitPrice(DecimalAmount.of(price));
		// this.fillType=FillType
		return this;
	}

	@Override
	public Order withMarketPrice(String price) {
		this.setMarketPrice(DecimalAmount.of(price));
		return this;
	}

	@Override
	public Order withMarketPrice(DiscreteAmount price) {
		this.setMarketPriceDecimal(price.asBigDecimal());
		return this;
	}

	@Override
	public Order withMarketPrice(BigDecimal price) {
		this.setMarketPrice(DecimalAmount.of(price));
		return this;
	}

	@Override
	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(name = "market")
	public Market getMarket() {
		return market;
	}

	@Column(precision = 18, scale = 8)
	public BigDecimal getVolumeDecimal() {

		if (volume == null)
			volume = DecimalAmount.of(BigDecimal.ZERO);
		return volume.asBigDecimal();

	}

	@Column(precision = 18, scale = 8)
	public BigDecimal getLimitPriceDecimal() {
		if (limitPrice == null)
			return null;
		return limitPrice.asBigDecimal();

	}

	@Column(precision = 18, scale = 8)
	public BigDecimal getStopAmountDecimal() {
		if (stopAmount == null)
			return null;
		return stopAmount.asBigDecimal();

	}

	@Column(precision = 18, scale = 8)
	public BigDecimal getTargetAmountDecimal() {
		if (targetAmount == null)
			return null;
		return targetAmount.asBigDecimal();

	}

	@Column(precision = 18, scale = 8)
	public BigDecimal getStopPriceDecimal() {
		if (stopPrice == null)
			return null;
		return stopPrice.asBigDecimal();

	}

	@Column(precision = 18, scale = 8)
	public BigDecimal getLastBestPriceDecimal() {
		if (lastBestPrice == null)
			return null;
		return lastBestPrice.asBigDecimal();

	}

	@Column(precision = 18, scale = 8)
	public BigDecimal getTargetPriceDecimal() {
		if (targetPrice == null)
			return null;
		return targetPrice.asBigDecimal();

	}

	@Column(precision = 18, scale = 8)
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
	public DecimalAmount getMarketPrice() {
		return marketPrice;
	}

	@Override
	@Transient
	public DecimalAmount getStopAmount() {
		return stopAmount;
	}

	@Override
	public double getStopPercentage() {
		return stopPercentage;
	}

	@Override
	public double getTargetPercentage() {
		return targetPercentage;
	}

	@Override
	public double getTriggerInterval() {
		return triggerInterval;
	}

	@Override
	@Transient
	public DecimalAmount getTrailingStopAmount() {
		return trailingStopAmount;
	}

	@Override
	@Transient
	public DecimalAmount getTargetAmount() {
		return targetAmount;
	}

	@Override
	@Transient
	public DecimalAmount getStopPrice() {
		return stopPrice;
	}

	@Override
	@Transient
	public DecimalAmount getLastBestPrice() {
		return lastBestPrice;
	}

	@Override
	@Transient
	public DecimalAmount getTargetPrice() {
		return targetPrice;
	}

	@Override
	@Transient
	public DecimalAmount getTrailingStopPrice() {
		return trailingStopPrice;
	}

	@Override
	@Transient
	public Amount getUnfilledVolume() {
		//TODO chache the unfille volume and reset when any child order get a fill, whne any child order is added, when any chile order is removed, 
		Amount filled = DecimalAmount.ZERO;
		Amount unfilled = DecimalAmount.ZERO;
		if (volume.isZero())
			return DecimalAmount.ZERO;
		for (Fill fill : getFills())
			filled = filled.plus(fill.getVolume());

		ArrayList<Fill> allChildFills = new ArrayList<Fill>();
		getAllFillsByParentOrder(this, allChildFills);

		for (Fill fill : allChildFills) {
			if (volume == null || fill.getVolume() == null)
				return null;
			if (!volume.isZero() && (fill.getVolume().isPositive() && volume.isPositive()) || (fill.getVolume().isNegative() && volume.isNegative()))
				filled = filled.plus(fill.getVolume());
		}
		unfilled = (volume.isNegative()) ? (volume.abs().minus(filled.abs())).negate() : volume.abs().minus(filled.abs());

		return unfilled;

	}

	@Override
	@Transient
	public Amount getOpenVolume() {
		if (this.getParentFill() != null)
			return this.getParentFill().getOpenVolume();
		else
			return DecimalAmount.ZERO;

	}

	public synchronized void addFill(Fill fill) {

		if (!getFills().contains(fill))
			synchronized (getFills()) {
				getFills().add(fill);
			}

	}

	@Override
	@Transient
	public boolean isFilled() {
		return getUnfilledVolume().equals(DecimalAmount.ZERO);
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return (getParentFill() != null) ? getParentFill() : getParentOrder();
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
		String s = "GeneralOrder{" + "id=" + getId() + "/" + System.identityHashCode(this) + ",time=" + (getTime() != null ? (FORMAT.print(getTime())) : "")
				+ ", parentOrder=" + (getParentOrder() == null ? "null" : getParentOrder().getId() + "/" + System.identityHashCode(getParentOrder()))
				+ ", parentFill=" + (getParentFill() == null ? "null" : getParentFill().getId()) + ", listing=" + listing + ", volume=" + volume;
		//+ ", unfilled volume="+ (getUnfilledVolume() == null ? "null" : getUnfilledVolume());
		if (market != null)
			s += ", market=" + market;
		if (limitPrice != null && limitPrice.asBigDecimal() != null)
			s += ", limitPrice=" + limitPrice;
		if (stopAmount != null && stopAmount.asBigDecimal() != null)
			s += ", stopAmount=" + stopAmount;
		if (stopPercentage != 0)
			s += ", stopPercentage=" + stopPercentage;
		if (targetAmount != null && targetAmount.asBigDecimal() != null)
			s += ", targetAmount=" + targetAmount;
		if (targetPercentage != 0)
			s += ", targetPercentage=" + targetPercentage;
		if (triggerInterval != 0)
			s += ", triggerInterval=" + triggerInterval;
		if (trailingStopAmount != null && trailingStopAmount.asBigDecimal() != null)
			s += ", trailingStopAmount=" + trailingStopAmount;
		if (trailingStopPrice != null && trailingStopPrice.asBigDecimal() != null)
			s += ", trailingStopPrice=" + trailingStopPrice;
		if (comment != null)
			s += ", comment=" + comment;
		if (positionEffect != null)
			s += ", position effect=" + positionEffect;
		if (getExpiryTime() != null)
			s += ", ExpiryTime=" + getExpiryTime();
		if (getTimeToLive() != 0)
			s += ", TimeToLive=" + getTimeToLive();
		if (fillType != null)
			s += ", type=" + fillType;
		if (executionInstruction != null)
			s += ", execution instruction=" + executionInstruction;
		if (stopPrice != null)
			s += ", stop price=" + stopPrice;
		if (lastBestPrice != null)
			s += ", last best price=" + lastBestPrice;
		if (targetPrice != null)
			s += ", target price=" + targetPrice;
		if (usePosition)
			s += ", use position=" + usePosition;
		if (hasFills())
			s += ", averageFillPrice=" + getAverageFillPrice();
		s += '}';
		return s;
	}

	protected GeneralOrder() {
	}

	protected GeneralOrder(Instant time) {
		super(time);
	}

	public synchronized void setVolume(DecimalAmount volume) {
		this.volume = volume;
	}

	public synchronized void setVolumeDecimal(BigDecimal volume) {
		this.volume = new DecimalAmount(volume);
	}

	protected synchronized void setLimitPrice(DecimalAmount limitPrice) {
		this.limitPrice = limitPrice;
	}

	protected synchronized void setMarketPrice(DecimalAmount marketPrice) {
		this.marketPrice = marketPrice;
	}

	protected synchronized void setLimitPriceDecimal(BigDecimal limitPrice) {
		if (limitPrice != null) {
			this.limitPrice = DecimalAmount.of(limitPrice);
		}
	}

	protected synchronized void setMarketPriceDecimal(BigDecimal marketPrice) {
		if (marketPrice != null) {
			this.marketPrice = DecimalAmount.of(marketPrice);
		}
	}

	@Override
	public synchronized void setTargetPrice(DecimalAmount targetPrice) {
		this.targetPrice = targetPrice;
		if (getParentFill() != null && targetPrice != null)
			getParentFill().setTargetPriceCount(targetPrice.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

	}

	public synchronized void setTargetPriceDecimal(BigDecimal targetPrice) {
		if (targetPrice != null) {
			this.targetPrice = DecimalAmount.of(targetPrice);
		}
	}

	@Override
	public synchronized void setStopAmount(DecimalAmount stopAmount) {
		this.stopAmount = stopAmount;
		if (getParentFill() != null && stopAmount != null && this.getMarket() != null && this.getMarket().getPriceBasis() != 0)
			getParentFill().setStopAmountCount(stopAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

	}

	@Override
	public synchronized void setStopPercentage(double stopPercentage) {
		this.stopPercentage = stopPercentage;
		if (getParentFill() != null && stopPercentage != 0 && getLimitPrice() != null)
			this.setStopAmount((DecimalAmount) getLimitPrice().times(stopPercentage, Remainder.ROUND_EVEN));

		// getParentFill().setStopAmountCount(stopAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());
		//getParentFill().set//StopAmountCount(stopAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

	}

	@Override
	public synchronized void setTargetPercentage(double targetPercentage) {
		this.targetPercentage = targetPercentage;
		if (getParentFill() != null && targetPercentage != 0)
			this.setTargetAmount((DecimalAmount) getLimitPrice().times(targetPercentage, Remainder.ROUND_EVEN));
		// getParentFill().setStopAmountCount(stopAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());
		//getParentFill().set//StopAmountCount(stopAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

	}

	@Override
	public synchronized void setTriggerInterval(double triggerInterval) {
		this.triggerInterval = triggerInterval;

	}

	@Override
	public synchronized void setTrailingStopAmount(DecimalAmount trailingStopAmount) {
		this.trailingStopAmount = trailingStopAmount;
		if (getParentFill() != null && trailingStopAmount != null)
			getParentFill().setTrailingStopAmountCount(trailingStopAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

	}

	@Override
	public synchronized void setTargetAmount(DecimalAmount targetAmount) {
		this.targetAmount = targetAmount;
		if (getParentFill() != null && targetAmount != null)
			getParentFill().setTargetAmountCount(targetAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

	}

	@Override
	public synchronized void setStopPrice(DecimalAmount stopPrice) {
		if (stopPrice != null) {
			this.stopPrice = stopPrice;
			if (getParentFill() != null)

				getParentFill().setStopPriceCount(stopPrice.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());
		}

	}

	@Override
	public synchronized void setLastBestPrice(DecimalAmount lastBestPrice) {
		if (lastBestPrice.isMax() || lastBestPrice.isMin())
			log.debug("test");
		if (lastBestPrice != null) {
			this.lastBestPrice = lastBestPrice;
			if (getParentFill() != null)

				getParentFill().setLastBestPriceCount(lastBestPrice.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());
		}

	}

	public synchronized void setStopAmountDecimal(BigDecimal stopAmount) {
		if (stopAmount != null) {
			this.stopAmount = DecimalAmount.of(stopAmount);
		}
	}

	public synchronized void setTargetAmountDecimal(BigDecimal targetAmount) {
		if (targetAmount != null) {
			this.targetAmount = DecimalAmount.of(targetAmount);
		}
	}

	public synchronized void setStopPriceDecimal(BigDecimal stopPrice) {
		if (stopPrice != null) {
			this.stopPrice = DecimalAmount.of(stopPrice);
		}
	}

	public synchronized void setLastBestPriceDecimal(BigDecimal lastBestPrice) {
		if (lastBestPrice != null) {
			if (lastBestPrice.compareTo(new BigDecimal("92233720368")) > 0)
				log.debug("test");
			this.lastBestPrice = DecimalAmount.of(lastBestPrice);
		}
	}

	@Override
	public synchronized void setTrailingStopPrice(DecimalAmount trailingStopPrice) {
		this.trailingStopPrice = trailingStopPrice;
	}

	public synchronized void setTrailingStopPriceDecimal(BigDecimal trailingStopPrice) {
		if (trailingStopPrice != null) {
			this.trailingStopPrice = DecimalAmount.of(trailingStopPrice);
		}
	}

	public synchronized void setListing(Listing listing) {
		this.listing = listing;
		//this.market = null;
	}

	/*	@Override
		public synchronized void setMarket(Market market) {
			this.market = market;
			if (market != null)
				this.listing = market.getListing();
		}*/

	private Listing listing;
	private Market market;
	private DecimalAmount volume;
	private DecimalAmount limitPrice;
	private DecimalAmount marketPrice;
	private DecimalAmount stopAmount;
	private double stopPercentage;
	private double targetPercentage;
	private double triggerInterval;
	private DecimalAmount trailingStopAmount;
	private DecimalAmount stopPrice;
	private DecimalAmount lastBestPrice;
	private DecimalAmount targetAmount;
	private DecimalAmount targetPrice;
	private DecimalAmount trailingStopPrice;
	private Amount forcastedFees;

	// private Amount unfilled;

	@Override
	public void delete() {
		// TODO Auto-generated method stub

	}

	@Override
	public void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void setMarket(Market market) {
		this.market = market;

	}

}
