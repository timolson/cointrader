package org.cryptocoinpartners.command;

import org.cryptocoinpartners.schema.Currency;

import java.util.ArrayList;


/**
 * @author Tim Olson
 */
public class CurrenciesCommand extends CommandBase {

    @Override
    public String getExtraHelp() {
        return "Lists all known Currency symbols along with the Currency's basis";
    }

    @Override
    public void run() {
        ArrayList<String> lines = new ArrayList<>();
        for( String symbol : Currency.allSymbols() ) {
            Currency currency = Currency.forSymbol(symbol);
            lines.add(symbol+" "+currency.getBasis());
        }
        out.printList(lines);
    }

}
