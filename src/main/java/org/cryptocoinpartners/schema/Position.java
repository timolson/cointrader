package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;


/**
 * A Position represents an withAmount of some Fungible within an Account.  The Position is owned by a Fund
 *
 * @author Tim Olson
 */
@Entity
public class Position extends EntityBase {


    // todo is Account one-to-one with Exchange?  Should we pass in the Account here instead?
    public Position(Fund fund, Exchange exchange, Fungible fungible, DiscreteAmount volume) {
        if( volume.getBasis() != fungible.getBasis() )
            throw new IllegalArgumentException("Basis for volume must match basis for Fungible");
        this.fund = fund;
        this.exchange = exchange;
        this.volumeCount = volume.getCount();
        this.fungible = fungible;
    }


    @ManyToOne(optional = false) public Fund getFund() { return fund; }
    @ManyToOne(optional = false) public Exchange getExchange() { return exchange; }
    @Transient public DiscreteAmount getVolume() {
        if( volume == null )
            volume = new DiscreteAmount(volumeCount,fungible.getBasis());
        return volume;
    }
    @OneToOne(optional = false) public Fungible getFungible() { return fungible; }


    // JPA
    protected Position() { }
    protected void setFund(Fund fund) { this.fund = fund; }
    protected void setExchange(Exchange exchange) { this.exchange = exchange; }
    protected void setFungible(Fungible fungible) { this.fungible = fungible; }
    protected long getVolumeCount() { return volume.getCount(); }
    protected void setVolumeCount(long volumeCount) { this.volumeCount = volumeCount; this.volume = null; }


    private Fund fund;
    private Exchange exchange;
    private DiscreteAmount volume;
    private long volumeCount;
    private Fungible fungible;
}
