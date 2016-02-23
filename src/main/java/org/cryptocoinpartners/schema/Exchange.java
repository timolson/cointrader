package org.cryptocoinpartners.schema;

import java.util.List;

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.ExchangeJpaDao;
import org.cryptocoinpartners.util.EM;

import com.google.inject.Inject;

/**
 * @author Tim Olson
 */
@Entity
@Cacheable
public class Exchange extends EntityBase {

    /**
     * 
     */
    // @Inject
    //protected static ExchangeJpaDao exchangeDao;

    @Inject
    protected static ExchangeJpaDao exchangeDao;

    public static Exchange forSymbolOrCreate(String symbol) {
        Exchange found = forSymbol(symbol);
        if (found == null) {
            found = new Exchange(symbol);
            exchangeDao.persistEntities(found);
        }
        return found;
    }

    public static Exchange forSymbolOrCreate(String symbol, int margin, double feeRate, FeeMethod feeMethod, boolean fillsProvided) {
        Exchange found = forSymbol(symbol);
        if (found == null) {
            found = new Exchange(symbol, margin, feeRate, feeMethod, fillsProvided);
            exchangeDao.persistEntities(found);

        }
        return found;
    }

    public static Exchange forSymbolOrCreate(String symbol, int margin, double feeRate, FeeMethod feeMethod, double marginFeeRate, FeeMethod marginFeeMethod,
            boolean fillsProvided) {
        Exchange found = forSymbol(symbol);
        if (found == null) {
            found = new Exchange(symbol, margin, feeRate, feeMethod, marginFeeRate, marginFeeMethod, fillsProvided);
            exchangeDao.persistEntities(found);
        }
        return found;
    }

    /** returns null if the symbol does not represent an existing exchange */
    public static Exchange forSymbol(String symbol) {
        return EM.queryZeroOne(Exchange.class, "select e from Exchange e where symbol=?1", symbol);
    }

    public static List<String> allSymbols() {
        return EM.queryList(String.class, "select symbol from Exchange");
    }

    @Basic(optional = false)
    public String getSymbol() {
        return symbol;
    }

    @Basic(optional = false)
    public double getFeeRate() {
        return feeRate;
    }

    protected void setFeeRate(double feeRate) {
        this.feeRate = feeRate;
    }

    @Basic(optional = true)
    public double getMarginFeeRate() {
        return marginFeeRate;
    }

    protected void setMarginFeeRate(double marginFeeRate) {
        this.marginFeeRate = marginFeeRate;
    }

    @ManyToOne(optional = false)
    private FeeMethod feeMethod;

    public FeeMethod getFeeMethod() {
        return feeMethod;
    }

    protected void setFeeMethod(FeeMethod feeMethod) {
        this.feeMethod = feeMethod;
    }

    @ManyToOne(optional = true)
    private FeeMethod marginFeeMethod;

    public FeeMethod getMarginFeeMethod() {
        return marginFeeMethod;
    }

    protected void setMarginFeeMethod(FeeMethod marginFeeMethod) {
        this.marginFeeMethod = marginFeeMethod;
    }

    @Basic(optional = true)
    public boolean getFillsProvided() {
        return fillsProvided;

    }

    protected void setFillsProvided(boolean fillsProvided) {
        this.fillsProvided = fillsProvided;
    }

    @Basic(optional = false)
    public int getMargin() {
        return Math.max(margin, 1);

    }

    protected void setMargin(int margin) {
        this.margin = margin;
    }

    @Override
    public String toString() {
        return symbol;
    }

    // JPA
    protected Exchange() {
    }

    protected void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    private Exchange(String symbol, int margin, double feeRate, FeeMethod feeMethod, boolean fillsProvided) {
        this.symbol = symbol;
        this.margin = margin;
        this.feeRate = feeRate;
        this.feeMethod = feeMethod;
        this.fillsProvided = fillsProvided;

    }

    private Exchange(String symbol, int margin, double feeRate, FeeMethod feeMethod, double marginFeeRate, FeeMethod marginFeeMethod, boolean fillsProvided) {
        this.symbol = symbol;
        this.margin = margin;
        this.feeRate = feeRate;
        this.feeMethod = feeMethod;
        this.marginFeeMethod = marginFeeMethod;
        this.marginFeeRate = marginFeeRate;
        this.fillsProvided = fillsProvided;
    }

    private Exchange(String symbol) {
        this.symbol = symbol;

    }

    private String symbol;
    private int margin;
    private double feeRate;
    private double marginFeeRate;
    private boolean fillsProvided;

    @Override
    public void persit() {
        exchangeDao.persist(this);
        // TODO Auto-generated method stub

    }

    @Override
    public EntityBase refresh() {
        return exchangeDao.refresh(this);
    }

    @Override
    public void detach() {
        exchangeDao.detach(this);
        // TODO Auto-generated method stub

    }

    @Override
    public void merge() {
        exchangeDao.merge(this);
        // TODO Auto-generated method stub

    }

    @Override
    @Transient
    public Dao getDao() {
        return exchangeDao;
    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

}
