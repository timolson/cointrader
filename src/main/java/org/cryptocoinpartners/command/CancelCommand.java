package org.cryptocoinpartners.command;

import java.math.BigDecimal;
import java.util.UUID;

import javax.inject.Inject;

import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;

/**
 * @author Tim Olson
 */
@CommandName("cancel")
@SuppressWarnings("UnusedDeclaration")
public class CancelCommand extends CommandBase {

    @Override
    public String getUsageHelp() {
        return "cancel {id}";
    }

    @Override
    public void parse(String commandArguments) {
        try {
            id = UUID.fromString(commandArguments);

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
        Order cancelledOrder = null;

        // so we need to get pending order by id
        //for (Portfolio portfolio : portfolioService.getPortfolios())
        for (Order order : orderService.getOrderStateMap().keySet()) {
            if (order.getId().equals(id)) {
                cancelledOrder = order;
                break;
            }

        }
        if (cancelledOrder != null && cancelledOrder instanceof SpecificOrder)

            return (orderService.handleCancelSpecificOrder((SpecificOrder) cancelledOrder));
        else if (cancelledOrder != null && cancelledOrder instanceof GeneralOrder)
            return (orderService.handleCancelGeneralOrder((GeneralOrder) cancelledOrder));
        else
            return false;
    }

    @Inject
    OrderService orderService;
    @Inject
    private PortfolioService portfolioService;
    @Inject
    private ConsoleWriter console;
    private BigDecimal volume;
    private UUID id;
    private Market market;
    private Listing listing;
    private BigDecimal limit;
    private BigDecimal stop;
    private boolean isSell;
}
