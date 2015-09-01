package org.cryptocoinpartners.schema;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;

import jline.internal.Log;

import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * A Position represents an amount of some Asset within an Exchange.  If the Position is related to an Order
 * then the Position is being held in reserve (not tradeable) to cover the costs of the open Order.
 *
 * @author Tim Olson
 */
@Entity
@Cacheable
public class Position extends Holding {

    private Portfolio portfolio;
    protected static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    // private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
    protected static final String SEPARATOR = ",";

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

    public Position(Fill fill) {

        this.addFill(fill);
        fill.setPosition(this);

        this.exchange = fill.getMarket().getExchange();
        this.market = fill.getMarket();
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
        this.portfolio = fill.getPortfolio();
        //   
        fill.getPortfolio().addPosition(this);

    }

    public Position(List<Fill> fills) {
        if (!fills.isEmpty()) {
            this.exchange = fills.get(0).getMarket().getExchange();
            this.market = fills.get(0).getMarket();
            this.asset = fills.get(0).getMarket().getListing().getBase();
            this.portfolio = fills.get(0).getPortfolio();
            // this.id = getId();
            this.setFills(fills);
        }

    }

    @Transient
    public boolean isOpen() {

        return ((getLongVolume() != null && !getLongVolume().isZero()) || (getShortVolume() != null && !getShortVolume().isZero()));
    }

    @Transient
    public boolean isLong() {

        return (getVolume() != null && getVolume().isPositive());
    }

    @Transient
    public boolean isShort() {

        return (getVolume() != null && getVolume().isNegative());
    }

    @Transient
    public boolean isFlat() {
        //if (getVolume()!=null)
        return ((getLongVolume() == null && getLongVolume() == null) || (getLongVolume().isZero() && getShortVolume().isZero()));
    }

    @ManyToOne(optional = false)
    public Market getMarket() {
        return market;
    }

    public @ManyToOne
    // @JoinColumn(name = "portfolio")
    Portfolio getPortfolio() {

        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

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

        return (getVolume().isNegative()) ? getShortAvgPrice() : getLongAvgPrice();
        // return ((getLongAvgPrice().times(getLongVolume(), Remainder.ROUND_EVEN)).plus(getShortAvgPrice().times(getShortVolume(), Remainder.ROUND_EVEN)))
        //       .dividedBy(getLongVolume().plus(getShortVolume()), Remainder.ROUND_EVEN);

    }

    @Transient
    public Amount getAvgStopPrice() {

        //  if (volume == null)
        //  volume = new DiscreteAmount(volumeCount, market.getVolumeBasis());
        //return volume;
        return (getVolume().isNegative()) ? getShortAvgStopPrice() : getLongAvgStopPrice();

    }

    @Transient
    public Amount getLongVolume() {
        if (market == null)
            return null;
        // if (longVolume == null)
        Amount longVolume = new DiscreteAmount(getLongVolumeCount(), market.getVolumeBasis());
        return longVolume;

    }

    @Transient
    public Amount getShortVolume() {
        if (market == null)
            return null;
        //  if (shortVolume == null)
        Amount shortVolume = new DiscreteAmount(getShortVolumeCount(), market.getVolumeBasis());
        return shortVolume;
    }

