package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import javax.persistence.Entity;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.Dao;
import org.joda.time.Instant;

/**
 * Offers represent a bid or ask, usually from a Book.  Asks are represented by using a negative volumeCount
 *
 * @author Tim Olson
 */
@Entity
public class Offer extends PriceData {

    /** same as new Offer() */
    public static Offer bid(Tradeable market, Instant time, Instant timeReceived, Long priceCount, Long volumeCount) {
        return new Offer(market, time, timeReceived, priceCount, volumeCount);
    }

    /** same as new Offer() except the volumeCount is negated */
    public static Offer ask(Tradeable market, Instant time, Instant timeReceived, Long priceCount, Long volumeCount) {
        return new Offer(market, time, timeReceived, priceCount, -volumeCount);
    }

    @Override
    @Transient
    public EntityBase getParent() {

        return null;
    }

    public static Offer bid(Tradeable market, Instant time, Instant timeReceived, BigDecimal price, BigDecimal volume) {
        return new Offer(market, time, timeReceived, price, volume);
    }

    public static Offer ask(Tradeable market, Instant time, Instant timeReceived, BigDecimal price, BigDecimal volume) {
        return new Offer(market, time, timeReceived, price, volume.negate());
    }

    public Offer(Tradeable market, Instant time, Instant timeReceived, Long priceCount, Long volumeCount) {

        super(time, timeReceived, null, market, priceCount, volumeCount);
    }

    public Offer(Tradeable market, Instant time, Instant timeReceived, BigDecimal price, BigDecimal volume) {

        super(time, timeReceived, null, market, price, volume);
    }

    @Override
    public String toString() {
        return "Offer{" + ", market=" + getMarket() + ", priceCount=" + getPriceCount() + ", volumeCount=" + getVolumeCount() + '}';
    }

    @SuppressWarnings("ConstantConditions")
    @Transient
    public Side getSide() {
        return getVolumeCount() >= 0 ? Side.BUY : Side.SELL;
    }

    // JPA
    protected Offer() {
    }

    @Override
    public void persit() {
        // TODO Auto-generated method stub

    }

    @Override
    public void detach() {
        // TODO Auto-generated method stub

    }

    @Override
    public void merge() {
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
    public void setDao(Dao dao) {
        // TODO Auto-generated method stub
        //  return null;
    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

    @Override
    public EntityBase refresh() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void prePersist() {
        // TODO Auto-generated method stub

    }

    @Override
    public void postPersist() {
        // TODO Auto-generated method stub

    }

}
