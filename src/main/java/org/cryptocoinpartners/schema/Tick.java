package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;


/**
 * A Tick is a point-in-time snapshot of a MarketListing's last price, volume and most recent Book
 *
 * @author Tim Olson
 */
@Entity
public class Tick extends PriceData implements Spread {


    public Instant getStartInstant() { return startInstant; }


    @Transient
    public Instant getEndInstant() { return getTime(); }


    @ManyToOne
    public Book getLastBook() { return lastBook; }


    /** @return null if no book was found prior to the window */
    @Transient
    public @Nullable Bid getBestBid() { return lastBook == null ? null : lastBook.getBestBid(); }


    /** @return null if no book was found prior to the window */
    @Transient
    public @Nullable Ask getBestAsk() { return lastBook == null ? null : lastBook.getBestAsk(); }


    public Tick( MarketListing marketListing, Instant startInstant, Instant endInstant,
                 @Nullable Long lastPriceCount, @Nullable Long volumeCount, Book lastBook )
    {
        super(endInstant,null,marketListing,lastPriceCount,volumeCount);
        this.startInstant = startInstant;
        this.lastBook = lastBook;
    }


    public String toString()
    {
        return String.format("Tick{%s last:%g@%g bid:%s ask:%s}",
                             getMarketListing(), getVolumeAsDouble(), getPriceAsDouble(), getBestBid(), getBestAsk() );
    }


    // JPA
    protected Tick() {}
    protected void setStartInstant( Instant startInstant ) { this.startInstant = startInstant; }
    protected void setLastBook(Book lastBook) { this.lastBook = lastBook; }


    private Instant startInstant;
    private Book lastBook;
}
