package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;


/**
 * SpecificOrders are bound to a MarketListing and express their prices and amounts in DiscreteAmounts with the correct
 * basis for the MarketListing.  A SpecificOrder may be immediately passed to a Market for execution without any further
 * reduction or processing.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
public class SpecificOrder extends Order {


    public SpecificOrder(MarketListing marketListing, long amountCount) {
        this.marketListing = marketListing;
        this.amountCount = amountCount;
    }


    public SpecificOrder(MarketListing marketListing, double amount) {
        this.marketListing = marketListing;
        this.amountCount = DiscreteAmount.countForValueRounded(amount,marketListing.getVolumeBasis());
    }


    public SpecificOrder(MarketListing marketListing, DiscreteAmount amount) {
        amount.assertBasis(marketListing.getVolumeBasis());
        this.marketListing = marketListing;
        this.amountCount = amount.getCount();
    }


    @ManyToOne(optional = false)
    public MarketListing getMarketListing() { return marketListing; }


    @Transient
    public DiscreteAmount getAmount() { return new DiscreteAmount(amountCount,marketListing.getVolumeBasis()); }


    public long getAmountCount() { return amountCount; }


    @Transient
    public DiscreteAmount getLimitPrice() { return new DiscreteAmount(limitPriceCount,marketListing.getPriceBasis()); }


    public long getLimitPriceCount() { return limitPriceCount; }


    @Transient
    public DiscreteAmount getStopPrice() { return new DiscreteAmount(stopPriceCount,marketListing.getPriceBasis()); }


    public long getStopPriceCount() { return stopPriceCount; }


    @Transient public boolean isBid() { return amountCount > 0; }

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


    protected SpecificOrder() { }
    protected void setMarketListing(MarketListing marketListing) { this.marketListing = marketListing; }
    protected void setAmountCount(long amountCount) { this.amountCount = amountCount; }
    protected void setLimitPriceCount(long limitPriceCount) { this.limitPriceCount = limitPriceCount; }
    protected void setStopPriceCount(long stopPriceCount) { this.stopPriceCount = stopPriceCount; }
    public void setParentOrder(GeneralOrder parentOrder) { this.parentOrder = parentOrder; }


    private GeneralOrder parentOrder;
    private MarketListing marketListing;
    private long amountCount;
    private long limitPriceCount;
    private long stopPriceCount;
}
