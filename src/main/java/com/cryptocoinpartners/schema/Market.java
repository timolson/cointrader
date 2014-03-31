package com.cryptocoinpartners.schema;


import com.cryptocoinpartners.util.PersistUtil;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Tim Olson
 */
@Entity
public class Market extends EntityBase {

    public static final Market BITFINEX = forSymbol("BITFINEX");
    public static final Market BTC_CHINA = forSymbol("BTC_CHINA");
    public static final Market BITSTAMP = forSymbol("BITSTAMP");
    public static final Market BTCE = forSymbol("BTCE");
    public static final Market CRYPTSY = forSymbol("CRYPTSY");
            

    public static Market forSymbol( String symbol ) {
        if( symbolMap == null ) {
            EntityManager em = PersistUtil.createEntityManager();
            TypedQuery<Market> query = em.createQuery("select m from Market m", Market.class);
            List<Market> markets = query.getResultList();
            symbolMap = new HashMap<String, Market>();
            for( Market market : markets )
                symbolMap.put(market.getSymbol(),market);
        }
        return symbolMap.get(symbol);
    }


    @Basic(optional = false)
    public String getSymbol() { return symbol; }


    public String toString() { return symbol; }


    // JPA
    protected Market() {}
    protected void setSymbol(String symbol) { this.symbol = symbol; }


    private Market(String symbol) { this.symbol = symbol; }


    private String symbol;
    private static Map<String,Market> symbolMap = null;
}
