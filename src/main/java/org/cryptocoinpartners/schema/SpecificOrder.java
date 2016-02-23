package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import jline.internal.Log;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.XchangeUtil;
import org.hibernate.annotations.Type;
import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * SpecificOrders are bound to a Market and express their prices and volumes in DiscreteAmounts with the correct
 * basis for the Market.  A SpecificOrder may be immediately passed to a Exchange for execution without any further
 * reduction or processing.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@Cacheable
public class SpecificOrder extends Order implements SpecOrder {
    @AssistedInject
    public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.externalFills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();

        this.remoteKey = getId().toString();
        this.market = market;
        this.volumeCount = volumeCount;
        super.setPortfolio(portfolio);
        this.placementCount = 1;
        //this.positionEffect = PositionEffect.OPEN;

    }

    @AssistedInject
    public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount,
            @Assisted @Nullable String comment) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.externalFills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.remoteKey = getId().toString();
        this.market = market;
        this.volumeCount = volumeCount;
        super.setComment(comment);
        super.setPortfolio(portfolio);
        this.placementCount = 1;
        //   this.positionEffect = PositionEffect.OPEN;

    }

    @AssistedInject
    public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount,
            @Assisted Order parentOrder, @Assisted @Nullable String comment) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.externalFills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.remoteKey = getId().toString();
        this.market = market;
        this.volumeCount = volumeCount;
        if (comment != null)
            super.setComment(comment);
        parentOrder.addChildOrder(this);
        this.setParentOrder(parentOrder);
        super.setPortfolio(portfolio);
        this.placementCount = 1;
        //  this.positionEffect = PositionEffect.OPEN;

    }

    @AssistedInject
    public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted long volumeCount, @Assisted Order parentOrder) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.externalFills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.remoteKey = getId().toString();
        this.market = market;
        this.volumeCount = volumeCount;
        parentOrder.addChildOrder(this);
        this.setParentOrder(parentOrder);
        super.setPortfolio(portfolio);
        this.placementCount = 1;
        //  this.positionEffect = PositionEffect.OPEN;

    }

    @AssistedInject
    public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted Amount volume,
            @Assisted @Nullable String comment) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.externalFills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.remoteKey = getId().toString();
        this.market = market;

        this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();

        super.setComment(comment);
        super.setPortfolio(portfolio);
        this.placementCount = 1;
        this.positionEffect = PositionEffect.OPEN;

    }

    @AssistedInject
    public SpecificOrder(@Assisted LimitOrder limitOrder, @Assisted com.xeiam.xchange.Exchange xchangeExchange, @Assisted Portfolio portfolio,
            @Assisted Date date) {
        super(new Instant(date.getTime()));
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
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
        long vol = limitOrder.getTradableAmount().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue();
        this.volume = new DiscreteAmount(vol, market.getPriceBasis());
        this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
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
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();
        this.externalFills = new CopyOnWriteArrayList<Fill>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.market = market;
        // setMarket(market);
        // this.market = market;
        this.remoteKey = getId().toString();
        this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
        super.setComment(comment);
        parentOrder.addChildOrder(this);
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
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.externalFills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.market = specficOrder.getMarket();
        this.remoteKey = getId().toString();
        if (!specficOrder.getFills().isEmpty())
            this.volumeCount = specficOrder.getUnfilledVolumeCount();
        this.volumeCount = specficOrder.getUnfilledVolumeCount();
        //  this.volumeCount = specficOrder.getOpenVolumeCount();
        if (specficOrder.getComment() != null)
            super.setComment(specficOrder.getComment());
        if (specficOrder.getParentOrder() != null) {
            specficOrder.getParentOrder().addChildOrder(this);
            this.setParentOrder(specficOrder.getParentOrder());
        }
        super.setPortfolio(specficOrder.getPortfolio());
        this.placementCount = 1;
        this.positionEffect = specficOrder.getPositionEffect();
        this.limitPriceCount = specficOrder.getLimitPriceCount();
        this.fillType = specficOrder.getFillType();
        this.executionInstruction = specficOrder.getExecutionInstruction();
    }

    public SpecificOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted double volume,
            @Assisted @Nullable String comment) {
        this(time, portfolio, market, new DecimalAmount(new BigDecimal(volume)), comment);
    }

    @Override
    @ManyToOne(optional = false)
    public Market getMarket() {
        if (market == null) {
            Log.debug("null market");
            return null;
        }
        if (market.getListing() == null) {
            Log.debug("null listing");
            return null;
        }

        return market;
    }

    @Transient
    public boolean update(LimitOrder limitOrder) {
        try {
            this.setRemoteKey(limitOrder.getId());
            this.setTimeReceived(new Instant(limitOrder.getTimestamp()));
            long vol = limitOrder.getTradableAmount().divide(BigDecimal.valueOf(market.getPriceBasis())).longValue();
            this.volume = new DiscreteAmount(vol, market.getPriceBasis());
            this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
            return true;
        } catch (Error e) {

            e.printStackTrace();
            return false;
        }

        //     parentOrder.addChild(this);
        //   this.setParentOrder(parentOrder);

    }

    @Override
    @Embedded
    public DiscreteAmount getVolume() {

        if (volume == null)

            volume = amount().fromVolumeCount(volumeCount);
        if (volume == null)
            System.out.println("volume is null");
        return volume;
    }

    protected void setVolume(DiscreteAmount volume) {
        this.volume = volume;
    }

    @Override
    @Nullable
    @Embedded
    public DiscreteAmount getLimitPrice() {
        if (limitPriceCount == 0)
            return null;
        if (limitPrice == null)
            limitPrice = amount().fromPriceCount(limitPriceCount);
        return limitPrice;
    }

    @Override
    @Nullable
    @Embedded
    public DiscreteAmount getMarketPrice() {
        if (marketPriceCount == 0)
            return null;
        if (marketPrice == null)
            marketPrice = amount().fromPriceCount(marketPriceCount);
        return marketPrice;
    }

    protected void setLimitPrice(DiscreteAmount limitPrice) {
        this.limitPrice = limitPrice;
    }

    protected void setMarketPrice(DiscreteAmount marketPrice) {
        this.marketPrice = marketPrice;
    }

    @Override
    @Transient
    public DiscreteAmount getStopAmount() {
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
        return null;
    }

    @Override
    @Transient
    public DiscreteAmount getStopPrice() {
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
        return new DiscreteAmount(getUnfilledVolumeCount(), market.getVolumeBasis());
    }

    @Transient
    public long getUnfilledVolumeCount() {
        long filled = 0;
        List<Fill> fills = getFills();
        if (fills == null || fills.isEmpty())
            return volumeCount;
        for (Fill fill : fills)
            filled += fill.getVolumeCount();

        long unfilled = (volumeCount < 0) ? (Math.abs(volumeCount) - Math.abs(filled)) * -1 : Math.abs(volumeCount) - Math.abs(filled);
        return unfilled;
    }

    @Transient
    public DiscreteAmount getExternalUnfilledVolume() {
        return new DiscreteAmount(getExternalUnfilledVolumeCount(), market.getVolumeBasis());
    }

    @Transient
    public long getExternalUnfilledVolumeCount() {
        long filled = 0;
        List<Fill> fills = getExternalFills();
        if (fills == null || fills.isEmpty())
            return volumeCount;
        for (Fill fill : fills)
            filled += fill.getVolumeCount();

        long unfilled = (volumeCount < 0) ? (Math.abs(volumeCount) - Math.abs(filled)) * -1 : Math.abs(volumeCount) - Math.abs(filled);
        return unfilled;
    }

    @Transient
    public long getOpenVolumeCount() {
        long filled = 0;
        List<Fill> fills = getFills();
        if (fills == null || fills.isEmpty())
            return volumeCount;
        for (Fill fill : fills)
            filled += fill.getOpenVolumeCount();
        return filled;
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

        return "SpecificOrder{ id=" + getId() + SEPARATOR + "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "remote key="
                + getRemoteKey() + SEPARATOR + "parentOrder=" + (getParentOrder() == null ? "null" : getParentOrder().getId()) + SEPARATOR + "parentFill={"
                + (getParentFill() == null ? "null}" : getParentFill() + "}") + SEPARATOR + "portfolio=" + getPortfolio() + SEPARATOR + "market=" + market
                + SEPARATOR + "unfilled volume=" + getUnfilledVolume() + SEPARATOR + "volumeCount=" + getVolume()
                + (limitPriceCount != 0 ? (SEPARATOR + "limitPriceCount=" + getLimitPrice()) : "") + (SEPARATOR + "PlacementCount=" + getPlacementCount())
                + (getComment() == null ? "" : (SEPARATOR + "Comment=" + getComment()))
                + (getFillType() == null ? "" : (SEPARATOR + "Order Type=" + getFillType()))
                + (getPositionEffect() == null ? "" : (SEPARATOR + "Position Effect=" + getPositionEffect()))
                + (getExecutionInstruction() == null ? "" : (SEPARATOR + "Execution Instruction=" + getExecutionInstruction()))
                + (hasFills() ? (SEPARATOR + "averageFillPrice=" + getAverageFillPrice()) : "") + "}";
    }

    // JPA
    protected long getVolumeCount() {
        return volumeCount;
    }

    /** 0 if no limit is set */
    protected long getLimitPriceCount() {
        return limitPriceCount;
    }

    protected long getMarketPriceCount() {
        return marketPriceCount;
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
    public void setMarket(Market market) {
        this.market = market;
    }

    public void setVolumeCount(long volumeCount) {
        this.volumeCount = volumeCount;
        volume = null;
    }

    public void setLimitPriceCount(long limitPriceCount) {
        this.limitPriceCount = limitPriceCount;
        limitPrice = null;
    }

    public void setMarketPriceCount(long marketPriceCount) {
        this.marketPriceCount = marketPriceCount;
        marketPrice = null;
    }

    @Override
    public Order withLimitPrice(String price) {
        this.setLimitPriceCount(DecimalAmount.of(price).toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount());
        return this;
    }

    @Override
    public Order withLimitPrice(DiscreteAmount price) {
        this.setLimitPriceCount(price.getCount());
        return this;
    }

    @Override
    public Order withLimitPrice(BigDecimal price) {
        this.setLimitPriceCount(DecimalAmount.of(price).toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount());

        return this;
    }

    @Override
    public Order withMarketPrice(String price) {
        this.setMarketPriceCount(DecimalAmount.of(price).toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount());
        return this;
    }

    @Override
    public Order withMarketPrice(DiscreteAmount price) {
        this.setMarketPriceCount(price.getCount());
        return this;
    }

    @Override
    public Order withMarketPrice(BigDecimal price) {
        this.setMarketPriceCount(DecimalAmount.of(price).toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount());

        return this;
    }

    public void setPlacementCount(int placementCount) {
        this.placementCount = placementCount;

    }

    @Override
    public void setStopAmount(DecimalAmount stopAmount) {
        throw new NotImplementedException();

    }

    @Override
    public void setTargetAmount(DecimalAmount targetAmount) {
        throw new NotImplementedException();

    }

    @Override
    public void setStopPrice(DecimalAmount stopPrice) {
        throw new NotImplementedException();

    }

    @Override
    public void setTargetPrice(DecimalAmount targetPrice) {
        throw new NotImplementedException();

    }

    @Override
    public void setTrailingStopPrice(DecimalAmount stopPrice) {
        throw new NotImplementedException();

    }

    @Basic(optional = true)
    public String getRemoteKey() {
        return remoteKey;
    }

    public void setRemoteKey(@Nullable String remoteKey) {
        this.remoteKey = remoteKey;
    }

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    @Basic(optional = true)
    public Instant getTimeReceived() {
        return timeReceived;
    }

    public void addExternalFill(Fill fill) {
        this.externalFills.add(fill);
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

    protected void setTimeReceived(@Nullable Instant timeReceived) {
        this.timeReceived = timeReceived;
        if (timeReceived != null)
            this.timestampReceived = timeReceived.getMillis();
    }

    private Market.MarketAmountBuilder amount() {
        if (getMarket() == null)
            Log.debug("test");
        if (amountBuilder == null)
            amountBuilder = getMarket().buildAmount();
        if (amountBuilder == null)
            Log.debug("test");
        return amountBuilder;
    }

    private Market market;

    private DiscreteAmount volume;
    private DiscreteAmount limitPrice;
    private DiscreteAmount marketPrice;
    private int placementCount;
    private long volumeCount;
    private long limitPriceCount;
    private long marketPriceCount;
    private String remoteKey;
    private Instant timeReceived;
    private long timestampReceived;
    protected List<Fill> externalFills;
    private static Object lock = new Object();

    private Market.MarketAmountBuilder amountBuilder;

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

}
