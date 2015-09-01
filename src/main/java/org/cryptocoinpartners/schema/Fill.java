package org.cryptocoinpartners.schema;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.util.FeesUtil;
import org.cryptocoinpartners.util.PersistUtil;
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
@Cacheable
public class Fill extends RemoteEvent {
    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static Object lock = new Object();
    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    private static final String SEPARATOR = ",";
    private PositionEffect positionEffect;

    public Fill(SpecificOrder order, Instant time, Instant timeReceived, Market market, long priceCount, long volumeCount, String remoteKey) {
        super(time, timeReceived, remoteKey);
        this.priceCount = priceCount;
        this.volumeCount = volumeCount;
        this.openVolumeCount = volumeCount;
        this.positionType = (openVolumeCount > 0) ? PositionType.LONG : PositionType.SHORT;
        this.order = order;
        this.order.addFill(this);

        this.market = market;
        if (priceCount == 0)
            this.priceCount = priceCount;
        this.portfolio = order.getPortfolio();
        this.stopAmountCount = (order.getStopAmount() != null) ? order.getStopAmount().getCount() : 0;
        this.positionEffect = order.getPositionEffect();
        //  this.id = getId();
        this.version = getVersion();

    }

    public Fill(SpecificOrder order, Instant time, Instant timeReceived, Market market, long priceCount, long volumeCount, Amount commission, String remoteKey) {
        super(time, timeReceived, remoteKey);
        this.order = order;
        this.order.addFill(this);

        this.market = market;
        if (priceCount == 0)
            this.priceCount = priceCount;

        this.priceCount = priceCount;
        this.volumeCount = volumeCount;
        this.openVolumeCount = volumeCount;
        this.positionType = (openVolumeCount > 0) ? PositionType.LONG : PositionType.SHORT;

        this.commission = commission;
        this.portfolio = order.getPortfolio();
        this.stopAmountCount = (order.getStopAmount() != null) ? order.getStopAmount().getCount() : 0;
        this.positionEffect = order.getPositionEffect();

    }

    // public @ManyToOne(cascade = { CascadeType.MERGE, CascadeType.REMOVE, CascadeType.REFRESH })
    public @ManyToOne
    @JoinColumn(name = "`order`")
    SpecificOrder getOrder() {
        return order;
    }

    public void persit() {
        //   synchronized (persistanceLock) {
        //  if (this.hasFills()) {
        //    for (Fill fill : this.getFills())

        //PersistUtil.merge(fill);
        //}

        //  List<Fill> duplicate = PersistUtil.queryList(Fill.class, "select f from Fill f where f=?1", this);
        // if (getPosition() != null)
        //   getPosition().Persit();

        //if (duplicate == null || duplicate.isEmpty())
        PersistUtil.insert(this);
        //else
        //  PersistUtil.merge(this);
        //  }
        //Iterator<Order> itc = getChildren().iterator();
        //while (itc.hasNext()) {
        //                //  for (Fill pos : getFills()) {
        //  Order order = itc.next();

        // order.persit();
        // }

        //   synchronized (persistanceLock) {
        //  if (this.hasFills()) {
        //    for (Fill fill : this.getFills())

        //PersistUtil.merge(fill);
        //}
        //if (this.hasFills()) {
        // for (Fill fill : getFills()) {
        // if (this.hasChildren()) {
        //   for (Order order : this.getChildren())
        //     if (order.getParentFill() == this)
        //       order.persit();
        //PersistUtil.merge(order);
        // }

    }

    @Nullable
    @ManyToOne(optional = true)
    //, fetch = FetchType.EAGER)
    //cascade = CascadeType.ALL)
    @JoinColumn(name = "position")
    public Position getPosition() {
        if (openVolumeCount == 0)
            return null;
        return position;
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
        if (getOpenVolume() == null)
            return getOpenVolume().isZero();
        return getOpenVolume().isPositive();
    }

    public void addChild(Order order) {
        synchronized (lock) {
            getChildren().add(order);
        }
    }

    //@Nullable
    //@OneToMany(cascade = CascadeType.MERGE)
    //(mappedBy = "parentFill")
    //@OrderBy
    @Transient
    public List<Order> getChildren() {
        if (children == null)
            children = new CopyOnWriteArrayList<Order>();
        //  synchronized (//) {
        return children;
        // }
    }

