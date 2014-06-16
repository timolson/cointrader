package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import java.math.BigDecimal;


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


    public SpecificOrder(Market market, double volume) {
        this.market = market;
        this.volumeCount = DiscreteAmount.countForValueRounded(volume, market.getVolumeBasis());
    }


    public SpecificOrder(Market market, DiscreteAmount volume) {
        volume.assertBasis(market.getVolumeBasis());
        this.market = market;
        this.volumeCount = volume.getCount();
    }


    public SpecificOrder(Market market, BigDecimal volume) {
        this.market = market;
        this.volumeCount = DiscreteAmount.fromValue(volume, market.getVolumeBasis(), Remainder.DISCARD).getCount();
    }


    @ManyToOne(optional = false)
    public Market getMarket() { return market; }


    @Transient
    public DiscreteAmount getVolume() { return new DiscreteAmount(volumeCount, market.getVolumeBasis()); }


    public long getVolumeCount() { return volumeCount; }


    @Transient DiscreteAmount getUnfilledVolume() {
        return new DiscreteAmount(getUnfilledVolumeCount(),market.getVolumeBasis());
    }


    @Transient long getUnfilledVolumeCount() {
        long filled = 0;
        for( Fill fill : getFills() )
            filled += fill.getVolumeCount();
        return volumeCount - filled;
    }


    @Transient
    public DiscreteAmount getLimitPrice() { return new DiscreteAmount(limitPriceCount, market.getPriceBasis()); }


    /** 0 if no limit is set */
    public long getLimitPriceCount() { return limitPriceCount; }


    @Transient
    public DiscreteAmount getStopPrice() { return new DiscreteAmount(stopPriceCount, market.getPriceBasis()); }


    /** 0 if no limit is set */
    public long getStopPriceCount() { return stopPriceCount; }


    @Transient
    public boolean isFilled() {
        return getUnfilledVolumeCount() == 0;
    }


    @Transient public boolean isBid() { return volumeCount > 0; }

    @ManyToOne
    public GeneralOrder getParentOrder() { return parentOrder; }


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
                       "parentOrder=" + parentOrder.getId() +
                       ", market=" + market +
                       ", volumeCount=" + volumeCount +
                       ", limitPriceCount=" + limitPriceCount +
                       ", stopPriceCount=" + stopPriceCount +
                       '}';
    }


    protected SpecificOrder() { }
    protected void setMarket(Market market) { this.market = market; }
    protected void setVolumeCount(long volumeCount) { this.volumeCount = volumeCount; }
    protected void setLimitPriceCount(long limitPriceCount) { this.limitPriceCount = limitPriceCount; }
    protected void setStopPriceCount(long stopPriceCount) { this.stopPriceCount = stopPriceCount; }
    public void setParentOrder(GeneralOrder parentOrder) { this.parentOrder = parentOrder; }


    private GeneralOrder parentOrder;
    private Market market;
    private long volumeCount;
    private long limitPriceCount;
    private long stopPriceCount;
}
