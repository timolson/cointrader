package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedSubgraph;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.PreRemove;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.PositionDao;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * A Position represents an amount of some Asset within an Exchange. If the Position is related to an Order then the Position is being held in reserve
 * (not tradeable) to cover the costs of the open Order.
 * 
 * @author Tim Olson
 */
@Entity
@Cacheable
@NamedEntityGraphs({
		@NamedEntityGraph(name = "graph.Position.fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "fills"), subgraphs = {
				@NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode("order")) }),
		@NamedEntityGraph(name = "graph.Position.portfolio", attributeNodes = @NamedAttributeNode("portfolio"))

		// @NamedAttributeNode("sender"),
		// @NamedAttributeNode("body")
})
public class Position extends Holding {

	@Inject
	protected transient PositionDao positionDao;

	private Amount shortAvgPrice;
	private Amount shortVolume;
	private Amount longVolume;
	private Amount openVolume;
	private Amount shortCumVolume;
	private Amount longCumVolume;
	private Amount longAvgPrice;
	private Amount longAvgStopPrice;
	private Amount shortAvgStopPrice;
	private PositionEffect positionEffect;
	private Amount originalLongAvgStopPrice;
	private Amount originalShortAvgStopPrice;

	private int exitCount = 1;
	protected transient static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.position");

	protected transient static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	protected transient static final String SEPARATOR = ",";

	//    public Position(Portfolio portfolio, Exchange exchange, Market market, Asset asset, Amount volume, Amount price) {
	//
	//        this.exchange = exchange;
	//        this.market = market;
	//        this.volume = volume;
	//        this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount();
	//        this.longVolume = volume.isPositive() ? volume : this.longVolume;
	//        this.longVolumeCount = volume.isPositive() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.longVolumeCount;
	//        this.shortVolume = volume.isNegative() ? volume : this.shortVolume;
	//        this.shortVolumeCount = volume.isNegative() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.shortVolumeCount;
	//        this.longAvgPrice = volume.isPositive() ? price : this.longAvgPrice;
	//        this.shortAvgPrice = volume.isNegative() ? price : this.shortAvgPrice;
	//        this.asset = asset;
	//        this.portfolio = portfolio;
	//    }

	@AssistedInject
	public Position(@Assisted Fill fill, @Assisted Market market) {

		this.getUuid();

		this.fills = new ArrayList<Fill>();
		fill.setPosition(this);
		this.addFill(fill);
		setPortfolio(fill.getPortfolio());

		getPortfolio().addPosition(this);

		this.exchange = fill.getMarket().getExchange();
		this.market = market;
		// this.volume = fill.getVolume();
		//this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount();
		//this.longVolume = volume.isPositive() ? volume : this.longVolume;
		//this.longVolumeCount = volume.isPositive() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.longVolumeCount;
		//this.shortVolume = volume.isNegative() ? volume : this.shortVolume;
		//this.shortVolumeCount = volume.isNegative() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.shortVolumeCount;
		//this.longAvgPrice = volume.isPositive() ? fill.getPrice() : this.longAvgPrice;
		//this.shortAvgPrice = volume.isNegative() ? fill.getPrice() : this.shortAvgPrice;
		//this.shortAvgStopPrice = fill.getStopPrice() != null && fill.isShort() ? fill.getStopPrice() : DecimalAmount.ZERO;
		//this.longAvgStopPrice = fill.getStopPrice() != null && fill.isLong() ? fill.getStopPrice() : DecimalAmount.ZERO;
		this.asset = fill.getMarket().getListing().getBase();

		// this.id = getId();
		//   

	}

	@AssistedInject
	public Position(@Assisted Collection<Fill> fills, @Assisted Market market) {
		this.getUuid();

		this.fills = new ArrayList<Fill>();
		this.market = market;
		this.addFills(fills);
		int index = 0;
		if (!fills.isEmpty()) {
			for (Fill fill : fills) {
				if (index > 0)
					break;
				this.exchange = fill.getMarket().getExchange();
				this.market = fill.getMarket();
				this.asset = fill.getMarket().getListing().getBase();
				this.portfolio = fill.getPortfolio();
				index++;

			}
			// this.id = getId();
			//     getDao().persist(this);

		}

	}

	@Transient
	public boolean isOpen() {

		return ((getLongVolume() != null && !getLongVolume().isZero()) || (getShortVolume() != null && !getShortVolume().isZero()));
	}

	@Transient
	public boolean isLong() {

		return (getOpenVolume() != null && getOpenVolume().isPositive());
	}

	@Override
	@OneToOne(optional = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "asset")
	public Asset getAsset() {
		return asset;
	}

	@Override
	public synchronized void setAsset(Asset asset) {
		this.asset = asset;
	}

	@Transient
	public boolean isShort() {

		return (getVolume() != null && getVolume().isNegative());
	}

	@Transient
	public boolean isFlat() {
		//if (getVolume()!=null)
		return ((getLongVolume() == null && getShortVolume() == null) || (getLongVolume().isZero() && getShortVolume().isZero()));
	}

