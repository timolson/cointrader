package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.Collection;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
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
        super.setPortfolio(portfolio);
        this.placementCount = 1;

    }

    public SpecificOrder(Instant time, Portfolio portfolio, Market market, long volumeCount, String comment) {
        super(time);
        this.market = market;
        this.volumeCount = volumeCount;
        super.setComment(comment);
        super.setPortfolio(portfolio);
        this.placementCount = 1;

    }

    public SpecificOrder(Instant time, Portfolio portfolio, Market market, long volumeCount, Order parentOrder, String comment) {
        super(time);
        this.market = market;
        this.volumeCount = volumeCount;
        super.setComment(comment);
        parentOrder.addChild(this);
        this.setParentOrder(parentOrder);
        super.setPortfolio(portfolio);
        this.placementCount = 1;

    }

    public SpecificOrder(Instant time, Portfolio portfolio, Market market, Amount volume, String comment) {
        super(time);
        this.market = market;
        this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
        super.setComment(comment);
        super.setPortfolio(portfolio);
        this.placementCount = 1;

    }

    public SpecificOrder(Instant time, Portfolio portfolio, Market market, Amount volume, Order parentOrder, String comment) {
        super(time);
        this.market = market;
        this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.DISCARD).getCount();
        super.setComment(comment);
        parentOrder.addChild(this);
        this.setParentOrder(parentOrder);
        super.setPortfolio(portfolio);
        this.placementCount = 1;
    }

    public SpecificOrder(Instant time, Portfolio portfolio, Market market, BigDecimal volume, String comment) {
        this(time, portfolio, market, new DecimalAmount(volume), comment);
    }

    public SpecificOrder(Instant time, Portfolio portfolio, Market market, double volume, String comment) {
        this(time, portfolio, market, new DecimalAmount(new BigDecimal(volume)), comment);
    }

    @Override
    @ManyToOne(optional = false, cascade = { CascadeType.MERGE, CascadeType.REMOVE })
    public Market getMarket() {
        return market;
    }

    @Override
    @Transient
    public DiscreteAmount getVolume() {
        if (volume == null)
            volume = amount().fromVolumeCount(volumeCount);
        return volume;
    }

    @Override
    @Transient
    @Nullable
    public DiscreteAmount getLimitPrice() {
        if (limitPriceCount == 0)
            return null;
        if (limitPrice == null)
            limitPrice = amount().fromPriceCount(limitPriceCount);
        return limitPrice;
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
                + (SEPARATOR + "PlacementCount=" + getPlacementCount()) + (getComment().isEmpty() ? "" : (SEPARATOR + "Comment=" + getComment()))
                + (getFillType().getValue().isEmpty() ? "" : (SEPARATOR + "Order Type=" + getFillType()))
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

    public int getPlacementCount() {
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

    protected void setVolumeCount(long volumeCount) {
        this.volumeCount = volumeCount;
        volume = null;
    }

    public void setLimitPriceCount(long limitPriceCount) {
        this.limitPriceCount = limitPriceCount;
        limitPrice = null;
    }

    public void setPlacementCount(int placementCount) {
        this.placementCount = placementCount;

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

    private Market.MarketAmountBuilder amount() {
        if (amountBuilder == null)
            amountBuilder = market.buildAmount();
        return amountBuilder;
    }

    private Market market;
    private DiscreteAmount volume;
    private DiscreteAmount limitPrice;
    private int placementCount;
    private long volumeCount;
    private long limitPriceCount;

    private Market.MarketAmountBuilder amountBuilder;

}
