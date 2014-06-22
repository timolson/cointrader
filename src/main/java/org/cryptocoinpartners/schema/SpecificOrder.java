package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import java.math.BigDecimal;
import java.util.Collection;


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


    public SpecificOrder(Market market, long volumeCount) {
        this.market = market;
        this.volumeCount = volumeCount;
    }


    public SpecificOrder(Market market, Amount volume) {
        this.market = market;
        this.volumeCount = volume.toBasis(market.getVolumeBasis(),Remainder.DISCARD).getCount();
    }


    public SpecificOrder(Market market, BigDecimal volume) {
        this(market,new DecimalAmount(volume));
    }


    public SpecificOrder(Market market, double volume) {
        this(market,new DecimalAmount(new BigDecimal(volume)));
    }


    @ManyToOne(optional = false)
    public Market getMarket() { return market; }


    @Transient
    public DiscreteAmount getVolume() {
        if( volume == null )
            volume = amount().fromVolumeCount(volumeCount);
        return volume;
    }


    @Transient
    @Nullable
    public DiscreteAmount getLimitPrice() {
        if( limitPriceCount == 0 )
            return null;
        if( limitPrice == null )
            limitPrice = amount().fromPriceCount(limitPriceCount);
        return limitPrice;
    }


    @Transient
    @Nullable
    public DiscreteAmount getStopPrice() {
        if( stopPriceCount == 0 )
            return null;
        if( stopPrice == null )
            stopPrice = amount().fromPriceCount(stopPriceCount);
        return stopPrice;
    }


    @Transient
    public DiscreteAmount getUnfilledVolume() {
        return new DiscreteAmount(getUnfilledVolumeCount(),market.getVolumeBasis());
    }


    @Transient
    public long getUnfilledVolumeCount() {
        long filled = 0;
        Collection<Fill> fills = getFills();
        if( fills == null )
            return volumeCount;
        for( Fill fill : fills )
            filled += fill.getVolumeCount();
        return volumeCount - filled;
    }


    @Transient
    public boolean isFilled() {
        return getUnfilledVolumeCount() == 0;
    }


    @Transient public boolean isBid() { return volumeCount > 0; }


    public void copyCommonOrderProperties(GeneralOrder generalOrder) {
        setTime(generalOrder.getTime());
        setEmulation(generalOrder.isEmulation());
        setExpiration(generalOrder.getExpiration());
        setFund(generalOrder.getFund());
        setMarginType(generalOrder.getMarginType());
        setPanicForce(generalOrder.getPanicForce());
    }


    public String toString() {
        return "SpecificOrder{" +
                       "id=" + getId() +
                       ", parentOrder=" + (getParentOrder() == null ? "null" : getParentOrder().getId()) +
                       ", market=" + market +
                       ", volumeCount=" + volumeCount +
                       ", limitPriceCount=" + limitPriceCount +
                       ", stopPriceCount=" + stopPriceCount +
                       '}';
    }

    
    // JPA
    protected long getVolumeCount() { return volumeCount; }
    /** 0 if no limit is set */
    protected long getLimitPriceCount() { return limitPriceCount; }
    /** 0 if no limit is set */
    protected long getStopPriceCount() { return stopPriceCount; }
    protected SpecificOrder() { }
    protected void setMarket(Market market) { this.market = market; }
    protected void setVolumeCount(long volumeCount) { this.volumeCount = volumeCount; volume = null; }
    protected void setLimitPriceCount(long limitPriceCount) { this.limitPriceCount = limitPriceCount; limitPrice = null; }
    protected void setStopPriceCount(long stopPriceCount) { this.stopPriceCount = stopPriceCount; stopPrice = null; }


    private Market.MarketAmountBuilder amount() {
        if( amountBuilder == null )
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