	@ManyToOne(optional = false)
	@JoinColumn(name = "market")
	public Market getMarket() {
		return market;
	}

	public synchronized void setExitCount(int exitCount) {
		this.exitCount = exitCount;
	}

	@Column(columnDefinition = "integer DEFAULT 0", nullable = false)
	public int getExitCount() {
		//  if (version == null)
		//    return 0;
		return exitCount;
	}

	@Override
	@ManyToOne
	@JoinColumn(name = "exchange")
	public Exchange getExchange() {

		return exchange;
	}

	@Override
	public synchronized void setExchange(Exchange exchange) {
		this.exchange = exchange;
	}

	// @Transient
	// @Inject
	// public PositionJpaDao getDao(PositionJpaDao localPositionDao) {
	//   if (positionDao != null)
	//     return positionDao;
	//  else
	//    return localPositionDao;

	//  }

	// @Override

	@Transient
	public Amount getMarginAmount() {

		Amount marginAmount = DecimalAmount.ZERO;
		if (isOpen() && marginAmount != null) {
			return marginAmount;
		} else {
			return DecimalAmount.ONE;
		}
	}

	@Transient
	public Amount getVolume() {

		// if (volume == null)
		//   volume = new DiscreteAmount(volumeCount, market.getVolumeBasis());
		//return volume;

		if (getLongVolume() != null && getShortVolume() != null) {
			return getLongVolume().plus(getShortVolume());
		} else if (getLongVolume() != null) {
			return getLongVolume();
		} else if (getShortVolume() != null) {

			return getShortVolume();
		} else {

			return DecimalAmount.ZERO;
		}

	}

	@Transient
	public Amount getAvgPrice() {

		//	if (volume == null)
		//	volume = new DiscreteAmount(volumeCount, market.getVolumeBasis());
		//return volume;

		Amount avgPrice = (getVolume().isNegative()) ? getShortAvgPrice() : getLongAvgPrice();

		if (avgPrice == null)
			return DecimalAmount.ZERO;
		else
			return avgPrice;
		// return ((getLongAvgPrice().times(getLongVolume(), Remainder.ROUND_EVEN)).plus(getShortAvgPrice().times(getShortVolume(), Remainder.ROUND_EVEN)))
		//       .dividedBy(getLongVolume().plus(getShortVolume()), Remainder.ROUND_EVEN);

	}

	@Transient
	public Amount getAvgStopPrice() {

		//  if (volume == null)
		//  volume = new DiscreteAmount(volumeCount, market.getVolumeBasis());
		//return volume;
		Amount avgStopPrice = (getVolume().isNegative()) ? getShortAvgStopPrice() : getLongAvgStopPrice();
		if (avgStopPrice == null)
			return DecimalAmount.ZERO;
		else
			return avgStopPrice;

	}

	@Transient
	public Amount getOriginalAvgStopPrice() {

		//  if (volume == null)
		//  volume = new DiscreteAmount(volumeCount, market.getVolumeBasis());
		//return volume;
		Amount avgStopPrice = (getVolume().isNegative()) ? getOriginalShortAvgStopPrice() : getOriginalLongAvgStopPrice();
		if (avgStopPrice == null)
			return DecimalAmount.ZERO;
		else
			return avgStopPrice;

	}

	@Transient
	public Amount getOpenVolume() {
		if (getMarket() == null)
			return DecimalAmount.ZERO;
		if (openVolume == null)
			openVolume = new DiscreteAmount(getOpenVolumeCount(), getMarket().getVolumeBasis());
		return openVolume;

	}

	@Transient
	public Amount getLongVolume() {
		if (getMarket() == null)
			return DecimalAmount.ZERO;
		if (longVolume == null)
			longVolume = new DiscreteAmount(getLongVolumeCount(), getMarket().getVolumeBasis());
		return longVolume;

	}

	@Transient
	public Amount getShortVolume() {
		if (getMarket() == null)
			return DecimalAmount.ZERO;
		if (shortVolume == null)
			shortVolume = new DiscreteAmount(getShortVolumeCount(), getMarket().getVolumeBasis());
		return shortVolume;
	}

	@Transient
	public Amount getLongAvgPrice() {
		if (longAvgPrice != null)
			return longAvgPrice;
		longCumVolume = DecimalAmount.ZERO;
		Amount longAvgPriceTmp = null;
		synchronized (getFills()) {
			Iterator<Fill> itf = getFills().iterator();
			while (itf.hasNext() && longAvgPrice == null) {
				Fill pos = itf.next();
				if (pos.isLong() && pos.getPrice() != null) {
					longAvgPriceTmp = longAvgPriceTmp == null ? pos.getPrice()
							: ((longAvgPriceTmp.times(longCumVolume, Remainder.ROUND_EVEN))
									.plus(pos.getOpenVolume().times(pos.getPrice(), Remainder.ROUND_EVEN))).divide(longCumVolume.plus(pos.getOpenVolume()),
											Remainder.ROUND_EVEN);
					longCumVolume = longCumVolume.plus(pos.getOpenVolume());
				}

			}
		}
		if (longAvgPriceTmp == null && longAvgPrice == null)
			longAvgPrice = DecimalAmount.ZERO;
		else if (longAvgPriceTmp != null && longAvgPrice == null)
			longAvgPrice = longAvgPriceTmp;
		return longAvgPrice;

	}

