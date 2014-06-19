grammar Base;

@header {
import java.util.Set;
import java.util.HashSet;
}

@lexer::members {

  private static Set<String> marketSymbols;
  private static Set<String> listingSymbols;
  private static Set<String> exchangeSymbols;
  private static Set<String> currencySymbols;

  static {
    marketSymbols = new HashSet<String>(org.cryptocoinpartners.schema.Market.allSymbols());
    listingSymbols = new HashSet<String>(org.cryptocoinpartners.schema.Listing.allSymbols());
    exchangeSymbols = new HashSet<String>(org.cryptocoinpartners.schema.Exchange.allSymbols());
    currencySymbols = new HashSet<String>(org.cryptocoinpartners.schema.Currency.allSymbols());
  }
  
}


Amount
: [0-9]+
| [0-9]* '.' [0-9]+
;


String
: WordChar+
{
    if(marketSymbols.contains(getText().toLowerCase())) _type=Market;
    else if(listingSymbols.contains(getText().toLowerCase())) _type=Listing;
    else if(exchangeSymbols.contains(getText().toLowerCase())) _type=Exchange;
    else if(currencySymbols.contains(getText().toLowerCase())) _type=Currency;
}
;


fragment
WordChar
: 'A'..'Z'
| 'a'..'z'
| '0'..'9'
| '_'
| '\u00B7'
| '\u00C0'..'\u00D6'
| '\u00D8'..'\u00F6'
| '\u00F8'..'\u02FF'
| '\u0300'..'\u036F'
| '\u0370'..'\u037D'
| '\u037F'..'\u1FFF'
| '\u200C'..'\u200D'
| '\u203F'..'\u2040'
| '\u2070'..'\u218F'
| '\u2C00'..'\u2FEF'
| '\u3001'..'\uD7FF'
| '\uF900'..'\uFDCF'
| '\uFDF0'..'\uFFFD'
;

// ignore whitespace.  leave this at the bottom as the lowest priority lexer rule
/*
WS : [ \t\r\n\f]+ -> channel(HIDDEN) ;
*/

WS
: [ \t\r\n\f]+
-> skip
;


// Dummy tokens are set by String
Currency: ;
Exchange: ;
Listing: ;
Market: ;
