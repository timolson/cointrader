package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.TransactionDao;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * A Transaction represents the modification of multiple Positions, whether it is a purchase on a Market or a
 * Transfer of Fungibles between Accounts
 * @author Tim Olson
 */
@Entity
@Cacheable
@Table(indexes = { @Index(columnList = "type") })
public class Transaction extends Event {

    enum TransactionStatus {
        OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED
    }

    private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SEPARATOR = ",";

    @Inject
    protected TransactionDao transactionDao;

    @AssistedInject
    public Transaction(@Assisted Portfolio portfolio, @Assisted Exchange exchange, @Assisted Asset currency, @Assisted TransactionType type,
            @Assisted("transactionAmount") Amount amount, @Assisted("transactionPrice") Amount price) {
        // this.id = getId();
        this.version = getVersion();
        this.setAmount(amount);
        this.amountCount = amount.toBasis(currency.getBasis(), Remainder.ROUND_EVEN).getCount();
        this.setCurrency(currency);
        this.setPrice(price);
        this.priceCount = price.toBasis(currency.getBasis(), Remainder.ROUND_EVEN).getCount();
        this.setType(type);
        this.setPortfolio(portfolio);
        this.setExchange(exchange);
        this.setPortfolioName(portfolio);
    }

    @AssistedInject
    public Transaction(@Assisted Fill fill, @Assisted Portfolio portfolio, @Assisted Exchange exchange, @Assisted Asset currency,
            @Assisted TransactionType type, @Assisted("transactionAmount") Amount amount, @Assisted("transactionPrice") Amount price) {
        // this.id = getId();
        this.version = getVersion();
        fill.addTransaction(this);
        this.setAmount(amount);
        this.amountCount = amount.toBasis(currency.getBasis(), Remainder.ROUND_EVEN).getCount();
        this.setCurrency(currency);
        this.setPrice(price);
        this.priceCount = price.toBasis(currency.getBasis(), Remainder.ROUND_EVEN).getCount();
        this.setType(type);
        this.setPortfolio(portfolio);
        this.setExchange(exchange);
        this.setPortfolioName(portfolio);
        this.fill = fill;
    }

    public Transaction(Portfolio portfolio, Exchange exchange, Asset currency, TransactionType type, Amount amount) {
        //   this.id = getId();
        this.version = getVersion();
        this.setAmount(amount);
        this.amountCount = amount.toBasis(currency.getBasis(), Remainder.ROUND_EVEN).getCount();
        this.setCurrency(currency);
        this.setType(type);
        this.setPortfolio(portfolio);
        this.setExchange(exchange);
        this.setPortfolioName(portfolio);
    }

    @AssistedInject
    public Transaction(@Assisted Fill fill, @Assisted Instant creationTime) {
        //  this.id = getId();
        this.version = getVersion();
        Portfolio portfolio = fill.getOrder().getPortfolio();
        TransactionType transactionType = null;

        if (fill.getOrder().getPositionEffect() == PositionEffect.OPEN || fill.getOrder().getPositionEffect() == PositionEffect.CLOSE) {
            //is entering or exiting trade
            transactionType = (fill.getVolume().isPositive()) ? TransactionType.BUY : TransactionType.SELL;
        } else {
            // is either  buying base currency and selling quote or selling base currency and buying quote
            transactionType = TransactionType.REBALANCE;
        }
        this.time = creationTime;
        this.asset = fill.getMarket().getTradedCurrency();
        this.currency = fill.getMarket().getBase();
        fill.addTransaction(this);
        this.setPositionEffect(fill.getOrder().getPositionEffect());
        this.setPrice(fill.getPrice());
        this.setPriceCount(fill.getPriceCount());
        //f this.time = fill.getTime();
        this.setType(transactionType);
        this.setPortfolio(portfolio);
        this.setPortfolioName(portfolio);
        this.setCommission(fill.getCommission());
        this.setMargin(fill.getMargin());
        this.setCommissionCurrency(fill.getMarket().getTradedCurrency());
        this.assetAmount = this.getCommission().plus(this.getMargin());

        this.amount = this.getCommission().plus(this.getMargin());

        this.setMarket(fill.getMarket());
        this.setExchange(fill.getMarket().getExchange());
        this.fill = fill;

    }

