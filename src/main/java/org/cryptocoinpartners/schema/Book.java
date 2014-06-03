package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Visitor;
import org.joda.time.Instant;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;


/**
 * Book represents a snapshot of all the limit orders for a MarketListing.  Book has a "compact" database representation
 *
 *
 * @author Tim Olson
 */
@Entity
@Table(indexes = {@Index(columnList = "time"),@Index(columnList = "timeReceived")})
public class Book extends MarketData implements Spread {
    

    public static void find(Interval timeInterval,Visitor<Book> visitor) {
        PersistUtil.queryEach(Book.class, visitor, "select b from Book b where time > ?1 and time < ?2");
    }


    public static void forAll(Visitor<Book> visitor) {
        PersistUtil.queryEach(Book.class,visitor,"select b from Book b");
    }


    @Transient
    public List<Bid> getBids() {
        return bids;
    }


    @Transient
    public List<Ask> getAsks() {
        return asks;
    }
    
    
    @Transient
    public Bid getBestBid() {
        if( bids.isEmpty() )
            return new Bid(getMarketListing(),getTime(),getTimeReceived(),0,0);
        return bids.get(0);
    }



    @Transient
    public Ask getBestAsk() {
        if( asks.isEmpty() ) {
            return new Ask(getMarketListing(),getTime(),getTimeReceived(), Long.MAX_VALUE, 0);
        }
        return asks.get(0);
    }


    @Nullable
    public Double getBidPrice() {
        if( bids.isEmpty() )
            return null;
        return bids.get(0).getPriceAsDouble();
    }
    
    
    @Nullable
    public Double getBidAmount() {
        if( bids.isEmpty() )
            return null;
        return bids.get(0).getVolumeAsDouble();
    }
    
    
    @Nullable
    public Double getAskPrice() {
        if( asks.isEmpty() )
            return null;
        return asks.get(0).getPriceAsDouble();
    }
    

    @Nullable
    public Double getAskAmount() {
        if( asks.isEmpty() )
            return null;
        return asks.get(0).getVolumeAsDouble();
    }


    public static class BookBuilder {

        public Book build() { book.sort(); return book; }


        public BookBuilder addBid( BigDecimal price, BigDecimal amount ) {
            MarketListing ml = book.getMarketListing();
            book.bids.add(new Bid(ml, book.getTime(), book.getTimeReceived(),
                                  DiscreteAmount.countForValueRounded(price,ml.getPriceBasis()),
                                  DiscreteAmount.countForValueRounded(amount,ml.getVolumeBasis())));
            return this;
        }


        public BookBuilder addAsk( BigDecimal price, BigDecimal amount ) {
            MarketListing ml = book.getMarketListing();
            book.asks.add(new Ask(ml,book.getTime(),book.getTimeReceived(),
                                  DiscreteAmount.countForValueRounded(price,ml.getPriceBasis()),
                                  DiscreteAmount.countForValueRounded(amount,ml.getVolumeBasis())));
            return this;
        }


        public BookBuilder(Book book) { this.book = book; }
        private Book book;
    }


    public static BookBuilder builder(Instant time, String remoteKey, MarketListing marketListing ) {
        return new BookBuilder(new Book(time, remoteKey, marketListing));
    }


    public String toString()
    {
        StringBuilder sb = new StringBuilder(getMarketListing().toString() + " Book at "+getTime()+" bids={");
        boolean first = true;
        for( Bid bid : bids ) {
            if( first )
                first = false;
            else
                sb.append(';');
            sb.append(bid.getVolumeCount());
            sb.append('@');
            sb.append(bid.getPriceCount());
        }
        sb.append("} asks={");
        first = true;
        for( Ask ask : asks ) {
            if( first )
                first = false;
            else
                sb.append(';');
            sb.append(ask.getVolumeCount());
            sb.append('@');
            sb.append(ask.getPriceCount());
        }
        sb.append('}');
        return sb.toString();
    }


    // JPA

    // These getters and setters are for conversion in JPA
    @Lob protected byte[] getBidBlob() { return convertToDatabaseColumn(bids); }
    protected void setBidBlob(byte[] blob) { bids = convertToEntityAttribute(blob,new BidCreator()); }
    @Lob protected byte[] getAskBlob() { return convertToDatabaseColumn(asks); }
    protected void setAskBlob(byte[] blob) { asks = convertToEntityAttribute(blob,new AskCreator()); }
    // these fields are derived from the blobs
    public void setBidPrice( @SuppressWarnings("UnusedParameters") Double ignored ) {}
    public void setBidAmount( @SuppressWarnings("UnusedParameters") Double ignored ) {}
    public void setAskPrice( @SuppressWarnings("UnusedParameters") Double ignored ) {}
    public void setAskAmount( @SuppressWarnings("UnusedParameters") Double ignored ) {}



    @SuppressWarnings("ConstantConditions")
    private static <T extends Quote> byte[] convertToDatabaseColumn(List<T> quotes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(bos);
            for( T quote : quotes ) {
                out.writeLong(quote.getPriceCount());
                out.writeLong(quote.getVolumeCount());
            }
        }
        catch( IOException e ) {
            throw new Error(e);
        }
        return bos.toByteArray();
    }


    private static <T> List<T> convertToEntityAttribute(byte[] bytes,QuoteCreator<T> quoteCreator) {
        List<T> result = new ArrayList<T>();
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream in = new ObjectInputStream(bin);
            long price = in.readLong();
            long amount = in.readLong();
            result.add(quoteCreator.create(price, amount));
        }
        catch( IOException e ) {
            throw new Error(e);
        }
        return result;
    }
        
        
    private static interface QuoteCreator<T> {
        T create( long price, long amount );
    }
    
    
    private class BidCreator implements QuoteCreator<Bid> {
        public Bid create(long price, long amount) {
            return new Bid(getMarketListing(),getTime(),getTimeReceived(),price,amount);
        }
    }


    private class AskCreator implements QuoteCreator<Ask> {
        public Ask create(long price, long amount) {
            return new Ask(getMarketListing(),getTime(),getTimeReceived(),price,amount);
        }
    }
    
    
    // JPA
    protected Book() { }


    private Book(Instant time, String remoteKey, MarketListing marketListing ) {
        super(time, remoteKey, marketListing);
        bids = new LinkedList<Bid>();
        asks = new LinkedList<Ask>();
    }


    private void sort() {
        Collections.sort(bids, new Comparator<Bid>() {
            @SuppressWarnings("ConstantConditions")
            public int compare(Bid bid, Bid bid2) {
                return - bid.getPriceCount().compareTo(bid2.getPriceCount()); // high to low
            }
        });
        Collections.sort(asks, new Comparator<Ask>() {
            @SuppressWarnings("ConstantConditions")
            public int compare(Ask ask, Ask ask2) {
                return ask.getPriceCount().compareTo(ask2.getPriceCount()); // low to high
            }
        });
    }


    private List<Bid> bids;
    private List<Ask> asks;
}
