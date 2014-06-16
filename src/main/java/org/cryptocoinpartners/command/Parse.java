package org.cryptocoinpartners.command;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;

import java.math.BigDecimal;


/**
 * This helper utility converts Antlr TerminalNodes into Cointrader schema classes
 *
 * @author Tim Olson
 */
public class Parse {


    public static BigDecimal amount(TerminalNode node) {
        String text = text(node);
        if( StringUtils.isBlank(text) )
            throw new ParseError("Expected an amount");
        try {
            return new BigDecimal(text);
        }
        catch( NumberFormatException e ) {
            throw new ParseError("Expected an amount: "+text);
        }
    }


    public static Currency currency(TerminalNode node) {
        String text = text(node);
        Currency currency = Currency.forSymbol(text);
        if( currency == null )
            throw new ParseError("Unknown currency "+text);
        return currency;
    }


    public static Listing listing(TerminalNode node) {
        String text = text(node);
        Listing listing = Listing.forSymbol(text);
        if( listing == null )
            throw new ParseError("Unknown listing "+text);
        return listing;
    }


    public static Market market(TerminalNode node) {
        String text = text(node);
        Market market = Market.forSymbol(text);
        if( market == null )
            throw new ParseError("Unknown market "+text);
        return market;
    }


    private static String text(TerminalNode node) {
        return node == null ? "" : node.getSymbol().getText();
    }


}
