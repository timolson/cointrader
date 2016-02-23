package org.cryptocoinpartners.command;

import java.math.BigDecimal;

import javax.inject.Inject;

import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;

/**
 * @author Tim Olson
 */
@CommandName("fills")
@SuppressWarnings("UnusedDeclaration")
public class FillsCommand extends CommandBase {

    @Override
    public String getUsageHelp() {
        return (isSell ? "sell" : "buy") + " {volume} {exchange}:{base}.{quote} [limit {price}] [stop {price}]";
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
    public void run() {
        for (Portfolio portfolio : portfolioService.getPortfolios())

            out.printList(orderService.getFills(market, portfolio));
    }

    @Inject
    OrderService orderService;
    @Inject
    private PortfolioService portfolioService;
    @Inject
    private ConsoleWriter console;
    private BigDecimal volume;
    @Inject
    private Market market;
    private Listing listing;
    private BigDecimal limit;
    private BigDecimal stop;
    private boolean isSell;
}
