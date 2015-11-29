package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.List;
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
import org.cryptocoinpartners.schema.dao.FillJpaDao;
import org.cryptocoinpartners.schema.dao.OrderJpaDao;
import org.cryptocoinpartners.util.FeesUtil;
import org.hibernate.annotations.Type;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.inject.Inject;

/**
 * This is the base class for GeneralOrder and SpecificOrder.  To create orders, see OrderBuilder or BaseStrategy.order
 *
 * @author Mike Olson
 * @author Tim Olson
 */

@Entity
@Cacheable
@Table(name = "\"Order\"", indexes = { @Index(columnList = "fillType"), @Index(columnList = "portfolio"), @Index(columnList = "market") })
//, @Index(columnList = "portfolio") })
// This is required because ORDER is a SQL keyword and must be escaped
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@SuppressWarnings({ "JpaDataSourceORMInspection", "UnusedDeclaration" })
public abstract class Order extends Event {
    @Inject
    protected OrderJpaDao orderDao;
    @Inject
    protected FillJpaDao fillDao;

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
    //  @JoinColumn(name = "parentFill")

    // @Transient
    @Nullable
    @ManyToOne(optional = true)
    //, cascade = { CascadeType.PERSIST })
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

    public abstract Order withLimitPrice(String price);

    public abstract Order withLimitPrice(BigDecimal price);

    public abstract Order withLimitPrice(DiscreteAmount price);

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
    (mappedBy = "order", orphanRemoval = true)
    //, cascade = CascadeType.MERGE)
    //, fetch = FetchType.LAZY)
    @OrderBy
    // @OrderColumn(name = "id")
    //, fetch = FetchType.EAGER)
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    public List<Fill> getFills() {
        return fills;
        // }
    }

    void getAllFillsByParentOrder(Order parentOrder, List allChildren) {
        for (Order child : parentOrder.getOrderChildren()) {
            allChildren.addAll(child.getFills());
            getAllFillsByParentOrder(child, allChildren);
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
    @Transient
    public List<OrderUpdate> getOrderUpdate() {
        return orderUpdates;
        // }
    }

    public Order withTargetPrice(String price) {
        if (this.fillType.equals(FillType.STOP_LIMIT) || this.fillType.equals(FillType.STOP_LOSS) || this.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
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
        this.setParentFill(fill);
        return this;

    }

    public Order withTargetPrice(BigDecimal price) {
        if (this.fillType.equals(FillType.STOP_LIMIT) || this.fillType.equals(FillType.STOP_LOSS) || this.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
            this.setTargetPrice(DecimalAmount.of(price));
            return this;
        }

        throw new NotImplementedException();
    }

    public Order withStopPrice(String price) {
        if (this.fillType.equals(FillType.STOP_LIMIT) || this.fillType.equals(FillType.STOP_LOSS) || this.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
            this.setStopAmount(DecimalAmount.of(price));
            return this;
        }
        throw new NotImplementedException();
    }

    public Order withStopPrice(BigDecimal price) {
        if (this.fillType.equals(FillType.STOP_LIMIT) || this.fillType.equals(FillType.STOP_LOSS) || this.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
            this.setStopPrice(DecimalAmount.of(price));
            return this;
        }

        throw new NotImplementedException();
    }

    public Order withStopAmount(BigDecimal price) {
        if (this.fillType.equals(FillType.STOP_LIMIT) || this.fillType.equals(FillType.STOP_LOSS) || this.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
            this.setStopAmount(DecimalAmount.of(price));
            return this;
        }

        throw new NotImplementedException();
    }

    public Order withTargetAmount(BigDecimal price) {
        this.setTargetAmount(DecimalAmount.of(price));
        return this;

    }

    public Order withTrailingStopPrice(BigDecimal price, BigDecimal trailingStopPrice) {
        if (this.fillType.equals(FillType.STOP_LIMIT) || this.fillType.equals(FillType.STOP_LOSS) || this.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
            this.setStopAmount(DecimalAmount.of(price));
            this.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
            return this;
        }
        throw new NotImplementedException();
    }

    public Order withTrailingStopPrice(String price, String trailingStopPrice) {
        if (this.fillType.equals(FillType.STOP_LIMIT) || this.fillType.equals(FillType.STOP_LOSS) || this.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
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
    @Transient
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

    @Override
    public void detach() {
        orderDao.detach(this);
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

            if (tran.getType().equals(TransactionType.BUY_RESERVATION) || tran.getType().equals(TransactionType.SELL_RESERVATION)) {
                return tran;

            }
        }
        return null;
    }

    @Transient
    public void removeTransaction(Transaction transaction) {
        this.transactions.remove(transaction);
        transaction.setOrder(null);

    }

    public void removeChildOrder(Order child) {
        this.children.remove(child);
        child.setParentOrder(null);

    }

    public void removeFill(Fill fill) {
        this.fills.remove(fill);
        fill.setOrder(null);

    }

    public void removeOrderUpdate(OrderUpdate orderUpdate) {
        this.orderUpdates.remove(orderUpdate);
        orderUpdate.setOrder(null);

    }

    public void removeChildOrders() {
        this.children.clear();

    }

    public void removeTransactions() {
        this.transactions.clear();

    }

    @Nullable
    @OneToMany(mappedBy = "parentOrder")
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

    public void addFill(Fill fill) {
        this.fills.add(fill);
    }

    public void addOrderUpdate(OrderUpdate orderUpdate) {
        this.orderUpdates.add(orderUpdate);
    }

    public void addTransaction(Transaction transaction) {
        this.transactions.add(transaction);
    }

    public void addChildOrder(Order order) {
        this.children.add(order);
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

    public DecimalAmount averageFillPrice() {
        if (!hasFills())
            return null;
        BigDecimal sumProduct = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        List<Fill> fills = getFills();
        for (Fill fill : fills) {
            BigDecimal priceBd = fill.getPrice().asBigDecimal();
            BigDecimal volumeBd = fill.getVolume().asBigDecimal();
            sumProduct = sumProduct.add(priceBd.multiply(volumeBd));
            volume = volume.add(volumeBd);
        }
        return new DecimalAmount(sumProduct.divide(volume, Amount.mc));
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

    protected void setOrderChildren(List<Order> children) {
        this.children = children;
    }

    protected List<Order> children;

    protected Instant entryTime;

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
