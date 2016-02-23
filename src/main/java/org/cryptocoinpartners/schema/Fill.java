package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.PostPersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.FillDao;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.FeesUtil;
import org.hibernate.Hibernate;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@Table(indexes = { @Index(columnList = "`order`"), @Index(columnList = "market"), @Index(columnList = "portfolio"), @Index(columnList = "position") })
@NamedQueries({ @NamedQuery(name = "Fill.findFill", query = "select f from Fill f where id=?1") })
//
@NamedEntityGraphs({ @NamedEntityGraph(name = "fillWithChildOrders", attributeNodes = { @NamedAttributeNode("fillChildOrders") })
//})
//, subgraph = "childrenWithFills") }, subgraphs = { @NamedSubgraph(name = "childrenWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })

// @NamedEntityGraph(name = "fillWithChildOrders", attributeNodes = { @NamedAttributeNode(value = "fillChildOrders", subgraph = "childrenWithFills") }, subgraphs = { @NamedSubgraph(name = "childrenWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })
// @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
})
public class Fill extends RemoteEvent {
    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static Object lock = new Object();
    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    private static final String SEPARATOR = ",";
    private PositionEffect positionEffect;
    @Inject
    protected FillDao fillDao;
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.fill");

    // @Inject

    @Inject
    public Fill(@Assisted SpecificOrder order, @Assisted("fillTime") Instant time, @Assisted("fillTimeReceived") Instant timeReceived, @Assisted Market market,
            @Assisted("fillPriceCount") long priceCount, @Assisted("fillVolumeCount") long volumeCount, @Assisted String remoteKey) {
        super(time, timeReceived, remoteKey);

        this.fillChildOrders = new CopyOnWriteArrayList<Order>();
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
        this.fillChildOrders = new CopyOnWriteArrayList<Order>();
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
    //(cascade = { CascadeType.MERGE })
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
    public EntityBase refresh() {
        return fillDao.refresh(this);
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
        if (!getFillChildOrders().contains(order))

            getFillChildOrders().add(order);

    }

    public synchronized void removeChildOrder(Order order) {
        order.setParentOrder(null);
        getFillChildOrders().remove(order);
    }

    @PostPersist
    private void postPersist() {
        //detach();
    }

    //  @PrePersist
    private void prePersist() {
        if (getDao() != null) {
            UUID orderId = null;
            UUID positionId = null;
            Order parentOrder = null;
            Position fillPosition = null;

            // context.

            if (getOrder() != null) {
                parentOrder = (getDao().find(Order.class, getOrder().getId()));

                // orderId = (fillDao.queryZeroOne(UUID.class, "select o.id from Order o where o.id=?1", order.getId()));

                if (parentOrder == null)
                    getDao().persist(getOrder());
                //  order.merge();
            }

            if (getPosition() != null) {
                fillPosition = (getDao().find(Position.class, getPosition().getId()));

                //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
                if (fillPosition == null)
                    getDao().persist(getPosition());
                //  position.merge();
            }
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
    @Nullable
    @OneToMany
    //(cascade = CascadeType.PERSIST)
    //, mappedBy = "order")
    (mappedBy = "parentFill")
    // @OrderColumn(name = "version")
    //  @OrderColumn(name = "time")
    //, fetch = FetchType.EAGER)
    //, cascade = CascadeType.MERGE)
    //, fetch = FetchType.LAZY)
    @OrderBy
    public List<Order> getFillChildOrders() {
        if (fillChildOrders == null)
            return new CopyOnWriteArrayList<Order>();
        //  synchronized (//) {
        return fillChildOrders;
        // }
    }

    protected void setFillChildOrders(List<Order> children) {
        this.fillChildOrders = children;
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
    // @Transient
    @OneToMany
    //(cascade = CascadeType.PERSIST)
    //, mappedBy = "order")
    (mappedBy = "fill", orphanRemoval = true)
    //, fetch = FetchType.EAGER)
    //, cascade = CascadeType.MERGE)
    //, fetch = FetchType.LAZY)
    @OrderBy
    public List<Transaction> getTransactions() {
        if (transactions == null)
            return new CopyOnWriteArrayList<Transaction>();

        //synchronized (lock) {
        return transactions;
        // }
    }

    public synchronized void addTransaction(Transaction transaction) {

        // synchronized (lock) {
        if (!getTransactions().contains(transaction))
            getTransactions().add(transaction);
        // this.transactions.add(transaction);
        //  }
    }

    public synchronized void removeTransaction(Transaction transaction) {
        transaction.setFill(null);
        getTransactions().remove(transaction);
    }

    protected void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    @ManyToOne
    public Market getMarket() {
        return market;
    }

    @Transient
    public DiscreteAmount getPrice() {
        if (getPriceCount() == 0)
            return null;
        if (market.getPriceBasis() == 0)
            return null;
        return new DiscreteAmount(getPriceCount(), getMarket().getPriceBasis());
    }

    @Transient
    public Amount getStopPrice() {
        if (getStopPriceCount() == 0)
            return null;
        return new DiscreteAmount(getStopPriceCount(), getMarket().getPriceBasis());
    }

    @Transient
    public Amount getTargetPrice() {
        if (getTargetPriceCount() == 0)
            return null;
        return new DiscreteAmount(getTargetPriceCount(), getMarket().getPriceBasis());
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
        if (getVolumeCount() == 0)
            return null;
        return new DiscreteAmount(getVolumeCount(), getMarket().getVolumeBasis());
    }

    public long getVolumeCount() {
        return volumeCount;
    }

    @Transient
    public Amount getOpenVolume() {
        //   if (openVolumeCount == 0)
        //     return null;
        if (openVolume == null)
            openVolume = new DiscreteAmount(getOpenVolumeCount(), getMarket().getVolumeBasis());
        return openVolume;

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
        if (this.positionType == (PositionType.FLAT))
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

    public void loadAllChildOrdersByFill(Fill parentFill, Map<Order, Order> orders, Map<Fill, Fill> fills) {
        Map withFillsHints = new HashMap();
        Map withTransHints = new HashMap();

        Map withChildrenHints = new HashMap();
        Map withChildOrderHints = new HashMap();

        // Map orderHints = new HashMap();

        // UUID portfolioID = EM.queryOne(UUID.class, queryStr, portfolioName);

        //  withFillsHints.put("javax.persistence.fetchgraph", "fillWithChildOrders");
        //    withTransHints.put("javax.persistence.fetchgraph", "orderWithTransactions");
        withChildOrderHints.put("javax.persistence.fetchgraph", "fillWithChildOrders");

        Fill fillWithFills;
        Fill fillWithChildren;
        Fill fillWithTransactions;

        // Set test = new HashSet();
        Map test = new HashMap();

        // so for each fill in the open position we need to load the whole order tree
        // getorder, then get all childe orders, then for each child, load child orders, so on and so forth.

        // load all child orders, and theri child ordres
        // load all parent orders and thier parent orders
        // need to laod all parent fills, their child orders, and their children

        // get a list of all orders in the tree then load 

        //  orderId = fill.getOrder().getId();

        // so we are 
        //   Fill
        //     -   Order
        //           - Fills
        //             -Fiil
        //              -Fill
        // so if any of the fills wihtin the order are equal to the order's parent fill, set this the fill to the parent fill memory ref.
        // if (getOrder() != null)
        //   getOrder().loadAllChildOrdersByParentOrder(getOrder(), orders, fills);
        try {
            fillWithChildren = EM.namedQueryZeroOne(Fill.class, "Fill.findFill", withChildOrderHints, parentFill.getId());

        } catch (Error | Exception ex) {
            log.error("Fill:loadAllChildOrdersByFill unable to get fill for fillID: " + parentFill.getId());
            return;
        }

        if (fillWithChildren != null && fillWithChildren.getFillChildOrders() != null && fillWithChildren.getId().equals(parentFill.getId())
                && Hibernate.isInitialized(fillWithChildren.getFillChildOrders())) {
            int index = 0;
            for (Order order : fillWithChildren.getFillChildOrders()) {

                if (order.getPortfolio().equals(parentFill.getPortfolio()))
                    order.setPortfolio(parentFill.getPortfolio());
                if (order.equals(getOrder()))
                    //child = parentOrder;
                    continue;
                if (!orders.containsKey(order)) {
                    orders.put(order, order);
                    System.out.println(" loading child order for " + order.getId());
                    order.loadAllChildOrdersByParentOrder(order, orders, fills);

                } else
                    fillWithChildren.getFillChildOrders().set(index, orders.get(order));

                index++;
            }

            parentFill.setFillChildOrders(fillWithChildren.getFillChildOrders());
        } else
            parentFill.setFillChildOrders(new CopyOnWriteArrayList<Order>());

        if (orders.containsKey(getOrder()))
            setOrder((SpecificOrder) orders.get(getOrder()));
        else if (getOrder().getPortfolio().equals(parentFill.getPortfolio())) {

            getOrder().setPortfolio(parentFill.getPortfolio());

            orders.put(order, order);
            System.out.println(" loading child order for " + order.getId());
            getOrder().loadAllChildOrdersByParentOrder(getOrder(), orders, fills);
        }

    }

    public void getAllSpecificOrdersByParentFill(Fill parentFill, Collection allChildren) {
        for (Order child : parentFill.getFillChildOrders()) {
            if (child instanceof SpecificOrder) {
                allChildren.add(child);
                child.getAllSpecificOrderByParentOrder(child, allChildren);
            }
        }
    }

    public void getAllOrdersByParentFill(Collection allChildren) {
        List<Order> allSpecificChildOrders = Collections.synchronizedList(new ArrayList<Order>());
        List<Order> allGeneralChildOrders = Collections.synchronizedList(new ArrayList<Order>());
        List<Order> allChildOrders = Collections.synchronizedList(new ArrayList<Order>());

        getAllSpecificOrdersByParentFill(this, allSpecificChildOrders);

        getAllGeneralOrdersByParentFill(this, allGeneralChildOrders);

        allChildren.addAll(allGeneralChildOrders);
        allChildren.addAll(allSpecificChildOrders);

    }

    void getAllGeneralOrdersByParentFill(Fill parentFill, Collection allChildren) {
        for (Order child : parentFill.getFillChildOrders()) {
            if (child instanceof GeneralOrder) {
                allChildren.add(child);
                child.getAllGeneralOrderByParentOrder(child, allChildren);
            }
        }
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
        openVolume = null;
        this.openVolumeCount = openVolumeCount;
    }

    protected void setCommission(Amount commission) {
        this.commission = commission;
    }

    protected void setMargin(Amount margin) {
        this.margin = margin;
    }

    @Override
    @Transient
    public Dao getDao() {
        return fillDao;
    }

    protected void setPosition(Position position) {
        this.position = position;
    }

    private volatile List<Order> fillChildOrders;
    private SpecificOrder order;
    private Market market;
    private volatile long priceCount;
    private volatile long stopAmountCount;
    private volatile long stopPriceCount;
    private volatile long targetAmountCount;
    private volatile long targetPriceCount;
    private volatile long volumeCount;
    private volatile long openVolumeCount;
    private DiscreteAmount openVolume;
    private volatile Amount commission;
    private volatile Amount margin;
    private PositionType positionType;
    private volatile List<Transaction> transactions;
    private Portfolio portfolio;
    private Position position;

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

}
