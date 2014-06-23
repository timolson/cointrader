package org.cryptocoinpartners.bin;

import org.cryptocoinpartners.command.ConsoleWriter;
import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;


/**
 * This is attached to the Context operated by ConsoleRunMode.  It is responsible for printing event alerts to the
 * console.
 *
 * @author Tim Olson
 */
@Singleton
public class ConsoleNotifications {


    public void watch( Listing listing ) {
        if( watchList.add(listing) )
            out.println("now watching "+listing);
        else
            out.println("already watching "+listing);
        out.flush();
    }


    public void unwatch( Listing listing ) {
        if( watchList.remove(listing) )
            out.println("no longer watching "+listing);
        out.flush();
    }


    public Set<Listing> getWatchList() {
        return watchList;
    }


    @When("select * from Book")
    private void watchBook( Book b ) {
        Market market = b.getMarket();
        if( watching(market.getListing()) ) {
            out.println(String.format("book: %s\t%s (%s) - %s (%s)",
                                      market, b.getBidPrice(), b.getBidVolume(), b.getAskPrice(), b.getAskVolume()));
            out.flush();
        }
    }


    @When("select * from Trade")
    private void watchTrade( Trade t ) {
        Market market = t.getMarket();
        if( watching(market.getListing()) ) {
            out.println(String.format("trade: %s\t%s (%s)",
                                      market, t.getPrice(), t.getVolume()));
            out.flush();
        }
    }


    @When("select * from Fill")
    private void announceFill( Fill f ) {
        out.println("Filled order " + f.getOrder().getId() + ": " + f);
        out.flush();
    }


    @When("select * from OrderUpdate")
    private void announceUpdate( OrderUpdate update ) {
        Order order = update.getOrder();
        switch( update.getState() ) {
            case NEW:
                out.println("Sending order " + order);
                break;
            case PLACED:
                out.println("Order has been placed. " + order);
                break;
            case PARTFILLED:
                out.println("Order is partially filled " + order);
                break;
            case FILLED:
                out.println("Order has been completely filled.  " + order);
                break;
            case CANCELLING:
                out.println("Cancelling order " + order);
                break;
            case CANCELLED:
                out.println("Cancelled order " + order);
                break;
            case REJECTED:
                out.println("Order REJECTED as unfillable. " + order);
                break;
            case EXPIRED:
                out.println("Order has expired.  " + order);
                break;
        }
    }


    private boolean watching(Listing listing) { return watchList.contains(listing); }


    @Inject
    private ConsoleWriter out;
    private Set<Listing> watchList = new HashSet<>();
}
