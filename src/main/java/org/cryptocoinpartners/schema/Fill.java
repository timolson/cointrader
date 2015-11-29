package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PostPersist;
import javax.persistence.PrePersist;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.schema.dao.FillDao;
import org.cryptocoinpartners.util.FeesUtil;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

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
    @Inject
    protected FillDao fillDao;

    // @Inject

    @Inject
    public Fill(@Assisted SpecificOrder order, @Assisted("fillTime") Instant time, @Assisted("fillTimeReceived") Instant timeReceived, @Assisted Market market,
            @Assisted("fillPriceCount") long priceCount, @Assisted("fillVolumeCount") long volumeCount, @Assisted String remoteKey) {
        super(time, timeReceived, remoteKey);

        this.children = new CopyOnWriteArrayList<Order>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.priceCount = priceCount;
        this.volumeCount = volumeCount;
        this.openVolumeCount = volumeCount;
        this.positionType = (openVolumeCount > 0) ? PositionType.LONG : PositionType.SHORT;
        this.order = order;
        this.order.addFill(this);
        this.remoteKey = order.getId().toString();
        this.market = market;
        if (priceCount == 0)
            this.priceCount = priceCount;
        this.portfolio = order.getPortfolio();
        this.stopAmountCount = (order.getStopAmount() != null) ? order.getStopAmount().getCount() : 0;
        this.positionEffect = order.getPositionEffect();
        //  this.id = getId();
        this.version = getVersion();

    }

    //    @Override
    //    @Basic(optional = true)
    //    public String getRemoteKey() {
    //        return remoteKey;
    //    }
    //
    //    @Override
    //    public void setRemoteKey(@Nullable String remoteKey) {
    //        this.remoteKey = remoteKey;
    //    }

    public Fill(@Assisted SpecificOrder order, @Assisted Instant time, @Assisted Instant timeReceived, @Assisted Market market, @Assisted long priceCount,
            @Assisted long volumeCount, @Assisted Amount commission, @Assisted String remoteKey) {
        super(time, timeReceived, remoteKey);
        this.children = new CopyOnWriteArrayList<Order>();
        this.transactions = new CopyOnWriteArrayList<Transaction>();
        this.remoteKey = order.getId().toString();
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

    public <T> T find() {
        //   synchronized (persistanceLock) {
        try {
            return (T) fillDao.find(Order.class, this.getId());
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {
            return null;
            // System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }

    }

    // public @ManyToOne(cascade = { CascadeType.MERGE, CascadeType.REMOVE, CascadeType.REFRESH })
    public @ManyToOne
    //(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinColumn(name = "`order`")
    SpecificOrder getOrder() {
        return order;
    }

    @Override
    public synchronized void merge() {
        // try {
        // if (getOrder() != null)
        //   getOrder().persit();
        //  i//f (getPosition() != null)
        // getPosition().persit();
        //   if (order != null)
        //     order.find();
        try {
            //   find();
            fillDao.merge(this);
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {

            System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);

            // ex.printStackTrace();

        }

        //fillDao.merge(this);
    }

    @Override
    public synchronized void persit() {
        // Any @ManyToOne I need to persist first to get them in the EM context.
        //   synchronized (persistanceLock) {
        //  if (this.hasFills()) {
        //    for (Fill fill : this.getFills())

        //PersistUtil.merge(fill);
        //}
        // if (getOrder() != null)
        //   getOrder().persit();
        // if (getPosition() != null)
        //   getPosition().persit();

        //List<Fill> duplicate = fillDao.queryList(Fill.class, "select f from Fill f where f=?1", this);

        //if (duplicate == null || duplicate.isEmpty())
        // if (fillDao != null)
        // avoid session conflicts due to lazy loading.
        //for (Order childOrder : this.getFillChildOrders())
        //    childOrder.p
        //  childOrder.persit();
        // try {

        fillDao.persist(this);
        //  } catch (Exception | Error ex) {
        //   fillDao.merge(this);

        //    System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
        //ex.printStackTrace();

        //  }

        //  PersistUtil.insert(this);
        //else
        //  fillDao.merge(this);
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
    //, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    //cascade = { CascadeType.ALL })
    @JoinColumn(name = "position")
    public Position getPosition() {

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

    public synchronized void addChildOrder(Order order) {
        this.children.add(order);

    }

    public void removeChildOrder(Order order) {
        order.setParentOrder(null);
        this.children.remove(order);
    }

    @PostPersist
    private void postPersist() {
        //detach();
    }

    @PrePersist
    private void prePersist() {

        UUID orderId = null;
        UUID positionId = null;
        if (fillDao == null)
            System.out.println("dude");
        if (order != null) {
            orderId = (fillDao.queryZeroOne(UUID.class, "select o.id from Order o where o.id=?1", order.getId()));

            if (orderId == null)
                order.persit();
        }

        if (position != null) {
            positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
            if (positionId == null)
                position.persit();
        }

        //detach();
    }

    @Override
    public void detach() {

        fillDao.detach(this);
    }

    //@Nullable
    //@OneToMany(cascade = CascadeType.MERGE)
    //(mappedBy = "parentFill")
    //@OrderBy
    //@Transient

    //@OneToMany(mappedBy = "parentFill")
    // @OneToMany(orphanRemoval = true)
    // @JoinColumn(name = "parentFill")
    //@Column
    //@ElementCollection(targetClass = Order.class)
    @Transient
    public List<Order> getFillChildOrders() {
        if (children == null)
            return new CopyOnWriteArrayList<Order>();
        //  synchronized (//) {
        return children;
        // }
    }

    protected void setFillChildOrders(List<Order> children) {
        this.children = children;
    }

    @Transient
    public boolean hasChildren() {
        return !getFillChildOrders().isEmpty();
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
    // @OneToMany(mappedBy = "fill")
    //, orphanRemoval = true, cascade = CascadeType.REMOVE)
    // , fetch = FetchType.EAGER)
    // , cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    //, mappedBy = "fill")
    //(fetch = FetchType.EAGER)
    @Transient
    public List<Transaction> getTransactions() {

        //synchronized (lock) {
        return transactions;
        // }
    }

    public void addTransaction(Transaction transaction) {

        // synchronized (lock) {
        this.transactions.add(transaction);
        //  }
    }

    public void removeTransaction(Transaction transaction) {
        transaction.setFill(null);
        this.transactions.remove(transactions);
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
        //  if (getOpenVolumeCount() != 0)
        if (positionType == null)
            if (getVolume().isPositive())
                return PositionType.LONG;
            else
                return PositionType.SHORT;
        // If i have children and all the children are fully filled, then I set to flat
        if (hasChildren() && getUnfilledVolume().isZero())
            return PositionType.FLAT;
        else
            return positionType;
        // return PositionType.FLAT;
    }

    @ManyToOne(optional = false)
    public Portfolio getPortfolio() {
        return portfolio;
    }

    @Transient
    public FillType getFillType() {
        if (getOrder() != null)
            return getOrder().getFillType();
        return null;
    }

    public PositionEffect getPositionEffect() {

        return positionEffect;

    }

    @Transient
    public FillDao getFillDao() {
        return fillDao;
    }

    protected void setPositionEffect(PositionEffect positionEffect) {
        this.positionEffect = positionEffect;
    }

    @Override
    public String toString() {
        // + (order.getId() != null ? order.getId() : "")
        //   + (getFillType() != null ? getFillType() : "")
        // getVolume()
        return "Id=" + (getId() != null ? getId() : "") + SEPARATOR + "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR
                + "PositionType=" + (getPositionType() != null ? getPositionType() : "") + SEPARATOR + "Market=" + (market != null ? market : "") + SEPARATOR
                + "Price=" + (getPrice() != null ? getPrice() : "") + SEPARATOR + "Volume=" + (getVolume() != null ? getVolume() : "") + SEPARATOR
                + "Unfilled Volume=" + (hasChildren() ? getUnfilledVolume() : "") + SEPARATOR + "Open Volume="
                + (getOpenVolume() != null ? getOpenVolume() : "") + SEPARATOR + "Position Effect=" + (getPositionEffect() != null ? getPositionEffect() : "")
                + SEPARATOR + "Position=" + (getPosition() != null ? getPosition() : "") + SEPARATOR + "Comment="
                + (getOrder() != null && getOrder().getComment() != null ? getOrder().getComment() : "") + SEPARATOR + "Order="
                + (getOrder() != null ? getOrder().getId() : "") + SEPARATOR + "Parent Fill="
                + ((getOrder() != null && getOrder().getParentFill() != null) ? getOrder().getParentFill().getId() : "");
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
        if (this.positionType == PositionType.FLAT)
            System.out.println("previous was flat");
        this.positionType = positionType;
    }

    @Transient
    public Amount getUnfilledVolume() {
        Amount filled = DecimalAmount.ZERO;
        Amount unfilled = getVolume();
        if (getVolume().isZero())
            return DecimalAmount.ZERO;
        for (Order childOrder : getFillChildOrders()) {

            ArrayList<Fill> allChildFills = new ArrayList<Fill>();
            childOrder.getAllFillsByParentOrder(childOrder, allChildFills);

            for (Fill childFill : allChildFills) {
                if (getVolume() == null || childFill.getVolume() == null)
                    System.out.println("null fill volume");
                if (getVolume() != null && !getVolume().isZero() && (childFill.getVolume().isPositive() && getVolume().isNegative())
                        || (childFill.getVolume().isNegative() && getVolume().isPositive()))
                    filled = filled.plus(childFill.getVolume());
            }
            unfilled = (getVolume().isNegative()) ? (getVolume().abs().minus(filled.abs())).negate() : getVolume().abs().minus(filled.abs());

        }
        return unfilled;

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

    private List<Order> children;
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
    private List<Transaction> transactions;
    private Portfolio portfolio;
    private Position position;

}
