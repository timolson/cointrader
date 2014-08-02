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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public  class BasicPortfolioService implements PortfolioService {




    @Inject protected Context context;
    @Inject private Logger log;
	@Override
	@Nullable
	public ArrayList<Position> getPositions(Portfolio portfolio) {
		// TODO Auto-generated method stub
		return (ArrayList<Position>) portfolio.getPositions();
		//return null;
	}
	@Override
	@Nullable
	public ArrayList<Position> getPositions(Portfolio portfolio, Exchange exchange) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void CreateTransaction(Portfolio portfolio,Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price ) {
		 Transaction transaction=new Transaction(portfolio, exchange, asset, type, amount, price);
         context.publish(transaction);	
	}
}
