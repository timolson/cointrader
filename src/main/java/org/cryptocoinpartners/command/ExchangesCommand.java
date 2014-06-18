package org.cryptocoinpartners.command;

import org.cryptocoinpartners.schema.Market;

import java.util.*;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ExchangesCommand extends CommandBase {

    public String getUsageHelp() { return "exchanges"; }
    public String getExtraHelp() { return "prints all exchanges available"; }


    public void run() {
        Set<String> symbols = new HashSet<>();
        for( Market market : Market.findAll() )
            symbols.add(market.getExchange().getSymbol());
        List<String> sorted = new ArrayList<>(symbols);
        Collections.sort(sorted);
        out.printList(sorted);
    }


}
