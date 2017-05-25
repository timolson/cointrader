package org.cryptocoinpartners.command;

import java.math.BigDecimal;

import javax.inject.Inject;

import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;

/**
 * @author Tim Olson
 */
@CommandName("pending")
@SuppressWarnings("UnusedDeclaration")
public class PendingCommand extends CommandBase {

    @Override
    public String getUsageHelp() {
        return "pending {market}";
    }

    @Override
    public void parse(String commandArguments) {
        try {
            market = market.forSymbol(commandArguments);

            //   listing = Listing.forSymbol(commandArguments);
        } catch (IllegalArgumentException e) {
            console.println("Unknown listing " + commandArguments);
        }
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
    public Object call() {
        for (Portfolio portfolio : portfolioService.getPortfolios())
            if (market == null)
                for (Tradeable tradeable : portfolio.getMarkets())
                    if (!tradeable.isSynthetic()) {
                        Market market = (Market) tradeable;
                        out.printList(orderService.getPendingOrders(market, portfolio));
                    } else
                        out.printList(orderService.getPendingOrders((Market) market, portfolio));
        return true;
    }

    @Inject
    OrderService orderService;
    @Inject
    private PortfolioService portfolioService;
    @Inject
    private ConsoleWriter console;
    private BigDecimal volume;
    @Inject
    private Tradeable market;
    private Listing listing;
    private BigDecimal limit;
    private BigDecimal stop;
    private boolean isSell;
}