	public <T> T find() {
		//   synchronized (persistanceLock) {
		try {
			return (T) positionDao.find(Order.class, this.getId());
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {
			return null;
			//System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}
	}

	@Transient
	public Amount getShortAvgPrice() {
		if (shortAvgPrice != null)
			return shortAvgPrice;
		shortCumVolume = DecimalAmount.ZERO;
		Amount shortAvgPriceTmp = null;
		synchronized (getFills()) {
			Iterator<Fill> itf = getFills().iterator();

			while (itf.hasNext() && shortAvgPrice == null) {
				Fill pos = itf.next();
				if (pos.isShort() && pos.getPrice() != null) {
					shortAvgPriceTmp = shortAvgPriceTmp == null ? pos.getPrice()
							: ((shortAvgPriceTmp.times(shortCumVolume, Remainder.ROUND_EVEN))
									.plus(pos.getOpenVolume().times(pos.getPrice(), Remainder.ROUND_EVEN))).dividedBy(shortCumVolume.plus(pos.getOpenVolume()),
											Remainder.ROUND_EVEN);
					shortCumVolume = shortCumVolume.plus(pos.getOpenVolume());

				}
			}
		}

		if (shortAvgPriceTmp == null && shortAvgPrice == null)
			shortAvgPrice = DecimalAmount.ZERO;
		else if (shortAvgPriceTmp != null && shortAvgPrice == null)
			shortAvgPrice = shortAvgPriceTmp;
		return shortAvgPrice;

	}

	@Transient
	public Amount getOriginalLongAvgStopPrice() {
		//  if (longAvgStopPrice == null) {
		//    Amount longCumVolume = DecimalAmount.ZERO;
		//  Amount longAvgStopPrice = DecimalAmount.ZERO;
		if (originalLongAvgStopPrice != null)
			return longAvgStopPrice;
		//  synchronized (this) {
		longCumVolume = DecimalAmount.ZERO;
		Amount originalLongAvgStopPriceTmp = null;
		synchronized (getFills()) {
			Iterator<Fill> itf = getFills().iterator();
			while (itf.hasNext() && longAvgStopPrice == null) {
				//  for (Fill pos : getFills()) {
				Fill pos = itf.next();
				if (pos.isLong()) {
					if (pos.getFillChildOrders() != null)
						synchronized (pos.getFillChildOrders()) {
							for (Order childOrder : pos.getFillChildOrders()) {
								if (childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()) {
									if (childOrder.isAsk() && childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()
											&& !(longCumVolume.plus(childOrder.getUnfilledVolume()).isZero())) {
										originalLongAvgStopPriceTmp = originalLongAvgStopPriceTmp == null ? childOrder.getStopPrice()
												: ((originalLongAvgStopPriceTmp.times(longCumVolume, Remainder.ROUND_EVEN))
														.plus(childOrder.getUnfilledVolume().times(childOrder.getStopPrice(), Remainder.ROUND_EVEN)))
																.divide(longCumVolume.plus(childOrder.getUnfilledVolume()), Remainder.ROUND_EVEN);
										longCumVolume = longCumVolume.plus(childOrder.getUnfilledVolume());
									}
								}
							}
						}
				}
			}
		}
		if (originalLongAvgStopPriceTmp == null && originalLongAvgStopPrice == null)
			originalLongAvgStopPrice = DecimalAmount.ZERO;
		else if (originalLongAvgStopPriceTmp != null && originalLongAvgStopPrice == null)
			originalLongAvgStopPrice = originalLongAvgStopPriceTmp;
		return originalLongAvgStopPrice;

	}

	@Transient
	public Amount getLongAvgStopPrice() {
		//  if (longAvgStopPrice == null) {
		//    Amount longCumVolume = DecimalAmount.ZERO;
		//  Amount longAvgStopPrice = DecimalAmount.ZERO;
		if (longAvgStopPrice != null)
			//&& ((getLongVolume() != null && getLongVolume().isZero()) || (longAvgStopPrice != null && !longAvgStopPrice.isZero())))
			return longAvgStopPrice;
		longCumVolume = DecimalAmount.ZERO;
		Amount longAvgStopPriceTmp = null;
		synchronized (getFills()) {
			Iterator<Fill> itf = getFills().iterator();
			while (itf.hasNext() && longAvgStopPrice == null) {
				//  for (Fill pos : getFills()) {
				Fill pos = itf.next();
				if (pos.isLong()) {
					if (pos.getFillChildOrders() != null)
						synchronized (pos.getFillChildOrders()) {
							for (Order childOrder : pos.getFillChildOrders()) {
								if (childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()) {
									if (childOrder.isAsk() && childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()
											&& !(longCumVolume.plus(childOrder.getUnfilledVolume()).isZero())) {
										longAvgStopPriceTmp = longAvgStopPriceTmp == null ? childOrder.getStopPrice()
												: ((longAvgStopPriceTmp.times(longCumVolume, Remainder.ROUND_EVEN))
														.plus(childOrder.getUnfilledVolume().times(childOrder.getStopPrice(), Remainder.ROUND_EVEN)))
																.divide(longCumVolume.plus(childOrder.getUnfilledVolume()), Remainder.ROUND_EVEN);
										longCumVolume = longCumVolume.plus(childOrder.getUnfilledVolume());
									}
								}
							}
						}
				}
			}
		}

		//   }
		if (longAvgStopPriceTmp == null && longAvgStopPrice == null)
			longAvgStopPrice = DecimalAmount.ZERO;
		else if (longAvgStopPriceTmp != null && longAvgStopPrice == null)
			longAvgStopPrice = longAvgStopPriceTmp;
		return longAvgStopPrice;

	}

	public synchronized void setMarket(Market market) {
		this.market = market;
		this.exchange = market.getExchange();
	}

	@Transient
	public PositionEffect getPositionEffect() {
		if (positionEffect != null)
			return positionEffect;
		PositionEffect positionEffectTmp = PositionEffect.DEFAULT;
		synchronized (getFills()) {
			Iterator<Fill> itf = getFills().iterator();
			while (itf.hasNext()) {
				Fill pos = itf.next();
				if (pos.getPositionEffect() != null && positionEffectTmp.equals(PositionEffect.DEFAULT))
					positionEffectTmp = pos.getPositionEffect();
				else if (pos.getPositionEffect() != null && !(positionEffectTmp.equals(pos.getPositionEffect()))) {
					positionEffectTmp = PositionEffect.DEFAULT;
					break;
				}
			}
		}
		positionEffect = positionEffectTmp;
		return positionEffect;
	}

	@Transient
	public Amount getShortAvgStopPrice() {
		//   if (shortAvgStopPrice == null) {
		//  Amount shortAvgStopPrice = DecimalAmount.ZERO;
		//Amount shortCumVolume = DecimalAmount.ZERO;
		// if (shortAvgStopPrice == null)
		if (shortAvgStopPrice != null)
			//        && ((getShortVolume() != null && getShortVolume().isZero()) || (shortAvgStopPrice != null && !shortAvgStopPrice.isZero())))
			return shortAvgStopPrice;
		shortCumVolume = DecimalAmount.ZERO;
		Amount shortAvgStopPriceTmp = null;
		synchronized (getFills()) {
			Iterator<Fill> itf = getFills().iterator();
			while (itf.hasNext() && shortAvgStopPrice == null) {
				//  for (Fill pos : getFills()) {
				Fill pos = itf.next();
				if (pos.isShort()) {
					if (pos.getFillChildOrders() != null)
						synchronized (pos.getFillChildOrders()) {
							for (Order childOrder : pos.getFillChildOrders()) {
								if (childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()) {
									if (childOrder.isBid() && childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()
											&& !(shortCumVolume.plus(childOrder.getUnfilledVolume()).isZero())) {
										shortAvgStopPriceTmp = shortAvgStopPriceTmp == null ? childOrder.getStopPrice()
												: ((shortAvgStopPriceTmp.times(shortCumVolume, Remainder.ROUND_EVEN))
														.plus(childOrder.getUnfilledVolume().times(childOrder.getStopPrice(), Remainder.ROUND_EVEN)))
																.divide(shortCumVolume.plus(childOrder.getUnfilledVolume()), Remainder.ROUND_EVEN);
										shortCumVolume = shortCumVolume.plus(childOrder.getUnfilledVolume());
									}
								}
							}
						}
				}
			}
		}
		if (shortAvgStopPriceTmp == null && shortAvgStopPrice == null)
			shortAvgStopPrice = DecimalAmount.ZERO;
		else if (shortAvgStopPriceTmp != null && shortAvgStopPrice == null)
			shortAvgStopPrice = shortAvgStopPriceTmp;
		return shortAvgStopPrice;

	}

	@Transient
	public Amount getOriginalShortAvgStopPrice() {
		if (originalShortAvgStopPrice != null)
			return originalShortAvgStopPrice;
		shortCumVolume = DecimalAmount.ZERO;
		Amount originalShortAvgStopPriceTmp = null;
		synchronized (getFills()) {
			Iterator<Fill> itf = getFills().iterator();
			while (itf.hasNext() && shortAvgStopPrice == null) {
				//  for (Fill pos : getFills()) {
				Fill pos = itf.next();
				if (pos.isShort()) {
					if (pos.getFillChildOrders() != null)
						synchronized (pos.getFillChildOrders()) {
							for (Order childOrder : pos.getFillChildOrders()) {
								if (childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()) {
									if (childOrder.isBid() && childOrder.getStopPrice() != null && !childOrder.getStopPrice().isZero()
											&& !(shortCumVolume.plus(childOrder.getUnfilledVolume()).isZero())) {
										originalShortAvgStopPriceTmp = originalShortAvgStopPriceTmp == null ? childOrder.getStopPrice()
												: ((originalShortAvgStopPriceTmp.times(shortCumVolume, Remainder.ROUND_EVEN))
														.plus(childOrder.getUnfilledVolume().times(childOrder.getStopPrice(), Remainder.ROUND_EVEN)))
																.divide(shortCumVolume.plus(childOrder.getUnfilledVolume()), Remainder.ROUND_EVEN);
										shortCumVolume = shortCumVolume.plus(childOrder.getUnfilledVolume());
									}
								}
							}
						}
				}
			}
		}

		if (originalShortAvgStopPriceTmp == null && originalShortAvgStopPrice == null)
			originalShortAvgStopPrice = DecimalAmount.ZERO;
		else if (originalShortAvgStopPriceTmp != null && originalShortAvgStopPrice == null)
			originalShortAvgStopPrice = originalShortAvgStopPriceTmp;
		return originalShortAvgStopPrice;

	}

	/** If the SpecificOrder is not null, then this Position is being held in reserve as payment for that Order */

	/**
	 * Modifies this Position in-place by the amount of the position argument.
	 * 
	 * @param position a Position to add to this one.
	 * @return true iff the positions both have the same Asset and the same Exchange, in which case this Position has modified its volume by the amount
	 * in the position argument.
	 */

	@Override
	public String toString() {
		return "UUID=" + (getUuid() != null ? getUuid() : "") + SEPARATOR + "Id=" + (getId() != null ? getId() : "") + SEPARATOR + "Exchange=" + exchange
				+ (getShortVolume() != null ? (SEPARATOR + ", Short Qty=" + getShortVolume()) : "")
				+ (getShortAvgPrice() != null ? (SEPARATOR + ", Short Avg Price=" + getShortAvgPrice()) : "")
				+ (getPositionEffect() != null ? (SEPARATOR + ",PositionEffect=" + getPositionEffect()) : "")
				+ (getShortAvgStopPrice() != null ? (SEPARATOR + ", Short Avg Stop Price=" + getShortAvgStopPrice()) : "")
				+ (getOriginalShortAvgStopPrice() != null ? (SEPARATOR + ", Original Short Avg Stop Price=" + getOriginalShortAvgStopPrice()) : "")
				+ (getLongVolume() != null ? (SEPARATOR + "Long Qty=" + getLongVolume()) : "")
				+ (getLongAvgPrice() != null ? (SEPARATOR + "Long Avg Price=" + getLongAvgPrice()) : "")
				+ (getLongAvgStopPrice() != null ? (SEPARATOR + "Long Avg Stop Price=" + getLongAvgStopPrice()) : "")
				+ (getOriginalLongAvgStopPrice() != null ? (SEPARATOR + "Original Long Avg Stop Price=" + getOriginalLongAvgStopPrice()) : "") + ", Net Qty="
				+ getVolume().toString() + " Vol Count=" + getVolumeCount() + " Vol Count=" + getVolumeCount() + ", Open Volume=" + getOpenVolume().toString()
				+ ",  Entry Date=" + ", Instrument=" + asset;
	}

	//JPA
	public Position() {
	}

	@Transient
	protected synchronized void setVolumeCount(long volumeCount) {

		this.volumeCount = new AtomicLong(volumeCount);

		shortAvgPrice = null;
		longAvgPrice = null;
		longAvgStopPrice = null;
		originalLongAvgStopPrice = null;
		shortAvgStopPrice = null;
		originalShortAvgStopPrice = null;
		longVolume = null;
		shortVolume = null;
		openVolume = null;
		longVolumeCount = null;
		openVolumeCount = null;
		shortVolumeCount = null;
	}

	@Transient
	protected synchronized void setOpenVolumeCount(long openVolumeCount) {
		this.openVolumeCount = new AtomicLong(openVolumeCount);
		shortAvgPrice = null;
		longAvgPrice = null;
		longAvgStopPrice = null;
		originalLongAvgStopPrice = null;
		shortAvgStopPrice = null;
		originalShortAvgStopPrice = null;
		longVolume = null;
		shortVolume = null;
		openVolume = null;
		longVolumeCount = null;
		volumeCount = null;

		shortVolumeCount = null;
	}

	@Transient
	protected synchronized void setLongVolumeCount(long longVolumeCount) {
		this.longVolumeCount = new AtomicLong(longVolumeCount);
		shortAvgPrice = null;
		longAvgPrice = null;
		longAvgStopPrice = null;
		originalLongAvgStopPrice = null;
		shortAvgStopPrice = null;
		originalShortAvgStopPrice = null;
		longVolume = null;
		shortVolume = null;
		openVolume = null;
		volumeCount = null;
		openVolumeCount = null;
		shortVolumeCount = null;

	}

	@Transient
	protected synchronized void setShortVolumeCount(long shortVolumeCount) {
		this.shortVolumeCount = new AtomicLong(shortVolumeCount);
		shortAvgPrice = null;
		longAvgPrice = null;
		longAvgStopPrice = null;
		originalLongAvgStopPrice = null;
		shortAvgStopPrice = null;
		originalShortAvgStopPrice = null;
		longVolume = null;
		shortVolume = null;
		openVolume = null;
		longVolumeCount = null;
		volumeCount = null;
		openVolumeCount = null;

	}

	@Nullable
	@Transient
	protected long getVolumeCount() {
		//    reset();
		long volumeCountTmp = 0;

		if (volumeCount == null) {
			synchronized (getFills()) {
				Iterator<Fill> itf = getFills().iterator();
				while (itf.hasNext()) {
					//  for (Fill pos : getFills()) {
					Fill fill = itf.next();

					volumeCountTmp += fill.getOpenVolumeCount();

				}
			}
			volumeCount = new AtomicLong(volumeCountTmp);
		}

		return volumeCount.get();

	}

	@Nullable
	@Transient
	protected long getOpenVolumeCount() {
		//    reset();
		if (openVolumeCount == null) {
			synchronized (getFills()) {
				long openVolumeCountTmp = 0;

				Iterator<Fill> itf = getFills().iterator();
				while (itf.hasNext()) {

					//  for (Fill pos : getFills()) {
					Fill fill = itf.next();

					// if (fill.getPositionEffect() == null || fill.getPositionEffect() == PositionEffect.OPEN)
					openVolumeCountTmp += fill.getOpenVolumeCount();

				}
				openVolumeCount = new AtomicLong(openVolumeCountTmp);

			}

		}

		return openVolumeCount.get();

	}

	@Nullable
	@Transient
	protected long getLongVolumeCount() {
		//  reset();
		//System.out.println(getFills().toString());
		long longVolumeCountTmp = 0;

		if (longVolumeCount == null) {
			synchronized (getFills()) {
				Iterator<Fill> itf = getFills().iterator();
				while (itf.hasNext()) {
					//  for (Fill pos : getFills()) {
					Fill fill = itf.next();

					if (fill.isLong()) {
						longVolumeCountTmp += fill.getOpenVolumeCount();
					}
					long vol = this.getVolumeCount();
					// if (vol > 0 && vol != longVolumeCount)
					//System.out.println("issue with long volume cacl");

				}
			}
			longVolumeCount = new AtomicLong(longVolumeCountTmp);
		}

		return longVolumeCount.get();

	}

	@Nullable
	@Transient
	protected long getShortVolumeCount() {
		//  reset();
		long shortVolumeCountTmp = 0;

		if (shortVolumeCount == null) {
			synchronized (getFills()) {
				Iterator<Fill> itf = getFills().iterator();
				while (itf.hasNext()) {
					//  for (Fill pos : getFills()) {
					Fill fill = itf.next();
					// if it is entering & short or exiting and long
					if (fill.isShort()) {
						shortVolumeCountTmp += fill.getOpenVolumeCount();

					}

				}
			}
			shortVolumeCount = new AtomicLong(shortVolumeCountTmp);

		}
		return shortVolumeCount.get();

	}

	//  fetch = FetchType.EAGER,

	@OneToMany(mappedBy = "position")
	//, fetch = FetchType.EAGER)
	// ;;@OrderColumn(name = "time")
	//, orphanRemoval = true, cascade = CascadeType.REMOVE)
	@OrderBy
	//, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	public List<Fill> getFills() {

		return this.fills;

	}

	@Override
	public Position clone() {
		Position clone = null;
		try {
			clone = (Position) super.clone();

			clone.fills = new ArrayList(this.getFills());

			//deep copying 
		} catch (CloneNotSupportedException cns) {
			log.error("Error while cloning fill", cns);
		}
		return clone;
	}

	@Override
	public synchronized void merge() {

		log.debug("Position - Merge : Merge of Position " + this.getUuid() + " called from class " + Thread.currentThread().getStackTrace()[2]);

		this.setPeristanceAction(PersistanceAction.MERGE);

		this.setRevision(this.getRevision() + 1);
		synchronized (this.getFills()) {
			try {
				positionDao.merge(this);
				//if (duplicate == null || duplicate.isEmpty())
			} catch (Exception | Error ex) {

				log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
				throw ex;
				// ex.printStackTrace();

			}
		}
	}

	@Override
	public synchronized void delete() {
		//   synchronized (this.getPortfolio().getPositions()) {

		try {
			log.debug("Position - delete : Delete of Position " + this.getUuid() + " called from class " + Thread.currentThread().getStackTrace()[2]);
			//  if (this.getPortfolio() != null)
			//  if (this.getPortfolio().removePosition(this)) {

			//  log.trace("removed this");
			// }
			// this.getPortfolio().merge();
			if (this.getPortfolio() != null) {
				this.getPortfolio().removePosition(this);
				this.setPortfolio(null);
			}
			this.getFills().clear();
			positionDao.delete(this);
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {
			log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":remove, full stack trace follows:", ex);
			throw ex;

			//  System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":remove, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}
		// }
	}

	@Override
	public synchronized EntityBase refresh() {
		try {
			log.debug("Position - Refresh : Refresh of Position " + this.getUuid() + " called from class " + Thread.currentThread().getStackTrace()[2]);

			return positionDao.refresh(this);
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":refresh, full stack trace follows:", ex);
			throw ex;
			// ex.printStackTrace();

		}
		// return null;
	}

	@Override
	@Transient
	public Dao getDao() {
		return positionDao;
	}

	@Override
	@Transient
	public void setDao(Dao dao) {
		positionDao = (PositionDao) dao;
		// TODO Auto-generated method stub
		//  return null;
	}

	@Override
	public synchronized void persit() {

		try {
			log.debug("Position - Persist : Persit of Position " + this.getUuid() + " called from class " + Thread.currentThread().getStackTrace()[2]);
			this.setPeristanceAction(PersistanceAction.NEW);
			this.setRevision(this.getRevision() + 1);

			positionDao.persist(this);

			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}

	}

	@PreRemove
	public synchronized void preRemove() {

		//  if (getPortfolio() != null) {
		//  Portfolio latestPort = getDao().findById(Portfolio.class, getPortfolio().getId());
		//  getPortfolio().setVersion(latestPort.getVersion());
		// }
		//  setFills(null);

		//  setPortfolio(null);
		//   synchronized (lock) {
	}

	//protected void Merge() {
	//     synchronized (persistanceLock) {
	// if (this.hasFills()) {
	//   for (Fill fill : this.getFills())
	//     PersistUtil.merge(fill);
	// }
	// ..  PersistUtil.merge(this);
	//    }

	// }

	public synchronized void reset() {
		shortAvgPrice = null;
		longAvgPrice = null;
		longAvgStopPrice = null;
		originalLongAvgStopPrice = null;
		shortAvgStopPrice = null;
		originalShortAvgStopPrice = null;
		longVolume = null;
		shortVolume = null;
		openVolume = null;
		longVolumeCount = null;
		volumeCount = null;
		openVolumeCount = null;
		shortVolumeCount = null;
		positionEffect = null;

	}

	public synchronized boolean addFill(Fill fill) {
		//   synchronized (lock) {

		if (this.fills.contains(fill))
			return false;
		else {

			synchronized (this.fills) {

				if (this.fills.add(fill)) {

					reset();

					return true;
				}
			}

			//  return (getFills().add(fill));
		}
		return false;
		//TODO We should do a check to make sure the fill is the samme attributes as position
		//}
		//this.exchange = fill.getMarket().getExchange();
		//this.market = fill.getMarket();
		//this.asset = fill.getMarket().getListing().getBase();
		//this.portfolio = fill.getPortfolio();

		// reset();

	}

	public synchronized void addFills(Collection<Fill> fills) {
		//   synchronized (lock) {

		synchronized (this.fills) {
			this.fills.addAll(fills);
			reset();

		}

		//TODO We should do a check to make sure the fill is the samme attributes as position
		//}
		//this.exchange = fill.getMarket().getExchange();
		//this.market = fill.getMarket();
		//this.asset = fill.getMarket().getListing().getBase();
		//this.portfolio = fill.getPortfolio();

		// reset();

	}

	public synchronized void removeFills(Collection<Fill> removedFills) {
		//   synchronized (lock) {
		synchronized (this.fills) {

			this.fills.removeAll(removedFills);
			reset();
		}
		for (Fill removedFill : removedFills) {

			removedFill.setPosition(null);
			//removedFill.setOpenVolumeCount(0);
			removedFill.merge();
		}

		//   removeFill(removedFill);
		//ODO We should do a check to make sure the fill is the samme attributes as position
		//}
		//this.exchange = fill.getMarket().getExchange();
		//this.market = fill.getMarket();
		//this.asset = fill.getMarket().getListing().getBase();
		//this.portfolio = fill.getPortfolio();

		// reset();

	}

	public synchronized void removeAllFills() {
		//   synchronized (lock) {
		// if (this.fills.removeAll(removedFills))

		for (Fill removedFill : getFills()) {
			removedFill.setPosition(null);
			//		removedFill.setOpenVolumeCount(0);
			// removedFill.merge();
		}
		reset();
		synchronized (this.fills) {
			this.fills.clear();
		}
		//   removeFill(removedFill);
		//ODO We should do a check to make sure the fill is the samme attributes as position
		//}
		//this.exchange = fill.getMarket().getExchange();
		//this.market = fill.getMarket();
		//this.asset = fill.getMarket().getListing().getBase();
		//this.portfolio = fill.getPortfolio();

		// reset();

	}

	public synchronized void removeFill(Fill fill) {
		//   synchronized (lock) {
		synchronized (this.fills) {
			if (this.fills.remove(fill)) {
				reset();
				fill.setPosition(null);
				//	fill.setOpenVolumeCount(0);
				//  fill.merge();
			}
		}
		//TODO We should do a check to make sure the fill is the samme attributes as position
		//}
		//this.exchange = fill.getMarket().getExchange();
		//this.market = fill.getMarket();
		//this.asset = fill.getMarket().getListing().getBase();
		//this.portfolio = fill.getPortfolio();

		// reset();

	}

	//    public void reset() {
	//        this.volume = null;
	//        this.longVolume = null;
	//        this.shortVolume = null;
	//        this.volumeCount = 0;
	//        this.longVolumeCount = 0;
	//        this.shortVolumeCount = 0;
	//        this.longAvgPrice = null;
	//        this.shortAvgPrice = null;
	//
	//        this.longAvgStopPrice = null;
	//        this.shortAvgStopPrice = null;
	//    }

	//  public void removeFill(Fill fill) {
	//  synchronized (lock) {
	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(name = "portfolio")
	public Portfolio getPortfolio() {
		if (portfolio == null)
			if (this.fills != null && !this.fills.isEmpty())
				return this.fills.get(0).getPortfolio();
		return portfolio;
	}

	public synchronized void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return getPortfolio();
	}

