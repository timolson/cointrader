package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.Dao;

/**
 * Panic is a class which records disaster conditions. Subclasses provide additional information about specific situations so that the engine can make
 * reasonable reactions to adverse conditions.
 * 
 * @author Tim Olson todo create Panic subtypes
 */
public class Panic extends Event {

  /** returns true iff there is any active Panic */
  public static boolean panicking() {
    return !panics.isEmpty();
  }

  public static List<Panic> getActivePanics() {
    return panics;
  }

  @Override
  @Transient
  public EntityBase getParent() {

    return null;
  }

  private static List<Panic> panics = new ArrayList<>();

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
  public void persitParents() {
    // TODO Auto-generated method stub

  }

}
