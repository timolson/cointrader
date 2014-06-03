package org.cryptocoinpartners.schema;

/**
 * @author Tim Olson
 */
public enum Side {
    BUY("bid"), SELL("ask");

    public String getBidAsk() { return bidAsk; }

    private Side(String s) { bidAsk = s; }
    private String bidAsk;
}
