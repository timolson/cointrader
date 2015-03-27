package org.cryptocoinpartners.command;

import java.math.BigDecimal;

import javax.inject.Inject;

import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderBuilder;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.util.Injector;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class OrderCommand extends AntlrCommandBase {

    @Override
    public String getUsageHelp() {
        return (isSell ? "sell" : "buy") + " {volume} {exchange}:{base}.{quote} [limit {price}] [stop {price}]";
    }

    @Override
    public String getExtraHelp() {
        String help = "Places an order for the given volume on the specified exchange." + "If a limit price is supplied, a limit order will be generated."
                + "Stop and stop-limit orders are not currently supported but no " + "error will be given.";
        if (isSell)
            help += "Selling is the same as buying with a negative volume.";
        return help;
    }

    @Override
    public void run() {
        if (market != null)
            placeSpecificOrder();
        else
            placeGeneralOrder();
    }

    protected void placeSpecificOrder() {
        volume = isSell ? volume.negate() : volume;
        OrderBuilder.SpecificOrderBuilder builder = new OrderBuilder(portfolio, orderService).create(context.getTime(), market, volume, "New Order");
        if (limit != null) {
            long limitCount = DiscreteAmount.roundedCountForBasis(limit, market.getPriceBasis());
            builder = builder.withLimitPriceCount(limitCount);
        }

        Order order = builder.place();
    }

    protected void placeGeneralOrder() {
        volume = isSell ? volume.negate() : volume;

        OrderBuilder.GeneralOrderBuilder builder = new OrderBuilder(portfolio, orderService).create(context.getTime(), listing, volume);
        if (limit != null)
            builder = builder.withLimitPrice(limit);
        if (stop != null)
            builder = builder.withStopPrice(stop);
        Order order = builder.place();
    }

    @Override
    protected void initCommandArgs() {
        // clear optional args
        stop = null;
        limit = null;
    }

    protected OrderCommand(boolean isSell) {
        super("org.cryptocoinpartners.command.Order");
        this.isSell = isSell;
    }

    @Override
    protected Injector getListenerInjector(Injector parentInjector) {
        return parentInjector.createChildInjector(new Module() {
            @Override
            public void configure(Binder binder) {
                binder.bind(OrderCommand.class).toProvider(new Provider<OrderCommand>() {
                    @Override
                    public OrderCommand get() {
                        return OrderCommand.this;
                    }
                });
            }
        });
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public Market getMarket() {
        return market;
    }

    public void setMarket(Market market) {
        this.market = market;
        this.listing = null;
    }

    public Listing getListing() {
        return listing;
    }

    public void setListing(Listing listing) {
        this.listing = listing;
        this.market = null;
    }

    public BigDecimal getLimit() {
        return limit;
    }

    public void setLimit(BigDecimal limit) {
        this.limit = limit;
    }

    public BigDecimal getStop() {
        return stop;
    }

    public void setStop(BigDecimal stop) {
        this.stop = stop;
    }

    public boolean isSell() {
        return isSell;
    }

    public void setSell(boolean isSell) {
        this.isSell = isSell;
    }

    @Inject
    OrderService orderService;
    @Inject
    private Portfolio portfolio;
    private BigDecimal volume;
    private Market market;
    private Listing listing;
    private BigDecimal limit;
    private BigDecimal stop;
    private boolean isSell;
}
