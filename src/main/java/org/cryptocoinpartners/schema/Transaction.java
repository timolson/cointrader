package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
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
 * A Transaction represents the modification of multiple Positions, whether it is a purchase on a Market or a Transfer of Fungibles between Accounts
 * 
 * @author Tim Olson
 */
@Entity
// @Cacheable
@Table(indexes = { @Index(columnList = "type") })
public class Transaction extends Event {

	enum TransactionStatus {
		OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED
	}

	private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
	private static final String SEPARATOR = ",";

	@Inject
	protected transient TransactionDao transactionDao;

	@AssistedInject
	public Transaction(@Assisted Portfolio portfolio, @Assisted Exchange exchange, @Assisted Asset currency, @Assisted TransactionType type,
			@Assisted("transactionAmount") Amount amount, @Assisted("transactionPrice") Amount price) {
		// this.id = getId();
		this.getId();
		this.version = getVersion();

		this.setCurrency(currency);
		this.setAsset(currency);
		//   this.setPrice(price);
		this.setPriceCount(price.toBasis(currency.getBasis(), Remainder.ROUND_EVEN).getCount());
		this.setType(type);
		this.setPortfolio(portfolio);
		this.setExchange(exchange);
		this.setPortfolioName(portfolio);

		if (getCurrency() != null && !getCurrency().equals(portfolio.getBaseAsset())) {
			if (getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing listing = Listing.forPair(getCurrency(), portfolio.getBaseAsset());
				Offer rate = portfolio.getQuoteService().getImpliedBestAskForListing(listing);
				//  DiscreteAmount fxRate = rate.getPrice();
				if (rate != null)
					this.baseRateCount = rate.getPriceCount();
			}

		} else if (getCurrency() != null && getCurrency().equals(portfolio.getBaseAsset()))
			this.baseRateCount = 1;

		if (getCommissionCurrency() != null && !getCommissionCurrency().equals(portfolio.getBaseAsset())) {
			if (!getCurrency().equals(getCommissionCurrency()) && getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing commListing = Listing.forPair(getCommissionCurrency(), portfolio.getBaseAsset());
				Offer commRate = portfolio.getQuoteService().getImpliedBestAskForListing(commListing);
				if (commRate != null)
					this.baseCommissionRateCount = commRate.getPriceCount();
			} else if (getCurrency().equals(getCommissionCurrency()))
				this.baseCommissionRateCount = getBaseRateCount();
		} else if (getCommissionCurrency() != null && getCommissionCurrency().equals(portfolio.getBaseAsset()))
			this.baseCommissionRateCount = 1;

		double basis;
		if (getExchange() != null && getExchange().getFeeBasis(getExchange()) != 0)
			basis = getExchange().getFeeBasis(getExchange());
		else

			basis = getCurrency() == null ? getAsset() == null ? 0 : getAsset().getBasis() : getCurrency().getBasis();
		this.setAmountDecimal(
				basis == 0 ? DecimalAmount.of(amount).asBigDecimal() : DecimalAmount.of(amount).toBasis(basis, Remainder.ROUND_UP).asBigDecimal());

	}

