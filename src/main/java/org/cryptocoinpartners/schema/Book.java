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
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@Table(indexes = {@Index(columnList = "time"),@Index(columnList = "timeReceived")})
public class Book extends MarketData implements Spread {

    /** Books will be saved in the database as diffs against the previous Book, but a full Book will be saved if the
     * number of parent hops to the previous full Book reaches MAX_PARENT_CHAIN_LENGTH */
    private static final int MAX_PARENT_CHAIN_LENGTH = 20;

    public static void find(Interval timeInterval,Visitor<Book> visitor) {
        PersistUtil.queryEach(Book.class, visitor, "select b from Book b where time > ?1 and time < ?2",
                              timeInterval.getStartMillis(),timeInterval.getEndMillis());
    }


    public static void findAll(Visitor<Book> visitor) {
        PersistUtil.queryEach(Book.class,visitor,"select b from Book b");
    }


    @Transient public List<Bid> getBids() { resolveDiff(); return bids; }
    @Transient public List<Ask> getAsks() { resolveDiff(); return asks; }


    @Transient
    public Bid getBestBid() {
        if( getBids().isEmpty() )
            return new Bid(getMarketListing(),getTime(),getTimeReceived(),0,0);
        return getBids().get(0);
    }


    @Transient
    public Ask getBestAsk() {
        if( getAsks().isEmpty() ) {
            return new Ask(getMarketListing(),getTime(),getTimeReceived(), Long.MAX_VALUE, 0);
        }
        return getAsks().get(0);
    }


    @Nullable
    @Transient
    public DiscreteAmount getBidPrice() {
        if( getBids().isEmpty() )
            return new DiscreteAmount(0,getMarketListing().getPriceBasis());
        return getBids().get(0).getPrice();
    }
    
    
    @Nullable
    @Transient
    public DiscreteAmount getBidAmount() {
        if( getBids().isEmpty() )
            return new DiscreteAmount(0,getMarketListing().getVolumeBasis());
        return getBids().get(0).getVolume();
    }
    
    
    @Nullable
    public Double getBidPriceAsDouble() {
        if( getBids().isEmpty() )
            return 0d;
        return getBids().get(0).getPriceAsDouble();
    }


    @Nullable
    public Double getBidAmountAsDouble() {
        if( getBids().isEmpty() )
            return 0d;
        return getBids().get(0).getVolumeAsDouble();
    }


    @Nullable
    @Transient
    public DiscreteAmount getAskPrice() {
        if( getAsks().isEmpty() )
            return new DiscreteAmount(Long.MAX_VALUE,getMarketListing().getPriceBasis());
        return getAsks().get(0).getPrice();
    }
    

    @Nullable
    @Transient
    public DiscreteAmount getAskAmount() {
        if( getAsks().isEmpty() )
            return new DiscreteAmount(0,getMarketListing().getVolumeBasis());
        return getAsks().get(0).getVolume();
    }


    /** saved to the db for query convenience */
    @Nullable
    public Double getAskPriceAsDouble() {
        if( getAsks().isEmpty() )
            return Double.MAX_VALUE;
        return getAsks().get(0).getPriceAsDouble();
    }


    /** saved to the db for query convenience */
    @Nullable
    public Double getAskAmountAsDouble() {
        if( getAsks().isEmpty() )
            return 0d;
        return getAsks().get(0).getVolumeAsDouble();
    }


    public static class DiffResult {
        Collection<Quote> newQuotes = new ArrayList<>();
        Collection<Quote> removedQuotes = new ArrayList<>();
    }


    public DiffResult diff( Book previousBook ) {
        DiffResult result = new DiffResult();
        diff( result, getBids(), previousBook.getBids() );
        diff( result, getAsks(), previousBook.getAsks() );
        return result;
    }


    /** Book.Builder remembers the previous Book it built, allowing for diffs to be saved in the db */
    public static class Builder {

        public Builder() { this.book = Book.create(); }


        public void start( Instant time, String remoteKey, MarketListing marketListing ) {
            book.setTime(time);
            book.setRemoteKey(remoteKey);
            book.setMarketListing(marketListing);
        }


        public Builder addBid( BigDecimal price, BigDecimal amount ) {
            MarketListing ml = book.getMarketListing();
            book.bids.add(new Bid(ml, book.getTime(), book.getTimeReceived(),
                                  DiscreteAmount.countForValueRounded(price,ml.getPriceBasis()),
                                  DiscreteAmount.countForValueRounded(amount,ml.getVolumeBasis())));
            return this;
        }


        public Builder addAsk( BigDecimal price, BigDecimal amount ) {
            MarketListing ml = book.getMarketListing();
            book.asks.add(new Ask(ml,book.getTime(),book.getTimeReceived(),
                                  DiscreteAmount.countForValueRounded(price,ml.getPriceBasis()),
                                  DiscreteAmount.countForValueRounded(amount,ml.getVolumeBasis())));
            return this;
        }


