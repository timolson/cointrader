package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.util.FeesUtil;
import org.hibernate.annotations.Type;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * This is the base class for GeneralOrder and SpecificOrder.  To create orders, see OrderBuilder or BaseStrategy.order
 *
 * @author Mike Olson
 * @author Tim Olson
 */

@Entity
@Table(name = "\"Order\"", indexes = { @Index(columnList = "fillType"), @Index(columnList = "portfolio"), @Index(columnList = "market") })
// This is required because ORDER is a SQL keyword and must be escaped
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@SuppressWarnings({ "JpaDataSourceORMInspection", "UnusedDeclaration" })
public abstract class Order extends Event {
    protected static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static Object lock = new Object();
    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    protected static final String SEPARATOR = ",";

    @ManyToOne(optional = true)
    public Order getParentOrder() {
        return parentOrder;
    }

    // @ManyToOne(optional = true)
    @Transient
    public Fill getParentFill() {
        return parentFill;
    }

    @Transient
    public abstract boolean isBid();

    @Transient
    public boolean isAsk() {
        return !isBid();
    }

    @ManyToOne(optional = false)
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
    @Transient
    @Column(insertable = false, updatable = false)
    public abstract Amount getLimitPrice();

    @ManyToOne(optional = true)
    @JoinColumn(insertable = false, updatable = false)
    public abstract Market getMarket();

    @Nullable
    @Transient
    @Column(insertable = false, updatable = false)
    public abstract Amount getStopAmount();

    @Nullable
    @Transient
    @Column(insertable = false, updatable = false)
    public abstract Amount getTargetAmount();

    @Nullable
    @Transient
    @Column(insertable = false, updatable = false)
    public abstract Amount getStopPrice();

    @Nullable
    @Transient
    @Column(insertable = false, updatable = false)
    public abstract Amount getTargetPrice();

    @Nullable
    @Transient
    @Column(insertable = false, updatable = false)
    public abstract Amount getTrailingStopPrice();

    @Transient
    @Column(insertable = false, updatable = false)
    public abstract Amount getVolume();

    @Transient
    public abstract Amount getUnfilledVolume();

    public abstract void setStopAmount(DecimalAmount stopAmount);

    public abstract void setTargetAmount(DecimalAmount targetAmount);

    public abstract void setStopPrice(DecimalAmount stopPrice);

    public abstract void setTargetPrice(DecimalAmount targetPrice);

    public abstract void setTrailingStopPrice(DecimalAmount trailingStopPrice);

    public abstract void setMarket(Market market);

    protected void setPositionEffect(PositionEffect positionEffect) {
        this.positionEffect = positionEffect;
    }

    protected void setParentOrder(Order order) {
        this.parentOrder = order;
    }

    protected void setParentFill(Fill fill) {
        this.parentFill = fill;
    }

    public FillType getFillType() {
        return fillType;
    }

    public PositionEffect getPositionEffect() {

        return positionEffect;

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

    @Nullable
    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER)
    public Collection<Fill> getFills() {
        if (fills == null)
            fills = Collections.synchronizedList(new ArrayList<Fill>());
        synchronized (lock) {
            return fills;
        }
    }

    @Nullable
    @OneToMany(mappedBy = "order", fetch = FetchType.EAGER)
    public Collection<Transaction> getTransactions() {
        if (transactions == null)
            transactions = Collections.synchronizedList(new ArrayList<Transaction>());
        synchronized (lock) {
            return transactions;
        }
    }

    @Transient
    public Transaction getReservation() {
        Iterator<Transaction> it = getTransactions().iterator();
        while (it.hasNext()) {
            Transaction tran = it.next();
            if (tran.getType().equals(TransactionType.BUY_RESERVATION) || tran.getType().equals(TransactionType.SELL_RESERVATION)) {
                return tran;

            }
        }
        return null;
    }

    @Transient
    public void removeTransaction(Transaction transaction) {
        Iterator<Transaction> it = getTransactions().iterator();
        while (it.hasNext()) {
            if (it.next().equals(transaction)) {
                it.remove();

            }
        }

    }

    @Nullable
    @OneToMany(fetch = FetchType.EAGER)
    public Collection<Order> getChildren() {
        if (children == null)
            children = new ArrayList<Order>();
        synchronized (lock) {
            return children;
        }
    }

    public void addFill(Fill fill) {
        synchronized (lock) {
            getFills().add(fill);
        }
    }

    public void addTransaction(Transaction transaction) {
        synchronized (lock) {
            getTransactions().add(transaction);
        }
    }

    public void addChild(Order order) {
        synchronized (lock) {
            getChildren().add(order);
        }
    }

    @Transient
    public boolean hasFills() {
        return !getFills().isEmpty();
    }

    @Transient
    public boolean hasChildren() {
        return !getChildren().isEmpty();
    }

    @Transient
    public abstract boolean isFilled();

    public DecimalAmount averageFillPrice() {
        if (!hasFills())
            return null;
        BigDecimal sumProduct = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        Collection<Fill> fills = getFills();
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

    protected void setFills(Collection<Fill> fills) {
        this.fills = fills;
    }

    protected void setTransactions(Collection<Transaction> transactions) {
        this.transactions = transactions;
    }

    protected void setFillType(FillType fillType) {
        this.fillType = fillType;
    }

    protected void setComment(String comment) {
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

    protected void setChildren(List<Order> children) {
        this.children = children;
    }

    private Collection<Order> children;

    protected Instant entryTime;

    private Portfolio portfolio;
    private Collection<Fill> fills;
    private Collection<Transaction> transactions;
    protected FillType fillType;
    private MarginType marginType;
    private Amount commission;
    private Amount margin;
    protected String comment;
    protected PositionEffect positionEffect;
    private Instant expiration;

    private boolean force; // allow this order to override various types of panic
    private boolean emulation; // ("allow order type emulation" [default, true] or "only use exchange's native functionality")
    protected Order parentOrder;
    protected Fill parentFill;

}
