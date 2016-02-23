package org.cryptocoinpartners.schema;

import java.util.List;

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NoResultException;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.PromptJpaDao;
import org.cryptocoinpartners.util.EM;

import com.google.inject.Inject;

/**
 * @author Tim Olson
 */
@Entity
@Cacheable
public class Prompt extends EntityBase {
    @Inject
    protected static PromptJpaDao promptDao;

    public static Prompt forSymbol(String symbol) {
        return EM.queryOne(Prompt.class, "select c from Prompt c where symbol=?1", symbol);
    }

    public static List<String> allSymbols() {
        return EM.queryList(String.class, "select symbol from Prompt");
    }

    // JPA
    protected Prompt() {
    }

    @Transient
    public Double getMultiplier() {
        return this.contractSize * this.tickSize;
    }

    protected void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Basic(optional = false)
    public String getSymbol() {
        return this.symbol;
    }

    protected void setTickValue(double tickValue) {
        this.tickValue = tickValue;
    }

    @Basic(optional = false)
    public double getTickValue() {
        return this.tickValue;
    }

    protected void setTickSize(double tickSize) {
        this.tickSize = tickSize;
    }

    @Basic(optional = false)
    public double getTickSize() {
        return this.tickSize;
    }

    protected void setContractSize(double contractSize) {
        this.contractSize = contractSize;
    }

    @Basic(optional = true)
    public double getContractSize() {
        return this.contractSize;
    }

    protected void setPriceBasis(double priceBasis) {
        this.priceBasis = priceBasis;
    }

    @Basic(optional = true)
    public double getPriceBasis() {
        return this.priceBasis;
    }

    protected void setVolumeBasis(double volumeBasis) {
        this.volumeBasis = volumeBasis;
    }

    @Basic(optional = true)
    public double getVolumeBasis() {
        return this.volumeBasis;
    }

    @Basic(optional = true)
    public int getMargin() {
        return this.margin;
    }

    protected void setMargin(int margin) {
        this.margin = margin;
    }

    @ManyToOne(optional = true)
    private FeeMethod marginMethod;

    @ManyToOne(optional = true)
    private FeeMethod marginFeeMethod;

    public FeeMethod getMarginMethod() {
        return marginMethod;
    }

    public FeeMethod getMarginFeeMethod() {
        return marginFeeMethod;
    }

    public FeeMethod getFeeMethod() {
        return feeMethod;
    }

    public double getFeeRate() {
        return feeRate;
    }

    protected void setMarginMethod(FeeMethod marginMethod) {
        this.marginMethod = marginMethod;
    }

    protected void setMarginFeeMethod(FeeMethod marginFeeMethod) {
        this.marginFeeMethod = marginFeeMethod;
    }

    protected void setFeeMethod(FeeMethod feeMethod) {
        this.feeMethod = feeMethod;
    }

    protected void setFeeRate(double feeRate) {
        this.feeRate = feeRate;
    }

    protected void setTradedCurrency(Asset tradedCurrency) {
        this.tradedCurrency = tradedCurrency;
    }

    @ManyToOne(optional = true)
    public Asset getTradedCurrency() {
        return this.tradedCurrency;
    }

    // used by Currencies

    static Prompt forSymbolOrCreate(String symbol, double tickValue, double tickSize, String currency, double volumeBasis, int margin, FeeMethod marginMethod,
            double feeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
        try {
            return forSymbol(symbol);
        } catch (NoResultException e) {
            Asset tradedCurrency = Currency.forSymbol(currency);
            final Prompt prompt = new Prompt(symbol, tickValue, tickSize, tradedCurrency, volumeBasis, margin, marginMethod, feeRate, feeMethod,
                    marginFeeMethod);
            promptDao.persistEntities(prompt);
            return prompt;
        }
    }

    @ManyToOne(optional = false)
    private FeeMethod feeMethod;

    private Prompt(String symbol, double tickValue, double tickSize, Asset tradedCurrency, double volumeBasis) {
        this.symbol = symbol;
        this.tickValue = tickValue;
        this.tickSize = tickSize;
        this.contractSize = tickValue / tickSize;
        this.tradedCurrency = tradedCurrency;
        this.volumeBasis = volumeBasis;
    }

    private Prompt(String symbol, double tickValue, double tickSize, Asset tradedCurrency, double volumeBasis, int margin, FeeMethod marginMethod,
            double feeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
        this.symbol = symbol;
        this.tickValue = tickValue;
        this.tickSize = tickSize;
        this.contractSize = tickValue / tickSize;
        this.tradedCurrency = tradedCurrency;
        this.volumeBasis = volumeBasis;
        this.margin = margin;
        this.marginMethod = marginMethod;
        this.marginFeeMethod = marginFeeMethod;
        this.feeRate = feeRate;
        this.feeMethod = feeMethod;
    }

    private String symbol;
    private double tickValue;
    private double tickSize;
    private double contractSize;
    private Asset tradedCurrency;
    private double priceBasis;
    private double volumeBasis;
    private int margin;
    private double feeRate;

    @Override
    public void persit() {
        promptDao.persist(this);

    }

    @Override
    public EntityBase refresh() {
        return promptDao.refresh(this);
    }

    @Override
    public void detach() {
        promptDao.detach(this);

    }

    @Override
    @Transient
    public Dao getDao() {
        return promptDao;
    }

    @Override
    public void merge() {
        promptDao.merge(this);

    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

}
