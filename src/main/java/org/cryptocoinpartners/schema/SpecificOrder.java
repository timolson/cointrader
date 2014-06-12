package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;


/**
 * SpecificOrders are bound to a Market and express their prices and amounts in DiscreteAmounts with the correct
 * basis for the Market.  A SpecificOrder may be immediately passed to a Exchange for execution without any further
 * reduction or processing.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
public class SpecificOrder extends Order {


    public SpecificOrder(Market market, long amountCount) {
        this.market = market;
        this.amountCount = amountCount;
    }


    public SpecificOrder(Market market, double amount) {
        this.market = market;
        this.amountCount = DiscreteAmount.countForValueRounded(amount, market.getVolumeBasis());
    }


    public SpecificOrder(Market market, DiscreteAmount amount) {
        amount.assertBasis(market.getVolumeBasis());
        this.market = market;
        this.amountCount = amount.getCount();
    }


    @ManyToOne(optional = false)
    public Market getMarket() { return market; }


    @Transient
    public DiscreteAmount getAmount() { return new DiscreteAmount(amountCount, market.getVolumeBasis()); }


    public long getAmountCount() { return amountCount; }


    @Transient
    public DiscreteAmount getLimitPrice() { return new DiscreteAmount(limitPriceCount, market.getPriceBasis()); }


    public long getLimitPriceCount() { return limitPriceCount; }


    @Transient
    public DiscreteAmount getStopPrice() { return new DiscreteAmount(stopPriceCount, market.getPriceBasis()); }


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
    protected void setMarket(Market market) { this.market = market; }
    protected void setAmountCount(long amountCount) { this.amountCount = amountCount; }
    protected void setLimitPriceCount(long limitPriceCount) { this.limitPriceCount = limitPriceCount; }
    protected void setStopPriceCount(long stopPriceCount) { this.stopPriceCount = stopPriceCount; }
    public void setParentOrder(GeneralOrder parentOrder) { this.parentOrder = parentOrder; }


    private GeneralOrder parentOrder;
    private Market market;
    private long amountCount;
    private long limitPriceCount;
    private long stopPriceCount;
}