    protected void setChildren(List<Order> children) {
        this.children = children;
    }

    @Transient
    public boolean hasChildren() {
        return !getChildren().isEmpty();
    }

    @Transient
    public boolean hasTransaction() {
        return !getTransactions().isEmpty();
    }

    @Transient
    public boolean isShort() {

        return getOpenVolume().isNegative();
    }

    @Nullable
    @OneToMany(mappedBy = "fill")
    // , fetch = FetchType.EAGER)
    // , cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    //, mappedBy = "fill")
    //(fetch = FetchType.EAGER)
    //  @Transient
    public List<Transaction> getTransactions() {
        if (transactions == null)
            transactions = new CopyOnWriteArrayList<Transaction>();

        //synchronized (lock) {
        return transactions;
        // }
    }

    public void addTransaction(Transaction transaction) {

        // synchronized (lock) {
        getTransactions().add(transaction);
        //  }
    }

    protected void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    @ManyToOne
    public Market getMarket() {
        return market;
    }

    @Transient
    public Amount getPrice() {
        if (priceCount == 0)
            return null;
        if (market.getPriceBasis() == 0)
            return null;
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
        if (getOpenVolumeCount() != 0)
            return stopPriceCount;
        else
            return 0;
    }

    public long getTargetPriceCount() {
        return targetPriceCount;
    }

    @Transient
    public Amount getVolume() {
        if (volumeCount == 0)
            return null;
        return new DiscreteAmount(getVolumeCount(), market.getVolumeBasis());
    }

    public long getVolumeCount() {
        return volumeCount;
    }

    @Transient
    public Amount getOpenVolume() {
        //   if (openVolumeCount == 0)
        //     return null;
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

    @Transient
    public PositionType getPositionType() {
        if (getOpenVolumeCount() != 0)
            return positionType;
        return PositionType.FLAT;
    }

    @ManyToOne(optional = false)
    public Portfolio getPortfolio() {
        return portfolio;
    }

    @Transient
    public FillType getFillType() {
        return getOrder().getFillType();
    }

    public PositionEffect getPositionEffect() {

        return positionEffect;

    }

    protected void setPositionEffect(PositionEffect positionEffect) {
        this.positionEffect = positionEffect;
    }

    @Override
    public String toString() {
        // + (order.getId() != null ? order.getId() : "")
        //   + (getFillType() != null ? getFillType() : "")
        return "Id=" + (getId() != null ? getId() : "") + SEPARATOR + "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR
                + "PositionType=" + (getPositionType() != null ? getPositionType() : "") + SEPARATOR + "Market=" + (market != null ? market : "") + SEPARATOR
                + "Price=" + (getPrice() != null ? getPrice() : "") + SEPARATOR + "Volume=" + (getVolume() != null ? getVolume() : "") + SEPARATOR
                + "Open Volume=" + (getOpenVolume() != null ? getOpenVolume() : "") + SEPARATOR + "Order=" + (getOrder() != null ? getOrder().getId() : "")
                + SEPARATOR + "Parent Fill=" + ((getOrder() != null && getOrder().getParentFill() != null) ? getOrder().getParentFill().getId() : "");
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

    public void setPositionType(PositionType positionType) {
        this.positionType = positionType;
    }

    protected void setPriceCount(long priceCount) {
        if (priceCount == 0)
            this.priceCount = priceCount;
        this.priceCount = priceCount;
    }

    public void setStopAmountCount(long stopAmountCount) {
        this.stopAmountCount = stopAmountCount;
    }

    public void setTargetAmountCount(long targetAmountCount) {
        this.targetAmountCount = targetAmountCount;
    }

    public void setStopPriceCount(long stopPriceCount) {
        if (stopPriceCount != 0)

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

    protected void setPosition(Position position) {
        this.position = position;
    }

    private List<Order> children = new CopyOnWriteArrayList<Order>();

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
    private PositionType positionType;
    private List<Transaction> transactions = new CopyOnWriteArrayList<Transaction>();
    private Portfolio portfolio;
    private Position position;

}
