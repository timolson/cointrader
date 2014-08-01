package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.RemainderHandler;
import org.hibernate.Hibernate;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.joda.time.Instant;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;


/**
 * A Transaction represents the modification of multiple Positions, whether it is a purchase on a Market or a
 * Transfer of Fungibles between Accounts
 * @author Tim Olson
 */
@Entity
public class Transaction extends Event {

    enum TransactionStatus { OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED }
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	private static final String SEPARATOR = ",";
	
     // todo add basis rounding
	   public Transaction(Portfolio portfolio, Asset asset, TransactionType type, long priceCount, Amount volume) {
		   this.volume=volume;
		   this.volumeCount = DiscreteAmount.roundedCountForBasis(volume.asBigDecimal(), asset.getBasis());
		   this.portfolio=portfolio;
		   this.asset=asset;
		   this.priceCount=priceCount;
		   this.type=type;
	       	     
	    }
	   public Transaction(Portfolio portfolio, Asset asset, TransactionType type, long priceCount, Amount volume, Currency currnecy, Amount Commission) {
		   this.volume=volume;
		   this.volumeCount = DiscreteAmount.roundedCountForBasis(volume.asBigDecimal(), asset.getBasis());
		   this.portfolio=portfolio;
		   this.asset=asset;
		   this.priceCount=priceCount;
		   this.type=type;
		   this.commission=commission;
		   this.currency=currency;
	       	     
	    }
	   
	   public Transaction(Fill fill) throws Exception {
			Portfolio portfolio = fill.getOrder().getPortfolio();
			Market security = fill.getMarket();
					

			TransactionType transactionType = fill.getVolume().isPositive() ? TransactionType.BUY : TransactionType.SELL;
			//long quantity = Side.BUY.equals(fill.getSide()) ? fill.getQuantity() : -fill.getQuantity();

			this.setDateTime(fill.getTime());
			this.setQuantity( fill.getVolume());
			this.setPrice(fill.getPrice());
			this.setType(transactionType);
			this.setSecurity(security);
			this.setPortfolio(portfolio);
			this.setCurrency(fill.getMarket().getBase());
			this.setCommission(fill.getCommission());
			//portfolio.getTransactions().add(this);
			log.debug(portfolio.getTransactions().toString());
	
			String logMessage = "executed transaction type: " + getType() + " quantity: " + getAmount() + " of " + getAsset()
					+ " price: " + getPrice() + " commission: " + getCommission();

			log.info(logMessage);
		}
     
	   private void setSecurity(Market security) {
		// TODO Auto-generated method stub
		
	}
	private void setQuantity(Amount volume2) {
		// TODO Auto-generated method stub
		
	}
	private void setDateTime(Instant time) {
		// TODO Auto-generated method stub
		
	}
	@Transient
	   public Amount getValue() {
		 
			if (this.value == null) {
				if (getType().equals(TransactionType.BUY) || getType().equals(TransactionType.SELL) ) {
					this.value = (getAmount().negate().times(getPrice(), Remainder.ROUND_EVEN)).minus(getCommission());
					
				} else if (getType().equals(TransactionType.CREDIT) || getType().equals(TransactionType.INTREST)) {
					this.value = (getPrice());
				} else if (getType().equals(TransactionType.DEBIT) || getType().equals(TransactionType.FEES)) {
					this.value = getPrice().negate();
				} else if (getType().equals(TransactionType.REBALANCE)) {
					this.value = getAmount().times(getPrice(), Remainder.ROUND_EVEN);
							
							
				} else {
					throw new IllegalArgumentException("unsupported transactionType: " + getType());
				}
			}
			return this.value;
		}
    @ManyToOne(optional = false)
    public Asset getAsset() { return asset; }

    @Transient
    public Asset getCurrency() { return currency; }
    
    @Transient
    public Amount getCommission() { return commission; }
    
    
    @ManyToOne(optional = false)
    public Portfolio getPortfolio() { return portfolio; }
    
    @ManyToOne(optional = false)
    private TransactionType type;
    public TransactionType getType() { return type; }
    
    @Transient
    public Amount getPrice() { 
    	
    	return (new DiscreteAmount(priceCount, getAsset().getBasis())); }

   

	public String toString() {
		
		
		return  "time=" + (getTime() != null ? (FORMAT.format(getTime())) : "") + SEPARATOR + "type=" +getType() + SEPARATOR + "volume=" +getAmount() + (getAsset() != null ? (SEPARATOR + "asset=" + getAsset()) : "")
				+ SEPARATOR + "price=" + getPrice() ;
	}
	@Transient
    protected Amount getAmount() { return new DiscreteAmount(volumeCount, getAsset().getBasis()); }
    protected void setAmount(long volumeCount) { this.volumeCount = volumeCount; this.volume = null; }
    protected void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    protected void setAsset(Asset asset) { this.asset = asset; }
    protected void setCommission(Amount commission) { this.commission = commission; }
    protected void setCurrency(Asset currency) { this.currency = currency; }
    protected void setType(TransactionType type) { this.type = type; }
    protected void setPrice(Amount price) { this.price = price; }
 //   protected Instant getTime() { return acceptedTime; }
    
    private Amount value; 
    private Amount volume;
    private Amount price;
    
    private long volumeCount;
    private Portfolio portfolio;
    private Asset asset;
    private long priceCount ;
    private Amount commission;
    private Asset currency;
     private Instant acceptedTime;
    private Instant closedTime;
    private Instant settledTime;
    @Inject private Logger log;
}
