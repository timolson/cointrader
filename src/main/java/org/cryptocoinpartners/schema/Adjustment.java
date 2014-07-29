package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.util.List;


/**
 * Adjustments are records of reconciliation where internal records of Positions in an Account do not match external
 * account statements, and therefore the internal accounting is adjusted.
 *
 * @author Tim Olson
 */
@Entity
public class Adjustment extends EntityBase {


    public Adjustment(Authorization authorization,
                      List<Position> deltas) {
        this.authorization = authorization;
        this.deltas = deltas;
    }


    /** You must call this method to enact the Adjustment and modify the relevant Portfolio Positions.  Only the first
     * call to apply() has any effect, and subsequent invocations are ignored.
     */
    /* UNIMPLEMENTED
    public void apply() {
        if( timeApplied != null )
            return;
        timeApplied = Instant.now();

        try {
            for( Position delta : deltas ) {
                delta.getPortfolio().modifyPosition( delta, authorization );
            }
            // todo modify Portfolios' Positions
        }
        catch( Throwable e ) {
            timeApplied = null;
            throw e;
        }
    }
    */


    /** this will be null if apply() has not yet been called */
    public Instant getTimeApplied() { return timeApplied; }


    @OneToOne(optional = false)
    public Authorization getAuthorization() { return authorization; }


    @OneToMany
    public List<Position> getDeltas() { return deltas; }


    // JPA
    protected Adjustment() { }
    protected void setAuthorization(Authorization authorization) { this.authorization = authorization; }
    protected void setDeltas(List<Position> deltas) { this.deltas = deltas; }
    protected void setTimeApplied(Instant timeApplied) { this.timeApplied = timeApplied; }


    private Instant timeApplied;
    private Authorization authorization;
    private List<Position> deltas;
}
