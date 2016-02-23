package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
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

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.FillJpaDao;
import org.cryptocoinpartners.schema.dao.OrderDao;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.FeesUtil;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * This is the base class for GeneralOrder and SpecificOrder.  To create orders, see OrderBuilder or BaseStrategy.order
 *
 * @author Mike Olson
 * @author Tim Olson
 */

@Entity
@Cacheable
@Table(name = "\"Order\"", indexes = { @Index(columnList = "fillType"), @Index(columnList = "portfolio"), @Index(columnList = "market"),
        @Index(columnList = "parentFill"), @Index(columnList = "parentOrder") })
//, @Index(columnList = "portfolio") })
// This is required because ORDER is a SQL keyword and must be escaped
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@SuppressWarnings({ "JpaDataSourceORMInspection", "UnusedDeclaration" })
@NamedQueries({ @NamedQuery(name = "Order.findOrder", query = "select o from Order o where id =?1") })
//
@NamedEntityGraphs({

        // @NamedEntityGraph(name = "orderWithParentFill", attributeNodes = { @NamedAttributeNode(value = "parentFill", subgraph = "orderWithParentFillDetails") }, subgraphs = { @NamedSubgraph(name = "orderWithParentFillDetails", attributeNodes = { @NamedAttributeNode("children") }) }),
        @NamedEntityGraph(name = "orderWithFills", attributeNodes = { @NamedAttributeNode(value = "fills") }),
        @NamedEntityGraph(name = "orderWithTransactions", attributeNodes = { @NamedAttributeNode(value = "transactions") }),
        @NamedEntityGraph(name = "orderWithChildOrders", attributeNodes = { @NamedAttributeNode(value = "orderChildren") })

//@NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
})
public abstract class Order extends Event {
    @Inject
    protected OrderDao orderDao;
    @Inject
    protected FillJpaDao fillDao;
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.order");

    protected static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static Object lock = new Object();
    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    protected static final String SEPARATOR = ",";

    @ManyToOne(optional = true)
    @JoinColumn(name = "parentOrder")
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    //@Transient
    public Order getParentOrder() {
        return parentOrder;
    }

    //@ManyToOne(optional = true)

    // @Transient
    @Nullable
    @ManyToOne(optional = true)
    //, cascade = { CascadeType.MERGE })
    //cascade = { CascadeType.ALL })
    @JoinColumn(name = "parentFill")
    public Fill getParentFill() {
        return parentFill;
    }

    @Transient
    public abstract boolean isBid();

    @Transient
    public boolean isAsk() {
        return !isBid();
    }

    @Transient
    public TransactionType getTransactionType() {
        if (isBid())
            return TransactionType.BUY;
        else
            return TransactionType.SELL;
    }

    @ManyToOne(optional = false)
    //@Transient
    public Portfolio getPortfolio() {
        return portfolio;
    }

    @Transient
    public Amount getForcastedCommission() {
        if (commission == null)
            setForcastedCommission(FeesUtil.getCommission(this));
        return commission;

    }

    @Transient
    public Amount getForcastedMargin() {
        if (margin == null)
            setForcastedMargin(FeesUtil.getMargin(this));
        return margin;

    }

    @Nullable
    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getLimitPrice();

    @Nullable
    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getMarketPrice();

    public abstract Order withLimitPrice(String price);

    public abstract Order withMarketPrice(String price);

    public abstract Order withLimitPrice(BigDecimal price);

    public abstract Order withMarketPrice(BigDecimal price);

    public abstract Order withLimitPrice(DiscreteAmount price);

    public abstract Order withMarketPrice(DiscreteAmount price);

    @ManyToOne(optional = true)
    @JoinColumn(insertable = false, updatable = false)
    public abstract Market getMarket();

    @Nullable
    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getStopAmount();

    @Nullable
    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getTargetAmount();

    @Nullable
    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getStopPrice();

    @Nullable
    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getTargetPrice();

    @Nullable
    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getTrailingStopPrice();

    @Column(insertable = false, updatable = false)
    @Transient
    public abstract Amount getVolume();

    @Transient
    public abstract Amount getUnfilledVolume();

    public abstract void setStopAmount(DecimalAmount stopAmount);

    public abstract void setTargetAmount(DecimalAmount targetAmount);

    public abstract void setStopPrice(DecimalAmount stopPrice);