        public Book build()
        {
            book.sortBook();

            // look for a Chain of Books of the same MarketListing
            String marketListingSymbol = book.getMarketListing().getSymbol();
            Chain chain = chains.get(marketListingSymbol);
            if( chain == null ) {
                // no chain exists for the MarketListing, so create one
                chain = new Chain();
                chain.previousBook = book;
                chains.put(marketListingSymbol,chain);
            }
            else {
                // a parent Book exists in the chain
                Book parentBook;
                if( chain.chainLength == MAX_PARENT_CHAIN_LENGTH ) {
                    // reached max chain length.  set parent to null and reset the chain length count
                    parentBook = null;
                    chain.chainLength = 0;
                }
                else {
                    // the chain is not too long.  use the previous book in the chain as a parent
                    parentBook = chain.previousBook;
                    chain.chainLength++;
                }
                book.setParent(parentBook);
                chain.previousBook = book;
            }

            Book result = book;
            book = Book.create();
            return result;
        }


        private static class Chain {
            private int chainLength;
            private Book previousBook;
            private String marketListingSymbol;
        }

        private Book book;
        private Map<String,Chain> chains = new HashMap<>();
    }


    public String toString()
    {
        StringBuilder sb = new StringBuilder(getMarketListing().toString() + " Book at "+getTime()+" bids={");
        boolean first = true;
        for( Bid bid : getBids() ) {
            if( first )
                first = false;
            else
                sb.append(';');
            sb.append(bid.getVolumeAsDouble());
            sb.append('@');
            sb.append(bid.getPriceAsDouble());
        }
        sb.append("} asks={");
        first = true;
        for( Ask ask : getAsks() ) {
            if( first )
                first = false;
            else
                sb.append(';');
            sb.append(ask.getVolumeAsDouble());
            sb.append('@');
            sb.append(ask.getPriceAsDouble());
        }
        sb.append('}');
        return sb.toString();
    }


    // JPA
    protected Book() { }
    // These getters and setters are for conversion in JPA
    protected @ManyToOne Book getParent() { return parent; }
    protected @Lob byte[] getBidDeletionsBlob() { return bidDeletionsBlob; }
    protected @Lob byte[] getAskDeletionsBlob() { return askDeletionsBlob; }
    protected @Lob byte[] getBidInsertionsBlob() { return bidInsertionsBlob; }
    protected @Lob byte[] getAskInsertionsBlob() { return askInsertionsBlob; }
    protected void setParent(Book parent) { this.parent = parent; }
    protected void setBidDeletionsBlob(byte[] bidDeletionsBlob) { this.bidDeletionsBlob = bidDeletionsBlob; }
    protected void setAskDeletionsBlob(byte[] askDeletionsBlob) { this.askDeletionsBlob = askDeletionsBlob; }
    protected void setBidInsertionsBlob(byte[] bidInsertionsBlob) { this.bidInsertionsBlob = bidInsertionsBlob; }
    protected void setAskInsertionsBlob(byte[] askInsertionsBlob) { this.askInsertionsBlob = askInsertionsBlob; }

    // these fields are derived from the blobs
    protected void setBidPriceAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {}
    protected void setBidAmountAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {}
    protected void setAskPriceAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {}
    protected void setAskAmountAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {}


    // this is separate from the empty JPA constructor.  it allows Book.Builder to start with a minimally initialized Book
    private static Book create() {
        Book result = new Book();
        result.bids = new ArrayList<>();
        result.asks = new ArrayList<>();
        return result;
    }


    @PrePersist
    private void prePersist() {
        if( parent == null ) {
            bidInsertionsBlob = convertQuotesToDatabaseBlob(bids);
            askInsertionsBlob = convertQuotesToDatabaseBlob(asks);
            bidDeletionsBlob = null;
            askDeletionsBlob = null;
        }
        else {
            DiffBlobs bidBlobs = diff( parent.getBids(), getBids() );
            bidInsertionsBlob = bidBlobs.insertBlob;
            bidDeletionsBlob = bidBlobs.deleteBlob;
            DiffBlobs askBlobs = diff( parent.getAsks(), getAsks() );
            askInsertionsBlob = askBlobs.insertBlob;
            askDeletionsBlob = askBlobs.deleteBlob;
        }
    }


    @SuppressWarnings("ConstantConditions")
    private boolean hasQuote(List<? extends Quote> list, Quote quote )
    {
        for( Quote item : list )
        {
            if( Long.compare(item.getPriceCount(),quote.getPriceCount()) == 0 &&
                        Long.compare(item.getVolumeCount(),quote.getVolumeCount()) == 0  )
                return true;
        }
        return false;
    }


    @PostPersist
    private void postPersist() {
        clearBlobs();
    }


