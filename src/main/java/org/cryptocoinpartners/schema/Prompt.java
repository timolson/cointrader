package org.cryptocoinpartners.schema;

import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NoResultException;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.PromptJpaDao;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.Remainder;

import com.google.inject.Inject;

/**
 * @author Tim Olson
 */
@Entity
@Cacheable
public class Prompt extends EntityBase {
    @Inject
    protected transient static PromptJpaDao promptDao;

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
    public Amount getMultiplier(Market market, Amount entryPrice, Amount exitPrice) {

        return (entryPrice.times(exitPrice, Remainder.ROUND_EVEN)).invert();
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
    private double getContractSize() {
        return this.contractSize;
    }

    @Transient
    public double getContractSize(Market market) {
        if (market.getBase().getSymbol().equals("LTC"))
            return this.contractSize * 0.1;
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

    @Nullable
    protected void setTradedCurrency(Asset tradedCurrency) {
        this.tradedCurrency = tradedCurrency;
    }

    @ManyToOne(optional = true)
    private Asset getTradedCurrency() {
        return this.tradedCurrency;
    }

    @ManyToOne(optional = true)
    public Asset getTradedCurrency(Market market) {
        if (getTradedCurrency() == null || market.getBase().getSymbol().equals("LTC"))
            return market.getListing().getBase();

        else
            return getTradedCurrency();
    }

    // used by Currencies

    static Prompt forSymbolOrCreate(String symbol, double tickValue, double tickSize, String currency, double volumeBasis, double priceBasis, int margin,
            FeeMethod marginMethod, double feeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
        try {
            return forSymbol(symbol);
        } catch (NoResultException e) {
            Asset tradedCurrency = Currency.forSymbol(currency);
            final Prompt prompt = new Prompt(symbol, tickValue, tickSize, tradedCurrency, volumeBasis, priceBasis, margin, marginMethod, feeRate, feeMethod,
                    marginFeeMethod);
            prompt.setRevision(prompt.getRevision() + 1);
            try {
                promptDao.persistEntities(prompt);
            } catch (Throwable e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            return prompt;
        }
    }

    static Prompt forSymbolOrCreate(String symbol, double tickValue, double tickSize, double volumeBasis, double priceBasis, int margin,
            FeeMethod marginMethod, double feeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
        try {
            return forSymbol(symbol);
        } catch (NoResultException e) {
            final Prompt prompt = new Prompt(symbol, tickValue, tickSize, volumeBasis, priceBasis, margin, marginMethod, feeRate, feeMethod, marginFeeMethod);
            prompt.setRevision(prompt.getRevision() + 1);
            try {
                promptDao.persistEntities(prompt);
            } catch (Throwable e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            return prompt;
        }
    }

    @ManyToOne(optional = false)
    private FeeMethod feeMethod;

    private Prompt(String symbol, double tickValue, double tickSize, Asset tradedCurrency, double volumeBasis, double priceBasis) {
        this.symbol = symbol;
        this.tickValue = tickValue;
        this.tickSize = tickSize;
        this.contractSize = tickValue / tickSize;
        this.tradedCurrency = tradedCurrency;
        this.volumeBasis = volumeBasis;
        this.priceBasis = priceBasis;
    }

    private Prompt(String symbol, double tickValue, double tickSize, Asset tradedCurrency, double volumeBasis, double priceBasis, int margin,
            FeeMethod marginMethod, double feeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
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
        this.priceBasis = priceBasis;
    }

    private Prompt(String symbol, double tickValue, double tickSize, double volumeBasis, double priceBasis, int margin, FeeMethod marginMethod, double feeRate,
            FeeMethod feeMethod, FeeMethod marginFeeMethod) {
        this.symbol = symbol;
        this.tickValue = tickValue;
        this.tickSize = tickSize;
        this.contractSize = tickValue / tickSize;
        this.volumeBasis = volumeBasis;
        this.margin = margin;
        this.marginMethod = marginMethod;
        this.marginFeeMethod = marginFeeMethod;
        this.feeRate = feeRate;
        this.feeMethod = feeMethod;
        this.priceBasis = priceBasis;
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
    public synchronized void persit() {

        this.setPeristanceAction(PersistanceAction.NEW);

        this.setRevision(this.getRevision() + 1);
        try {
            promptDao.persistEntities(this);
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

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
    @Transient
    public void setDao(Dao dao) {
        promptDao = (PromptJpaDao) dao;
        // TODO Auto-generated method stub
        //  return null;
    }

    @Override
    public synchronized void merge() {

        this.setPeristanceAction(PersistanceAction.MERGE);

        this.setRevision(this.getRevision() + 1);
        promptDao.merge(this);

    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

    @Override
    public void prePersist() {
        // TODO Auto-generated method stub

    }

    @Override
    public void postPersist() {
        // TODO Auto-generated method stub

    }

}
