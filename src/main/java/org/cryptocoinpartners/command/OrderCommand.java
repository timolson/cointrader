package org.cryptocoinpartners.command;


import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;
import org.cryptocoinpartners.schema.*;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.util.Injector;

import javax.inject.Inject;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class OrderCommand extends AntlrCommandBase {


    public String getUsageHelp() {
        return (isSell?"sell":"buy") + " {volume} {exchange}:{base}.{quote} [limit {price}] [stop {price}]";
    }


    public String getExtraHelp() {
        String help = "Places an order for the given volume on the specified exchange."+
                      "If a limit price is supplied, a limit order will be generated." +
                      "Stop and stop-limit orders are not currently supported but no " +
                      "error will be given.";
        if(isSell)
            help += "Selling is the same as buying with a negative volume.";
        return help;
    }


    public void run() {
        OrderBuilder.SpecificOrderBuilder builder =
                new OrderBuilder(fund, orderService).create(market, volume);
        if( limit != null ) {
            long limitCount = DecimalAmount.roundedCountForBasis(limit, market.getPriceBasis());
            builder = builder.withLimitPriceCount(limitCount);
        }
        if( stop != null ) {
            long stopCount = DecimalAmount.roundedCountForBasis(stop, market.getPriceBasis());
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
        super("org.cryptocoinpartners.command.Order");
        this.isSell = isSell;
    }


    protected Injector getListenerInjector(Injector parentInjector) {
        return parentInjector.createChildInjector(new Module() {
            public void configure(Binder binder) {
                binder.bind(OrderCommand.class).toProvider(new Provider<OrderCommand>() {
                    public OrderCommand get() {
                        return OrderCommand.this;
                    }
                });
            }
        });
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
