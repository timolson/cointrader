package org.cryptocoinpartners.schema;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;


/**
 * Many Owners may have Stakes in the Portfolio, but there is only one PortfolioManager, who is not necessarily an Owner.  The
 * Portfolio has multiple Positions.
 *
 * @author Tim Olson
 */
@Entity
public class Portfolio extends EntityBase {


    /** returns all Positions, whether they are tied to an open Order or not.  Use getTradeablePositions() */
    public @OneToMany Collection<Position> getPositions() { return positions; }


    /**
     * Returns all Positions in the Portfolio which are not reserved as payment for an open Order
     */
    @Transient
    public Collection<Position> getTradeablePositions() {
        ArrayList<Position> result = new ArrayList<>();
        for( Position position : positions ) {
            if( !position.isReserved() )
                result.add(position);
        }
        return result;
    }


    /**
     * This is the main way for a Strategy to determine what assets it has available for trading
     */
    @Transient
    public Collection<Position> getReservePositions() {
        ArrayList<Position> result = new ArrayList<>();
        for( Position position : positions ) {
            if( position.isReserved() )
                result.add(position);
        }
        return result;
    }


    /**
     * This is the main way for a Strategy to determine how much of a given asset it has available for trading
     * @param f
     * @return
     */
    @Transient
    public Collection<Position> getTradeablePositionsOf( Asset f ) {
        ArrayList<Position> result = new ArrayList<>();
        for( Position position : positions ) {
            if( position.getAsset().equals(f) && !position.isReserved() )
                result.add(position);
        }
        return result;
    }


    /**
     * Finds a Position in the Portfolio which has the same Asset as p, then breaks it into the amount p requires
     * plus an unreserved amount.  The resevered Position is then associated with the given order, while
     * the unreserved remainder of the Position has getOrder()==null.  To un-reserve the Position, call release(order)
     *
     * @param order the order which will be placed
     * @param p the cost of the order.  could be a different fungible than the order's quote fungible
     * @throws IllegalArgumentException
     */
    public void reserve( SpecificOrder order, Position p )
            throws IllegalArgumentException
    {
        for( Position position : positions ) {
            if( !position.isReserved()
                && position.getAsset().equals(order.getMarket().getQuote())
                && position.getExchange().equals(order.getMarket().getExchange())
              ) {
                if( position.getVolumeCount() < p.getVolumeCount() )
                    throw new IllegalArgumentException("Insufficient funds");
                // subtract the reserve from the existing Position
                position.setVolumeCount(position.getVolumeCount()-p.getVolumeCount());
                // add a new reserve Position
                Position reserve = new Position(p.getExchange(), p.getAsset(), p.getVolume());
                reserve.setOrder(order);
                positions.add(reserve);
            }
        }
    }


    public void release( SpecificOrder order ) {
        Iterator<Position> iterator = positions.iterator();
        while( iterator.hasNext() ) {
            Position position = iterator.next();
            SpecificOrder positionOrder = position.getOrder();
            if( positionOrder != null && positionOrder.equals(order) ) {
                position.setOrder(null);
                if( merge(position) )
                    iterator.remove();
            }
        }
    }


    /**
     * finds other Positions in this portfolio which have the same Exchange and Asset and merges this position's
     * amount into the found position's amount, thus maintaining only one Position for each Exchange/Asset pair.
     * this method does not remove the position from the positions list.
     * @return true iff another position was found and merged
     */
    private boolean merge(Position position) {
        for( Position p : positions ) {
            if( p.getExchange().equals(position.getExchange()) && p.getAsset().equals(position.getAsset()) ) {
                p.setVolumeCount(p.getVolumeCount()+position.getVolumeCount());
                return true;
            }
        }
        return false;
    }


    public Portfolio(String name, PortfolioManager manager) {
        this.name = name;
        this.manager = manager;
    }


    public String getName() { return name; }


    @OneToMany
    public Collection<Stake> getStakes() { return stakes; }


    @ManyToOne(optional = false)
    public PortfolioManager getManager() { return manager; }


    /**
     * Adds the given position to this Portfolio.  Must be authorized.
     * @param position
     * @param authorization
     */
    public void modifyPosition( Position position, Authorization authorization ) {
        assert authorization != null;
        assert position != null;
        boolean modifiedExistingPosition = false;
        for( Position curPosition : positions ) {
            if( curPosition.merge(position) ) {
                modifiedExistingPosition = true;
                break;
            }
        }
        if( !modifiedExistingPosition )
            positions.add(position);
    }


    // JPA
    protected Portfolio() {}
    protected void setPositions(Collection<Position> positions) { this.positions = positions; }
    protected void setName(String name) { this.name = name; }
    protected void setStakes(Collection<Stake> stakes) { this.stakes = stakes; }
    protected void setManager(PortfolioManager manager) { this.manager = manager; }


    private String name;
    private PortfolioManager manager;
    private Collection<Position> positions = Collections.emptyList();
    private Collection<Stake> stakes = Collections.emptyList();
}