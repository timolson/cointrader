package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.MappedSuperclass;


/**
 * @author Tim Olson
 */
@MappedSuperclass
public class Quote extends PriceData {

    public Quote(Side side, Market market, Instant time, String remoteKey,
                 Long priceCount, Long volumeCount) {
        super(time, remoteKey, market, priceCount, volumeCount);
        this.side = side;
    }


    @Enumerated(EnumType.STRING)
    public Side getSide() {
        return side;
    }


    // JPA
    protected Quote() {}
    protected void setSide(Side side) { this.side = side; }


    private Side side;
}