    public abstract void setTargetPrice(DecimalAmount targetPrice);

    public abstract void setTrailingStopPrice(DecimalAmount trailingStopPrice);

    public abstract void setMarket(Market market);

    public void setPositionEffect(PositionEffect positionEffect) {
        this.positionEffect = positionEffect;
    }

    public void setExecutionInstruction(ExecutionInstruction executionInstruction) {
        this.executionInstruction = executionInstruction;
    }

    public void setParentOrder(Order order) {
        this.parentOrder = order;
    }

    public void setParentFill(Fill fill) {
        this.parentFill = fill;
    }

    public FillType getFillType() {
        return fillType;
    }

    public PositionEffect getPositionEffect() {

        return positionEffect;

    }

    public ExecutionInstruction getExecutionInstruction() {

        return executionInstruction;

    }

    public String getComment() {
        return comment;
    }

    public enum MarginType {
        USE_MARGIN, // trade up to the limit of credit in the quote fungible
        CASH_ONLY, // do not trade more than the available cash-on-hand (quote fungible)
    }

    public MarginType getMarginType() {
        return marginType;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public boolean getPanicForce() {
        return force;
    }

    public boolean setPanicForce() {
        return force;
    }

    public boolean isEmulation() {
        return emulation;
    }

    // @Transient
    @Nullable
    @OneToMany
    //(cascade = CascadeType.PERSIST)
    //, mappedBy = "order")

    (mappedBy = "order")
    //, fetch = FetchType.EAGER)
    //, cascade = CascadeType.MERGE)
    //, fetch = FetchType.LAZY)
    // @OrderBy
    @OrderBy
    // @OrderColumn(name = "time")
    // @OrderColumn(name = "id")
    //, fetch = FetchType.EAGER)
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    public List<Fill> getFills() {
        // if (fills == null)
        //      System.out.println("test");
        return fills;
        // }
    }

    void getAllFillsByParentOrder(Order parentOrder, List allChildren) {
        for (Order child : parentOrder.getOrderChildren()) {
            allChildren.addAll(child.getFills());
            getAllFillsByParentOrder(child, allChildren);
        }
    }

    public void loadAllChildOrdersByParentOrder(Order parentOrder, Map<Order, Order> orders, Map<Fill, Fill> fills) {
        Map withFillsHints = new HashMap();
        Map withTransHints = new HashMap();

        Map withChildrenHints = new HashMap();

        // Map orderHints = new HashMap();

        // UUID portfolioID = EM.queryOne(UUID.class, queryStr, portfolioName);

        withFillsHints.put("javax.persistence.fetchgraph", "orderWithFills");
        withTransHints.put("javax.persistence.fetchgraph", "orderWithTransactions");
        withChildrenHints.put("javax.persistence.fetchgraph", "orderWithChildOrders");

        Order orderWithFills;
        Order orderWithChildren;
        Order orderWithTransactions;
        // so for each fill in the open position we need to load the whole order tree
        // getorder, then get all childe orders, then for each child, load child orders, so on and so forth.

        // load all child orders, and theri child ordres
        // load all parent orders and thier parent orders
        // need to laod all parent fills, their child orders, and their children

        // get a list of all orders in the tree then load 

        //  orderId = fill.getOrder().getId();
        //  if (parentOrder.getParentFill() != null) {

        //    parentOrder.getParentFill().loadAllChildOrdersByFill(parentOrder.getParentFill(), orders, fills);

        // }

        // if (parentOrder.getParentOrder() != null)
        //   parentOrder.getParentOrder().loadAllChildOrdersByParentOrder(parentOrder.getParentOrder());
        try {
            orderWithFills = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withFillsHints, parentOrder.getId());
            orderWithChildren = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withChildrenHints, parentOrder.getId());
            orderWithTransactions = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withTransHints, parentOrder.getId());
        } catch (Error | Exception ex) {
            log.error("Order:loadAllChildOrdersByParentOrdere unable to get order for orderID: " + parentOrder.getId());
            return;
        }

        if (orderWithFills != null && orderWithFills.getFills() != null && Hibernate.isInitialized(orderWithFills.getFills())
                && orderWithFills.getId().equals(parentOrder.getId())) {
            int index = 0;
            for (Fill fill : orderWithFills.getFills()) {
                if (fill.getPortfolio().equals(parentOrder.getPortfolio()))
                    fill.setPortfolio(parentOrder.getPortfolio());

                if (!fills.containsKey(fill)) {
                    fills.put(fill, fill);
                    fill.loadAllChildOrdersByFill(fill, orders, fills);

                } else
                    orderWithFills.getFills().set(index, fills.get(fill));
                index++;
            }

            parentOrder.setFills(orderWithFills.getFills());
        } else
            parentOrder.setFills(new CopyOnWriteArrayList<Fill>());

        if (orderWithTransactions != null && orderWithTransactions.getTransactions() != null
                && Hibernate.isInitialized(orderWithTransactions.getTransactions()) && orderWithTransactions.getId().equals(parentOrder.getId())) {

            for (Transaction transaction : orderWithTransactions.getTransactions()) {
                if (transaction.getPortfolio().equals(parentOrder.getPortfolio()))
                    transaction.setPortfolio(parentOrder.getPortfolio());

                //  if (transaction.getOrder().equals(parentOrder))
                //child = parentOrder;
                //    continue;
                if (!orders.containsKey(transaction.getOrder())) {
                    orders.put(transaction.getOrder(), transaction.getOrder());
                    transaction.getOrder().loadAllChildOrdersByParentOrder(transaction.getOrder(), orders, fills);
                } else
                    transaction.setOrder(orders.get(transaction.getOrder()));

            }

            parentOrder.setTransactions(orderWithTransactions.getTransactions());
        } else
            parentOrder.setTransactions(new CopyOnWriteArrayList<Transaction>());
        if (orderWithChildren != null && orderWithChildren.getOrderChildren() != null && Hibernate.isInitialized(orderWithChildren.getOrderChildren())
                && orderWithChildren.getId().equals(parentOrder.getId())) {
            int index = 0;
            for (Order order : orderWithChildren.getOrderChildren()) {
                if (order.getPortfolio().equals(parentOrder.getPortfolio()))
                    order.setPortfolio(parentOrder.getPortfolio());

                if (order.equals(parentOrder))
                    //child = parentOrder;
                    continue;
                if (!orders.containsKey(order)) {
                    orders.put(order, order);
                    System.out.println("loading child orders for: " + order.getId());
                    order.loadAllChildOrdersByParentOrder(order, orders, fills);
                } else
                    orderWithChildren.getOrderChildren().set(index, orders.get(order));
                index++;
            }

            parentOrder.setOrderChildren(orderWithChildren.getOrderChildren());
        } else
            parentOrder.setOrderChildren(new CopyOnWriteArrayList<Order>());
        if (orders.containsKey(getParentOrder()))
            setParentOrder(orders.get(getParentOrder()));
        if (fills.containsKey(getParentFill()))
            setParentFill(fills.get(getParentFill()));
    }

    void getAllGeneralOrderByParentOrder(Order paretnOrder, Collection allChildren) {
        for (Order child : paretnOrder.getOrderChildren()) {
            if (child instanceof GeneralOrder)
                allChildren.add(child);
            getAllGeneralOrderByParentOrder(child, allChildren);
        }
    }

    void getAllSpecificOrderByParentOrder(Order parentOrder, Collection allChildren) {
        for (Order child : parentOrder.getOrderChildren()) {
            if (child instanceof SpecificOrder)
                allChildren.add(child);
            getAllSpecificOrderByParentOrder(child, allChildren);
        }
    }

    //  @Nullable
    //@OneToMany(cascade = CascadeType.PERSIST, mappedBy = "order")
    //, orphanRemoval = true, cascade = CascadeType.REMOVE)
    //, fetch = FetchType.LAZY)
    //@OrderBy
    // @OrderColumn(name = "id")
    //, fetch = FetchType.EAGER)
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    // @Transient
    @Nullable
    @OneToMany
    //(cascade = CascadeType.PERSIST)
    //, mappedBy = "order")
    (mappedBy = "order", orphanRemoval = true)
    //, cascade = CascadeType.MERGE)
    //, fetch = FetchType.LAZY)
    //  @OrderBy
    @OrderBy
    public List<OrderUpdate> getOrderUpdates() {
        return orderUpdates;
        // }
    }

    public Order withTargetPrice(String price) {
        if (this.fillType == (FillType.STOP_LIMIT) || this.fillType == (FillType.STOP_LOSS) || this.fillType == (FillType.TRAILING_STOP_LIMIT)) {
            this.setTargetPrice(DecimalAmount.of(price));
            return this;
        }
        throw new NotImplementedException();
    }

    public Order withFillType(FillType fillType) {
        this.setFillType(fillType);
        return this;

    }

    public Order withParentFill(Fill fill) {
        if (fill != null) {
            fill.addChildOrder(this);
            this.setParentFill(fill);

        }
        return this;

    }

    public Order withTargetPrice(BigDecimal price) {
        if (getFillType() == (FillType.STOP_LIMIT) || getFillType() == (FillType.STOP_LOSS) || getFillType() == (FillType.TRAILING_STOP_LIMIT)) {
            this.setTargetPrice(DecimalAmount.of(price));
            return this;
        }

        throw new NotImplementedException();
    }

    public Order withStopPrice(String price) {
        if (getFillType() == (FillType.STOP_LIMIT) || getFillType() == (FillType.STOP_LOSS) || getFillType() == (FillType.TRAILING_STOP_LIMIT)) {
            this.setStopAmount(DecimalAmount.of(price));
            return this;
        }
        throw new NotImplementedException();
    }

    public Order withStopPrice(BigDecimal price) {
        if (getFillType() == (FillType.STOP_LIMIT) || getFillType() == (FillType.STOP_LOSS) || getFillType() == (FillType.TRAILING_STOP_LIMIT)) {
            this.setStopPrice(DecimalAmount.of(price));
            return this;
        }

        throw new NotImplementedException();
    }

    public Order withStopAmount(BigDecimal price) {
        if (getFillType() == (FillType.STOP_LIMIT) || getFillType() == (FillType.STOP_LOSS) || getFillType() == (FillType.TRAILING_STOP_LIMIT)) {
            this.setStopAmount(DecimalAmount.of(price));
            return this;
        }

        throw new NotImplementedException();
    }

    public Order withTargetAmount(BigDecimal price) {
        this.setTargetAmount(DecimalAmount.of(price));
        return this;

    }

    public Order withTimeToLive(long timeToLive) {
        this.setTimeToLive(timeToLive);
        return this;

    }

    public Order withTrailingStopPrice(BigDecimal price, BigDecimal trailingStopPrice) {
        if (getFillType() == (FillType.STOP_LIMIT) || getFillType() == (FillType.STOP_LOSS) || getFillType() == (FillType.TRAILING_STOP_LIMIT)) {
            this.setStopAmount(DecimalAmount.of(price));
            this.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
            return this;
        }
        throw new NotImplementedException();
    }

    public Order withTrailingStopPrice(String price, String trailingStopPrice) {
        if (this.fillType == (FillType.STOP_LIMIT) || getFillType() == (FillType.STOP_LOSS) || getFillType() == (FillType.TRAILING_STOP_LIMIT)) {
            this.setStopAmount(DecimalAmount.of(price));
            this.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
            return this;
        }
        throw new NotImplementedException();
    }

    public Order withComment(String comment) {
        this.setComment(comment);
        return this;
    }

    public Order withPositionEffect(PositionEffect positionEffect) {
        this.setPositionEffect(positionEffect);
        return this;
    }

    public Order withExecutionInstruction(ExecutionInstruction executionInstruction) {
        this.setExecutionInstruction(executionInstruction);
        return this;
    }

    @Nullable
    //@OneToMany(mappedBy = "order")
    //, orphanRemoval = true, cascade = CascadeType.REMOVE)
    //@OrderBy
    // @OrderColumn(name = "id")
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    // @Transient
    @OneToMany
    //(cascade = CascadeType.PERSIST)
    //, mappedBy = "order")
    (mappedBy = "order", orphanRemoval = true)
    //, fetch = FetchType.EAGER)
    //, cascade = CascadeType.MERGE)
    //, fetch = FetchType.LAZY)
    @OrderBy
    public List<Transaction> getTransactions() {
        if (transactions == null)
            transactions = new CopyOnWriteArrayList<Transaction>();
        //  synchronized (lock) {
        return transactions;
        // }
    }

    @Override
    public synchronized void merge() {
        //   synchronized (persistanceLock) {
        try {
            orderDao.merge(this);
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {

            System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }
    }

    public <T> T find() {
        //   synchronized (persistanceLock) {
        try {
            return (T) orderDao.find(Order.class, this.getId());
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {
            return null;
            //System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }
        // return null;
    }

    @PostPersist
    private void postPersist() {
        //   detach();

    }

    //  @PrePersist
    private void prePersist() {
        if (getDao() != null) {

            Portfolio orderPortfolio = null;
            Order orderParent = null;
            Fill fillParent = null;
            UUID parentOrderId = null;
            if (getPortfolio() != null) {
                orderPortfolio = (getDao().find(Portfolio.class, getPortfolio().getId()));

                //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
                if (orderPortfolio == null)
                    getDao().persist(getPortfolio());
                //    portfolio.merge();
            }

            //   if (parentOrder != null) {
            //     orderParent = (orderDao.find(Order.class, parentOrder.getId()));

            //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
            //   if (orderParent == null)
            //     orderDao.persist(orderParent);
            // }

            if (getParentFill() != null) {
                fillParent = (getDao().find(Fill.class, getParentFill().getId()));

                //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
                if (fillParent == null)
                    getDao().persist(getParentFill());
                //       parentFill.merge();
            }

            if (getParentOrder() != null) {
                orderParent = (getDao().find(Order.class, getParentOrder().getId()));

                //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
                if (orderParent == null)
                    getDao().persist(getParentOrder());
                //       parentFill.merge();
            }
        }

        //        if (portfolio != null) {
        //            portfolioId = (orderDao == null) ? (EM.queryZeroOne(UUID.class, "select p.id from Portfolio p where p.id=?1", portfolio.getId())) : (orderDao
        //                    .queryZeroOne(UUID.class, "select p.id from Portfolio p where p.id=?1", portfolio.getId()));
        //            if (portfolioId == null)
        //                portfolio.persit();
        //        }
        //
        //        if (parentOrder != null) {
        //            parentOrderId = (orderDao == null) ? (EM.queryZeroOne(UUID.class, "select o.id from Order o where o.id=?1", parentOrder.getId())) : (orderDao
        //                    .queryZeroOne(UUID.class, "select o.id from Order o where o.id=?1", parentOrder.getId()));
        //
        //            if (parentOrderId == null)
        //                parentOrder.persit();
        //        }

        //detach();
    }

    @Override
    public void detach() {
        orderDao.detach(this);
    }

    @Override
    @Transient
    public Dao getDao() {
        return orderDao;
    }

    @Override
    public EntityBase refresh() {
        return orderDao.refresh(this);
    }

    @Override
    public synchronized void persit() {
        //   synchronized (persistanceLock) {
        //  if (this.hasFills()) {
        //    for (Fill fill : this.getFills())

        //PersistUtil.merge(fill);
        //}
        //if (this.hasFills()) {
        // for (Fill fill : getFills()) {

        // if (this.parentOrder != null)
        //   parentOrder.persit();

        // if (this.parentFill != null)
        //   parentFill.persit();
        //   PersistUtil.insert(this);

        //  PersistUtil.find(this);
        //  List<Order> duplicate = orderDao.queryList(Order.class, "select o from Order o where o=?1", this);
        //List<Order> duplicate = null;
        //  EntityBase entity = PersistUtil.find(this);
        try {

            orderDao.persist(this);
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {

            System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }
        //else
        //  orderDao.merge(this);

        //  PersistUtil.insert(this);
        // else
        //   PersistUtil.merge(this);
        //  }
        // Iterator<Fill> itf = getFills().iterator();
        //while (itf.hasNext()) {
        //                //  for (Fill pos : getFills()) {

        //  Fill fill = itf.next();

        //fillDao.persist(fill);
        //   fill.persit();

        // }

    }

    @Transient
    public Transaction getReservation() {
        for (Transaction tran : getTransactions()) {

            if (tran.getType() == (TransactionType.BUY_RESERVATION) || tran.getType() == (TransactionType.SELL_RESERVATION)) {
                return tran;

            }
        }
        return null;
    }

    @Transient
    public synchronized void removeTransaction(Transaction transaction) {
        getTransactions().remove(transaction);
        transaction.setOrder(null);

    }

    public synchronized void removeChildOrder(Order child) {
        getOrderChildren().remove(child);
        child.setParentOrder(null);

    }

    public synchronized void removeFill(Fill fill) {
        getFills().remove(fill);
        fill.setOrder(null);

    }

    public synchronized void removeOrderUpdate(OrderUpdate orderUpdate) {
        getOrderUpdates().remove(orderUpdate);
        orderUpdate.setOrder(null);

    }

    public synchronized void removeChildOrders() {
        getOrderChildren().clear();

    }

    public synchronized void removeTransactions() {
        getTransactions().clear();

    }

    @Nullable
    @OneToMany(mappedBy = "parentOrder")
    //, fetch = FetchType.EAGER)
    //(orphanRemoval = true, cascade = CascadeType.REMOVE)
    //
    @OrderBy
    //  @OrderColumn(name = "id")
    //  @Transient
    public List<Order> getOrderChildren() {
        //  synchronized (lock) {
        return children;
        //  }
    }

    public synchronized void addFill(Fill fill) {
        if (!getFills().contains(fill))
            getFills().add(fill);
    }

    public synchronized void addOrderUpdate(OrderUpdate orderUpdate) {
        getOrderUpdates().add(orderUpdate);
    }

    public synchronized void addTransaction(Transaction transaction) {
        if (!getTransactions().contains(transaction))

            getTransactions().add(transaction);
    }

    public synchronized void addChildOrder(Order order) {
        if (!getOrderChildren().contains(order))

            getOrderChildren().add(order);
    }

    @Transient
    public boolean hasFills() {
        return !getFills().isEmpty();
    }

    @Transient
    public boolean hasChildren() {
        return !getOrderChildren().isEmpty();
    }

    @Transient
    public abstract boolean isFilled();

    @Transient
    public DecimalAmount getAverageFillPrice() {
        if (!hasFills())
            return DecimalAmount.ZERO;
        BigDecimal sumProduct = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        List<Fill> fills = getFills();
        for (Fill fill : fills) {
            BigDecimal priceBd = fill.getPrice().asBigDecimal();
            BigDecimal volumeBd = fill.getVolume().asBigDecimal();
            sumProduct = sumProduct.add(priceBd.multiply(volumeBd));
            volume = volume.add(volumeBd);
        }
        return (sumProduct.equals(BigDecimal.ZERO)) ? DecimalAmount.ZERO : new DecimalAmount(sumProduct.divide(volume, Amount.mc));
    }

    protected Order() {

    }

    protected Order(Instant time) {
        super(time);

    }

    protected void setFills(List<Fill> fills) {
        this.fills = fills;
    }

    protected void setOrderUpdates(List<OrderUpdate> orderUpdates) {
        this.orderUpdates = orderUpdates;
    }

    protected void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    public void setFillType(FillType fillType) {
        this.fillType = fillType;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    protected void setMarginType(MarginType marginType) {
        this.marginType = marginType;
    }

    protected void setForcastedCommission(Amount commission) {
        this.commission = commission;
    }

    protected void setForcastedMargin(Amount margin) {
        this.margin = margin;
    }

    protected void setExpiration(Instant expiration) {
        this.expiration = expiration;
    }

    protected void setPanicForce(boolean force) {
        this.force = force;
    }

    protected void setEmulation(boolean emulation) {
        this.emulation = emulation;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
    @Basic(optional = true)
    public Instant getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(Instant time) {
        this.entryTime = time;

    }

    @Basic(optional = true)
    public long getTimeToLive() {
        return timeToLive;
    }

    @Transient
    public Instant getExpiryTime() {
        if (getTimeToLive() != 0)
            return getTime().plus(getTimeToLive());
        else
            return null;
    }

    public void setTimeToLive(long time) {
        this.timeToLive = time;

    }

    protected void setOrderChildren(List<Order> children) {
        this.children = children;
    }

    protected List<Order> children;

    protected Instant entryTime;
    protected long timeToLive;
    protected Portfolio portfolio;
    protected List<Fill> fills;
    protected List<OrderUpdate> orderUpdates;
    protected List<Transaction> transactions;
    protected FillType fillType;
    protected MarginType marginType;
    protected Amount commission;
    protected Amount margin;
    protected String comment;
    protected PositionEffect positionEffect;
    protected Instant expiration;
    protected ExecutionInstruction executionInstruction;
    protected boolean force; // allow this order to override various types of panic
    protected boolean emulation; // ("allow order type emulation" [default, true] or "only use exchange's native functionality")
    protected Order parentOrder;
    protected Fill parentFill;

}
