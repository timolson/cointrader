package org.cryptocoinpartners.command;

import java.util.ArrayList;

import org.cryptocoinpartners.schema.Currency;

/**
 * @author Tim Olson
 */
public class CurrenciesCommand extends CommandBase {

    @Override
    public String getExtraHelp() {
        return "Lists all known Currency symbols along with the Currency's basis";
    }

    @Override
    public Object call() {
        ArrayList<String> lines = new ArrayList<>();
        for (String symbol : Currency.allSymbols()) {
            Currency currency = Currency.forSymbol(symbol);
            lines.add(symbol + " " + currency.getBasis());
        }
        out.printList(lines);
        return true;
    }

}