    @AssistedInject
    public Transaction(@Assisted Order order, @Assisted Instant creationTime) {
        // this.id = getId();
        this.version = getVersion();
        Portfolio portfolio = order.getPortfolio();

        TransactionType transactionType = order.getVolume().isPositive() ? TransactionType.BUY_RESERVATION : TransactionType.SELL_RESERVATION;
        order.addTransaction(this);
        this.time = creationTime;
        this.asset = order.getMarket().getTradedCurrency();

        this.currency = order.getMarket().getBase();
        this.setPrice(order.getLimitPrice());
        this.setType(transactionType);
        this.setPortfolio(portfolio);
        this.setPositionEffect(order.getPositionEffect());
        this.setCommission(order.getForcastedCommission());
        this.setMargin(order.getForcastedMargin());
        this.setCommissionCurrency(order.getMarket().getTradedCurrency());
        //if traded=quote, then do this, if traded== base then just volume
        this.amount = this.getCommission().plus(this.getMargin());
        this.assetAmount = this.getCommission().plus(this.getMargin());
        this.setMarket(order.getMarket());
        this.setPortfolioName(portfolio);
        // this.time = order.getTime();
        this.setExchange(order.getMarket().getExchange());
        this.order = order;

    }

    private void setDateTime(Instant time) {
        // TODO Auto-generated method stub

    }

    @Transient
    public Amount getValue() {
        Amount value = DecimalAmount.ZERO;

        if (getType() == (TransactionType.BUY) || getType() == (TransactionType.SELL)) {

            Amount notional = getAssetAmount();
            //Amount totalvalue = notional.plus(getCommission());
            value = notional;
        } else if (getType() == (TransactionType.BUY_RESERVATION) || getType() == (TransactionType.SELL_RESERVATION)) {
            value = getAssetAmount().minus(getCommission());

        } else if (getType() == (TransactionType.CREDIT) || getType() == (TransactionType.INTREST)) {
            value = getAmount();
        } else if (getType() == (TransactionType.DEBIT) || getType() == (TransactionType.FEES)) {
            value = getAmount();
        } else if (getType() == (TransactionType.REBALANCE)) {
            value = getAmount();

        } else {
            throw new IllegalArgumentException("unsupported transactionType: " + getType());
        }

        return value;
    }

    @Transient
    public Amount getCost() {
        Amount value = DecimalAmount.ZERO;
        if (getType() == (TransactionType.BUY) || getType() == (TransactionType.SELL) || getType() == (TransactionType.REBALANCE)) {
            // issue works when entering position on margin, howeever when exiting no margin applies.
            // so open postion with 3 times mulitpler, so it costs me a 3rd
            // whne a close a postion it still tinks i it is a 3rd so we over cacluated by 1/3rd of the PnL so always overstating the cash balance

            //(FeesUtil.getMargin(orderBuilder.getOrder()).plus(FeesUtil.getCommission(orderBuilder.getOrder()))).negate()

            //	if (getAmount().isNegative() && getMarket().getContractSize() == 1)
            //	cost = cost.negate();
            //Amount notional = getAssetAmount();
            //Amount cost = notional.divide(getExchange().getMargin(), Remainder.ROUND_EVEN);

            Amount totalcost = value.plus(getCommission());
            value = totalcost;
        } else if (getType() == (TransactionType.BUY_RESERVATION) || getType() == (TransactionType.SELL_RESERVATION)) {
            Amount notional = (getCommission());
            value = notional;

        } else if (getType() == (TransactionType.CREDIT) || getType() == (TransactionType.INTREST)) {
            value = getAmount();
        } else if (getType() == (TransactionType.DEBIT) || getType() == (TransactionType.FEES)) {
            value = getAmount();
        }

        return value;
    }

    @Nullable
    @ManyToOne(optional = true)
    public Asset getAsset() {
        return asset;
    }

