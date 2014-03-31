package com.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.Collection;


/**
 * Many Owners may have shares in the Fund.  The Fund has multiple Positions.
 *
 * @author Tim Olson
 */
@Entity
public class Fund extends EntityBase {

    public @OneToMany Collection<Position> getPositions() { return positions; }


    public Fund(String name) { this.name = name; }


    public String getName() { return name; }


    @OneToMany
    public Collection<Stake> getStakes() { return stakes; }


    // JPA
    protected Fund() {}
    protected void setPositions(Collection<Position> positions) { this.positions = positions; }
    protected void setName(String name) { this.name = name; }
    protected void setStakes(Collection<Stake> stakes) { this.stakes = stakes; }


    private String name;
    private Collection<Position> positions;
    private Collection<Stake> stakes;
}
