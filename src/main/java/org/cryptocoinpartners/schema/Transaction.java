package org.cryptocoinpartners.schema;

import java.text.SimpleDateFormat;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.joda.time.Instant;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;


/**
 * A Transaction represents the modification of multiple Positions, whether it is a purchase on a Market or a
 * Transfer of Fungibles between Accounts
 * @author Tim Olson
 */
@Entity
public class Transaction extends EntityBase {

    enum TransactionStatus { OFFERED, ACCEPTED, CLOSED, SETTLED, CANCELLED }
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	private static final String SEPARATOR = " ";
	
     // todo add basis rounding
    protected Transaction() { }
    
    @ManyToOne(optional = false)
    public Asset getAsset() { return asset; }


   

	public String toString() {
		
		return FORMAT.format(getTime()) + SEPARATOR + getType() + SEPARATOR + getVolumeCount() + (getAsset() != null ? (SEPARATOR + getAsset()) : "")
				+ SEPARATOR + getPriceCount() + SEPARATOR + getCurrency();
	}
    protected long getVolumeCount() { return volumeCount; }
    protected void setVolumeCount(long volumeCount) { this.volumeCount = volumeCount; this.volume = null; }
    protected Portfolio getPortfolio() { return portfolio; }
    protected void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    protected StrategyInstance getStrategyInstance() { return strategy; }
    protected void setStrategyInstance(StrategyInstance strategy) { this.strategy = strategy; }
    protected void setAsset(Asset asset) { this.asset = asset; }
  //  protected Asset getCurrency() { return currency; }
  //  protected void setCurrency(Currency currency) { this.currency = currency; }
    protected TransactionType getType() { return type; }
    protected void setType(TransactionType type) { this.type = type; }
    protected long getPriceCount() { return priceCount; }
    protected void setPriceCount(long priceCount) { this.priceCount = priceCount; }
    protected Instant getTime() { return acceptedTime; }
    protected void setTime(Instant acceptedTime) { this.acceptedTime = acceptedTime; }
  
    private Amount volume;
    private long volumeCount;
    private Portfolio portfolio;
    private StrategyInstance strategy;
    private Asset asset;
    private Currency currency;
    private long priceCount ;
    private TransactionType type;
    private Instant acceptedTime;
    private Instant closedTime;
    private Instant settledTime;
}
