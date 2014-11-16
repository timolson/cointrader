package org.cryptocoinpartners.schema;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Visitor;
import org.joda.time.Instant;
import org.joda.time.Interval;

/**
 * Book represents a snapshot of all the limit orders for a Market.  Book has a "compact" database representation
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@Table(indexes = { @Index(columnList = "time"), @Index(columnList = "timeReceived") })
public class Book extends MarketData implements Spread {

	/** Books will be saved in the database as diffs against the previous Book, but a full Book will be saved if the
	 * number of parent hops to the previous full Book reaches MAX_PARENT_CHAIN_LENGTH */
	private static final int MAX_PARENT_CHAIN_LENGTH = 20;

	public static void find(Interval timeInterval, Visitor<Book> visitor) {
		PersistUtil.queryEach(Book.class, visitor, "select b from Book b where time > ?1 and time < ?2", timeInterval.getStartMillis(),
				timeInterval.getEndMillis());
	}

	public static void findAll(Visitor<Book> visitor) {
		PersistUtil.queryEach(Book.class, visitor, "select b from Book b");
	}

	private static Object lock = new Object();

	@Transient
	public List<Offer> getBids() {
		resolveDiff();
		return bids;
	}

	@Transient
	public List<Offer> getAsks() {
		resolveDiff();
		return asks;
	}

	@Override
	@Transient
	public Offer getBestBid() {
		if (getBids().isEmpty())
			return new Offer(getMarket(), getTime(), getTimeReceived(), 0L, 0L);
		return getBids().get(0);
	}

	@Override
	@Transient
	public Offer getBestAsk() {
		if (getAsks().isEmpty()) {
			return new Offer(getMarket(), getTime(), getTimeReceived(), Long.MAX_VALUE, 0L);
		}
		return getAsks().get(0);
	}

	@Nullable
	@Transient
	public DiscreteAmount getBidPrice() {
		if (getBids().isEmpty())
			return new DiscreteAmount(0, getMarket().getPriceBasis());
		return getBids().get(0).getPrice();
	}

	@Nullable
	@Transient
	public DiscreteAmount getBidVolume() {
		if (getBids().isEmpty())
			return new DiscreteAmount(0, getMarket().getVolumeBasis());
		return getBids().get(0).getVolume();
	}

	@Nullable
	public Double getBidPriceAsDouble() {
		if (getBids().isEmpty())
			return 0d;
		return getBids().get(0).getPriceAsDouble();
	}

	@Nullable
	@Transient
	public Double getBidPriceCountAsDouble() {
		if (getBids().isEmpty())
			return 0d;
		return getBids().get(0).getPriceCountAsDouble();
	}

	@Nullable
	public Double getBidVolumeAsDouble() {
		if (getBids().isEmpty())
			return 0d;
		return getBids().get(0).getVolumeAsDouble();
	}

	@Nullable
	@Transient
	public Double getBidVolumeCountAsDouble() {
		if (getBids().isEmpty())
			return 0d;
		return getBids().get(0).getVolumeCountAsDouble();
	}

	@Nullable
	@Transient
	public DiscreteAmount getAskPrice() {
		if (getAsks().isEmpty())
			return new DiscreteAmount(Long.MAX_VALUE, getMarket().getPriceBasis());
		return getAsks().get(0).getPrice();
	}

	@Nullable
	@Transient
	public DiscreteAmount getAskVolume() {
		if (getAsks().isEmpty())
			return new DiscreteAmount(0, getMarket().getVolumeBasis());
		return getAsks().get(0).getVolume();
	}

	/** saved to the db for query convenience */
	@Nullable
	public Double getAskPriceAsDouble() {
		if (getAsks().isEmpty())
			return Double.MAX_VALUE;
		return getAsks().get(0).getPriceAsDouble();
	}

	@Nullable
	@Transient
	public Double getAskPriceCountAsDouble() {
		if (getAsks().isEmpty())
			return Double.MAX_VALUE;
		return getAsks().get(0).getPriceCountAsDouble();
	}

	/** saved to the db for query convenience */
	@Nullable
	public Double getAskVolumeAsDouble() {
		if (getAsks().isEmpty())
			return 0d;
		return getAsks().get(0).getVolumeAsDouble();
	}

	@Nullable
	@Transient
	public Double getAskVolumeCountAsDouble() {
		if (getAsks().isEmpty())
			return 0d;
		return getAsks().get(0).getVolumeCountAsDouble();
	}

	public static class DiffResult {
		Collection<Offer> newOffers = new ArrayList<>();
		Collection<Offer> removedOffers = new ArrayList<>();
	}

	public DiffResult diff(Book previousBook) {
		DiffResult result = new DiffResult();
		diff(result, getBids(), previousBook.getBids());
		diff(result, getAsks(), previousBook.getAsks());
		return result;
	}

	/** Book.Builder remembers the previous Book it built, allowing for diffs to be saved in the db */
	public static class Builder {

		public Builder() {
			this.book = Book.create();
		}

		public void start(Instant time, String remoteKey, Market market) {
			book.setTime(time);
			book.setTimeReceived(Instant.now());
			book.setRemoteKey(remoteKey);
			book.setMarket(market);
		}

		public void start(Instant time, Instant timeReceived, String remoteKey, Market market) {
			book.setTime(time);
			book.setTimeReceived(timeReceived);
			book.setRemoteKey(remoteKey);
			book.setMarket(market);
		}

		public Builder addBid(BigDecimal price, BigDecimal volume) {
			Market market = book.getMarket();
			book.bids.add(Offer.bid(market, book.getTime(), book.getTimeReceived(), DiscreteAmount.roundedCountForBasis(price, market.getPriceBasis()),
					DiscreteAmount.roundedCountForBasis(volume, market.getVolumeBasis())));
			return this;
		}

		public Builder addAsk(BigDecimal price, BigDecimal volume) {
			Market market = book.getMarket();
			book.asks.add(Offer.ask(market, book.getTime(), book.getTimeReceived(), DiscreteAmount.roundedCountForBasis(price, market.getPriceBasis()),
					DiscreteAmount.roundedCountForBasis(volume, market.getVolumeBasis())));
			return this;
		}

		public Book build() {
			book.sortBook();

			// look for a Chain of Books of the same Market
			String marketSymbol = book.getMarket().getSymbol();
			Chain chain = chains.get(marketSymbol);
			if (chain == null) {
				// no chain exists for the Market, so create one
				chain = new Chain();
				chain.previousBook = book;
				chains.put(marketSymbol, chain);
			} else {
				// a parent Book exists in the chain
				Book parentBook;
				if (chain.chainLength == MAX_PARENT_CHAIN_LENGTH) {
					// reached max chain length.  set parent to null and reset the chain length count
					parentBook = null;
					chain.chainLength = 0;
				} else {
					// the chain is not too long.  use the previous book in the chain as a parent
					parentBook = chain.previousBook;
					chain.chainLength++;
				}
				if (parentBook != null) {
					//	//parentBook.addChild(book);
					//book.setParent(parentBook);
				}
				book.setParent(parentBook);

				//	
				chain.previousBook = book;
			}

			Book result = book;
			book = Book.create();
			return result;
		}

		private static class Chain {
			private int chainLength;
			private Book previousBook;
			private String marketSymbol;
		}

		private Book book;
		private final Map<String, Chain> chains = new HashMap<>();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getMarket().toString() + " Book at " + getTime() + " bids={");
		boolean first = true;
		for (Offer bid : getBids()) {
			if (first)
				first = false;
			else
				sb.append(';');
			sb.append(bid.getVolumeAsDouble());
			sb.append('@');
			sb.append(bid.getPriceAsDouble());
		}
		sb.append("} asks={");
		first = true;
		for (Offer ask : getAsks()) {
			if (first)
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
	protected Book() {
	}

	// These getters and setters are for conversion in JPA
	//@ManyToOne(cascade = CascadeType.MERGE)
	//@JoinColumn(name = "parent", insertable = false, updatable = false)

	@Nullable
	@ManyToOne(optional = true, cascade = { CascadeType.MERGE, CascadeType.REMOVE, }, fetch = FetchType.EAGER)
	public Book getParent() {
		return parent;
	}

	@Nullable
	@OneToMany(cascade = { CascadeType.MERGE, CascadeType.REMOVE })
	public Collection<Book> getChildren() {
		if (children == null)
			children = new ArrayList<Book>();
		synchronized (lock) {
			return children;
		}
	}

	public void addChild(Book book) {
		synchronized (lock) {
			getChildren().add(book);
		}
	}

	protected @Lob
	byte[] getBidDeletionsBlob() {
		return bidDeletionsBlob;
	}

	protected @Lob
	byte[] getAskDeletionsBlob() {
		return askDeletionsBlob;
	}

	protected @Lob
	byte[] getBidInsertionsBlob() {
		return bidInsertionsBlob;
	}

	protected @Lob
	byte[] getAskInsertionsBlob() {
		return askInsertionsBlob;
	}

	protected void setChildren(List<Book> children) {
		this.children = children;
	}

	protected void setParent(Book parent) {
		this.parent = parent;
	}

	protected void setBidDeletionsBlob(byte[] bidDeletionsBlob) {
		this.bidDeletionsBlob = bidDeletionsBlob;
	}

	protected void setAskDeletionsBlob(byte[] askDeletionsBlob) {
		this.askDeletionsBlob = askDeletionsBlob;
	}

	protected void setBidInsertionsBlob(byte[] bidInsertionsBlob) {
		this.bidInsertionsBlob = bidInsertionsBlob;
	}

	protected void setAskInsertionsBlob(byte[] askInsertionsBlob) {
		this.askInsertionsBlob = askInsertionsBlob;
	}

	// these fields are derived from the blobs
	protected void setBidPriceAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {
	}

	protected void setBidVolumeAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {
	}

	protected void setAskPriceAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {
	}

	protected void setAskVolumeAsDouble(@SuppressWarnings("UnusedParameters") Double ignored) {
	}

	// this is separate from the empty JPA constructor.  it allows Book.Builder to start with a minimally initialized Book
	private static Book create() {
		Book result = new Book();
		result.bids = new ArrayList<>();
		result.asks = new ArrayList<>();
		return result;
	}

	@PrePersist
	private void prePersist() {
		if (parent == null) {
			bidInsertionsBlob = convertQuotesToDatabaseBlob(bids);
			askInsertionsBlob = convertQuotesToDatabaseBlob(asks);
			bidDeletionsBlob = null;
			askDeletionsBlob = null;
		} else {
			DiffBlobs bidBlobs = diff(parent.getBids(), getBids());
			bidInsertionsBlob = bidBlobs.insertBlob;
			bidDeletionsBlob = bidBlobs.deleteBlob;
			DiffBlobs askBlobs = diff(parent.getAsks(), getAsks());
			askInsertionsBlob = askBlobs.insertBlob;
			askDeletionsBlob = askBlobs.deleteBlob;
		}
	}

	@SuppressWarnings("ConstantConditions")
	private boolean hasQuote(List<? extends Offer> list, Offer offer) {
		for (Offer item : list) {
			if (Long.compare(item.getPriceCount(), offer.getPriceCount()) == 0 && Long.compare(item.getVolumeCount(), offer.getVolumeCount()) == 0)
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
		bids = convertDatabaseBlobToQuoteList(bidInsertionsBlob);
		asks = convertDatabaseBlobToQuoteList(askInsertionsBlob);
		if (parent != null)
			needToResolveDiff = true;
	}

	// if this is implemented as a @PostLoad, the transitive dependencies for the parent's parent are not resolved
	private void resolveDiff() {
		if (!needToResolveDiff)
			return;
		// add any non-deleted entries from the parent
		List<Integer> bidDeletionIndexes = convertDatabaseBlobToIndexList(bidDeletionsBlob); // these should be already sorted
		List<Offer> parentBids = parent.getBids();
		for (int i = 0; i < parentBids.size(); i++) {
			if (!bidDeletionIndexes.contains(i))
				bids.add(parentBids.get(i));
		}
		List<Integer> askDeletionIndexes = convertDatabaseBlobToIndexList(askDeletionsBlob); // these should be already sorted
		List<Offer> parentAsks = parent.getAsks();
		for (int i = 0; i < parentAsks.size(); i++) {
			if (!askDeletionIndexes.contains(i))
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
	private static <T extends Offer> byte[] convertQuotesToDatabaseBlob(List<T> quotes) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeInt(quotes.size());
			for (T quote : quotes) {
				out.writeLong(quote.getPriceCount());
				out.writeLong(quote.getVolumeCount());
			}
			out.close();
			bos.close();
		} catch (IOException e) {
			throw new Error(e);
		}
		return bos.toByteArray();
	}

	private List<Offer> convertDatabaseBlobToQuoteList(byte[] bytes) {
        if( bytes == null )
            return new ArrayList<>();
		List<Offer> result = new ArrayList<>();
		ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
		//noinspection EmptyCatchBlock
		try {
			ObjectInputStream in = new ObjectInputStream(bin);
			int size = in.readInt();
			for (int i = 0; i < size; i++) {
				long price = in.readLong();
				long volume = in.readLong();
				result.add(new Offer(getMarket(), getTime(), getTimeReceived(), price, volume));
			}
			in.close();
			bin.close();
		} catch (IOException e) {
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
			for (Integer index : indexes)
				out.writeInt(index);
			out.close();
			bos.close();
		} catch (IOException e) {
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
			for (int i = 0; i < size; i++)
				result.add(in.readInt());
			in.close();
			bin.close();
		} catch (IOException e) {
			throw new Error(e);
		}
		return result;
	}

	/** this implements the public diff() */
	private void diff(DiffResult result, List<? extends Offer> childQuotes, List<? extends Offer> parentQuotes) {
		for (Offer childOffer : childQuotes) {
			if (!hasQuote(parentQuotes, childOffer))
				result.newOffers.add(childOffer);
		}
		for (Offer parentOffer : parentQuotes) {
			if (!hasQuote(childQuotes, parentOffer))
				result.removedOffers.add(parentOffer);
		}
	}

	private static class DiffBlobs {
		byte[] insertBlob;
		byte[] deleteBlob;
	}

	/** this is separate from the public diff for efficiency */
	private DiffBlobs diff(List<? extends Offer> parentQuotes, List<? extends Offer> childQuotes) {
		List<Offer> insertions = new ArrayList<>();
		for (Offer childOffer : childQuotes) {
			if (!hasQuote(parentQuotes, childOffer))
				insertions.add(childOffer);
		}

		List<Integer> deletionIndexes = new ArrayList<>();
		for (int i = 0; i < parentQuotes.size(); i++) {
			Offer offer = parentQuotes.get(i);
			if (!hasQuote(childQuotes, offer))
				deletionIndexes.add(i);
		}
		DiffBlobs result = new DiffBlobs();
		result.insertBlob = convertQuotesToDatabaseBlob(insertions);
		result.deleteBlob = convertIndexesToDatabaseBlob(deletionIndexes);
		return result;
	}

	private void sortBook() {
		Collections.sort(bids, new Comparator<Offer>() {
			@Override
			@SuppressWarnings("ConstantConditions")
			public int compare(Offer bid, Offer bid2) {
				return -bid.getPriceCount().compareTo(bid2.getPriceCount()); // high to low
			}
		});
		Collections.sort(asks, new Comparator<Offer>() {
			@Override
			@SuppressWarnings("ConstantConditions")
			public int compare(Offer ask, Offer ask2) {
				return ask.getPriceCount().compareTo(ask2.getPriceCount()); // low to high
			}
		});
	}

	private List<Offer> bids;
	private List<Offer> asks;
	private Book parent; // if this is not null, then the Book is persisted as a diff against the parent Book
	private byte[] bidDeletionsBlob;
	private byte[] askDeletionsBlob;
	private byte[] bidInsertionsBlob;
	private byte[] askInsertionsBlob;
	private boolean needToResolveDiff;
	private Collection<Book> children;

}
