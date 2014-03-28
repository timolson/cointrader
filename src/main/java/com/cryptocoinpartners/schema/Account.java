package com.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.Collection;


/**
 * Relates an Owner to Positions
 * @author Tim Olson
 */
@Entity
public class Account extends EntityBase {
    // todo an Account is both a Listing and an Owner?
    public @OneToMany Collection<Position> getPositions() { return positions; }


    protected void setPositions(Collection<Position> positions) { this.positions = positions; }


    private Collection<Position> positions;
}
