package org.cryptocoinpartners.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Tradeable;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ExchangesCommand extends CommandBase {
    @Inject
    Market markets;

    @Override
    public String getUsageHelp() {
        return "exchanges";
    }

    @Override
    public String getExtraHelp() {
        return "prints all exchanges available";
    }

    @Override
    public Object call() {
        Set<String> symbols = new HashSet<>();
        for (Tradeable tradeable : markets.findAll())
            if (!tradeable.isSynthetic()) {
                Market market = (Market) tradeable;
                symbols.add(market.getExchange().getSymbol());
            }
        List<String> sorted = new ArrayList<>(symbols);
        Collections.sort(sorted);
        out.printList(sorted);
        return true;
    }

}
