package org.cryptocoinpartners.schema;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

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

        this.exchange = fill.getMarket().getExchange();
        this.market = fill.getMarket();
        this.volume = fill.getVolume();
        this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount();
        this.longVolume = volume.isPositive() ? volume : this.longVolume;
        this.longVolumeCount = volume.isPositive() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.longVolumeCount;
        this.shortVolume = volume.isNegative() ? volume : this.shortVolume;
        this.shortVolumeCount = volume.isNegative() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.shortVolumeCount;
        this.longAvgPrice = volume.isPositive() ? fill.getPrice() : this.longAvgPrice;
        this.shortAvgPrice = volume.isNegative() ? fill.getPrice() : this.shortAvgPrice;
        this.shortAvgStopPrice = fill.getStopPrice() != null && fill.isShort() ? fill.getStopPrice() : DecimalAmount.ZERO;
        this.longAvgStopPrice = fill.getStopPrice() != null && fill.isLong() ? fill.getStopPrice() : DecimalAmount.ZERO;
        this.asset = fill.getMarket().getListing().getBase();
        this.portfolio = fill.getPortfolio();
        this.addFill(fill);
    }

    public Position(Collection<Fill> fills) {
        for (Fill fill : fills) {

            this.exchange = fill.getMarket().getExchange();
            this.market = fill.getMarket();
            this.volume = fill.getVolume();
            this.volumeCount = volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount();
            this.longVolume = volume.isPositive() ? volume : this.longVolume;
            this.longVolumeCount = volume.isPositive() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.longVolumeCount;
            this.shortVolume = volume.isNegative() ? volume : this.shortVolume;
            this.shortVolumeCount = volume.isNegative() ? volume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount() : this.shortVolumeCount;
            this.longAvgPrice = volume.isPositive() ? fill.getPrice() : this.longAvgPrice;
            this.shortAvgPrice = volume.isNegative() ? fill.getPrice() : this.shortAvgPrice;
            this.shortAvgStopPrice = fill.getStopPrice() != null && fill.isShort() ? fill.getStopPrice() : DecimalAmount.ZERO;
            this.longAvgStopPrice = fill.getStopPrice() != null && fill.isLong() ? fill.getStopPrice() : DecimalAmount.ZERO;
            this.asset = fill.getMarket().getListing().getBase();
            this.portfolio = fill.getPortfolio();
            //       this.fills.add(fill);
            this.addFill(fill);
        }
    }

    @Transient
    public boolean isOpen() {

        return (getVolume() != null && !getVolume().isZero());
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
        return (getVolume() == null || getVolume().isZero());
    }

    @ManyToOne(optional = true)
    public Market getMarket() {
        return market;
    }

    @ManyToOne(optional = true)
    public Portfolio getPortfolio() {

        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    @Transient
    public Amount getMarginAmount() {

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
        if (longVolume == null)
            longVolume = new DiscreteAmount(getLongVolumeCount(), market.getVolumeBasis());
        return longVolume;

    }

    @Transient
    public Amount getShortVolume() {
        if (market == null)
            return null;
        if (shortVolume == null)
            shortVolume = new DiscreteAmount(getShortVolumeCount(), market.getVolumeBasis());
        return shortVolume;
    }

    @Transient
    public Amount getLongAvgPrice() {
        // if (longAvgPrice == null) {
        Amount longCumVolume = DecimalAmount.ZERO;
        longAvgPrice = DecimalAmount.ZERO;

        for (Fill pos : getFills()) {

            if (pos.isLong()) {
                longAvgPrice = ((longAvgPrice.times(longCumVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getPrice(), Remainder.ROUND_EVEN)))
                        .dividedBy(longCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
                longCumVolume = longCumVolume.plus(pos.getOpenVolume());

            }
        }

        return longAvgPrice;

    }

    @Transient
    public Amount getShortAvgPrice() {
        //   if (shortAvgPrice == null) {
        Amount shortCumVolume = DecimalAmount.ZERO;
        shortAvgPrice = DecimalAmount.ZERO;

        for (Fill pos : getFills()) {

            if (pos.isShort()) {
                shortAvgPrice = ((shortAvgPrice.times(shortCumVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getPrice(),
                        Remainder.ROUND_EVEN))).dividedBy(shortCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
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
        longAvgStopPrice = DecimalAmount.ZERO;

        for (Fill pos : getFills()) {

            if (pos.isLong()) {

                if (pos.getStopPrice() != null)
                    longAvgStopPrice = ((longAvgStopPrice.times(longCumVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getStopPrice(),
                            Remainder.ROUND_EVEN))).dividedBy(longCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);

                longCumVolume = longCumVolume.plus(pos.getOpenVolume());
            }

        }
        //   }

        return longAvgStopPrice;
    }

    protected void setMarket(Market market) {
        this.market = market;
    }

    @Transient
    public Amount getShortAvgStopPrice() {
        //   if (shortAvgStopPrice == null) {
        shortAvgStopPrice = DecimalAmount.ZERO;
        Amount shortCumVolume = DecimalAmount.ZERO;
        // if (shortAvgStopPrice == null)
        for (Fill pos : getFills()) {

            if (pos.isShort()) {

                if (pos.getStopPrice() != null)
                    shortAvgStopPrice = ((shortAvgStopPrice.times(shortCumVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getStopPrice(),
                            Remainder.ROUND_EVEN))).dividedBy(shortCumVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);

                shortCumVolume = shortCumVolume.plus(pos.getOpenVolume());
            }

        }
        //}

        return shortAvgStopPrice;
    }

    /** If the SpecificOrder is not null, then this Position is being held in reserve as payment for that Order */

    @Transient
    protected boolean isReserved() {
        return order != null;
    }

    /**
     * Modifies this Position in-place by the amount of the position argument.
     * @param position a Position to add to this one.
     * @return true iff the positions both have the same Asset and the same Exchange, in which case this Position
     * has modified its volume by the amount in the position argument.
     */
    public boolean merge(Position position) {
        if (exchange == null && position.exchange == null)
            return false;
        if (asset == null && position.asset == null)
            return false;
        if (!exchange.equals(position.exchange) || !asset.equals(position.asset))
            return false;
        // longVolumeCount += position.longVolumeCount;
        //shortVolumeCount += position.shortVolumeCount;
        setLongVolumeCount(position.getLongVolumeCount() + getLongVolumeCount());
        setShortVolumeCount(position.getShortVolumeCount() + getShortVolumeCount());
        return true;
    }

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
    protected long getVolumeCount() {
        if (hasFills())
            for (Fill fill : getFills()) {
                volumeCount += fill.getOpenVolumeCount();
            }

        return volumeCount;
    }

    @Nullable
    protected long getLongVolumeCount() {
        if (hasFills())
            for (Fill fill : getFills()) {
                if (fill.isLong())
                    longVolumeCount += fill.getOpenVolumeCount();
            }

        return longVolumeCount;
    }

    @Nullable
    protected long getShortVolumeCount() {
        if (hasFills())
            for (Fill fill : getFills()) {
                if (fill.isShort())
                    shortVolumeCount += fill.getOpenVolumeCount();
            }

        return shortVolumeCount;
    }

    protected void setLongVolumeCount(long longVolumeCount) {
        reset();
        this.longVolumeCount = longVolumeCount;

    }

    protected void setShortVolumeCount(long shortVolumeCount) {
        reset();
        this.shortVolumeCount = shortVolumeCount;

    }

    protected void setVolumeCount(long volumeCount) {
        reset();
        this.volumeCount = volumeCount;
        this.volume = null;
        if (volumeCount > 0)
            setLongVolumeCount(volumeCount);
        if (volumeCount < 0)
            setShortVolumeCount(volumeCount);

    }

    //  fetch = FetchType.EAGER,
    @Nullable
    @OneToMany(cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    public Collection<Fill> getFills() {
        if (fills == null)
            fills = new ConcurrentLinkedQueue<Fill>();
        return fills;

    }

    public void addFill(Fill fill) {
        synchronized (lock) {
            getFills().add(fill);
        }
        this.exchange = fill.getMarket().getExchange();
        this.market = fill.getMarket();
        this.asset = fill.getMarket().getListing().getBase();
        this.portfolio = fill.getPortfolio();

        reset();

    }

    public void reset() {
        this.volume = null;
        this.longVolume = null;
        this.shortVolume = null;
        this.volumeCount = 0;
        this.longVolumeCount = 0;
        this.shortVolumeCount = 0;
        this.longAvgPrice = null;
        this.shortAvgPrice = null;

        this.longAvgStopPrice = null;
        this.shortAvgStopPrice = null;
    }

    public void removeFill(Fill fill) {
        synchronized (lock) {
            getFills().remove(fill);
        }
        this.exchange = fill.getMarket().getExchange();
        this.market = fill.getMarket();
        this.portfolio = fill.getPortfolio();
        reset();

    }

    @Transient
    public boolean hasFills() {
        return !getFills().isEmpty();
    }

    protected void setFills(Collection<Fill> fills) {
        reset();
        this.fills = fills;

    }

    public void setLongAvgPrice(Amount longAvgPrice) {
        reset();
        this.longAvgPrice = longAvgPrice;

    }

    public void setShortAvgPrice(Amount shortAvgPrice) {
        reset();
        this.shortAvgPrice = shortAvgPrice;

    }

    public void setLongAvgStopPrice(Amount longAvgStopPrice) {
        reset();
        this.longAvgStopPrice = longAvgStopPrice;

    }

    public void setShortAvgStopPrice(Amount shortAvgStopPrice) {
        reset();
        this.shortAvgStopPrice = shortAvgStopPrice;

    }

    private Amount longVolume;
    private Amount shortVolume;
    private Amount volume;
    private Market market;
    private Amount longAvgPrice;
    private Amount shortAvgPrice;
    private Amount longAvgStopPrice;
    private Amount shortAvgStopPrice;
    private Amount marginAmount;
    private long longVolumeCount;
    private long shortVolumeCount;
    private long volumeCount;
    private SpecificOrder order;
    private Collection<Fill> fills;
    private static Object lock = new Object();

}
