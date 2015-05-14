package org.cryptocoinpartners.schema;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    public Fill(SpecificOrder order, Instant time, Instant timeReceived, Market market, long priceCount, long volumeCount, String remoteKey) {
        //   super(time, timeReceived, remoteKey);
        this.order = order;
        this.market = market;
        this.priceCount = priceCount;
        this.volumeCount = volumeCount;
        this.openVolumeCount = volumeCount;
        this.portfolio = order.getPortfolio();
        this.stopAmountCount = (order.getStopAmount() != null) ? order.getStopAmount().getCount() : 0;

    }

    public Fill(SpecificOrder order, Instant time, Instant timeReceived, Market market, long priceCount, long volumeCount, Amount commission, String remoteKey) {
        // super(time, timeReceived, remoteKey);
        this.order = order;
        this.market = market;
        this.priceCount = priceCount;
        this.volumeCount = volumeCount;
        this.openVolumeCount = volumeCount;
        this.commission = commission;
        this.portfolio = order.getPortfolio();
        this.stopAmountCount = (order.getStopAmount() != null) ? order.getStopAmount().getCount() : 0;
    }

    public @ManyToOne(cascade = { CascadeType.MERGE, CascadeType.REMOVE, CascadeType.REFRESH })
    @JoinColumn(name = "`order`")
    SpecificOrder getOrder() {
        return order;
    }

    @Transient
    @Nullable
    public Double getPriceAsDouble() {
        Amount price = getPrice();
        return price == null ? null : price.asDouble();
    }

    @Transient
    @Nullable
    public Double getPriceCountAsDouble() {
        Long price = getPriceCount();
        return price == null ? null : price.doubleValue();
    }

    @Transient
    public boolean isLong() {

        return getOpenVolume().isPositive();
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
            children = new ConcurrentLinkedQueue<Order>();
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

        return getOpenVolume().isNegative();
    }

    @Nullable
    @OneToMany(mappedBy = "fill", cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    //, mappedBy = "fill")
    //(fetch = FetchType.EAGER)
    public Collection<Transaction> getTransactions() {
        if (transactions == null)
            transactions = new ConcurrentLinkedQueue<Transaction>();

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

    @ManyToOne
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

    @Transient
    public Amount getTargetPrice() {
        if (targetPriceCount == 0)
            return null;
        return new DiscreteAmount(targetPriceCount, market.getPriceBasis());
    }

    public long getPriceCount() {
        return priceCount;
    }

    public long getStopPriceCount() {
        return stopPriceCount;
    }

    public long getTargetPriceCount() {
        return targetPriceCount;
    }

    @Transient
    public Amount getVolume() {
        return new DiscreteAmount(volumeCount, market.getVolumeBasis());
    }

    public long getVolumeCount() {
        return volumeCount;
    }

    @Transient
    public Amount getOpenVolume() {
        return new DiscreteAmount(openVolumeCount, market.getVolumeBasis());
    }

    public long getOpenVolumeCount() {
        return openVolumeCount;
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

    @ManyToOne(optional = false)
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

    public void setStopAmountCount(long stopAmountCount) {
        this.stopAmountCount = stopAmountCount;
    }

    public void setTargetAmountCount(long targetAmountCount) {
        this.targetAmountCount = targetAmountCount;
    }

    public void setStopPriceCount(long stopPriceCount) {
        this.stopPriceCount = stopPriceCount;
    }

    public void setTargetPriceCount(long targetPriceCount) {
        this.targetPriceCount = targetPriceCount;
    }

    protected void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    protected void setVolumeCount(long volumeCount) {
        this.volumeCount = volumeCount;
    }

    protected void setOpenVolumeCount(long openVolumeCount) {
        this.openVolumeCount = openVolumeCount;
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
    private long stopAmountCount;
    private long stopPriceCount;
    private long targetAmountCount;
    private long targetPriceCount;
    private long volumeCount;
    private long openVolumeCount;
    private Amount commission;
    private Amount margin;
    private Collection<Transaction> transactions;
    private Portfolio portfolio;

}
