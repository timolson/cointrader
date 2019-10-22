package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.PostPersist;
import javax.persistence.QueryHint;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.MarketJpaDao;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.RemainderHandler;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * Represents the possibility to trade one Asset for another at a specific Exchange.
 * 
 * @author Tim Olson
 */
@Entity
@Cacheable
@NamedQuery(name = "Market.findByMarket", query = "select m from Market m where exchange=?1 and listing=?2", hints = {
		@QueryHint(name = "org.hibernate.cacheable", value = "true") })
@Table(indexes = { @Index(columnList = "exchange"), @Index(columnList = "listing"), @Index(columnList = "active"), @Index(columnList = "version"),
		@Index(columnList = "revision") })
public class Market extends Tradeable {

	protected List<SyntheticMarket> syntheticMarkets = new ArrayList<>();
	protected static Set<Market> markets = new HashSet<Market>();
	@Inject
	protected transient static MarketFactory marketFactory;
	@Inject
	protected transient static ExchangeFactory exchangeFactory;
	@Inject
	protected transient static MarketJpaDao marketDao;

	//TODO
	//add a set of markets, that we keep and get from here, if not presnet in set go to db
	/** adds the Market to the database if it does not already exist */
	public synchronized static Market findOrCreate(Exchange exchange, Listing listing) {
		return findOrCreate(exchange, listing, listing.getPriceBasis(), listing.getVolumeBasis());
	}

	public synchronized static Market findOrCreate(Exchange exchange, Listing listing, double minOrderSize) {
		return findOrCreate(exchange, listing, listing.getPriceBasis(), listing.getVolumeBasis(), minOrderSize);
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return null;
	}

	@ManyToMany(fetch = FetchType.EAGER)
	//@JoinTable(name = "market_synthetic_market", joinColumns = @JoinColumn(name = "market_id"), inverseJoinColumns = @JoinColumn(name = "synthetic_id"))
	public List<SyntheticMarket> getSyntheticMarkets() {
		return syntheticMarkets;
	}

	public synchronized void setSyntheticMarkets(List<SyntheticMarket> syntheticMarkets) {
		if (syntheticMarkets == null || (syntheticMarkets != null && !syntheticMarkets.equals(this)))
			this.syntheticMarkets = syntheticMarkets;
	}

	public synchronized void addSyntheticMarket(SyntheticMarket market) {
		synchronized (getSyntheticMarkets()) {
			if (!getSyntheticMarkets().contains(market))
				getSyntheticMarkets().add(market);
		}
	}

	public synchronized void removeSyntheticMarket(SyntheticMarket market) {
		getSyntheticMarkets().remove(market);
		market.getMarkets().remove(this);

	}

