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

    public PositionType getLastType() {
        return lastType;
    }

    public void setLastType(PositionType lastType) {
        this.lastType = lastType;
    }

    public PositionUpdate(Position position, Market market, PositionType lastType, PositionType type) {
        this.position = position;
        this.market = market;
        this.type = type;
        this.lastType = lastType;
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

    public void setType(PositionType type) {
        this.type = type;
    }

    private Position position;
    private PositionType type;
    private PositionType lastType;
    private Market market;

}
