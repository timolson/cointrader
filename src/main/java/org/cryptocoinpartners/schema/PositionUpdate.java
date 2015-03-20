package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import org.cryptocoinpartners.enumeration.PositionType;

/**
 * When Fill change Position, this Event is published
 *
 * @author Tim Olson
 */
@Entity
public class PositionUpdate extends Event {

    @ManyToOne
    public Position getPosition() {
        return position;
    }

    @ManyToOne
    public Market getMarket() {
        return market;
    }

    public PositionType getType() {
        return type;
    }

    public PositionUpdate(Position position, Market market, PositionType type) {
        this.position = position;
        this.market = market;
        this.type = type;
    }

    // JPA
    protected PositionUpdate() {
    }

    protected void setPosition(Position position) {
        this.position = position;
    }

    protected void setMarket(Market market) {
        this.market = market;
    }

    protected void setType(PositionType type) {
        this.type = type;
    }

    private Position position;
    private PositionType type;
    private Market market;

}
