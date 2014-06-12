package org.cryptocoinpartners.module.mockorder;

import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.service.BaseOrderService;
import org.cryptocoinpartners.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * MockOrderService simulates the Filling of Orders by looking at subsequent Book data for price and volume information.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class MockOrderService extends BaseOrderService {


    protected void handleSpecificOrder(SpecificOrder specificOrder) {
        if( specificOrder.getStopPriceCount() != 0 )
            reject(specificOrder,"Stop prices unsupported");
        pendingOrders.add(specificOrder);
    }


    @SuppressWarnings("ConstantConditions")
    @When("select * from Book")
    private void handleBook( Book b ) {
        List<Fill> fills = new ArrayList<>();
        // todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
        for( SpecificOrder order : pendingOrders ) {
            if( order.getMarket().equals(b.getMarket()) ) {
                if( order.isBid() ) {
                    long remainingVolume = order.getVolumeCount();
                    for( Offer ask : b.getAsks() ) {
                        if( order.getLimitPriceCount() < ask.getPriceCount() )
                            break;
                        long fillVolume = Math.min(Math.abs(ask.getVolumeCount()), remainingVolume);
                        Fill fill = new Fill(order, ask.getTime(), ask.getMarket(), ask.getPriceCount(), fillVolume);
                        fills.add(fill);
                        remainingVolume -= fillVolume;
                        logFill(order,ask,fill);
                        if( remainingVolume == 0 )
                            break;
                    }
                }
                if( order.isAsk() ) {
                    long remainingVolume = order.getVolumeCount(); // this will be negative
                    for( Offer bid : b.getBids() ) {
                        if( order.getLimitPriceCount() > bid.getPriceCount() )
                            break;
                        long fillVolume = -Math.min(bid.getVolumeCount(), Math.abs(remainingVolume));
                        Fill fill = new Fill(order, bid.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume);
                        fills.add(fill);
                        remainingVolume -= fillVolume;
                        logFill(order,bid,fill);
                        if( remainingVolume == 0 )
                            break;
                    }
                }
            }
        }
        for( Fill fill : fills )
            esper.publish(fill);
    }


    private void logFill(SpecificOrder order, Offer offer, Fill fill) {
        if( log.isDebugEnabled() )
            log.debug("Mock fill of Order "+order+" with Offer "+offer+": "+fill);
    }


    private static final Logger log = LoggerFactory.getLogger(MockOrderService.class);

    private Collection<SpecificOrder> pendingOrders = new ArrayList<>();
    private QuoteService quotes;
}
