package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.math.BigDecimal;


/**
 * A GeneralOrder only specifies a Listing but not an Exchange.  The GeneralOrder must be processed and broken down into
 * a series of SpecificOrders before it can be placed on Markets.  GeneralOrders express their volumes and prices using
 * BigDecimal, since the trading basis at each Exchange may be different, thus a DiscreteAmount cannot be used.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
public class GeneralOrder extends Order {

    public GeneralOrder(Listing listing, BigDecimal volume)
    {
        this.listing = listing;
        this.volume = DecimalAmount.of(volume);
    }


    public GeneralOrder(Listing listing, String volume)
    {
        this.listing = listing;
        this.volume = DecimalAmount.of(volume);
    }


    @ManyToOne(optional = false)
    public Listing getListing() { return listing; }


    @Embedded
    @AttributeOverride(name = "bd", column = @Column(name = "volume"))
    public DecimalAmount getVolume() { return volume; }


    @Nullable @Embedded
    @AttributeOverride(name = "bd", column = @Column(name = "limitPrice"))
    public DecimalAmount getLimitPrice() { return limitPrice; }


    @Nullable @Embedded
    @AttributeOverride(name = "bd", column = @Column(name = "stopPrice"))
    public DecimalAmount getStopPrice() { return stopPrice; }


    @Transient BigDecimal getUnfilledVolume() {
        BigDecimal filled = BigDecimal.ZERO;
        for( Fill fill : getFills() )
            filled = filled.add(fill.getVolume().asBigDecimal());
        return filled;
    }


    @Transient
    public boolean isFilled() {
        return getUnfilledVolume().equals(BigDecimal.ZERO);
    }


    @Transient
    public boolean isBid() { return !volume.isNegative(); }


    protected GeneralOrder() { }
    protected void setVolume(DecimalAmount volume) { this.volume = volume; }
    protected void setLimitPrice(DecimalAmount limitPrice) { this.limitPrice = limitPrice; }
    protected void setStopPrice(DecimalAmount stopPrice) { this.stopPrice = stopPrice; }
    protected void setListing(Listing listing) { this.listing = listing; }


    private Listing listing;
    private DecimalAmount volume;
    private DecimalAmount limitPrice;
    private DecimalAmount stopPrice;
}
