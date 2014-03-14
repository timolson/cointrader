package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.OneToMany;
import java.util.List;


/**
 * @author Tim Olson
 */
public class Book extends MarketData {

    public Book(Instant time, Security security, List<Bid> bids, List<Ask> asks) {
        super(time, security);
        this.bids = bids;
        this.asks = asks;
    }


    public Book(Instant time, Security security) {
        super(time, security);
    }


    @OneToMany
    public List<Bid> getBids() {
        return bids;
    }


    @OneToMany
    public List<Ask> getAsks() {
        return asks;
    }


    private List<Bid> bids;
    private List<Ask> asks;
}
