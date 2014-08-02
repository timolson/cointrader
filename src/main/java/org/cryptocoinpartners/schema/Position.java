package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.util.Remainder;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;


/**
 * A Position represents an amount of some Asset within an Exchange.  If the Position is related to an Order
 * then the Position is being held in reserve (not tradeable) to cover the costs of the open Order.
 *
 * @author Tim Olson
 */
@Entity
public class Position extends Holding {


    public Position(Exchange exchange, Asset asset, Amount volume, Amount price ) {
        //volume.assertBasis(asset.getBasis());
        this.exchange = exchange;
        this.volumeCount = volume.toBasis(asset.getBasis(), Remainder.TO_HOUSE).getCount();
        this.priceCount= price.toBasis(asset.getBasis(), Remainder.TO_HOUSE).getCount();
        this.asset = asset;
    }


    @Transient
    protected Amount getVolume() {
        if( volume == null )
            volume = new DiscreteAmount(volumeCount, asset.getBasis());
        return volume;
    }

    @Transient
    protected Amount getPrice() {
        if( price == null )
            price = new DiscreteAmount(priceCount, asset.getBasis());
        return price;
    }

    /** If the SpecificOrder is not null, then this Position is being held in reserve as payment for that Order */
    @OneToOne
    @Nullable
    protected SpecificOrder getOrder() { return order; }


    @Transient
    protected boolean isReserved() { return order != null; }


    /**
     * Modifies this Position in-place by the amount of the position argument.
     * @param position a Position to add to this one.
     * @return true iff the positions both have the same Asset and the same Exchange, in which case this Position
     * has modified its volume by the amount in the position argument.
     */
    protected boolean merge(Position position) {
        if( !exchange.equals(position.exchange) || !asset.equals(position.asset)  )
            return false;
        volumeCount += position.volumeCount;
        return true;
    }

    public String toString() {
        return "Position=[Exchange=" + exchange + ", qty=" + volumeCount
                        + ", price="  + priceCount
                        + ", entyDate=" 
                        + ", instrument=" + asset + "]";
}

    // JPA
    protected Position() { }


    protected long getVolumeCount() { return volumeCount; }
    protected void setVolumeCount(long volumeCount) { this.volumeCount = volumeCount; this.volume = null; }
    protected void setOrder(SpecificOrder order) { this.order = order; }


    private Amount volume;
    private Amount price;
    private long volumeCount;
    private long priceCount;
    private SpecificOrder order;
}
