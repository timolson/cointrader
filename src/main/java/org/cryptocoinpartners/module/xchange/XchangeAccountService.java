package org.cryptocoinpartners.module.xchange;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.service.AccountService;
import org.slf4j.Logger;


/**
 * @author Tim Olson
 */
public class XchangeAccountService implements AccountService {


    public Portfolio getPositions() {
        // todo
        return null;
    }


    public Portfolio getPositions(Exchange e) {
        // todo
        return null;
    }
    
    @When("select * from Fill")
    public void handleFill( Fill fill ) {
       
            log.info("Received Fill by account service "+fill);
    }
    @Inject private Logger log;
    private Collection<SpecificOrder> pendingOrders = new ArrayList<>();



}
