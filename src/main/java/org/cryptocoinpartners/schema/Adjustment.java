package org.cryptocoinpartners.schema;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.Dao;
import org.joda.time.Instant;

/**
 * Adjustments are records of reconciliation where internal records of Positions in an Account do not match external account statements, and therefore
 * the internal accounting is adjusted.
 * 
 * @author Tim Olson
 */
@Entity
public class Adjustment extends EntityBase {

  public Adjustment(Authorization authorization, List<Position> deltas) {
    this.authorization = authorization;
    this.deltas = deltas;
  }

  /**
   * You must call this method to enact the Adjustment and modify the relevant Portfolio Positions. Only the first call to apply() has any effect, and
   * subsequent invocations are ignored.
   */
  /*
   * UNIMPLEMENTED public void apply() { if( timeApplied != null ) return; timeApplied = Instant.now(); try { for( Position delta : deltas ) {
   * delta.getPortfolio().modifyPosition( delta, authorization ); } // todo modify Portfolios' Positions } catch( Throwable e ) { timeApplied = null;
   * throw e; } }
   */

  /** this will be null if apply() has not yet been called */
  public Instant getTimeApplied() {
    return timeApplied;
  }

  @OneToOne(optional = false)
  public Authorization getAuthorization() {
    return authorization;
  }

  @OneToMany
  public List<Position> getDeltas() {
    return deltas;
  }

  // JPA
  protected Adjustment() {
  }

  protected void setAuthorization(Authorization authorization) {
    this.authorization = authorization;
  }

  protected void setDeltas(List<Position> deltas) {
    this.deltas = deltas;
  }

  protected void setTimeApplied(Instant timeApplied) {
    this.timeApplied = timeApplied;
  }

  private Instant timeApplied;
  private Authorization authorization;
  private List<Position> deltas;

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
  @Transient
  public Dao getDao() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  @Transient
  public void setDao(Dao dao) {
    // TODO Auto-generated method stub
    //  return null;
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

  @Override
  public void prePersist() {
    // TODO Auto-generated method stub

  }

  @Override
  public void postPersist() {
    // TODO Auto-generated method stub

  }

  @Override
  @Transient
  public EntityBase getParent() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void persitParents() {
    // TODO Auto-generated method stub

  }

}