    @Transient
    public Amount getLongAvgPrice() {
        // if (longAvgPrice == null) {
        Amount longCumVolume = DecimalAmount.ZERO;
        Amount longAvgPrice = DecimalAmount.ZERO;
        Iterator<Fill> itf = getFills().iterator();
        while (itf.hasNext()) {
            //  for (Fill pos : getFills()) {
            Fill pos = itf.next();
            if (pos.isLong()) {
                longAvgPrice = longAvgPrice == null ? DecimalAmount.ZERO : ((longAvgPrice.times(longCumVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume()
                        .times(pos.getPrice(), Remainder.ROUND_EVEN))).dividedBy(longCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
                longCumVolume = longCumVolume.plus(pos.getOpenVolume());

            }
        }

        return longAvgPrice;

    }

    @Transient
    public Amount getShortAvgPrice() {
        //   if (shortAvgPrice == null) {
        Amount shortCumVolume = DecimalAmount.ZERO;
        Amount shortAvgPrice = DecimalAmount.ZERO;

        Iterator<Fill> itf = getFills().iterator();
        while (itf.hasNext()) {
            //  for (Fill pos : getFills()) {
            Fill pos = itf.next();

            if (pos.isShort()) {
                shortAvgPrice = shortAvgPrice == null ? DecimalAmount.ZERO : ((shortAvgPrice.times(shortCumVolume, Remainder.ROUND_EVEN)).plus(pos
                        .getOpenVolume().times(pos.getPrice(), Remainder.ROUND_EVEN)))
                        .dividedBy(shortCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
                shortCumVolume = shortCumVolume.plus(pos.getOpenVolume());

            }
        }
        // }

        return shortAvgPrice;

    }

    @Transient
    public Amount getLongAvgStopPrice() {
        //  if (longAvgStopPrice == null) {
        Amount longCumVolume = DecimalAmount.ZERO;
        Amount longAvgStopPrice = DecimalAmount.ZERO;

        Iterator<Fill> itf = getFills().iterator();
        while (itf.hasNext()) {
            //  for (Fill pos : getFills()) {
            Fill pos = itf.next();
            if (pos.isLong()) {
                Amount parentStopPrice = null;
                if (pos.getOrder() != null && pos.getOrder().getParentOrder() != null && pos.getOrder().getParentOrder().getStopAmount() != null)

                    parentStopPrice = pos.getPrice().minus(pos.getOrder().getParentOrder().getStopAmount());
                //  Amount stopPrice = (pos.getStopPrice() == null) ? parentStopPrice : pos.getStopPrice();
                Amount stopPrice = pos.getStopPrice();
                if (stopPrice == null)
                    continue;
                if (stopPrice != null || stopPrice.compareTo(pos.getPrice()) < 0)
                    longAvgStopPrice = ((longAvgStopPrice.times(longCumVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(stopPrice,
                            Remainder.ROUND_EVEN))).dividedBy(longCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);

                longCumVolume = longCumVolume.plus(pos.getOpenVolume());
            }

        }
        //   }
        if (longAvgStopPrice == DecimalAmount.ZERO)
            Log.info("sero stops");
        return longAvgStopPrice;
    }

    protected void setMarket(Market market) {
        this.market = market;
    }

    @Transient
    public Amount getShortAvgStopPrice() {
        //   if (shortAvgStopPrice == null) {
        Amount shortAvgStopPrice = DecimalAmount.ZERO;
        Amount shortCumVolume = DecimalAmount.ZERO;
        // if (shortAvgStopPrice == null)
        Iterator<Fill> itf = getFills().iterator();
        while (itf.hasNext()) {
            //  for (Fill pos : getFills()) {
            Fill pos = itf.next();

            if (pos.isShort()) {
                Amount parentStopPrice = null;
                if (pos.getOrder() != null && pos.getOrder().getParentOrder() != null && pos.getOrder().getParentOrder().getStopAmount() != null)

                    parentStopPrice = pos.getPrice().plus(pos.getOrder().getParentOrder().getStopAmount());
                // Amount stopPrice = (pos.getStopPrice() == null) ? parentStopPrice : pos.getStopPrice();
                Amount stopPrice = pos.getStopPrice();
                if (stopPrice == null)
                    continue;
                if (stopPrice != null || stopPrice.compareTo(pos.getPrice()) > 0)

                    shortAvgStopPrice = ((shortAvgStopPrice.times(shortCumVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(stopPrice,
                            Remainder.ROUND_EVEN))).dividedBy(shortCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);

                shortCumVolume = shortCumVolume.plus(pos.getOpenVolume());
            }

        }
        //}

        return shortAvgStopPrice;
    }

    /** If the SpecificOrder is not null, then this Position is being held in reserve as payment for that Order */

    /**
     * Modifies this Position in-place by the amount of the position argument.
     * @param position a Position to add to this one.
     * @return true iff the positions both have the same Asset and the same Exchange, in which case this Position
     * has modified its volume by the amount in the position argument.
     */

    @Override
    public String toString() {
        return "Position=[Exchange=" + exchange + (getShortVolume() != null ? (SEPARATOR + ", Short Qty=" + getShortVolume()) : "")
                + (getShortAvgPrice() != null ? (SEPARATOR + ", Short Avg Price=" + getShortAvgPrice()) : "")
                + (getShortAvgStopPrice() != null ? (SEPARATOR + ", Short Avg Stop Price=" + getShortAvgStopPrice()) : "")
                + (getLongVolume() != null ? (SEPARATOR + "Long Qty=" + getLongVolume()) : "")
                + (getLongAvgPrice() != null ? (SEPARATOR + "Long Avg Price=" + getLongAvgPrice()) : "")
                + (getLongAvgStopPrice() != null ? (SEPARATOR + "Long Avg Stop Price=" + getLongAvgStopPrice()) : "") + ", Net Qty=" + getVolume().toString()
                + " Vol Count=" + getVolumeCount() + ",  Entry Date=" + ", Instrument=" + asset;
    }

    // JPA
    public Position() {
    }

    @Nullable
    @Transient
    protected long getVolumeCount() {
        long volumeCount = 0;
        //    reset();
        if (hasFills()) {
            Iterator<Fill> itf = getFills().iterator();
            while (itf.hasNext()) {
                //  for (Fill pos : getFills()) {
                Fill fill = itf.next();

                volumeCount += fill.getOpenVolumeCount();
            }
        }

        return volumeCount;
    }

    @Nullable
    @Transient
    protected long getLongVolumeCount() {
        //  reset();
        Iterator<Fill> itf = getFills().iterator();
        long longVolumeCount = 0;
        while (itf.hasNext()) {
            //  for (Fill pos : getFills()) {
            Fill fill = itf.next();
            if (fill.getPositionEffect() != null
                    && ((fill.getPositionEffect() == PositionEffect.OPEN && fill.isLong()) || (fill.getPositionEffect() == PositionEffect.CLOSE && fill
                            .isShort()))) {
                longVolumeCount += fill.getOpenVolumeCount();
            } else if ((fill.getPositionEffect() == null || fill.getPositionEffect() == PositionEffect.OPEN) && fill.isLong())
                longVolumeCount += fill.getOpenVolumeCount();
        }

        return longVolumeCount;
    }

    @Nullable
    @Transient
    protected long getShortVolumeCount() {
        long shortVolumeCount = 0;
        //  reset();
        if (hasFills()) {
            Iterator<Fill> itf = getFills().iterator();
            while (itf.hasNext()) {
                //  for (Fill pos : getFills()) {
                Fill fill = itf.next();
                // if it is entering & short or exiting and long
                if (fill.getPositionEffect() != null
                        && ((fill.getPositionEffect() == PositionEffect.OPEN && fill.isShort()) || (fill.getPositionEffect() == PositionEffect.CLOSE && fill
                                .isLong()))) {
                    shortVolumeCount += fill.getOpenVolumeCount();

                } else if ((fill.getPositionEffect() == null || fill.getPositionEffect() == PositionEffect.OPEN) && fill.isShort())
                    shortVolumeCount += fill.getOpenVolumeCount();
            }
        }
        return shortVolumeCount;
    }

    //  fetch = FetchType.EAGER,

    @OneToMany(mappedBy = "position")
    @OrderBy
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    public List<Fill> getFills() {
        if (fills == null)
            fills = new CopyOnWriteArrayList<Fill>();
        return fills;

    }

    protected void Persit() {
        //   synchronized (persistanceLock) {
        //   List<Position> duplicate = PersistUtil.queryList(Position.class, "select p from Position p where p=?1", this);

        // if (this.hasFills()) {
        //   for (Fill fill : this.getFills())

        //     PersistUtil.merge(fill);
        // }

        //   if (duplicate == null || duplicate.isEmpty())
        PersistUtil.insert(this);
        // else
        //   PersistUtil.merge(this);
        //  }
        // if (hasFills()) {
        //   Iterator<Fill> itf = getFills().iterator();
        //  while (itf.hasNext()) {
        //  for (Fill pos : getFills()) {
        //    Fill fill = itf.next();

        //if (fill.getPosition() == this)
        //  fill.persit();
        //PersistUtil.merge(fill);
        // }
        // }

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

    public void addFill(Fill fill) {
        //   synchronized (lock) {
        getFills().add(fill);
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

    public void removeFill(Fill fill) {
        //  synchronized (lock) {
        fill.setPosition(null);

        getFills().remove(fill);

        //}
        //  fill.persit();
        //PersistUtil.merge(fill);
        //        this.exchange = fill.getMarket().getExchange();
        //        this.market = fill.getMarket();
        //        this.portfolio = fill.getPortfolio();
        //reset();

    }

    @Transient
    public boolean hasFills() {
        return !getFills().isEmpty();
    }

    protected void setFills(List<Fill> fills) {
        // reset();
        this.fills = fills;

    }

    // private Amount longVolume = DecimalAmount.ZERO;
    //private Amount shortVolume = DecimalAmount.ZERO;
    //private Amount volume = DecimalAmount.ZERO;
    private Market market;
    //private Amount longAvgPrice = DecimalAmount.ZERO;
    //private Amount shortAvgPrice = DecimalAmount.ZERO;
    //private Amount longAvgStopPrice = DecimalAmount.ZERO;
    //private Amount shortAvgStopPrice = DecimalAmount.ZERO;
    //private final Amount marginAmount = DecimalAmount.ZERO;
    //private long longVolumeCount;
    //private long shortVolumeCount;
    //private long volumeCount;
    //private SpecificOrder order;
    private List<Fill> fills = new CopyOnWriteArrayList<Fill>();
    private static Object lock = new Object();
    private static Object persistanceLock = new Object();

}