	@AssistedInject
	public Transaction(@Assisted Fill fill, @Assisted Portfolio portfolio, @Assisted Exchange exchange, @Assisted Asset currency,
			@Assisted TransactionType type, @Assisted("transactionAmount") Amount amount, @Assisted("transactionPrice") Amount price) {
		// this.id = getId();
		this.getId();
		this.version = getVersion();
		this.fill = fill;
		synchronized (this.fill) {
			this.fill.addTransaction(this);
		}

		this.setCurrency(currency);
		this.setAsset(currency);
		//  this.setPrice(price);
		this.setPriceCount(price.toBasis(currency.getBasis(), Remainder.ROUND_EVEN).getCount());
		this.setType(type);
		this.setPortfolio(portfolio);
		this.setExchange(exchange);
		this.setPortfolioName(portfolio);

		if (getCurrency() != null && !getCurrency().equals(portfolio.getBaseAsset())) {
			if (getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing listing = Listing.forPair(getCurrency(), portfolio.getBaseAsset());
				Offer rate = portfolio.getQuoteService().getImpliedBestAskForListing(listing);
				//  DiscreteAmount fxRate = rate.getPrice();
				if (rate != null)
					this.baseRateCount = rate.getPriceCount();
			}

		} else if (getCurrency() != null && getCurrency().equals(portfolio.getBaseAsset()))
			this.baseRateCount = 1;

		if (getCommissionCurrency() != null && !getCommissionCurrency().equals(portfolio.getBaseAsset())) {
			if (!getCurrency().equals(getCommissionCurrency()) && getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing commListing = Listing.forPair(getCommissionCurrency(), portfolio.getBaseAsset());
				Offer commRate = portfolio.getQuoteService().getImpliedBestAskForListing(commListing);
				if (commRate != null)
					this.baseCommissionRateCount = commRate.getPriceCount();
			} else if (getCurrency().equals(getCommissionCurrency()))
				this.baseCommissionRateCount = getBaseRateCount();
		} else if (getCommissionCurrency() != null && getCommissionCurrency().equals(portfolio.getBaseAsset()))
			this.baseCommissionRateCount = 1;
		double basis;
		if (getExchange() != null && getExchange().getFeeBasis(getExchange()) != 0)
			basis = getExchange().getFeeBasis(getExchange());
		else

			basis = getCurrency() == null ? getAsset() == null ? 0 : getAsset().getBasis() : getCurrency().getBasis();

		this.setAmountDecimal(
				basis == 0 ? DecimalAmount.of(amount).asBigDecimal() : DecimalAmount.of(amount).toBasis(basis, Remainder.ROUND_UP).asBigDecimal());

	}

	public Transaction(Portfolio portfolio, Exchange exchange, Asset currency, TransactionType type, Amount amount) {
		//   this.id = getId();
		this.getId();
		this.version = getVersion();
		this.setCurrency(currency);
		this.setAsset(currency);
		this.setType(type);
		this.setPortfolio(portfolio);
		this.setExchange(exchange);
		this.setPortfolioName(portfolio);
		if (getCurrency() != null && !getCurrency().equals(portfolio.getBaseAsset())) {
			if (getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing listing = Listing.forPair(getCurrency(), portfolio.getBaseAsset());
				Offer rate = portfolio.getQuoteService().getImpliedBestAskForListing(listing);
				//  DiscreteAmount fxRate = rate.getPrice();
				if (rate != null)
					this.baseRateCount = rate.getPriceCount();
			}

		} else if (getCurrency() != null && getCurrency().equals(portfolio.getBaseAsset()))
			this.baseRateCount = 1;

		if (getCommissionCurrency() != null && !getCommissionCurrency().equals(portfolio.getBaseAsset())) {
			if (!getCurrency().equals(getCommissionCurrency()) && getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing commListing = Listing.forPair(getCommissionCurrency(), portfolio.getBaseAsset());
				Offer commRate = portfolio.getQuoteService().getImpliedBestAskForListing(commListing);
				if (commRate != null)
					this.baseCommissionRateCount = commRate.getPriceCount();
			} else if (getCurrency().equals(getCommissionCurrency()))
				this.baseCommissionRateCount = getBaseRateCount();
		} else if (getCommissionCurrency() != null && getCommissionCurrency().equals(portfolio.getBaseAsset()))
			this.baseCommissionRateCount = 1;

		double basis;
		if (getExchange() != null && getExchange().getFeeBasis(getExchange()) != 0)
			basis = getExchange().getFeeBasis(getExchange());
		else

			basis = getCurrency() == null ? getAsset() == null ? 0 : getAsset().getBasis() : getCurrency().getBasis();

		this.setAmountDecimal(
				basis == 0 ? DecimalAmount.of(amount).asBigDecimal() : DecimalAmount.of(amount).toBasis(basis, Remainder.ROUND_UP).asBigDecimal());

	}

