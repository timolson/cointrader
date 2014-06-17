package org.cryptocoinpartners.command;


import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.service.OrderService;

import javax.inject.Inject;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class OrderCommand extends AntlrCommandBase {


    public void printHelp() {
        out.print(isSell?"sell":"buy");
        out.println(" {volume} {exchange}:{base}.{quote} [limit {price}] [stop {price}]");
        out.println();
        out.println("\tPlaces an order for the given volume on the specified exchange.");
        out.println("\tIf a limit price is supplied, a limit order will be generated.");
        out.println("\tStop and stop-limit orders are not currently supported but no ");
        out.println("\terror will be given.");
        if(isSell)
            out.println("Selling is the same as buying with a negative volume.");
    }


    public void run() {
        OrderBuilder.SpecificOrderBuilder builder =
                new OrderBuilder(fund, orderService).create(market, volume);
        if( limit != null ) {
            long limitCount = DiscreteAmount.countForValueRounded(limit, market.getPriceBasis());
            builder = builder.withLimitPriceCount(limitCount);
        }
        if( stop != null ) {
            long stopCount = DiscreteAmount.countForValueRounded(stop, market.getPriceBasis());
            builder = builder.withStopPriceCount(stopCount);
        }
        Order order = builder.place();
        out.println("Placing order " + order);
    }


    protected void initCommandArgs() {
        // clear optional args
        stop = null;
        limit = null;
    }


    protected OrderCommand(boolean isSell) {
        this.isSell = isSell;
    }


    public Fund getFund() { return fund; }
    public void setFund(Fund fund) { this.fund = fund; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }

    public Market getMarket() { return market; }
    public void setMarket(Market market) { this.market = market; }

    public BigDecimal getLimit() { return limit; }
    public void setLimit(BigDecimal limit) { this.limit = limit; }

    public BigDecimal getStop() { return stop; }
    public void setStop(BigDecimal stop) { this.stop = stop; }

    public boolean isSell() { return isSell; }
    public void setSell(boolean isSell) { this.isSell = isSell; }


    @Inject
    OrderService orderService;
    private Fund fund;
    private BigDecimal volume;
    private Market market;
    private BigDecimal limit;
    private BigDecimal stop;
    private boolean isSell;
}
