package org.cryptocoinpartners.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Tradeable;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class MarketsCommand extends CommandBase {
    @Inject
    Market markets;

    @Override
    public String getUsageHelp() {
        return "markets";
    }

    @Override
    public String getExtraHelp() {
        return "prints a list of all available exchanges and listings";
    }

    @Override
    public Object call() {
        List<String> symbols = new ArrayList<>();
        for (Tradeable tradeable : markets.findAll())
            if (!tradeable.isSynthetic()) {
                Market market = (Market) tradeable;
                symbols.add(market.getSymbol());
            }
        Collections.sort(symbols);
        out.printList(symbols);
        return true;
    }

}
