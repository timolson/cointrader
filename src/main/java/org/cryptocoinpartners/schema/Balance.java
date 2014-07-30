package org.cryptocoinpartners.schema;

import java.math.BigDecimal;



import com.xeiam.xchange.dto.trade.Wallet;

import javax.persistence.Entity;
import javax.persistence.Transient;


/**
 * A Position represents an amount of some Asset within an Exchange.  If the Position is related to an Order
 * then the Position is being held in reserve (not tradeable) to cover the costs of the open Order.
 *
 * @author Tim Olson
 */
@Entity
public class Balance extends Holding{


    public enum BalanceType {
		ACTUAL,
		RESERVED,
		AVAILABLE
	}


    
    public Balance(Exchange exchange,Asset asset,BalanceType type) {
    	this.exchange=exchange;
    	String ccy = asset.getSymbol().toString();
		//this.currencyPair=Utils.getCurrencyPair(instrument);
    	this.wallet=new Wallet(ccy, BigDecimal.ZERO, type.toString());
		
		
	}
  
	public Balance(Exchange exchange,Asset asset,Amount amount,BalanceType type ) {
		
		this.exchange=exchange;
		String ccy = asset.getSymbol().toString();
		this.wallet=new Wallet(ccy, amount.asBigDecimal(), type.toString());
		

		
	}
	    @Transient
    public Wallet getWallet() {
        
        return wallet;
    }

    @Transient
    public Exchange getExchange() {
        
        return exchange;
    }

    
   

    public boolean merge(Balance balance) {
        if( !exchange.equals(balance.exchange) || !wallet.getDescription().equals(balance.wallet.getDescription()) || !wallet.getCurrency().equals(balance.wallet.getCurrency())  )
            return false;
        BigDecimal amount = this.wallet.getBalance().add(balance.wallet.getBalance())  ;   		
        this.wallet= new Wallet(wallet.getCurrency(), amount, wallet.getDescription());
        
        return true;
    }

    public String toString() {
        return "Balance=[Exchange=" + exchange + ", wallet=" + wallet
                        + "]";
}

    // JPA
    protected Balance() { }


  
	protected void setWallet(Wallet wallet) { this.wallet = wallet; }
   
 
    private Wallet wallet;

    
}
