package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;


/**
 * @author Tim Olson
 */
@Entity
public class Book extends MarketData implements Spread {
    
    public static class BookBuilder {
        
        public Book build() { book.sort(); return book; }

        
        public BookBuilder addBid( BigDecimal price, BigDecimal amount ) {
            book.bids.add(new Bid(book.getMarketListing(),book.getTime(),book.getTimeReceived(),price,amount));
            return this;
        }
    
    
        public BookBuilder addAsk( BigDecimal price, BigDecimal amount ) {
            book.asks.add(new Ask(book.getMarketListing(),book.getTime(),book.getTimeReceived(),price,amount));
            return this;
        }

        
        public BookBuilder(Book book) { this.book = book; }
        private Book book;
    }

    
    public static BookBuilder builder(Instant time, String remoteKey, MarketListing marketListing ) {
        return new BookBuilder(new Book(time, remoteKey, marketListing));
    }
    

    @Transient
    public List<Bid> getBids() {
        return bids;
    }


    @Transient
    public List<Ask> getAsks() {
        return asks;
    }
    
    
    // todo how to split into multiple columns?
    // todo how to make JPA ignore field?
    @Transient
    public Bid getBestBid() {
        if( bids.isEmpty() )
            return new Bid(getMarketListing(),getTime(),getTimeReceived(),BigDecimal.ZERO,BigDecimal.ZERO);
        return bids.get(0);
    }


    @Transient
    public Ask getBestAsk() {
        if( asks.isEmpty() ) {
            return new Ask(getMarketListing(),getTime(),getTimeReceived(), MAX_ASK_PRICE,BigDecimal.ZERO);
        }
        return asks.get(0);
    }


    public BigDecimal getBidPrice() {
        if( bids.isEmpty() )
            return BigDecimal.ZERO;
        return bids.get(0).getPrice();
    }
    
    
    public BigDecimal getBidAmount() {
        if( bids.isEmpty() )
            return BigDecimal.ZERO;
        return bids.get(0).getAmount();
    }
    
    
    public BigDecimal getAskPrice() {
        if( asks.isEmpty() )
            return BigDecimal.ZERO;
        return asks.get(0).getPrice();
    }
    
    
    public BigDecimal getAskAmount() {
        if( asks.isEmpty() )
            return BigDecimal.ZERO;
        return asks.get(0).getAmount();
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
            sb.append(bid.getAmount());
            sb.append('@');
            sb.append(bid.getPrice());
        }
        sb.append("} asks={");
        first = true;
        for( Ask ask : asks ) {
            if( first )
                first = false;
            else
                sb.append(';');
            sb.append(ask.getAmount());
            sb.append('@');
            sb.append(ask.getPrice());
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
    public void setBidPrice( BigDecimal ignored ) {}
    public void setBidAmount( BigDecimal ignored ) {}
    public void setAskPrice( BigDecimal ignored ) {}
    public void setAskAmount( BigDecimal ignored ) {}



    private static <T extends Quote> byte[] convertToDatabaseColumn(List<T> quotes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(bos);
            for( T quote : quotes ) {
                out.writeObject(quote.getPrice());
                out.writeObject(quote.getAmount());
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
            BigDecimal price = (BigDecimal) in.readObject();
            BigDecimal amount = (BigDecimal) in.readObject();
            result.add(quoteCreator.create(price, amount));
        }
        catch( IOException e ) {
            throw new Error(e);
        }
        catch( ClassNotFoundException e ) {
            throw new Error(e);
        }
        return result;
    }
        
        
    private static interface QuoteCreator<T> {
        T create( BigDecimal price, BigDecimal amount );
    }
    
    
    private class BidCreator implements QuoteCreator<Bid> {
        public Bid create(BigDecimal price, BigDecimal amount) {
            return new Bid(getMarketListing(),getTime(),getTimeReceived(),price,amount);
        }
    }


    private class AskCreator implements QuoteCreator<Ask> {
        public Ask create(BigDecimal price, BigDecimal amount) {
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
            public int compare(Bid bid, Bid bid2) {
                return - bid.getPrice().compareTo(bid2.getPrice()); // high to low
            }
        });
        Collections.sort(asks, new Comparator<Ask>() {
            public int compare(Ask ask, Ask ask2) {
                return ask.getPrice().compareTo(ask2.getPrice()); // low to high
            }
        });
    }


    private List<Bid> bids;
    private List<Ask> asks;
    private final BigDecimal MAX_ASK_PRICE = BigDecimal.valueOf(Double.MAX_VALUE);
}