    public @Nullable
    Long getPriceCount() {
        return priceCount;
    }

    public Long getAmountCount() {
        return amountCount;
    }

    public @Nullable
    Long getCommissionCount() {
        return commissionCount;
    }

    @Nullable
    @ManyToOne(optional = true)
    public Market getMarket() {
        return market;
    }

    private Asset currency;

    @ManyToOne(optional = false)
    public Asset getCurrency() {
        return currency;
    }

    @Transient
    public Amount getAmount() {
        return amount;
    }

    @Transient
    public Amount getAssetAmount() {
        return assetAmount;
    }

    @Transient
    public Amount getCommission() {
        return commission;
    }

    @Transient
    public Amount getMargin() {
        return margin;
    }

    @Nullable
    @ManyToOne(optional = true)
    public Asset getCommissionCurrency() {
        return commissionCurrency;
    }

    public @ManyToOne(optional = true)
    //, cascade = { CascadeType.MERGE, CascadeType.REMOVE })
    @JoinColumn(name = "`order`")
    Order getOrder() {
        return order;
    }

    // @PrePersist
    private void prePersist() {

        if (getDao() != null) {

            Portfolio transactionPortfolio = null;
            Order transactionOrder = null;
            Fill transactionFill = null;

            //UUID parentOrderId = null;
            //  if (portfolio != null) {
            // transactionPortfolio = (transactionDao.find(Portfolio.class, portfolio.getId()));

            //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
            //    if (!transactionDao.contains(getPortfolio()))
            //      transactionDao.persist(getPortfolio());
            //  portfolio.merge();
            //}

            if (getOrder() != null) {
                //  transactionOrder = (transactionDao.find(Order.class, order.getId()));

                //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
                //    if (transactionOrder == null)
                //    transactionDao.persist(order);
                //    order.merge();
                transactionOrder = (getDao().find(getOrder().getClass(), getOrder().getId()));

                if (transactionOrder == null)
                    getDao().persist(getOrder());
            }

            if (getFill() != null) {
                //transactionFill = (transactionDao.find(Fill.class, fill.getId()));
                transactionFill = (getDao().find(getFill().getClass(), getFill().getId()));
                //  positionId = (fillDao.queryZeroOne(UUID.class, "select p.id from Position p where p.id=?1", position.getId()));
                if (transactionFill == null)
                    getDao().persist(getFill());
            }
        }

        //        
        //        UUID portfolioId = null;
        //        UUID orderId = null;
        //        UUID fillId = null;
        //        if (portfolio != null) {
        //            portfolioId = (transactionDao == null) ? (EM.queryZeroOne(UUID.class, "select p.id from Portfolio p where p.id=?1", portfolio.getId()))
        //                    : (transactionDao.queryZeroOne(UUID.class, "select p.id from Portfolio p where p.id=?1", portfolio.getId()));
        //            if (portfolioId == null)
        //                portfolio.persit();
        //        }
        //
        //        if (order != null) {
        //            orderId = (transactionDao == null) ? (EM.queryZeroOne(UUID.class, "select o.id from Order o where o.id=?1", order.getId())) : (transactionDao
        //                    .queryZeroOne(UUID.class, "select o.id from Order o where o.id=?1", order.getId()));
        //
        //            if (orderId == null)
        //                order.persit();
        //        }
        //        if (fill != null) {
        //            fillId = (transactionDao == null) ? (EM.queryZeroOne(UUID.class, "select f.id from Fill f where f.id=?1", fill.getId())) : (transactionDao
        //                    .queryZeroOne(UUID.class, "select f.id from Fill f where f.id=?1", fill.getId()));
        //
        //            if (fillId == null)
        //                fill.persit();
        //        }

        //detach();
    }

    public @ManyToOne(optional = true)
    @JoinColumn(name = "fill")
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    Fill getFill() {
        return fill;
    }

