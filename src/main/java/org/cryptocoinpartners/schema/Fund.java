package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.Collection;


/**
 * Many Owners may have Stakes in the Fund, but there is only one FundManager, who is not necessarily an Owner.  The
 * Fund has multiple Positions.
 *
 * @author Tim Olson
 */
@Entity
public class Fund extends BaseEntity {


    public @OneToMany Collection<Position> getPositions() { return positions; }


    public Fund(String name) { this.name = name; }


    public String getName() { return name; }


    @OneToMany
    public Collection<Stake> getStakes() { return stakes; }


    @ManyToOne
    public FundManager getManager() { return manager; }


    // JPA
    protected Fund() {}
    protected void setPositions(Collection<Position> positions) { this.positions = positions; }
    protected void setName(String name) { this.name = name; }
    protected void setStakes(Collection<Stake> stakes) { this.stakes = stakes; }
    protected void setManager(FundManager manager) { this.manager = manager; }


    private String name;
    private FundManager manager;
    private Collection<Position> positions;
    private Collection<Stake> stakes;
}