	//this.fills.remove(fill);
	//fill.setPosition(null);

	//}
	//  fill.persit();
	//PersistUtil.merge(fill);
	//        this.exchange = fill.getMarket().getExchange();
	//        this.market = fill.getMarket();
	//        this.portfolio = fill.getPortfolio();
	//reset();

	// }

	@Transient
	public boolean hasFills() {

		return (getFills() != null && !getFills().isEmpty());

	}

	protected synchronized void setFills(List<Fill> fills) {

		this.fills = fills;
		this.reset();

	}

	// private Amount longVolume = DecimalAmount.ZERO;
	//private Amount shortVolume = DecimalAmount.ZERO;
	//private Amount volume = DecimalAmount.ZERO;
	private Market market;
	AtomicLong longVolumeCount;
	AtomicLong volumeCount;
	AtomicLong openVolumeCount;
	AtomicLong shortVolumeCount;

	//private Amount longAvgPrice = DecimalAmount.ZERO;
	//private Amount shortAvgPrice = DecimalAmount.ZERO;
	//private Amount longAvgStopPrice = DecimalAmount.ZERO;
	//private Amount shortAvgStopPrice = DecimalAmount.ZERO;
	//private final Amount marginAmount = DecimalAmount.ZERO;
	//private long longVolumeCount;
	//private long shortVolumeCount;
	//private long volumeCount;
	//private SpecificOrder order;
	private List<Fill> fills;
	protected Portfolio portfolio;

