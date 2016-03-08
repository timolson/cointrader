package org.cryptocoinpartners.command;

import java.math.BigDecimal;

import javax.inject.Inject;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.GeneralOrderFactory;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.util.Injector;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class OrderCommand extends AntlrCommandBase {

    private Portfolio portfolio;

    @Override
    public String getUsageHelp() {
        return (isSell ? "sell" : "buy") + " {volume} {exchange}:{base}.{quote} [limit {price}] [stop {price}] [position {\"open|close\"}]";
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
        //  PortfolioManager strategy = context.getInjector().getInstance(PortfolioManager.class);
        for (Portfolio port : portfolioService.getPortfolios())
            portfolio = port;
        if (market != null)
            placeSpecificOrder();
        else
            placeGeneralOrder();

    }

    protected void placeSpecificOrder() {
        //FillType.STOP_LOSS
        volume = isSell ? volume.negate() : volume;
        GeneralOrder order = generalOrderFactory.create(context.getTime(), portfolio, market, volume, FillType.MARKET);
        if (limit != null) {
            long limitCount = DiscreteAmount.roundedCountForBasis(limit, market.getPriceBasis());
            order.withLimitPrice(limit);
            order.withFillType(FillType.LIMIT);

        }
        if (positionEffect != null)
            order.withPositionEffect(positionEffect);

        try {
            orderService.placeOrder(order);
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            out.println("Unable to place order " + order + ". Stack Trace " + e);

        }

    }

    protected void placeGeneralOrder() {
        volume = isSell ? volume.negate() : volume;
        // GeneralOrder longOrder = generalOrderFactory.create(context.getTime(), portfolio, market, orderDiscrete.asBigDecimal(), FillType.STOP_LOSS);

        GeneralOrder order = generalOrderFactory.create(context.getTime(), portfolio, listing, volume, FillType.MARKET);

        //
        // GeneralOrder order = generalOrderFactory.create(context.getTime(), portfolio, listing, volume);
        if (limit != null) {
            order.withLimitPrice(limit);
            order.withFillType(FillType.LIMIT);
        }
        if (stop != null) {
            order.withStopAmount(stop);
            order.withFillType(FillType.STOP_LIMIT);
        }
        if (positionEffect != null)
            order.withPositionEffect(positionEffect);

        try {
            orderService.placeOrder(order);
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            out.println("Unable to place order " + order + ". Stack Trace " + e);

        }

    }

    @Override
    protected void initCommandArgs() {
        // clear optional args
        stop = null;
        limit = null;
        positionEffect = null;
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

    public PositionEffect getPositionEffect() {
        return positionEffect;
    }

    public void setLimit(BigDecimal limit) {
        this.limit = limit;
    }

    public void setPositionEffect(PositionEffect positionEffect) {
        this.positionEffect = positionEffect;
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
    private PortfolioService portfolioService;
    @Inject
    protected transient GeneralOrderFactory generalOrderFactory;

    // @Inject
    // private Portfolio portfolio;
    private BigDecimal volume;
    private Market market;
    private Listing listing;
    private BigDecimal limit;
    private PositionEffect positionEffect;
    private BigDecimal stop;
    private boolean isSell;
}