	@AssistedInject
	public Transaction(@Assisted Fill fill, @Assisted Instant creationTime) {
		//  this.id = getId();
		this.getId();
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
		this.asset = (fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? fill.getMarket().getQuote()
				: fill.getMarket().getTradedCurrency(fill.getMarket());
		this.currency = this.asset;
		this.fill = fill;
		synchronized (this.fill) {
			this.fill.addTransaction(this);
		}
		this.setPositionEffect(fill.getOrder().getPositionEffect());
		// this.setPrice(fill.getPrice());
		this.setPriceCount(fill.getPriceCount());
		//f this.time = fill.getTime();
		this.setType(transactionType);
		this.setPortfolio(portfolio);
		this.setPortfolioName(portfolio);
		//BTC/USD, ETH(base)/BTC(quote)

		this.setCommissionCurrency(getAsset());

		this.setMarket(fill.getMarket());
		this.setExchange(fill.getMarket().getExchange());

		if (getCurrency() != null && !getCurrency().equals(portfolio.getBaseAsset())) {
			if (getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing listing = Listing.forPair(getCurrency(), portfolio.getBaseAsset());
				Offer rate = portfolio.getQuoteService().getImpliedBestAskForListing(listing);
				//  DiscreteAmount fxRate = rate.getPrice();
				if (rate != null)
					this.baseRateCount = rate.getPriceCount();
			}

		} else if (getCurrency() != null && getCurrency().equals(portfolio.getBaseAsset()))
			this.baseRateCount = 1;

		if (getCommissionCurrency() != null && !getCommissionCurrency().equals(portfolio.getBaseAsset())) {
			if (!getCurrency().equals(getCommissionCurrency()) && getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing commListing = Listing.forPair(getCommissionCurrency(), portfolio.getBaseAsset());
				Offer commRate = portfolio.getQuoteService().getImpliedBestAskForListing(commListing);
				if (commRate != null)
					this.baseCommissionRateCount = commRate.getPriceCount();
			} else if (getCurrency().equals(getCommissionCurrency()))
				this.baseCommissionRateCount = getBaseRateCount();
		} else if (getCommissionCurrency() != null && getCommissionCurrency().equals(portfolio.getBaseAsset()))
			this.baseCommissionRateCount = 1;
		double basis;
		if (getExchange() != null && getExchange().getFeeBasis(getExchange()) != 0)
			basis = getExchange().getFeeBasis(getExchange());
		else

			basis = getCurrency() == null ? getAsset() == null ? 0 : getAsset().getBasis() : getCurrency().getBasis();

		//  this.setAmountDecimal((basis==0 ? ((fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? (amount.times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal() : amount.asBigDecimal()) : DecimalAmount.of((fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? (amount.times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal() : amount.asBigDecimal()).asBigDecimal())) ;
		this.setCommissionDecimal((basis == 0
				? ((fill.getMarket().getTradedCurrency(fill.getMarket()) == null)
						? (fill.getCommission().times(getPrice(), Remainder.ROUND_EVEN).asBigDecimal())
						: fill.getCommission().asBigDecimal())
				: DecimalAmount.of((fill.getMarket().getTradedCurrency(fill.getMarket()) == null)
						? (fill.getCommission().times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal()
						: fill.getCommission().asBigDecimal()).toBasis(basis, Remainder.ROUND_UP).asBigDecimal()));
		this.setMarginDecimal((basis == 0
				? ((fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? (fill.getMargin().times(getPrice(), Remainder.ROUND_EVEN).asBigDecimal())
						: fill.getMargin().asBigDecimal())
				: DecimalAmount.of((fill.getMarket().getTradedCurrency(fill.getMarket()) == null)
						? (fill.getMargin().times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal()
						: fill.getMargin().asBigDecimal()).toBasis(basis, Remainder.ROUND_UP).asBigDecimal()));
		this.assetAmount = this.getCommission().plus(this.getMargin());

		this.amount = assetAmount;

	}

	@AssistedInject
	public Transaction(@Assisted Order order, @Assisted Instant creationTime) {
		// this.id = getId();
		this.getId();
		this.version = getVersion();
		this.portfolio = order.getPortfolio();

		TransactionType transactionType = order.getVolume().isPositive() ? TransactionType.BUY_RESERVATION : TransactionType.SELL_RESERVATION;
		this.order = order;
		synchronized (this.order) {
			this.order.addTransaction(this);
		}
		this.time = creationTime;
		this.asset = (order.getMarket().getTradedCurrency(order.getMarket()) == null) ? order.getMarket().getBase()
				: order.getMarket().getTradedCurrency(order.getMarket());

		//  this.asset = order.getMarket().getTradedCurrency(order.getMarket());

		this.currency = order.getMarket().getBase();
		this.setAsset(currency);
		this.setPrice(order.getLimitPrice());
		this.setType(transactionType);
		this.setPortfolio(portfolio);
		this.setPositionEffect(order.getPositionEffect());
		if (getAsset().equals(order.getMarket().getQuote()))
			this.setCommissionCurrency(order.getMarket().getBase());

		else
			this.setCommissionCurrency(getAsset());
		//if traded=quote, then do this, if traded== base then just volume
		this.setMarket(order.getMarket());
		this.setPortfolioName(portfolio);
		// this.time = order.getTime();
		this.setExchange(order.getMarket().getExchange());

		if (getCurrency() != null && !getCurrency().equals(portfolio.getBaseAsset())) {
			if (getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing listing = Listing.forPair(getCurrency(), portfolio.getBaseAsset());
				Offer rate = portfolio.getQuoteService().getImpliedBestAskForListing(listing);
				//  DiscreteAmount fxRate = rate.getPrice();
				if (rate != null)
					this.baseRateCount = rate.getPriceCount();
			}

		} else if (getCurrency() != null && getCurrency().equals(portfolio.getBaseAsset()))
			this.baseRateCount = 1;

		if (getCommissionCurrency() != null && !getCommissionCurrency().equals(portfolio.getBaseAsset())) {
			if (!getCurrency().equals(getCommissionCurrency()) && getPortfolio() != null && getPortfolio().getQuoteService() != null) {
				Listing commListing = Listing.forPair(getCommissionCurrency(), portfolio.getBaseAsset());
				Offer commRate = portfolio.getQuoteService().getImpliedBestAskForListing(commListing);
				if (commRate != null)
					this.baseCommissionRateCount = commRate.getPriceCount();
			} else if (getCurrency().equals(getCommissionCurrency()))
				this.baseCommissionRateCount = getBaseRateCount();
		} else if (getCommissionCurrency() != null && getCommissionCurrency().equals(portfolio.getBaseAsset()))
			this.baseCommissionRateCount = 1;

		double basis;
		if (getExchange() != null && getExchange().getFeeBasis(getExchange()) != 0)
			basis = getExchange().getFeeBasis(getExchange());
		else

			basis = getCurrency() == null ? getAsset() == null ? 0 : getAsset().getBasis() : getCurrency().getBasis();

		//  this.setAmountDecimal((basis==0 ? ((fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? (amount.times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal() : amount.asBigDecimal()) : DecimalAmount.of((fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? (amount.times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal() : amount.asBigDecimal()).asBigDecimal())) ;
		this.setCommissionDecimal((basis == 0
				? ((order.getMarket().getTradedCurrency(order.getMarket()) == null)
						? (order.getForcastedCommission().times(getPrice(), Remainder.ROUND_EVEN).asBigDecimal())
						: order.getForcastedCommission().asBigDecimal())
				: DecimalAmount.of((order.getMarket().getTradedCurrency(order.getMarket()) == null)
						? (order.getForcastedCommission().times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal()
						: order.getForcastedCommission().asBigDecimal()).toBasis(basis, Remainder.ROUND_UP).asBigDecimal()));
		this.setMarginDecimal((basis == 0
				? ((order.getMarket().getTradedCurrency(order.getMarket()) == null)
						? (order.getForcastedMargin().times(getPrice(), Remainder.ROUND_EVEN).asBigDecimal())
						: order.getForcastedMargin().asBigDecimal())
				: DecimalAmount.of((order.getMarket().getTradedCurrency(order.getMarket()) == null)
						? (order.getForcastedMargin().times(getPrice(), Remainder.ROUND_EVEN)).asBigDecimal()
						: order.getForcastedMargin().asBigDecimal()).toBasis(basis, Remainder.ROUND_UP).asBigDecimal()));

		this.assetAmount = this.getCommission().plus(this.getMargin());

		this.amount = assetAmount;
	}

	private synchronized void setDateTime(Instant time) {
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
	@JoinColumn(name = "asset")
	public Asset getAsset() {
		return asset;
	}

	public @Nullable Long getPriceCount() {
		return priceCount;
	}

	public @Nullable Long getBaseRateCount() {
		return baseRateCount;
	}

	@Transient
	public Amount getBaseRate() {
		if (portfolio.getBaseAsset().getBasis() == 0)
			return null;
		if (baseRate == null)
			this.baseRate = new DiscreteAmount(getBaseRateCount(), portfolio.getBaseAsset().getBasis());

		return baseRate;
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return getOrder() != null ? getOrder() : getFill() != null ? getFill() : getExchange() != null ? getExchange() : getPortfolio();
	}

	@Nullable
	public long getBaseCommissionRateCount() {

		return baseCommissionRateCount;
	}

	@Transient
	public Amount getBaseCommissionRate() {

		if (getPortfolio().getBaseAsset().getBasis() == 0)
			return null;
		if (baseCommissionRate == null)
			this.baseCommissionRate = new DiscreteAmount(getBaseCommissionRateCount(), getPortfolio().getBaseAsset().getBasis());

		return baseCommissionRate;

	}

	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(name = "market")
	public Market getMarket() {
		return market;
	}

	private Asset currency;

	@ManyToOne(optional = false)
	@JoinColumn(name = "currency")
	public Asset getCurrency() {
		return currency;
	}

	@Nullable
	@Column(precision = 18, scale = 8)
	public BigDecimal getAmountDecimal() {
		if (amount == null)
			return BigDecimal.ZERO;
		return amount.asBigDecimal();
	}

	protected synchronized void setAmountDecimal(BigDecimal amount) {
		if (amount != null) {
			this.amount = DecimalAmount.of(amount);
		}
	}

	@Transient
	public Amount getAmount() {
		return amount;
	}

	@Nullable
	@Column(precision = 18, scale = 8)
	public BigDecimal getAssetAmountDecimal() {
		if (assetAmount == null)
			return BigDecimal.ZERO;
		return assetAmount.asBigDecimal();
	}

	protected synchronized void setAssetAmountDecimal(BigDecimal assetAmount) {
		if (assetAmount != null) {
			this.assetAmount = DecimalAmount.of(assetAmount);
		}
	}

	@Transient
	public Amount getAssetAmount() {
		return assetAmount;
	}

	@Transient
	public Amount getCommission() {
		return commission;

	}

	@Nullable
	@Column(precision = 18, scale = 8)
	public BigDecimal getCommissionDecimal() {
		if (commission == null)
			return BigDecimal.ZERO;
		return commission.asBigDecimal();

	}

	protected synchronized void setCommissionDecimal(BigDecimal commission) {
		if (commission != null) {
			this.commission = DecimalAmount.of(commission);
		}
	}

	@Nullable
	@Column(precision = 18, scale = 8)
	public BigDecimal getMarginDecimal() {
		if (margin == null)
			return BigDecimal.ZERO;
		return margin.asBigDecimal();

	}

	protected synchronized void setMarginDecimal(BigDecimal margin) {
		if (margin != null) {
			this.margin = DecimalAmount.of(margin);
		}
	}

	@Transient
	public Amount getMargin() {
		return margin;

	}

	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(name = "commissionCurrency")
	public Asset getCommissionCurrency() {
		return commissionCurrency;
	}

	public @ManyToOne(optional = true)
	//, cascade = { CascadeType.MERGE, CascadeType.REMOVE })
	@JoinColumn(name = "`order`") Order getOrder() {
		return order;
	}

	// @PrePersist
	@Override
	public synchronized void prePersist() {
	};

	public synchronized void prePersist1() {

		if (getDao() != null) {

			EntityBase dbPortfolio = null;
			EntityBase dbOrder = null;
			EntityBase dbFill = null;
			/*
			 * if (getPortfolio() != null) { try { dbPortfolio = getDao().findById(getPortfolio().getClass(), getPortfolio().getId()); if (dbPortfolio !=
			 * null) { getPortfolio().setVersion(dbPortfolio.getVersion()); if (getPortfolio().getRevision() > dbPortfolio.getRevision()) {
			 * getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getPortfolio()); } } else {
			 * getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getPortfolio()); } } catch (Exception | Error ex) { if
			 * (dbPortfolio != null) if (getPortfolio().getRevision() > dbPortfolio.getRevision()) {
			 * getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getPortfolio()); } else {
			 * getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getPortfolio()); } } }
			 */

			if (getFill() != null) {
				try {
					dbFill = getFill().getDao().findById(getFill().getClass(), getFill().getId());
					if (dbFill != null) {
						getFill().setVersion(dbFill.getVersion());
						if (getFill().getRevision() > dbFill.getRevision()) {
							getFill().setPeristanceAction(PersistanceAction.MERGE);
							getDao().merge(getFill());
						}
					} else if (dbFill == null) {
						getFill().setPeristanceAction(PersistanceAction.NEW);
						getDao().persist(getFill());
					}
				} catch (Exception | Error ex) {
					if (dbFill != null)
						if (getFill().getRevision() > dbFill.getRevision()) {
							getFill().setPeristanceAction(PersistanceAction.MERGE);
							getDao().merge(getFill());
						} else {
							getFill().setPeristanceAction(PersistanceAction.NEW);
							getDao().persist(getFill());
						}
				}
			}

			if (getOrder() != null) {
				try {
					dbOrder = getDao().findById(getOrder().getClass(), getOrder().getId());
					if (dbOrder != null) {
						getOrder().setVersion(dbOrder.getVersion());
						if (getOrder().getRevision() > dbOrder.getRevision()) {
							getOrder().setPeristanceAction(PersistanceAction.MERGE);
							getDao().merge(getOrder());
						}
					} else if (dbOrder == null) {
						getOrder().setPeristanceAction(PersistanceAction.NEW);
						getDao().persist(getOrder());
					}
				} catch (Exception | Error ex) {
					if (dbOrder != null)
						if (getOrder().getRevision() > dbOrder.getRevision()) {
							getOrder().setPeristanceAction(PersistanceAction.MERGE);
							getDao().merge(getOrder());
						} else {
							getOrder().setPeristanceAction(PersistanceAction.NEW);
							getDao().persist(getOrder());
						}
				}
			}

		}

	}

	public @ManyToOne(optional = true) @JoinColumn(name = "fill")
	//, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	Fill getFill() {
		return fill;
	}

	@Override
	public synchronized void merge() {

		this.setPeristanceAction(PersistanceAction.MERGE);

		this.setRevision(this.getRevision() + 1);
		try {
			transactionDao.merge(this);
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}

	}

	@Override
	public synchronized EntityBase refresh() {
		return transactionDao.refresh(this);
	}

	@Override
	public synchronized void persit() {

		this.setPeristanceAction(PersistanceAction.NEW);

		this.setRevision(this.getRevision() + 1);
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
	@JoinColumn(name = "portfolio")
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
		if (getAsset().getBasis() == 0)
			return null;
		if (price == null)
			this.price = new DiscreteAmount(getPriceCount(), getAsset().getBasis());

		return price;
	}

	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(name = "exchange")
	public Exchange getExchange() {

		return exchange;
	}

	@Override
	public String toString() {

		return "id=" + getId() + SEPARATOR + "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "Portfolio=" + getPortfolio()
				+ SEPARATOR + "Exchange=" + getExchange() + SEPARATOR + "type=" + getType()
				+ (getFill() != null ? (SEPARATOR + "fill=" + getFill().getId()) : "") + (getOrder() != null ? (SEPARATOR + "order=" + getOrder()) : "")
				+ SEPARATOR + "amount=" + getAmount() + (getAsset() != null ? (SEPARATOR + "asset=" + getAsset()) : "") + SEPARATOR + "price="
				+ (getPrice() == null || getPrice() != DecimalAmount.ZERO ? getPrice() : "")
				+ (getCurrency() != null ? (SEPARATOR + "currency=" + getCurrency()) : "")
				+ (getCommission() != null ? (SEPARATOR + "commission=" + getCommission()) : "")
				+ (getMargin() != null ? (SEPARATOR + "margin=" + getMargin()) : "") + (getBaseRate() != null ? (SEPARATOR + "base rate=" + getBaseRate()) : "")
				+ (getBaseCommissionRate() != null ? (SEPARATOR + "getBaseCommissionRate=" + getBaseCommissionRate()) : "");
	}

	protected synchronized void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	protected synchronized void setOrder(Order order) {
		this.order = order;

	}

	protected synchronized void setFill(Fill fill) {
		this.fill = fill;

	}

	protected synchronized void setExchange(Exchange exchange) {
		this.exchange = exchange;
	}

	protected synchronized void setAsset(Asset asset) {
		this.asset = asset;
	}

	protected synchronized void setBaseCommissionRateCount(long baseCommissionRateCount) {
		this.baseCommissionRateCount = baseCommissionRateCount;
	}

	protected synchronized void setBaseRateCount(long baseRateCount) {
		this.baseRateCount = baseRateCount;
	}

	protected synchronized void setPriceCount(long priceCount) {
		this.priceCount = priceCount;
		this.price = null;
	}

	protected synchronized void setCurrency(Asset asset) {
		this.currency = asset;
	}

	protected synchronized void setCommissionCurrency(Asset asset) {
		this.commissionCurrency = asset;
	}

	protected synchronized void setPortfolioName(Portfolio portfolio) {
		this.portfolioName = portfolio.getName();
	}

	protected synchronized void setType(TransactionType type) {
		this.type = type;
	}

	protected synchronized void setPositionEffect(PositionEffect positionEffect) {
		this.positionEffect = positionEffect;
	}

	protected synchronized void setPrice(Amount price) {
		this.price = price;
	}

	protected synchronized void setMarket(Market market) {
		this.market = market;
	}

	Transaction() {

	}

	//   protected Instant getTime() { return acceptedTime; }

	private Amount price;
	private Amount baseRate;
	private Amount baseCommissionRate;

	@Nullable
	private Portfolio portfolio;
	@Nullable
	private Order order;
	@Nullable
	private Fill fill;
	private Asset asset;
	private Amount amount;
	private Amount assetAmount;
	private String portfolioName;
	private Amount commission;
	private Amount margin;
	private Exchange exchange;
	private long priceCount;
	private long baseRateCount;
	private long baseCommissionRateCount;

	private Asset commissionCurrency;
	private Market market;
	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.transaction");

	@Override
	@Transient
	public Dao getDao() {
		return transactionDao;
	}

	@Override
	@Transient
	public synchronized void setDao(Dao dao) {
		transactionDao = (TransactionDao) dao;
		// TODO Auto-generated method stub
		//  return null;
	}

	@Override
	public synchronized void detach() {

		transactionDao.detach(this);

	}

	@Override
	public synchronized void delete() {
		log.debug("Transaction - Delete : Delete of Transaction " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void persitParents() {
		// TODO Auto-generated method stub

	}

}
