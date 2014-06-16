package org.cryptocoinpartners.command;

import org.cryptocoinpartners.schema.Market;

import java.util.*;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ExchangesCommand extends CommandBase {

    public void printHelp() {
        out.println("exchanges");
        out.println();
        out.println("\tprints all exchanges available");
    }


    public void run() {
        Set<String> symbols = new HashSet<>();
        for( Market market : Market.findAll() )
            symbols.add(market.getExchange().getSymbol());
        List<String> sorted = new ArrayList<>(symbols);
        Collections.sort(sorted);
        out.printList(sorted);
    }

}