    @Override
    public synchronized void merge() {
        try {
            if (fill != null)
                fill.find();
            if (order != null)
                order.find();
            transactionDao.merge(this);
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {

            System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }

    }

    @Override
    public EntityBase refresh() {
        return transactionDao.refresh(this);
    }

    @Override
    public synchronized void persit() {
        //  
        //
        // List<Transaction> duplicate = transactionDao.queryList(Transaction.class, "select t from  Transaction t where t=?1", this);
        //if (duplicate == null || duplicate.isEmpty())
        try {

            transactionDao.persist(this);
            //  System.out.println("saved:" + this); //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {

            System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }
        //
        //PersistUtil.insert(this);
        //else
        //  transactionDao.merge(this);
        //  PersistUtil.merge(this);
        //  }
        // if (this.parentOrder != null)
        //    parentOrder.persit();

    }

    @ManyToOne(optional = false)
    public Portfolio getPortfolio() {
        return portfolio;
    }

    @Transient
    public String getPortfolioName() {
        return portfolioName;

    }

    @ManyToOne(optional = false)
    private TransactionType type;

    public TransactionType getType() {
        return type;
    }

    @ManyToOne(optional = true)
    private PositionEffect positionEffect;

    public PositionEffect getPositionEffect() {
        return positionEffect;
    }

    @Transient
    public Amount getPrice() {

        return price;
    }

    @Nullable
    @ManyToOne(optional = true)
    public Exchange getExchange() {

        return exchange;
    }

    @Override
    public String toString() {

        return "id=" + getId() + SEPARATOR + "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "Portfolio=" + getPortfolio()
                + SEPARATOR + "Exchange=" + getExchange() + SEPARATOR + "type=" + getType() + (getFill() != null ? (SEPARATOR + "fill=" + getFill()) : "")
                + (getOrder() != null ? (SEPARATOR + "order=" + getOrder()) : "") + SEPARATOR + "amount=" + getAmount()
                + (getAsset() != null ? (SEPARATOR + "currency=" + getAsset()) : "") + SEPARATOR + "price="
                + (getPrice() != DecimalAmount.ZERO ? getPrice() : "") + (getCurrency() != null ? (SEPARATOR + "currency=" + getCurrency()) : "");
    }

    protected void setAmount(Amount amount) {
        this.amount = amount;
    }

    protected void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    protected void setOrder(Order order) {
        this.order = order;

    }

    protected void setFill(Fill fill) {
        this.fill = fill;

    }

    protected void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    protected void setAsset(Asset asset) {
        this.asset = asset;
    }

    protected void setCommission(Amount commission) {
        this.commission = commission;
    }

    protected void setMargin(Amount margin) {
        this.margin = margin;
    }

    protected void setCommissionCount(Long commissionCount) {
        this.commissionCount = commissionCount;
    }

    protected void setPriceCount(Long priceCount) {
        this.priceCount = priceCount;
    }

    protected void setAmountCount(Long amountCount) {
        this.amountCount = amountCount;
    }

    protected void setCurrency(Asset asset) {
        this.currency = asset;
    }

    protected void setCommissionCurrency(Asset asset) {
        this.commissionCurrency = asset;
    }

    protected void setPortfolioName(Portfolio portfolio) {
        this.portfolioName = portfolio.getName();
    }

    protected void setType(TransactionType type) {
        this.type = type;
    }

    protected void setPositionEffect(PositionEffect positionEffect) {
        this.positionEffect = positionEffect;
    }

    protected void setPrice(Amount price) {
        this.price = price;
    }

    protected void setMarket(Market market) {
        this.market = market;
    }

    Transaction() {

    }

    //   protected Instant getTime() { return acceptedTime; }

    private Amount price;
    @Nullable
    private Portfolio portfolio;
    @Nullable
    private Order order;
    @Nullable
    private Fill fill;
    private Asset asset;
    private Amount amount;
    private Amount assetAmount;
    private Long commissionCount;
    private long amountCount;
    private String portfolioName;

    private long priceCount;
    private Amount commission;
    private Amount margin;
    private Exchange exchange;

    private Asset commissionCurrency;
    private Market market;
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.transaction");

    @Override
    @Transient
    public Dao getDao() {
        return transactionDao;
    }

    @Override
    public void detach() {

        transactionDao.detach(this);

    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

}
