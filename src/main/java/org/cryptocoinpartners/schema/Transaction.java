package org.cryptocoinpartners.schema;

import java.text.SimpleDateFormat;

import org.cryptocoinpartners.enumeration.TransactionType;
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
	private static final String SEPARATOR = " ";
	
     // todo add basis rounding
	   public Transaction(Portfolio portfolio, Asset asset, long priceCount, Amount volume) {
		   this.volume=volume;
		   this.portfolio=portfolio;
		   this.asset=asset;
		   this.priceCount=priceCount;
		   
	       	     
	    }
     
    @ManyToOne(optional = false)
    public Asset getAsset() { return asset; }

    @ManyToOne(optional = false)
    public Portfolio getPortfolio() { return portfolio; }
    
    @ManyToOne(optional = false)
    private TransactionType type;
    public TransactionType getType() { return type; }
    
    @Transient
    public long getPriceCount() { return priceCount; }

   

	public String toString() {
		
		
		return  (getTime() != null ? (FORMAT.format(getTime())) : "") + SEPARATOR + getType() + SEPARATOR + getVolumeCount() + (getAsset() != null ? (SEPARATOR + getAsset()) : "")
				+ SEPARATOR + getPriceCount() ;
	}
    protected long getVolumeCount() { return volumeCount; }
    protected void setVolumeCount(long volumeCount) { this.volumeCount = volumeCount; this.volume = null; }
    protected void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    protected void setAsset(Asset asset) { this.asset = asset; }
    protected void setType(TransactionType type) { this.type = type; }
    protected void setPriceCount(long priceCount) { this.priceCount = priceCount; }
 //   protected Instant getTime() { return acceptedTime; }
    
    private Amount volume;
    private long volumeCount;
    private Portfolio portfolio;
    private Asset asset;
    private long priceCount ;
     private Instant acceptedTime;
    private Instant closedTime;
    private Instant settledTime;
    @Inject private Logger log;
}
