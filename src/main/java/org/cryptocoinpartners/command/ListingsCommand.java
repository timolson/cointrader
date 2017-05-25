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
public class ListingsCommand extends CommandBase {
    @Inject
    Market markets;

    @Override
    public Object call() {
        Set<String> symbols = new HashSet<>();
        for (Tradeable tradeable : markets.findAll())
            if (!tradeable.isSynthetic()) {
                Market market = (Market) tradeable;

                symbols.add(market.getListing().getSymbol());
            }
        List<String> sorted = new ArrayList<>(symbols);
        Collections.sort(sorted);
        out.printList(symbols);
        return true;
    }

    @Override
    public String getUsageHelp() {
        return "listings";
    }

    @Override
    public String getExtraHelp() {
        return "prints all listings available on at least one exchange";
    }
}
