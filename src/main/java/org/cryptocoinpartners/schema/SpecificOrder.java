package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.Market.MarketAmountBuilder;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.XchangeUtil;
import org.hibernate.annotations.Type;
import org.joda.time.Instant;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * SpecificOrders are bound to a Market and express their prices and volumes in DiscreteAmounts with the correct basis for the Market. A SpecificOrder
 * may be immediately passed to a Exchange for execution without any further reduction or processing.
 * 
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@DiscriminatorValue(value = "SpecificOrder")
//@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
// @Cacheable
public class SpecificOrder extends Order {
	@AssistedInject
	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount) {
		super(time);
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.remoteKey = getUuid().toString();
		this.market = market;
		//set it to the order size or the minumum size for the market.

		double minimumOrderSize = volumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//	long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(volumeCount);
		this.setUnfilledVolumeCount(volumeCount);
		super.setPortfolio(portfolio);
		this.placementCount = 1;

		//this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount,
			@Assisted @Nullable String comment) {
		super(time);
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.children = new CopyOnWriteArrayList<Order>();
		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.remoteKey = getUuid().toString();
		this.market = market;
		//set it to the order size or the minumum size for the market.
		double minimumOrderSize = volumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//	long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(volumeCount);
		this.setUnfilledVolumeCount(volumeCount);
		super.setComment(comment);
		super.setPortfolio(portfolio);
		this.placementCount = 1;

		//   this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount,
			@Assisted Order parentOrder, @Assisted @Nullable String comment) {
		super(time);
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();
		this.children = new CopyOnWriteArrayList<Order>();
		this.usePosition = parentOrder.getUsePosition();
		this.setTimeToLive(parentOrder.getTimeToLive());
		this.setExecutionInstruction(parentOrder.getExecutionInstruction());
		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.remoteKey = getUuid().toString();
		this.market = market;
		//set it to the order size or the minumum size for the market.
		double minimumOrderSize = volumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(volumeCount);
		this.setUnfilledVolumeCount(volumeCount);
		if (comment != null)
			super.setComment(comment);
		synchronized (parentOrder) {
			parentOrder.addChildOrder(this);
		}
		this.setParentOrder(parentOrder);
		super.setPortfolio(portfolio);
		this.placementCount = 1;

		//  this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount,
			@Assisted Order parentOrder) {
		super(time);
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();
		this.usePosition = parentOrder.getUsePosition();
		this.setTimeToLive(parentOrder.getTimeToLive());
		this.setExecutionInstruction(parentOrder.getExecutionInstruction());
		this.children = new CopyOnWriteArrayList<Order>();
		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.remoteKey = getUuid().toString();
		this.market = market;
		//set it to the order size or the minumum size for the market.
		double minimumOrderSize = volumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(volumeCount);
		this.setUnfilledVolumeCount(volumeCount);
		synchronized (parentOrder) {
			parentOrder.addChildOrder(this);
		}
		this.setParentOrder(parentOrder);
		super.setPortfolio(portfolio);
		this.placementCount = 1;

		//  this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted Amount volume,
			@Assisted @Nullable String comment) {
		super(time);
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.children = new CopyOnWriteArrayList<Order>();

		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.remoteKey = getUuid().toString();
		this.market = market;
		long unadjustedVolumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount();
		double minimumOrderSize = unadjustedVolumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//	long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(unadjustedVolumeCount);

		//			(unadjustedVolumeCount != 0 && (Math.abs(unadjustedVolumeCount) < Math.abs(minOrderSizeCount)) &&) ? minOrderSizeCount : unadjustedVolumeCount);
		this.setUnfilledVolumeCount(unadjustedVolumeCount);
		super.setComment(comment);
		super.setPortfolio(portfolio);
		this.placementCount = 1;
		this.positionEffect = PositionEffect.OPEN;

	}

	@AssistedInject
	public SpecificOrder(@Assisted LimitOrder limitOrder, @Assisted org.knowm.xchange.Exchange xchangeExchange, @Assisted Portfolio portfolio,
			@Assisted Date date) {
		super(new Instant(date.getTime()));
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.children = new CopyOnWriteArrayList<Order>();
		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		// currencyPair
		// Asset baseCCY = Asset.forSymbol(limitOrder.getBaseSymbol().toUpperCase());
		// Asset quoteCCY = Asset.forSymbol(limitOrder.getCounterSymbol().toUpperCase());
		Listing listing = Listing.forPair(Asset.forSymbol(limitOrder.getCurrencyPair().base.getCurrencyCode().toUpperCase()),
				Asset.forSymbol(limitOrder.getCurrencyPair().counter.getCurrencyCode().toUpperCase()));
		Exchange exchange = XchangeUtil.getExchangeForMarket(xchangeExchange);
		this.market = market.findOrCreate(exchange, listing);

		this.setRemoteKey(limitOrder.getId());
		long vol = limitOrder.getOriginalAmount().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue();

		// this.volume = 
		//set it to the order size or the minumum size for the market.
		long unadjustedVolumeCount = new DiscreteAmount(vol, market.getVolumeBasis()).toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount();
		double minimumOrderSize = unadjustedVolumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//	long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(unadjustedVolumeCount);
		//		(unadjustedVolumeCount != 0 && (Math.abs(unadjustedVolumeCount)) < Math.abs(minOrderSizeCount)) ? minOrderSizeCount : unadjustedVolumeCount);
		this.setUnfilledVolumeCount(unadjustedVolumeCount);
		this.positionEffect = PositionEffect.OPEN;
		super.setComment(comment);
		this.placementCount = 1;
		//     parentOrder.addChild(this);
		//   this.setParentOrder(parentOrder);
		super.setPortfolio(portfolio);
		// this.placementCount = 1;

	}

	@AssistedInject
	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted Amount volume, @Assisted Order parentOrder,
			@Assisted @Nullable String comment) {
		super(time);
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();
		this.usePosition = parentOrder.getUsePosition();
		this.setTimeToLive(parentOrder.getTimeToLive());
		this.setExecutionInstruction(parentOrder.getExecutionInstruction());
		this.children = new CopyOnWriteArrayList<Order>();
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.market = market;
		// setMarket(market);
		// this.market = market;
		this.remoteKey = getUuid().toString();

		long unadjustedVolumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount();
		double minimumOrderSize = unadjustedVolumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//	long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(unadjustedVolumeCount);
		//		(unadjustedVolumeCount != 0 && (Math.abs(unadjustedVolumeCount) < Math.abs(minOrderSizeCount))) ? minOrderSizeCount : unadjustedVolumeCount);
		this.setUnfilledVolumeCount(unadjustedVolumeCount);
		super.setComment(comment);
		synchronized (parentOrder) {
			parentOrder.addChildOrder(this);
		}
		this.setParentOrder(parentOrder);
		super.setPortfolio(portfolio);
		this.placementCount = 1;
		this.positionEffect = (parentOrder.getPositionEffect() == null) ? PositionEffect.OPEN : parentOrder.getPositionEffect();

	}

	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted BigDecimal volume,
			@Assisted @Nullable String comment) {
		this(time, portfolio, market, new DecimalAmount(volume), comment);
	}

	@AssistedInject
	public SpecificOrder(@Assisted SpecificOrder specficOrder) {

		super(specficOrder.getTime());
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();
		this.usePosition = specficOrder.getUsePosition();
		this.children = new CopyOnWriteArrayList<Order>();
		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new CopyOnWriteArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.market = specficOrder.getMarket();
		this.orderGroup = specficOrder.getOrderGroup();
		this.remoteKey = getUuid().toString();

		//set it to the order size or the minumum size for the market.
		double minimumOrderSize = specficOrder.getUnfilledVolumeCount() < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//	long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(specficOrder.getUnfilledVolumeCount());
		//(specficOrder.getUnfilledVolumeCount() != 0 && Math.abs(specficOrder.getUnfilledVolumeCount()) < Math.abs(minOrderSizeCount))
		//		? minOrderSizeCount
		//		: specficOrder.getUnfilledVolumeCount());
		//  this.volumeCount = specficOrder.getOpenVolumeCount();
		this.setUnfilledVolumeCount(specficOrder.getUnfilledVolumeCount());
		if (specficOrder.getComment() != null)
			super.setComment(specficOrder.getComment());
		if (specficOrder.getParentOrder() != null) {
			synchronized (specficOrder.getParentOrder()) {
				specficOrder.getParentOrder().addChildOrder(this);
			}
			this.setParentOrder(specficOrder.getParentOrder());
		}
		if (specficOrder.getParentFill() != null) {
			synchronized (specficOrder.getParentFill()) {
				specficOrder.getParentFill().addChildOrder(this);
			}
			this.setParentFill(specficOrder.getParentFill());
		}
		super.setPortfolio(specficOrder.getPortfolio());
		this.placementCount = 1;
		this.positionEffect = specficOrder.getPositionEffect();
		this.limitPriceCount.set(specficOrder.getLimitPriceCount());
		this.fillType = specficOrder.getFillType();
		this.executionInstruction = specficOrder.getExecutionInstruction();

	}

	@AssistedInject
	public SpecificOrder(@Assisted org.knowm.xchange.dto.Order exchangeOrder, @Assisted Market market, @Assisted @Nullable Portfolio portfolio) {

		super(new Instant(exchangeOrder.getTimestamp()));
		this.getUuid();
		this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

		this.children = new CopyOnWriteArrayList<Order>();
		this.fills = Collections.synchronizedList(new ArrayList<Fill>());
		this.externalFills = new ArrayList<Fill>();
		this.transactions = new CopyOnWriteArrayList<Transaction>();
		this.market = market;
		this.remoteKey = exchangeOrder.getId();

		long unadjustedVolumeCount = DiscreteAmount.roundedCountForBasis(
				(exchangeOrder.getType() != null && (exchangeOrder.getType() == OrderType.ASK || exchangeOrder.getType() == OrderType.EXIT_BID)
						? exchangeOrder.getOriginalAmount().negate()
						: exchangeOrder.getOriginalAmount()),
				market.getVolumeBasis());
		double minimumOrderSize = unadjustedVolumeCount < 0 ? market.getMinimumOrderSize(market) * -1 : market.getMinimumOrderSize(market);
		//long minOrderSizeCount = (long) (minimumOrderSize * (1 / market.getVolumeBasis()));
		this.setVolumeCount(unadjustedVolumeCount);
		//		unadjustedVolumeCount != 0 && (Math.abs(unadjustedVolumeCount) < Math.abs(minOrderSizeCount)) ? minOrderSizeCount : unadjustedVolumeCount);
		this.setUnfilledVolumeCount(unadjustedVolumeCount);
		super.setPortfolio(portfolio);
		this.placementCount = 1;
		this.positionEffect = (exchangeOrder.getType() != null
				&& (exchangeOrder.getType() == OrderType.EXIT_BID || exchangeOrder.getType() == OrderType.EXIT_ASK) ? PositionEffect.CLOSE
						: PositionEffect.OPEN);
		LimitOrder limitXchangeOrder;
		if (exchangeOrder instanceof org.knowm.xchange.dto.trade.LimitOrder) {
			limitXchangeOrder = (LimitOrder) exchangeOrder;
			if (this.limitPriceCount == null)
				this.limitPriceCount = new AtomicLong(DiscreteAmount.roundedCountForBasis(limitXchangeOrder.getLimitPrice(), market.getPriceBasis()));
			else
				this.limitPriceCount.set(DiscreteAmount.roundedCountForBasis(limitXchangeOrder.getLimitPrice(), market.getPriceBasis()));
			this.fillType = FillType.LIMIT;
		} else {
			this.fillType = FillType.MARKET;
		}

		this.executionInstruction = ExecutionInstruction.TAKER;

	}

	public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted double volume,
			@Assisted @Nullable String comment) {
		this(time, portfolio, market, new DecimalAmount(new BigDecimal(volume)), comment);
	}

	@Override
	@ManyToOne(optional = false)
	@JoinColumn(name = "market", nullable = false)
	public Market getMarket() {

		return market;
	}

	@Transient
	public boolean update(LimitOrder limitOrder) {
		try {
			this.setRemoteKey(limitOrder.getId());
			this.setTimeReceived(new Instant(limitOrder.getTimestamp()));
			long vol = limitOrder.getOriginalAmount().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue();
			this.volume = new DiscreteAmount(vol, market.getPriceBasis());
			this.volumeCount.set(volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount());
			unfilledVolume = null;
			//	this.unfilledVolumeCount = new AtomicLong(this.volumeCount.get());

			return true;
		} catch (Error e) {

			e.printStackTrace();
			return false;
		}

		//     parentOrder.addChild(this);
		//   this.setParentOrder(parentOrder);

	}

	@Override
	@Nullable
	@Embedded
	public DiscreteAmount getVolume() {

		if (volume == null && amount() != null)

			volume = amount().fromVolumeCount(volumeCount.get());
		//  if (volume == null)
		//    System.out.println("volume is null");
		return volume;
	}

	protected synchronized void setVolume(DiscreteAmount volume) {
		this.volume = volume;
	}

	@Override
	@Nullable
	@Embedded
	public DiscreteAmount getLimitPrice() {

		if (limitPrice == null && amount() != null)

			limitPrice = amount().fromPriceCount(limitPriceCount.get());
		//  if (volume == null)
		//    System.out.println("volume is null");
		return limitPrice;

		//   if (limitPriceCount == 0)
		//     return null;
	}

	@Nullable
	public long getLimitPriceCount() {
		if (limitPriceCount != null)

			return limitPriceCount.get();
		else
			return Long.MIN_VALUE;

	}

	@Override
	@Nullable
	@Embedded
	public DiscreteAmount getMarketPrice() {
		//     if (marketPriceCount == 0)
		//       return null;
		if (marketPrice == null && marketPriceCount == null)
			return null;
		if (marketPrice == null && marketPriceCount == null && amount() != null)
			marketPrice = amount().fromPriceCount(marketPriceCount.get());
		return marketPrice;
	}

	protected synchronized void setLimitPrice(DiscreteAmount limitPrice) {
		this.limitPrice = limitPrice;
	}

	protected synchronized void setMarketPrice(DiscreteAmount marketPrice) {
		this.marketPrice = marketPrice;
	}

	@Override
	@Transient
	public DiscreteAmount getStopAmount() {
		return null;
	}

	@Override
	@Transient
	public double getStopPercentage() {
		return 0.0;
	}

	@Override
	@Transient
	public double getTargetPercentage() {
		return 0.0;
	}

	@Override
	@Transient
	public double getTriggerInterval() {
		return 0.0;
	}

	@Override
	@Transient
	public DiscreteAmount getTrailingStopAmount() {
		return null;
	}

	@Override
	@Transient
	public DiscreteAmount getTargetAmount() {
		return null;
	}

	@Override
	@Transient
	public TransactionType getTransactionType() {
		if (isBid())
			return TransactionType.BUY;
		else
			return TransactionType.SELL;
		// return null;
	}

	@Override
	@Transient
	public DiscreteAmount getStopPrice() {
		return null;
	}

	@Override
	@Transient
	public DiscreteAmount getLastBestPrice() {
		return null;
	}

	@Override
	@Transient
	public DiscreteAmount getTargetPrice() {
		return null;
	}

	@Override
	@Transient
	public DiscreteAmount getTrailingStopPrice() {

		return null;
	}

	@Override
	@Transient
	public DiscreteAmount getUnfilledVolume() {

		if (unfilledVolume == null)
			unfilledVolume = new DiscreteAmount(getUnfilledVolumeCount(), market.getVolumeBasis());
		return unfilledVolume;
	}

	@Transient
	public long getUnfilledVolumeCount() {
		//	if (this.fills == null || this.fills.isEmpty())
		//		return getVolumeCount();

		return unfilledVolumeCount.get();

	}

	@Transient
	public DiscreteAmount getExternalUnfilledVolume() {
		return new DiscreteAmount(getExternalUnfilledVolumeCount(), market.getVolumeBasis());
	}

	@Transient
	public long getExternalUnfilledVolumeCount() {
		return externalUnfilledVolumeCount.get();

	}

	@Transient
	public long getOpenVolumeCount() {
		long filled = 0;
		List<Fill> fills = getFills();
		if (fills == null || fills.isEmpty())
			return volumeCount.get();
		synchronized (fills) {
			for (Fill fill : fills)
				synchronized (fill) {
					filled += fill.getOpenVolumeCount();
				}
		}
		return filled;
	}

	@Override
	@Transient
	public boolean isFilled() {
		return (getUnfilledVolumeCount() == 0);
	}

	@Override
	@Transient
	public boolean isBid() {
		return volumeCount.get() > 0;
	}

	public void copyCommonOrderProperties(GeneralOrder generalOrder) {
		//setTime(generalOrder.getTime());
		setTimeToLive(generalOrder.getTimeToLive());
		setEmulation(generalOrder.isEmulation());
		setExpiration(generalOrder.getExpiration());
		setPortfolio(generalOrder.getPortfolio());
		setMarginType(generalOrder.getMarginType());
		setPanicForce(generalOrder.getPanicForce());
	}

	@Override
	public String toString() {

		return "SpecificOrder{ UUID=" + getUuid() + "/" + System.identityHashCode(this) + SEPARATOR + "Id=" + (getId() == null ? "null" : getId()) + SEPARATOR
				+ "version=" + getVersion() + SEPARATOR + "revision=" + getRevision() + SEPARATOR + "time="
				+ (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "remote key=" + getRemoteKey() + SEPARATOR + "parentOrder="
				+ (getParentOrder() == null ? "null" : getParentOrder().getUuid() + "/" + System.identityHashCode(getParentOrder())) + SEPARATOR
				+ "parentFill={" + (getParentFill() == null ? "null}" : getParentFill() + "}") + SEPARATOR + "portfolio=" + getPortfolio() + SEPARATOR
				+ "market=" + getMarket() + SEPARATOR + "unfilled volume=" + getUnfilledVolume() + SEPARATOR + "volume=" + getVolume() + SEPARATOR
				+ (getLimitPrice() == null ? "limitPrice=null" : "limitPrice=" + getLimitPrice()) + SEPARATOR + "PlacementCount=" + getPlacementCount()
				+ (getComment() == null ? SEPARATOR + "getComment=null" : (SEPARATOR + "Comment=" + getComment())) + SEPARATOR
				+ (getFillType() == null ? "Order Type=null" : ("Order Type=" + getFillType())) + SEPARATOR
				+ (getExpiryTime() == null ? "expiry time=null" : ("expiry time=" + getExpiryTime())) + SEPARATOR + "time to live=" + getTimeToLive()
				+ SEPARATOR + (getUsePosition() ? "Use Position=null" : ("Use Position=" + getUsePosition())) + SEPARATOR
				+ (getPositionEffect() == null ? "Position Effect=null" : ("Position Effect=" + getPositionEffect())) + SEPARATOR
				+ (getExecutionInstruction() == null ? "Execution Instruction=null" : ("Execution Instruction=" + getExecutionInstruction())) + SEPARATOR
				+ (hasFills() ? ("averageFillPrice=" + getAverageFillPrice()) : "averageFillPrice=null") + "}";
	}

	// JPA
	protected long getVolumeCount() {
		if (volumeCount != null)

			return volumeCount.get();
		else
			return 0L;

	}

	protected long getMarketPriceCount() {
		if (marketPriceCount != null)
			return marketPriceCount.get();
		else
			return Long.MIN_VALUE;
	}

	public int getPlacementCount() {
		if (placementCount == 0)
			return 1;
		return placementCount;

	}

	protected SpecificOrder() {

	}

	protected SpecificOrder(Instant time) {
		super(time);
	}

	@Override
	public synchronized void setMarket(Market market) {
		this.market = market;
	}

	public synchronized void setVolumeCount(long volumeCount) {
		this.volumeCount.set(volumeCount);
		//	this.unfilledVolumeCount = new AtomicLong(volumeCount);
		volume = null;
		unfilledVolume = null;
	}

	@Transient
	public synchronized void setUnfilledVolumeCount(long unfilledVolumeCount) {
		this.unfilledVolumeCount.set(unfilledVolumeCount);
		unfilledVolume = null;
	}

	public synchronized void setLimitPriceCount(long limitPriceCount) {

		this.limitPriceCount.set(limitPriceCount);
		limitPrice = null;

	}

	public synchronized void setMarketPriceCount(long marketPriceCount) {

		this.marketPriceCount.set(marketPriceCount);
		marketPrice = null;
		getMarketPrice();
	}

	@Override
	public synchronized Order withLimitPrice(String price) {
		this.setLimitPriceCount(DecimalAmount.of(price).toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN).getCount());
		return this;
	}

	@Override
	public synchronized Order withLimitPrice(DiscreteAmount price) {
		this.setLimitPriceCount(price.getCount());
		return this;
	}

	@Override
	public synchronized Order withLimitPrice(BigDecimal price) {
		this.setLimitPriceCount(DecimalAmount.of(price).toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN).getCount());

		return this;
	}

	@Override
	public synchronized Order withMarketPrice(String price) {
		this.setMarketPriceCount(DecimalAmount.of(price).toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN).getCount());
		return this;
	}

	@Override
	public synchronized Order withMarketPrice(DiscreteAmount price) {
		this.setMarketPriceCount(price.getCount());
		return this;
	}

	@Override
	public synchronized Order withMarketPrice(BigDecimal price) {
		this.setMarketPriceCount(DecimalAmount.of(price).toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN).getCount());

		return this;
	}

	public synchronized void setPlacementCount(int placementCount) {
		this.placementCount = placementCount;

	}

	@Override
	public synchronized void setStopAmount(DecimalAmount stopAmount) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setStopPercentage(double stopPercentage) {
		throw new NotImplementedException();

	}

	@Override
	@Transient
	public EntityBase getParent() {

		return getParentOrder();
	}

	@Override
	public synchronized void setTargetPercentage(double targetPercentage) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setTriggerInterval(double triggerInterval) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setTrailingStopAmount(DecimalAmount stopAmount) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setTargetAmount(DecimalAmount targetAmount) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setStopPrice(DecimalAmount stopPrice) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setLastBestPrice(DecimalAmount lastBestPrice) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setTargetPrice(DecimalAmount targetPrice) {
		throw new NotImplementedException();

	}

	@Override
	public synchronized void setTrailingStopPrice(DecimalAmount stopPrice) {
		throw new NotImplementedException();

	}

	@Basic(optional = true)
	public String getRemoteKey() {
		return remoteKey;
	}

	@Transient
	public boolean isInternal() {
		return getUuid().toString().equals(getRemoteKey());
	}

	@Transient
	public boolean isExternal() {
		return !isInternal();
	}

	public synchronized void setRemoteKey(@Nullable String remoteKey) {
		this.remoteKey = remoteKey;
	}

	@Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
	@Basic(optional = true)
	public Instant getTimeReceived() {
		return timeReceived;
	}

	public synchronized void addExternalFill(Fill fill) {

		this.externalFills.add(fill);
		externalUnfilledVolumeCount.set(externalUnfilledVolumeCount.get() - fill.getVolumeCount());

	}

	@Override
	public synchronized void addFill(Fill fill) {
		this.fills.add(fill);
		long totalFilledCount = 0;
		for (Fill orderFill : this.fills) {
			totalFilledCount = totalFilledCount + orderFill.getVolumeCount();

		}
		unfilledVolumeCount.set(getVolumeCount() - totalFilledCount);
		unfilledVolume = null;
	}

	@Transient
	public List<Fill> getExternalFills() {
		return externalFills;
		// }
	}

	@Transient
	public long getTimestampReceived() {
		return timestampReceived;
	}

	protected synchronized void setTimeReceived(@Nullable Instant timeReceived) {
		this.timeReceived = timeReceived;
		if (timeReceived != null)
			this.timestampReceived = timeReceived.getMillis();
	}

	private MarketAmountBuilder amount() {
		if (getMarket() == null)
			return null;
		if (amountBuilder == null)
			amountBuilder = getMarket().buildAmount();
		if (amountBuilder != null)

			return amountBuilder;
		return null;
	}

	private Market market;
	private DiscreteAmount volume;
	private DiscreteAmount limitPrice;
	private DiscreteAmount marketPrice;
	private DiscreteAmount unfilledVolume;
	private int placementCount;
	private AtomicLong volumeCount = new AtomicLong(0L);
	private AtomicLong unfilledVolumeCount = new AtomicLong(0L);
	private AtomicLong externalUnfilledVolumeCount = new AtomicLong(0L);
	private AtomicLong limitPriceCount = new AtomicLong(0L);
	private AtomicLong marketPriceCount = new AtomicLong(0L);
	private String remoteKey;
	private Instant timeReceived;
	private long timestampReceived;
	protected List<Fill> externalFills;
	private static Object lock = new Object();

	private transient Market.MarketAmountBuilder amountBuilder;

	@Override
	public void delete() {
		// TODO Auto-generated method stub

	}

	@Override
	@Transient
	public Amount getOpenVolume() {
		if (this.getParentFill() != null)
			return this.getParentFill().getOpenVolume();
		else
			return DecimalAmount.ZERO;

	}
}
