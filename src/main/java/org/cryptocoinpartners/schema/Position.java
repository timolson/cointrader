package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.util.Remainder;

import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;


/**
 * A Position represents an amount of some Fungible within an Exchange.  If the Position is related to an Order
 * then the Position is being held in reserve (not tradeable) to cover the costs of the open Order.
 *
 * @author Tim Olson
 */
@Entity
public class Position extends EntityBase {


    public Position(Exchange exchange, Fungible fungible, Amount volume) {
        volume.assertBasis(fungible.getBasis());
        this.exchange = exchange;
        this.volumeCount = volume.toBasis(fungible.getBasis(), Remainder.TO_HOUSE).getCount();
        this.fungible = fungible;
    }


    @ManyToOne(optional = false)
    public Exchange getExchange() { return exchange; }


    @OneToOne(optional = false) public Fungible getFungible() { return fungible; }


    @Transient
    public Amount getVolume() {
        if( volume == null )
            volume = new DiscreteAmount(volumeCount,fungible.getBasis());
        return volume;
    }


    /** If the SpecificOrder is not null, then this Position is being held in reserve as payment for that Order */
    @OneToOne
    @Nullable
    public SpecificOrder getOrder() { return order; }


    @Transient
    public boolean isReserved() { return order != null; }


    /**
     * Modifies this Position in-place by the amount of the position argument.
     * @param position a Position to add to this one.
     * @return true iff the positions both have the same Fungible and the same Exchange, in which case this Position
     * has modified its volume by the amount in the position argument.
     */
    public boolean merge(Position position) {
        if( !exchange.equals(position.exchange) || !fungible.equals(position.fungible) )
            return false;
        volumeCount += position.volumeCount;
        return true;
    }


    // JPA
    protected Position() { }
    protected void setExchange(Exchange exchange) { this.exchange = exchange; }
    protected void setFungible(Fungible fungible) { this.fungible = fungible; }
    protected long getVolumeCount() { return volumeCount; }
    protected void setVolumeCount(long volumeCount) { this.volumeCount = volumeCount; this.volume = null; }
    protected void setOrder(SpecificOrder order) { this.order = order; }


    private Exchange exchange;
    private Amount volume;
    private long volumeCount;
    private Fungible fungible;
    private SpecificOrder order;
}
