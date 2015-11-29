package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.Instant;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * A GeneralOrder only specifies a Listing but not an Exchange.  The GeneralOrder must be processed and broken down into
 * a series of SpecificOrders before it can be placed on Markets.  GeneralOrders express their volumes and prices using
 * BigDecimal, since the trading basis at each Exchange may be different, thus a DiscreteAmount cannot be used.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@Cacheable
public class GeneralOrder extends Order {

    public GeneralOrder(Instant time, Portfolio portfolio, Listing listing, BigDecimal volume) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();
        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        super.setPortfolio(portfolio);
        this.listing = listing;
        this.volume = DecimalAmount.of(volume);
    }

    public GeneralOrder(Instant time, Portfolio portfolio, Order parentOrder, Listing listing, BigDecimal volume) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        super.setPortfolio(portfolio);
        parentOrder.addChildOrder(this);
        this.setParentOrder(parentOrder);
        this.listing = listing;
        this.volume = DecimalAmount.of(volume);
    }

    @AssistedInject
    public GeneralOrder(@Assisted Instant time, @Assisted Portfolio portfolio, @Assisted Market market, @Assisted BigDecimal volume, @Assisted FillType type) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        super.setPortfolio(portfolio);
        this.market = market;
        this.listing = market.getListing();
        this.volume = DecimalAmount.of(volume);
        this.fillType = type;
    }

    public GeneralOrder(Instant time, Portfolio portfolio, Order parentOrder, Market market, BigDecimal volume, FillType type) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        super.setPortfolio(portfolio);
        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        parentOrder.addChildOrder(this);
        this.setParentOrder(parentOrder);
        this.market = market;
        this.listing = market.getListing();
        this.volume = DecimalAmount.of(volume);
        this.fillType = type;
    }

    @AssistedInject
    public GeneralOrder(@Assisted Instant time, @Assisted Fill parentFill, @Assisted Market market, @Assisted BigDecimal volume, @Assisted FillType type) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        super.setPortfolio(parentFill.getPortfolio());
        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        parentFill.addChildOrder(this);
        this.setParentFill(parentFill);
        this.market = market;
        this.listing = market.getListing();
        this.volume = DecimalAmount.of(volume);
        this.fillType = type;
    }

    public GeneralOrder(Instant time, Portfolio portfolio, Listing listing, String volume) {
        super(time);
        this.orderUpdates = new CopyOnWriteArrayList<OrderUpdate>();

        super.setPortfolio(portfolio);
        this.children = new CopyOnWriteArrayList<Order>();

        this.fills = new CopyOnWriteArrayList<Fill>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.listing = listing;
        this.volume = DecimalAmount.of(volume);
    }

    @Nullable
    @ManyToOne(optional = true)
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
        return this;
    }

    @Override
    @Nullable
    @ManyToOne(optional = true)
    public Market getMarket() {
        return market;
    }

    public BigDecimal getVolumeDecimal() {

        if (volume == null)
            volume = DecimalAmount.of(BigDecimal.ZERO);
        return volume.asBigDecimal();

    }

    public BigDecimal getLimitPriceDecimal() {
        if (limitPrice == null)
            return null;
        return limitPrice.asBigDecimal();

    }

    public BigDecimal getStopAmountDecimal() {
        if (stopAmount == null)
            return null;
        return stopAmount.asBigDecimal();

    }

    public BigDecimal getTargetAmountDecimal() {
        if (targetAmount == null)
            return null;
        return targetAmount.asBigDecimal();

    }

    public BigDecimal getStopPriceDecimal() {
        if (stopPrice == null)
            return null;
        return stopPrice.asBigDecimal();

    }

    public BigDecimal getTargetPriceDecimal() {
        if (targetPrice == null)
            return null;
        return targetPrice.asBigDecimal();

    }

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
    public DecimalAmount getStopAmount() {
        return stopAmount;
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
        Amount filled = DecimalAmount.ZERO;
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
        Amount unfilled = (volume.isNegative()) ? (volume.abs().minus(filled.abs())).negate() : volume.abs().minus(filled.abs());
        return unfilled;

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
        String s = "GeneralOrder{" + "id=" + getId() + ", parentOrder=" + (getParentOrder() == null ? "null" : getParentOrder().getId()) + ", parentFill="
                + (getParentFill() == null ? "null" : getParentFill().getId()) + ", listing=" + listing + ", volume=" + volume + ", unfilled volume="
                + (getUnfilledVolume() == null ? "null" : getUnfilledVolume());
        if (limitPrice != null && limitPrice.asBigDecimal() != null)
            s += ", limitPrice=" + limitPrice;
        if (stopAmount != null && stopAmount.asBigDecimal() != null)
            s += ", stopPrice=" + stopAmount;
        if (trailingStopPrice != null && trailingStopPrice.asBigDecimal() != null)
            s += ", trailingStopPrice=" + trailingStopPrice;
        if (comment != null)
            s += ", comment=" + comment;
        if (positionEffect != null)
            s += ", position effect=" + positionEffect;
        if (fillType != null)
            s += ", type=" + fillType;
        if (executionInstruction != null)
            s += ", execution instruction=" + executionInstruction;
        if (stopPrice != null)
            s += ", stop price=" + stopPrice;
        if (targetPrice != null)
            s += ", target price=" + targetPrice;
        if (hasFills())
            s += ", averageFillPrice=" + averageFillPrice();
        s += '}';
        return s;
    }

    protected GeneralOrder() {
    }

    protected GeneralOrder(Instant time) {
        super(time);
    }

    public void setVolume(DecimalAmount volume) {
        this.volume = volume;
    }

    public void setVolumeDecimal(BigDecimal volume) {
        this.volume = new DecimalAmount(volume);
    }

    protected void setLimitPrice(DecimalAmount limitPrice) {
        this.limitPrice = limitPrice;
    }

    protected void setLimitPriceDecimal(BigDecimal limitPrice) {
        if (limitPrice != null) {
            this.limitPrice = DecimalAmount.of(limitPrice);
        }
    }

    @Override
    public void setTargetPrice(DecimalAmount targetPrice) {
        this.targetPrice = targetPrice;
        if (getParentFill() != null && targetPrice != null)
            getParentFill().setTargetPriceCount(targetPrice.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

    }

    public void setTargetPriceDecimal(BigDecimal targetPrice) {
        if (targetPrice != null) {
            this.targetPrice = DecimalAmount.of(targetPrice);
        }
    }

    @Override
    public void setStopAmount(DecimalAmount stopAmount) {
        this.stopAmount = stopAmount;
        if (getParentFill() != null && stopAmount != null)
            getParentFill().setStopAmountCount(stopAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

    }

    @Override
    public void setTargetAmount(DecimalAmount targetAmount) {
        this.targetAmount = targetAmount;
        if (getParentFill() != null && targetAmount != null)
            getParentFill().setTargetAmountCount(targetAmount.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());

    }

    @Override
    public void setStopPrice(DecimalAmount stopPrice) {
        if (stopPrice != null) {
            this.stopPrice = stopPrice;
            if (getParentFill() != null)

                getParentFill().setStopPriceCount(stopPrice.toBasis(this.getMarket().getPriceBasis(), Remainder.ROUND_EVEN).getCount());
        }

    }

    public void setStopAmountDecimal(BigDecimal stopAmount) {
        if (stopAmount != null) {
            this.stopAmount = DecimalAmount.of(stopAmount);
        }
    }

    public void setTargetAmountDecimal(BigDecimal targetAmount) {
        if (targetAmount != null) {
            this.targetAmount = DecimalAmount.of(targetAmount);
        }
    }

    public void setStopPriceDecimal(BigDecimal stopPrice) {
        if (stopPrice != null) {
            this.stopPrice = DecimalAmount.of(stopPrice);
        }
    }

    @Override
    public void setTrailingStopPrice(DecimalAmount trailingStopPrice) {
        this.trailingStopPrice = trailingStopPrice;
    }

    public void setTrailingStopPriceDecimal(BigDecimal trailingStopPrice) {
        if (trailingStopPrice != null) {
            this.trailingStopPrice = DecimalAmount.of(trailingStopPrice);
        }
    }

    protected void setListing(Listing listing) {
        this.listing = listing;
        this.market = null;
    }

    @Override
    public void setMarket(Market market) {
        this.market = market;
        this.listing = market.getListing();
    }

    private Listing listing;
    private Market market;
    private DecimalAmount volume;
    private DecimalAmount limitPrice;
    private DecimalAmount stopAmount;
    private DecimalAmount stopPrice;
    private DecimalAmount targetAmount;
    private DecimalAmount targetPrice;
    private DecimalAmount trailingStopPrice;
    private Amount forcastedFees;

}
