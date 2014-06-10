package org.cryptocoinpartners.schema;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.module.Esper;
import org.cryptocoinpartners.module.ModuleListener;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.QuoteService;

import javax.persistence.Entity;


/**
 * A Strategy represents an approach to trading.  Every Strategy is attached to one and only one Fund.  The Strategy
 * places Orders to manipulate Positions in the Fund on behalf of the owners of the Fund.
 *
 * @author Tim Olson
 */
@Entity
public class Strategy extends FundManager implements ModuleListener {


    public void initModule(Esper esper, Configuration config) {
        this.esper = esper;
        this.config = config;
        order = new OrderBuilder(getFund(),orderService);
    }


    public void destroyModule() {
    }


    protected Esper esper;
    protected Configuration config;

    /** This is what you use to place orders:
     * <pre>
     * order.buy(Listing.BTC_USD,1.00).withLimit(651.538).place();
     * </pre>
     */
    protected OrderBuilder order;

    /** You may use this service to query the most recent Trades and Books for all Listings and MarketListings */
    protected QuoteService quotes;

    private OrderService orderService;
}
