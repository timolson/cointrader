package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NoResultException;
import javax.persistence.PostPersist;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.RemainderHandler;

/**
 * Represents the possibility to trade one Asset for another at a specific Exchange.
 *
 * @author Tim Olson
 */
@Entity
public class Market extends EntityBase {

	public static Collection<Market> findAll() {
		return PersistUtil.queryList(Market.class, "select m from Market m");
	}

	/** adds the Market to the database if it does not already exist */
	public static Market findOrCreate(Exchange exchange, Listing listing) {
		return findOrCreate(exchange, listing, listing.getQuote().getBasis(), listing.getBase().getBasis());
	}

	@PostPersist
	private void postPersist() {

		PersistUtil.detach(this);

	}

	public static Market findOrCreate(Exchange exchange, Listing listing, double quoteBasis, double volumeBasis) {
		final String queryStr = "select m from Market m where exchange=?1 and listing=?2";
		try {
			return PersistUtil.queryOne(Market.class, queryStr, exchange, listing);
		} catch (NoResultException e) {
			final Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);
			PersistUtil.insert(ml);
			return ml;
		}
	}

	/**
	 @return active Markets for the given exchange
	 */
	public static Collection<Market> find(Exchange exchange) {
		return PersistUtil.queryList(Market.class, "select s from Market s where exchange=?1 and active=?2", exchange, true);
	}

	/**
	 @return active Markets for the given listing
	 */
	public static Collection<Market> find(Listing listing) {
		return PersistUtil.queryList(Market.class, "select s from Market s where listing=?1 and active=?2", listing, true);
	}

	@ManyToOne(optional = false)
	public Exchange getExchange() {
		return exchange;
	}

	@ManyToOne(optional = false)
	public Listing getListing() {
		return listing;
	}

	@Basic(optional = false)
	public double getPriceBasis() {
		return priceBasis;
	}

	@Transient
	public int getScale() {

		int length = (int) (Math.log10(getPriceBasis()));
		return length;
	}

	@Basic(optional = false)
	public double getVolumeBasis() {
		return volumeBasis;
	}

	/** @return true iff the Listing is currently traded at the Exchange.  The Market could have been retired. */
	public boolean isActive() {
		return active;
	}

	@Transient
	public Asset getBase() {
		return listing.getBase();
	}

	@Transient
	public Asset getQuote() {
		return listing.getQuote();
	}

	@Transient
	public String getSymbol() {
		return exchange.toString() + ':' + listing.toString();
	}

	@Override
	public String toString() {
		return getSymbol();
	}

	public static Market forSymbol(String marketSymbol) {
		for (Market market : findAll()) {
			if (market.getSymbol().equalsIgnoreCase(marketSymbol))
				return market;
		}
		return null;
	}

	public static List<String> allSymbols() {
		List<String> result = new ArrayList<>();
		List<Market> markets = PersistUtil.queryList(Market.class, "select m from Market m");
		for (Market market : markets)
			result.add((market.getSymbol()));
		return result;
	}

	public static class MarketAmountBuilder {

		public DiscreteAmount fromPriceCount(long count) {
			return priceBuilder.fromCount(count);
		}

		public DiscreteAmount fromVolumeCount(long count) {
			return volumeBuilder.fromCount(count);
		}

		public DiscreteAmount fromPrice(BigDecimal amount, RemainderHandler remainderHandler) {
			return priceBuilder.fromValue(amount, remainderHandler);
		}

		public DiscreteAmount fromVolume(BigDecimal amount, RemainderHandler remainderHandler) {
			return volumeBuilder.fromValue(amount, remainderHandler);
		}

		public MarketAmountBuilder(double priceBasis, double volumeBasis) {
			this.priceBuilder = DiscreteAmount.withBasis(priceBasis);
			this.volumeBuilder = DiscreteAmount.withBasis(volumeBasis);
		}

		private final DiscreteAmount.DiscreteAmountBuilder priceBuilder;
		private final DiscreteAmount.DiscreteAmountBuilder volumeBuilder;
	}

	public MarketAmountBuilder buildAmount() {
		if (marketAmountBuilder == null)
			marketAmountBuilder = new MarketAmountBuilder(priceBasis, volumeBasis);
		return marketAmountBuilder;
	}

	// JPA
	protected Market() {
	}

	protected void setExchange(Exchange exchange) {
		this.exchange = exchange;
	}

	protected void setListing(Listing listing) {
		this.listing = listing;
	}

	protected void setActive(boolean active) {
		this.active = active;
	}

	protected void setPriceBasis(double quoteBasis) {
		this.priceBasis = quoteBasis;
	}

	protected void setVolumeBasis(double volumeBasis) {
		this.volumeBasis = volumeBasis;
	}

	private Market(Exchange exchange, Listing listing, double priceBasis, double volumeBasis) {
		this.exchange = exchange;
		this.listing = listing;
		this.priceBasis = priceBasis;
		this.volumeBasis = volumeBasis;
		this.active = true;
	}

	private Exchange exchange;
	private Listing listing;
	private double priceBasis;
	private double volumeBasis;
	private boolean active;
	private MarketAmountBuilder marketAmountBuilder;
}
