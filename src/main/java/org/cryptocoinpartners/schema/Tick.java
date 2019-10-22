package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.Dao;
import org.joda.time.Instant;

/**
 * A Tick is a point-in-time snapshot of a Market's last price, volume and most recent Book
 * 
 * @author Tim Olson
 */
@Entity
public class Tick extends PriceData implements Spread {

	public Instant getStartInstant() {
		return startInstant;
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return null;
	}

	@Transient
	public Instant getEndInstant() {
		return getTime();
	}

	// @ManyToOne
	public Book getLastBook() {
		return lastBook;
	}

	/** @return null if no book was found prior to the window */
	@Override
	@Transient
	public @Nullable Offer getBestBid() {
		return lastBook == null ? null : lastBook.getBestBid();
	}

	/** @return null if no book was found prior to the window */
	@Override
	@Transient
	public @Nullable Offer getBestAsk() {
		return lastBook == null ? null : lastBook.getBestAsk();
	}

	public Tick(Tradeable market, Instant startInstant, Instant endInstant, @Nullable Long lastPriceCount, @Nullable Long volumeCount, Book lastBook) {
		super(endInstant, null, market, lastPriceCount, volumeCount);
		this.startInstant = startInstant;
		this.lastBook = lastBook;
	}

	@Override
	public String toString() {
		return String.format("Tick{%s last:%g@%g bid:%s ask:%s}", getMarket(), getVolumeAsDouble(), getPriceAsDouble(), getBestBid(), getBestAsk());
	}

	// JPA
	protected Tick() {
	}

	protected synchronized void setStartInstant(Instant startInstant) {
		this.startInstant = startInstant;
	}

	protected synchronized void setLastBook(Book lastBook) {
		this.lastBook = lastBook;
	}

	private Instant startInstant;
	private Book lastBook;

	@Override
	public Offer getBestBidByVolume(DiscreteAmount volume) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Offer getBestAskByVolume(DiscreteAmount volume) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized void persit() {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void detach() {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void merge() {
		// TODO Auto-generated method stub

	}

	@Override
	@Transient
	public Dao getDao() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	@Transient
	public synchronized void setDao(Dao dao) {
		// TODO Auto-generated method stub
		//  return null;
	}

	@Override
	public void delete() {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized EntityBase refresh() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized void prePersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void persitParents() {
		// TODO Auto-generated method stub

	}
}
