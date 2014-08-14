package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;

/**
 * A Transaction represents the modification of multiple Positions, whether it is a purchase on a Market or a
 * Transfer of Fungibles between Accounts
 * @author Tim Olson
 */
@Entity
public class Transaction extends Event {

	enum TransactionStatus {
		OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED
	}

	private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	private static final String SEPARATOR = ",";

	public Transaction(Portfolio portfolio, Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price) {

		this.setAmount(amount);
		this.setAsset(asset);
		this.setPrice(price);
		this.setType(type);
		this.setPortfolio(portfolio);
		this.setExchange(exchange);
		this.amountCount = DiscreteAmount.roundedCountForBasis(amount.asBigDecimal(), asset.getBasis());
		this.priceCount = DiscreteAmount.roundedCountForBasis(price.asBigDecimal(), asset.getBasis());

	}

	public Transaction(Fill fill) throws Exception {
		Portfolio portfolio = fill.getOrder().getPortfolio();

		TransactionType transactionType = fill.getVolume().isPositive() ? TransactionType.BUY : TransactionType.SELL;
		//long quantity = Side.BUY.equals(fill.getSide()) ? fill.getQuantity() : -fill.getQuantity();

		this.setAmount(fill.getVolume());
		this.setAsset(fill.getMarket().getBase());
		this.setPrice(fill.getPrice());
		this.setPriceCount(fill.getPriceCount());
		this.setAmountCount(fill.getVolumeCount());
		this.setType(transactionType);
		this.setPortfolio(portfolio);
		this.setCurrency(fill.getMarket().getBase());
		this.setCommission(fill.getCommission());
		this.setMarket(fill.getMarket());
		this.setExchange(fill.getMarket().getExchange());

	}

	public Transaction(SpecificOrder order) throws Exception {
		Portfolio portfolio = order.getPortfolio();

		TransactionType transactionType = order.getVolume().isPositive() ? TransactionType.BUY_RESERVATION : TransactionType.SELL_RESERVATION;
		//long quantity = Side.BUY.equals(fill.getSide()) ? fill.getQuantity() : -fill.getQuantity();

		this.setAmount(order.getVolume());
		this.setAsset(order.getMarket().getBase());
		this.setPrice(order.getLimitPrice());
		this.setType(transactionType);
		this.setPortfolio(portfolio);
		this.setCurrency(order.getMarket().getBase());
		this.setCommission(order.getForcastedCommission());
		this.setMarket(order.getMarket());
		this.setExchange(order.getMarket().getExchange());

	}

	private void setDateTime(Instant time) {
		// TODO Auto-generated method stub

	}

	@Transient
	public Amount getValue() {

		if (getType().equals(TransactionType.BUY) || getType().equals(TransactionType.SELL)) {
			value = (getAmount().negate().times(getPrice(), Remainder.ROUND_EVEN)).plus(getCommission());
		} else if (getType().equals(TransactionType.BUY_RESERVATION) || getType().equals(TransactionType.SELL_RESERVATION)) {
			value = (getAmount().negate().times(getPrice(), Remainder.ROUND_EVEN)).minus(getCommission());

		} else if (getType().equals(TransactionType.CREDIT) || getType().equals(TransactionType.INTREST)) {
			value = (getAmount());
		} else if (getType().equals(TransactionType.DEBIT) || getType().equals(TransactionType.FEES)) {
			value = getAmount().negate();
		} else if (getType().equals(TransactionType.REBALANCE)) {
			value = getAmount().times(getPrice(), Remainder.ROUND_EVEN);

		} else {
			throw new IllegalArgumentException("unsupported transactionType: " + getType());
		}

		return value;
	}

	@ManyToOne(optional = false)
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

	@Nullable
	@ManyToOne(optional = true)
	public Asset getCurrency() {
		return currency;
	}

	@Transient
	public Amount getAmount() {
		return amount;
	}

	@Transient
	public Amount getCommission() {
		return commission;
	}

	//@ManyToOne(optional = false)

	@ManyToOne(optional = false)
	public Portfolio getPortfolio() {
		return portfolio;
	}

	@ManyToOne(optional = false)
	private TransactionType type;

	public TransactionType getType() {
		return type;
	}

	@Transient
	public Amount getPrice() {

		return price;
	}

	private Exchange exchange;

	@Nullable
	@ManyToOne(optional = true)
	public Exchange getExchange() {

		return exchange;
	}

	@Override
	public String toString() {

		return "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "Portfolio=" + getPortfolio() + SEPARATOR + "Exchange="
				+ getExchange() + SEPARATOR + "type=" + getType() + SEPARATOR + "volume=" + getAmount()
				+ (getAsset() != null ? (SEPARATOR + "asset=" + getAsset()) : "") + SEPARATOR + "price=" + getPrice();
	}

	protected void setAmount(Amount amount) {
		this.amount = amount;
	}

	protected void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
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

	protected void setType(TransactionType type) {
		this.type = type;
	}

	protected void setPrice(Amount price) {
		this.price = price;
	}

	protected void setMarket(Market market) {
		this.market = market;
	}

	//   protected Instant getTime() { return acceptedTime; }

	private Amount value;
	private Amount price;

	private Portfolio portfolio;
	private Asset asset;
	private Amount amount;
	private Long commissionCount;
	private long amountCount;

	private long priceCount;
	private Amount commission;

	private Market market;
	@Inject
	private Logger log;
}
