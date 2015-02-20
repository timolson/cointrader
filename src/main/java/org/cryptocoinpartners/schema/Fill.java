package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.util.FeesUtil;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * A Fill represents some completion of an Order.  The volume of the Fill might be less than the requested volume of the
 * Order
 *
 * @author Tim Olson
 */
@Entity
public class Fill extends RemoteEvent {
    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static Object lock = new Object();
    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    private static final String SEPARATOR = ",";

    public Fill(SpecificOrder order, Instant time, Market market, long priceCount, long volumeCount) {
        this.order = order;
        this.market = market;
        this.priceCount = priceCount;
        this.volumeCount = volumeCount;
        this.portfolio = order.getPortfolio();
        this.stopPriceCount = (order.getStopPrice() != null) ? order.getStopPrice().getCount() : 0;

    }

    public Fill(SpecificOrder order, Instant time, Market market, long priceCount, long volumeCount, Amount commission) {
        this.order = order;
        this.market = market;
        this.priceCount = priceCount;
        this.volumeCount = volumeCount;
        this.commission = commission;
        this.portfolio = order.getPortfolio();
        this.stopPriceCount = (order.getStopPrice() != null) ? order.getStopPrice().getCount() : 0;
    }

    public @ManyToOne(cascade = { CascadeType.MERGE, CascadeType.REMOVE })
    @JoinColumn(name = "`order`")
    SpecificOrder getOrder() {
        return order;
    }

    @Transient
    public boolean isLong() {

        return getVolume().isPositive();
    }

    public void addChild(Order order) {
        synchronized (lock) {
            getChildren().add(order);
        }
    }

    @Nullable
    @OneToMany
    public Collection<Order> getChildren() {
        if (children == null)
            children = new ArrayList<Order>();
        synchronized (lock) {
            return children;
        }
    }

    protected void setChildren(List<Order> children) {
        this.children = children;
    }

    @Transient
    public boolean hasChildren() {
        return !getChildren().isEmpty();
    }

    @Transient
    public boolean isShort() {

        return getVolume().isNegative();
    }

    @Nullable
    @OneToMany(mappedBy = "fill")
    //, mappedBy = "fill")
    //(fetch = FetchType.EAGER)
    public Collection<Transaction> getTransactions() {
        if (transactions == null)
            transactions = Collections.synchronizedList(new ArrayList<Transaction>());
        synchronized (lock) {
            return transactions;
        }
    }

    public void addTransaction(Transaction transaction) {

        synchronized (lock) {
            getTransactions().add(transaction);
        }
    }

    protected void setTransactions(Collection<Transaction> transactions) {
        this.transactions = transactions;
    }

    @ManyToOne(cascade = { CascadeType.MERGE, CascadeType.REMOVE })
    public Market getMarket() {
        return market;
    }

    @Transient
    public Amount getPrice() {
        return new DiscreteAmount(priceCount, market.getPriceBasis());
    }

    @Transient
    public Amount getStopPrice() {
        if (stopPriceCount == 0)
            return null;
        return new DiscreteAmount(stopPriceCount, market.getPriceBasis());
    }

    public long getPriceCount() {
        return priceCount;
    }

    public long getStopPriceCount() {
        return stopPriceCount;
    }

    @Transient
    public Amount getVolume() {
        return new DiscreteAmount(volumeCount, market.getVolumeBasis());
    }

    public long getVolumeCount() {
        return volumeCount;
    }

    @Transient
    public Amount getCommission() {
        if (commission == null)
            setCommission(FeesUtil.getCommission(this));
        return commission;
    }

    @Transient
    public Amount getMargin() {
        if (margin == null)
            setMargin(FeesUtil.getMargin(this));

        return margin;
    }

    @Transient
    public Portfolio getPortfolio() {
        return portfolio;
    }

    @Transient
    public FillType getFillType() {
        return getOrder().getFillType();
    }

    @Override
    public String toString() {

        return "FillID" + getId() + "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "OrderID=" + order.getId() + SEPARATOR
                + "Type=" + getFillType() + SEPARATOR + "Market=" + market + SEPARATOR + "Price=" + getPrice() + SEPARATOR + "Volume=" + getVolume();
    }

    // JPA
    protected Fill() {
    }

    public void setOrder(SpecificOrder order) {
        this.order = order;
    }

    protected void setMarket(Market market) {
        this.market = market;
    }

    protected void setPriceCount(long priceCount) {
        this.priceCount = priceCount;
    }

    public void setStopPriceCount(long stopPriceCount) {
        this.stopPriceCount = stopPriceCount;
    }

    protected void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    protected void setVolumeCount(long volumeCount) {
        this.volumeCount = volumeCount;
    }

    protected void setCommission(Amount commission) {
        this.commission = commission;
    }

    protected void setMargin(Amount margin) {
        this.margin = margin;
    }

    private Collection<Order> children;

    private SpecificOrder order;
    private Market market;
    private long priceCount;
    private long stopPriceCount;
    private long volumeCount;
    private Amount commission;
    private Amount margin;
    private Collection<Transaction> transactions;
    private Portfolio portfolio;

}