    @PostLoad
    private void postLoad() {
        bids = convertDatabaseBlobToQuoteList(bidInsertionsBlob, new BidCreator());
        asks = convertDatabaseBlobToQuoteList(askInsertionsBlob, new AskCreator());
        if( parent != null )
            needToResolveDiff = true;
    }

    // if this is implemented as a @PostLoad, the transitive dependencies for the parent's parent are not resolved
    private void resolveDiff() {
        if( !needToResolveDiff )
            return;
        // add any non-deleted entries from the parent
        List<Integer> bidDeletionIndexes = convertDatabaseBlobToIndexList(bidDeletionsBlob); // these should be already sorted
        List<Bid> parentBids = parent.getBids();
        for( int i = 0; i < parentBids.size(); i++ ) {
            if( !bidDeletionIndexes.contains(i) )
                bids.add(parentBids.get(i));
        }
        List<Integer> askDeletionIndexes = convertDatabaseBlobToIndexList(askDeletionsBlob); // these should be already sorted
        List<Ask> parentAsks = parent.getAsks();
        for( int i = 0; i < parentAsks.size(); i++ ) {
            if( !askDeletionIndexes.contains(i) )
                asks.add(parentAsks.get(i));
        }
        sortBook();
        clearBlobs();
        needToResolveDiff = false;
    }


    private void clearBlobs() {
        bidDeletionsBlob = null;
        askDeletionsBlob = null;
        bidInsertionsBlob = null;
        askInsertionsBlob = null;
    }


    @SuppressWarnings("ConstantConditions")
    private static <T extends Quote> byte[] convertQuotesToDatabaseBlob(List<T> quotes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeInt(quotes.size());
            for( T quote : quotes ) {
                out.writeLong(quote.getPriceCount());
                out.writeLong(quote.getVolumeCount());
            }
            out.close();
            bos.close();
        }
        catch( IOException e ) {
            throw new Error(e);
        }
        return bos.toByteArray();
    }


    private static <T> List<T> convertDatabaseBlobToQuoteList(byte[] bytes, QuoteCreator<T> quoteCreator) {
        List<T> result = new ArrayList<>();
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        //noinspection EmptyCatchBlock
        try {
            ObjectInputStream in = new ObjectInputStream(bin);
            int size = in.readInt();
            for( int i = 0; i < size; i++ ) {
                long price = in.readLong();
                long amount = in.readLong();
                result.add(quoteCreator.create(price, amount));
            }
            in.close();
            bin.close();
        }
        catch( IOException e ) {
            throw new Error(e);
        }
        return result;
    }


    @SuppressWarnings("ConstantConditions")
    private static byte[] convertIndexesToDatabaseBlob(List<Integer> indexes) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeInt(indexes.size());
            for( Integer index : indexes )
                out.writeInt(index);
            out.close();
            bos.close();
        }
        catch( IOException e ) {
            throw new Error(e);
        }
        return bos.toByteArray();
    }


    private static List<Integer> convertDatabaseBlobToIndexList(byte[] bytes) {
        List<Integer> result = new ArrayList<>();
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream in = new ObjectInputStream(bin);
            int size = in.readInt();
            for( int i = 0; i < size; i++ )
                result.add(in.readInt());
            in.close();
            bin.close();
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


    /** this implements the public diff() */
    private void diff( DiffResult result, List<? extends Quote> childQuotes, List<? extends Quote> parentQuotes ) {
        for( Quote childQuote : childQuotes ) {
            if( !hasQuote(parentQuotes,childQuote) )
                result.newQuotes.add(childQuote);
        }
        for( Quote parentQuote : parentQuotes ) {
            if( !hasQuote(childQuotes,parentQuote) )
                result.removedQuotes.add(parentQuote);
        }
    }


    private static class DiffBlobs {
        byte[] insertBlob;
        byte[] deleteBlob;
    }


    /** this is separate from the public diff for efficiency */
    private DiffBlobs diff(List<? extends Quote> parentQuotes, List<? extends Quote> childQuotes) {
        List<Quote> insertions = new ArrayList<>();
        for( Quote childQuote : childQuotes ) {
            if( !hasQuote(parentQuotes,childQuote) )
                insertions.add(childQuote);
        }

        List<Integer> deletionIndexes = new ArrayList<>();
        for( int i = 0; i < parentQuotes.size(); i++ ) {
            Quote quote = parentQuotes.get(i);
            if( !hasQuote(childQuotes,quote) )
                deletionIndexes.add(i);
        }
        DiffBlobs result = new DiffBlobs();
        result.insertBlob = convertQuotesToDatabaseBlob(insertions);
        result.deleteBlob = convertIndexesToDatabaseBlob(deletionIndexes);
        return result;
    }


    private void sortBook() {
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
    private Book parent; // if this is not null, then the Book is persisted as a diff against the parent Book
    private byte[] bidDeletionsBlob;
    private byte[] askDeletionsBlob;
    private byte[] bidInsertionsBlob;
    private byte[] askInsertionsBlob;
    private boolean needToResolveDiff;
}