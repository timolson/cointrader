package org.cryptocoinpartners.schema;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToOne;

/**
 * A Holding represents an Asset on an Exchange.  It does not specify how much of the asset is held. See Position
 *
 * @author Tim Olson
 */
@MappedSuperclass
public class Holding extends EntityBase {

    public static Holding forSymbol(String symbol) {
        Matcher matcher = Pattern.compile("(\\w+):(\\w+)").matcher(symbol);
        if (!matcher.matches())
            throw new IllegalArgumentException("Could not parse Holding symbol " + symbol);
        return new Holding(Exchange.forSymbol(matcher.group(1)), Asset.forSymbol(matcher.group(2)));
    }

    public Holding(Exchange exchange, Asset asset) {
        this.exchange = exchange;
        this.asset = asset;
    }

    @ManyToOne(optional = true)
    public Exchange getExchange() {
        return exchange;
    }

    @OneToOne(optional = true)
    public Asset getAsset() {
        return asset;
    }

    @Override
    public String toString() {
        return exchange.getSymbol() + asset.getSymbol();
    }

    // JPA
    protected Holding() {
    }

    protected void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    protected void setAsset(Asset asset) {
        this.asset = asset;
    }

    protected Exchange exchange;
    protected Asset asset;

    @Override
    public void persit() {
        // TODO Auto-generated method stub

    }

    @Override
    public void detach() {
        // TODO Auto-generated method stub

    }

    @Override
    public void merge() {
        // TODO Auto-generated method stub

    }
}
