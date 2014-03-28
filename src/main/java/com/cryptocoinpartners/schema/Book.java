package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.List;


/**
 * @author Tim Olson
 */
public class Book extends MarketData {

    public Book(Instant time, String remoteKey, Listing listing, List<Bid> bids, List<Ask> asks) {
        super(time, remoteKey, listing);
        this.bids = bids;
        this.asks = asks;
    }


    public Book(Instant time, String remoteKey, Listing listing) {
        super(time, remoteKey, listing);
    }


    // todo custom JPA persistence as a complete book
    @OneToMany(fetch = FetchType.EAGER)
    public List<Bid> getBids() {
        return bids;
    }


    @OneToMany(fetch = FetchType.EAGER)
    public List<Ask> getAsks() {
        return asks;
    }


    private List<Bid> bids;
    private List<Ask> asks;
}