	public synchronized static Market findOrCreate(Exchange exchange, Listing listing, double quoteBasis, double volumeBasis) {
		// final String queryStr = "select m from Market m where exchange=?1 and listing=?2";
		try {
			for (Market market : markets) {
				if (market.getExchange().equals(exchange) && market.getListing().equals(listing))
					return market;
			}
			List<Market> results = EM.namedQueryList(Market.class, "Market.findByMarket", exchange, listing);
			if (results != null && !results.isEmpty() && results.get(0) != null) {
				for (Market result : results) {
					result.setPersisted(true);
					if (result != null)
						markets.add(result);
				}
				return results.get(0);
			} else {
				Market ml = marketFactory.create(exchange, listing, quoteBasis, volumeBasis);
				ml.setPeristanceAction(PersistanceAction.NEW);
				ml.setRevision(ml.getRevision() + 1);
				ml.getDao().persistEntities(false, ml);
				if (ml != null)
					markets.add(ml);
				//  Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);

				// marketDao.persist(ml);
				return ml;
			}

		} catch (NoResultException e) {

			Market ml = marketFactory.create(exchange, listing, quoteBasis, volumeBasis);
			try {
				ml.setPeristanceAction(PersistanceAction.NEW);
				ml.setRevision(ml.getRevision() + 1);
				marketDao.persistEntities(false, ml);
				if (ml != null)
					markets.add(ml);
			} catch (Throwable e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			//  Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);

			// marketDao.persist(ml);
			return ml;
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public synchronized static Market findOrCreate(Exchange exchange, Listing listing, double quoteBasis, double volumeBasis, double minOrderSize) {
		// final String queryStr = "select m from Market m where exchange=?1 and listing=?2";
		try {
			for (Market market : markets) {
				if (market.getExchange().equals(exchange) && market.getListing().equals(listing))
					return market;
			}
			List<Market> results = EM.namedQueryList(Market.class, "Market.findByMarket", exchange, listing);
			if (results != null && !results.isEmpty() && results.get(0) != null) {
				for (Market result : results) {
					result.setPersisted(true);
					if (result != null)
						markets.add(result);
				}

				if (results.get(0).getMinimumOrderSize(results.get(0)) == minOrderSize)
					return results.get(0);
				else {
					results.get(0).setMinimumOrderSize(minOrderSize);
					results.get(0).merge();
					return results.get(0);
				}
			} else {
				Market ml = marketFactory.create(exchange, listing, quoteBasis, volumeBasis, minOrderSize);
				try {
					ml.setPeristanceAction(PersistanceAction.NEW);
					ml.setRevision(ml.getRevision() + 1);
					marketDao.persistEntities(false, ml);
					if (ml != null)
						markets.add(ml);
				} catch (Throwable e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				//  Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);

				// marketDao.persist(ml);
				return ml;
			}

		} catch (NoResultException e) {

			Market ml = marketFactory.create(exchange, listing, quoteBasis, volumeBasis, minOrderSize);
			try {
				ml.setPeristanceAction(PersistanceAction.NEW);
				ml.setRevision(ml.getRevision() + 1);
				marketDao.persistEntities(false, ml);
				if (ml != null)
					markets.add(ml);
			} catch (Throwable e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			//  Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);

			// marketDao.persist(ml);
			return ml;
		}
	}

	/**
	 * @return active Markets for the given exchange
	 */
	public static List<Market> find(Exchange exchange) {

		List<Market> markets = EM.queryList(Market.class, "select s from Market s where exchange=?1 and active=?2", exchange, true);

		for (Market market : markets)
			market.setPersisted(true);
		return markets;
	}

	/**
	 * @return active Markets for the given listing
	 */
	public static List<Market> find(Listing listing) {
		List<Market> markets = EM.queryList(Market.class, "select s from Market s where listing=?1 and active=?2", listing, true);
		for (Market market : markets)
			market.setPersisted(true);
		return markets;
	}

	@ManyToOne(optional = false)
	@JoinColumn(name = "exchange")
	public Exchange getExchange() {
		return exchange;
	}

	@ManyToOne(optional = true, cascade = CascadeType.PERSIST)
	@JoinColumn(name = "listing")
	public Listing getListing() {
		return listing;
	}

	@Override
	@Basic(optional = false)
	public double getPriceBasis() {
		if (priceBasis == 0 && listing != null)
			return listing.getPriceBasis() == 0 ? priceBasis : listing.getPriceBasis();
		else
			return priceBasis;

	}

	protected synchronized void setPriceBasis(double priceBasis) {
		this.priceBasis = priceBasis;
	}

	@Override
	@Basic(optional = false)
	public double getVolumeBasis() {
		if (listing == null)
			return volumeBasis;
		return listing.getVolumeBasis() == 0 ? volumeBasis : listing.getVolumeBasis();

	}

	/** @return true iff the Listing is currently traded at the Exchange. The Market could have been retired. */

	@Transient
	public Asset getBase() {
		return listing.getBase();
	}

	@Override
	@Transient
	public Amount getBalance(Asset currency) {
		if (getExchange() != null && getExchange().getBalances() != null && getExchange().getBalances().get(currency) != null)
			return getExchange().getBalances().get(currency).getAmount();
		return new DiscreteAmount(0, getVolumeBasis());

	}

	@Transient
	public Asset getQuote() {
		return listing.getQuote();
	}

	@Transient
	public int getMargin() {
		return listing.getMargin() == 0 ? exchange.getMargin() : listing.getMargin();

	}

	@Transient
	public double getLiquidation() {
		return listing.getLiquidation() == 0 ? exchange.getLiquidation() : listing.getLiquidation();

	}

	@Transient
	public double getFeeRate(ExecutionInstruction executionInstruction) {
		return listing.getFeeRate(executionInstruction) == 0 ? exchange.getFeeRate(executionInstruction) : listing.getFeeRate(executionInstruction);

	}

	@Transient
	public FeeMethod getMarginFeeMethod() {
		return listing.getMarginFeeMethod() == null ? exchange.getMarginFeeMethod() : listing.getMarginFeeMethod();

	}

	@Transient
	public FeeMethod getFeeMethod() {
		return listing.getFeeMethod() == null ? exchange.getFeeMethod() : listing.getFeeMethod();

	}

	@Transient
	public Amount getMultiplier(Market market, Amount entryPrice, Amount exitPrice) {
		return listing.getMultiplier(market, entryPrice, exitPrice);

	}

	@Transient
	public double getTickValue() {
		return listing.getTickValue();

	}

	public double getContractSize(Market market) {
		return listing.getContractSize(market);

	}

	@Transient
	public double getTickSize() {
		return listing.getTickSize();

	}

	@Transient
	public Asset getTradedCurrency(Tradeable market) {
		return listing.getTradedCurrency((Market) market);

	}

	@Override
	@Transient
	public String getSymbol() {
		return exchange.toString() + ':' + listing.toString();
	}

	@Override
	public String toString() {
		return getSymbol();
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

		private final transient DiscreteAmount.DiscreteAmountBuilder priceBuilder;
		private final transient DiscreteAmount.DiscreteAmountBuilder volumeBuilder;
	}

	public MarketAmountBuilder buildAmount() {
		if (marketAmountBuilder == null)
			marketAmountBuilder = new MarketAmountBuilder(getPriceBasis(), getVolumeBasis());
		return marketAmountBuilder;
	}

	// JPA
	protected Market() {
	}

	protected synchronized void setExchange(Exchange exchange) {
		this.exchange = exchange;
	}

	@Transient
	public double getMinimumOrderSize(Tradeable market) {
		if (minimumOrderSize == 0)
			return exchange.getMinimumOrderSize(market);
		else
			return minimumOrderSize;

	}

	protected synchronized void setListing(Listing listing) {
		this.listing = listing;
	}

	@Basic(optional = true)
	protected double getMinimumOrderSize() {

		return minimumOrderSize;
	}

	protected void setMinimumOrderSize(double minimumOrderSize) {
		this.minimumOrderSize = minimumOrderSize;
	}

	@AssistedInject
	public Market(@Assisted Exchange exchange, @Assisted Listing listing, @Assisted("marketPriceBasis") double priceBasis,
			@Assisted("marketVolumeBasis") double volumeBasis) {
		this.exchange = exchange;
		this.listing = listing;
		this.priceBasis = priceBasis;
		this.volumeBasis = volumeBasis;
		this.active = true;
	}

	@AssistedInject
	public Market(@Assisted Exchange exchange, @Assisted Listing listing, @Assisted("marketPriceBasis") double priceBasis,
			@Assisted("marketVolumeBasis") double volumeBasis, @Assisted("minimumOrderSize") double minimumOrderSize) {
		this.exchange = exchange;
		this.listing = listing;
		this.priceBasis = priceBasis;
		this.volumeBasis = volumeBasis;
		this.minimumOrderSize = minimumOrderSize;
		this.active = true;
	}

	@Override
	@Transient
	public boolean isSynthetic() {

		return false;
	}

	protected Exchange exchange;
	protected Listing listing;
	protected double minimumOrderSize;

	protected transient MarketAmountBuilder marketAmountBuilder;

	@Override
	public synchronized void prePersist() {

		if (getDao() != null) {

			EntityBase dbListing = null;
			EntityBase dbExchange = null;

			if (getListing() != null) {
				try {
					dbListing = getDao().find(getListing().getClass(), getListing().getId());
					if (dbListing != null) {
						dbListing = getDao().mergeEntities(false, getListing());
						this.setListing((Listing) dbListing);

					} else {
						//getOrder().setPeristanceAction(PersistanceAction.NEW);
						getDao().persistEntities(false, getListing());
					}
				} catch (Throwable ex) {
					if (dbListing != null)
						if (getListing().getRevision() > dbListing.getRevision()) {
							//  getOrder().setPeristanceAction(PersistanceAction.MERGE);
							try {
								getDao().mergeEntities(false, getListing());
							} catch (Throwable e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						} else {
							//   getOrder().setPeristanceAction(PersistanceAction.NEW);
							try {
								getDao().persistEntities(false, getListing());
							} catch (Throwable e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
				}
			}
			if (getExchange() != null) {
				try {
					dbExchange = getDao().find(getExchange().getClass(), getExchange().getId());
					if (dbExchange != null) {
						dbExchange = getDao().mergeEntities(false, getExchange());
						this.setExchange((Exchange) dbExchange);
					} else {
						//getOrder().setPeristanceAction(PersistanceAction.NEW);
						getDao().persistEntities(false, getExchange());
					}
				} catch (Throwable ex) {
					if (dbExchange != null)
						if (getExchange().getRevision() > dbExchange.getRevision()) {
							//  getOrder().setPeristanceAction(PersistanceAction.MERGE);
							try {
								getDao().mergeEntities(false, getExchange());
							} catch (Throwable e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						} else {
							//   getOrder().setPeristanceAction(PersistanceAction.NEW);
							try {
								getDao().persistEntities(false, getExchange());
							} catch (Throwable e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
				}
			}
		}

		// TODO Auto-generated method stub

	}

	@Override
	@PostPersist
	public synchronized void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void persitParents() {
		// TODO Auto-generated method stub

	}

}
