package org.cryptocoinpartners.schema;

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.NoResultException;
import javax.persistence.Transient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents anything valuable and tradable, like currency or stock
 * 
 * @author Tim Olson
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
//@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Cacheable
public abstract class Asset extends EntityBase {

  public static Asset forSymbol(String symbol) {
    // only Currency is supported

    try {
      return Currency.forSymbol(symbol);
    } catch (NoResultException e) {
      return null;
    }

  }

  @Basic(optional = false)
  public String getSymbol() {
    return symbol;
  }

  @Basic(optional = false)
  public double getBasis() {
    return basis;
  }

  @Transient
  public int getScale() {

    int length = (int) (Math.log10(Math.round(1 / basis)));
    return length;
  }

  @Override
  public String toString() {
    return symbol;
  }

  protected Asset(String symbol, double basis) {
    this.symbol = symbol;
    this.basis = basis;
  }

  // JPA
  protected Asset() {
  }

  protected synchronized void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  protected synchronized void setBasis(double basis) {
    this.basis = basis;
  }

  private String symbol;
  private double basis;

  protected static Logger log = LoggerFactory.getLogger(Asset.class);
}
