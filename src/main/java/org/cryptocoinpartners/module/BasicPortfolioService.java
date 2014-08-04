package org.cryptocoinpartners.module;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;
import org.cryptocoinpartners.util.RemainderHandler;
import org.joda.time.Instant;
import org.slf4j.Logger;

import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.SafeIterator;
import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public  class BasicPortfolioService implements PortfolioService {

	 private double avgTrade;


    @Inject protected Context context;
    @Inject private Logger log;
   
   public BasicPortfolioService(){
	   
    }
    
	@Override
	@Nullable
	public ArrayList<Position> getPositions(Portfolio portfolio) {
		// TODO Auto-generated method stub
	
		//log.info("Last Trade:");
		//log.info("Last Trade: " + getLastTrade(portfolio) != null ? getLastTrade(portfolio).toString() : "");
		return (ArrayList<Position>) portfolio.getPositions();
		//return null;
	}
	@Override
	@Nullable
	public ArrayList<Position> getPositions(Portfolio portfolio, Exchange exchange) {
		// TODO Auto-generated method stub
		return null;
	}
	@IntoMethod("GET_AVG")
    public DiscreteAmount getLastTrade(Portfolio portfolio) {
//    	try {
//			context.loadStatements("BasicPortfolioService",null);
//		} catch (ParseException | DeploymentException | IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
    	
//    	List<Object> events = null;
//		try {
//			events = context.loadRules("GET_LAST_TICK");
//			 if(events.size()>0)
//				 {
//				 Trade trade= ((Trade) events.get(events.size()-1));
//						 return(trade.getPrice());
//				 }
//			
//			
//		} catch (ParseException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (DeploymentException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		

    	
    	return null;
        //avgTrade = avg;
    }
//	select 
//	tick.security.id as securityId, 
//	tick.* as tick 
//from 
//	Tick.std:groupwin(security.id).win:time(7 days).win:length(1) as tick;
//	
	@Transient
    public Amount getCashBalance(Portfolio portfolio) {

		// sum of all transactions that belongs to this strategy
		BigDecimal balance = BigDecimal.ZERO;
		Collection<Transaction> transactions = getTrades(portfolio);
		Iterator <Transaction> balItr=transactions.iterator();
		while (balItr.hasNext()){
			balance.add( balItr.next().getValue().asBigDecimal());
			
		}

		// plus part of all cashFlows
		BigDecimal cashFlows = BigDecimal.ZERO;
		
		
		Collection<Transaction> cashFlowTransactions = getCashFlows(portfolio);
		Iterator <Transaction> cashlItr=cashFlowTransactions.iterator();
		while (cashlItr.hasNext()){
			cashFlows.add( cashlItr.next().getValue().asBigDecimal());
			
		}
		
		Amount amount=DecimalAmount.of(balance.add(cashFlows));
		return amount ;
	}
	  @Transient
	    @SuppressWarnings("null")
		public List<Transaction> getCashFlows(Portfolio portfolio) {
	    	// return all CREDIT,DEBIT,INTREST and FEES
			   ArrayList<Transaction>  cashFlows = null;
			   Collection<Transaction> transactions = portfolio.getTransactions();
				Iterator <Transaction> cashItr=transactions.iterator();
				while (cashItr.hasNext()){
					Transaction transaction = cashItr.next();
					if(transaction.getType()==TransactionType.CREDIT||transaction.getType()==TransactionType.DEBIT||transaction.getType()==TransactionType.INTREST||transaction.getType()==TransactionType.FEES)
					{
						cashFlows.add(transaction);
					}
							
				}
			return cashFlows;
		    }
	    @Transient 
	   @SuppressWarnings("null")
	public List<Transaction> getTrades(Portfolio portfolio) {
		   //return all BUY and SELL
		   ArrayList<Transaction>  trades = null;
		   Collection<Transaction> transactions = portfolio.getTransactions();
			Iterator <Transaction> balItr=transactions.iterator();
			while (balItr.hasNext()){
				Transaction transaction = balItr.next();
				if(transaction.getType()==TransactionType.BUY||transaction.getType()==TransactionType.SELL)
				{
					trades.add(transaction);
				}
						
			}
		return trades;
	    }
	
	public void CreateTransaction(Portfolio portfolio,Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price ) {
		 Transaction transaction=new Transaction(portfolio, exchange, asset, type, amount, price);
         context.publish(transaction);	
	}
}