	private static Object lock = new Object();
	private static Object persistanceLock = new Object();

	@Override
	public synchronized void prePersist() {
		if (getDao() != null) {

			EntityBase dbPortfolio = null;
			EntityBase dbMarket = null;
			EntityBase dbExchange = null;
			/*
			 * if (getPortfolio() != null) { try { dbPortfolio = getDao().find(getPortfolio().getClass(), getPortfolio().getId()); if (dbPortfolio != null
			 * && dbPortfolio.getVersion() != getPortfolio().getVersion()) { getPortfolio().setVersion(dbPortfolio.getVersion()); if
			 * (getPortfolio().getRevision() > dbPortfolio.getRevision()) { // getPortfolio().setPeristanceAction(PersistanceAction.MERGE);
			 * getDao().merge(getPortfolio()); } } else { getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getPortfolio()); } }
			 * catch (Exception | Error ex) { if (dbPortfolio != null) if (getPortfolio().getRevision() > dbPortfolio.getRevision()) { //
			 * getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getPortfolio()); } else { //
			 * getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getPortfolio()); } } }
			 */

			if (getMarket() != null) {
				getDao().merge(getMarket());

				/*
				 * try { dbMarket = getDao().find(getMarket().getClass(), getMarket().getId()); if (dbMarket != null && dbMarket.getVersion() !=
				 * getMarket().getVersion()) { getMarket().setVersion(dbMarket.getVersion()); if (getMarket().getRevision() > dbMarket.getRevision()) {
				 * getMarket().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getMarket()); } } else if (dbMarket == null) {
				 * getMarket().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getMarket()); } } catch (Exception | Error ex) { if (dbMarket !=
				 * null) if (getMarket().getRevision() > dbMarket.getRevision()) { getMarket().setPeristanceAction(PersistanceAction.MERGE);
				 * getDao().merge(getMarket()); } else { getMarket().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getMarket()); } }
				 */
			}

			if (getExchange() != null) {
				getDao().merge(getExchange());
				/*
				 * try { dbExchange = getDao().find(getExchange().getClass(), getExchange().getId()); if (dbExchange != null && dbExchange.getVersion() !=
				 * getExchange().getVersion()) { getExchange().setVersion(dbExchange.getVersion()); if (getExchange().getRevision() >
				 * dbExchange.getRevision()) { getExchange().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getExchange()); } } else if
				 * (dbExchange == null) { getExchange().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getExchange()); } } catch (Exception |
				 * Error ex) { if (dbExchange != null) if (getExchange().getRevision() > dbExchange.getRevision()) {
				 * getExchange().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getExchange()); } else {
				 * getExchange().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getExchange()); } }
				 */
			}

		}

	}

	@Override
	public synchronized void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void persitParents() {

	}

}
