package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.schema.dao.Dao;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * When Fill change Position, this Event is published
 *
 * @author Tim Olson
 */
@Entity
public class PositionUpdate extends Event {
    protected static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    protected static final String SEPARATOR = ",";

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
    public String toString() {

        return "PositionUpdate{ id=" + getId() + SEPARATOR + "time=" + (getTime() != null ? (FORMAT.print(getTime())) : "") + SEPARATOR + "Type=" + getType()
                + SEPARATOR + "Last Type=" + (getLastType() == null ? "null" : getLastType()) + SEPARATOR + "market={"
                + (getMarket() == null ? "null}" : getMarket() + "}") + SEPARATOR + "position=" + getPosition() + "}";
    }

    @Override
    @Transient
    public Dao getDao() {
        // TODO Auto-generated method stub
        return null;
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

}
