package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.PostPersist;
import javax.persistence.QueryHint;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.ListingDao;
import org.cryptocoinpartners.util.EM;

import com.google.inject.Inject;

/**
 * Represents the possibility to trade one Asset for another
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name = "Listing.findByQuoteBase", query = "select a from Listing a where base=?1 and quote=?2 and prompt IS NULL", hints = { @QueryHint(name = "org.hibernate.cacheable", value = "true") }),
        @NamedQuery(name = "Listing.findByQuoteBasePrompt", query = "select a from Listing a where base=?1 and quote=?2 and prompt=?3", hints = { @QueryHint(name = "org.hibernate.cacheable", value = "true") }) })
@Table(indexes = { @Index(columnList = "base"), @Index(columnList = "quote"), @Index(columnList = "prompt") })
//@Table(name = "listing", uniqueConstraints = { @UniqueConstraint(columnNames = { "base", "quote", "prompt" }),
//@UniqueConstraint(columnNames = { "base", "quote" }) })
public class Listing extends EntityBase {
    @Inject
    protected transient static ListingDao listingDao;
    protected static Set<Listing> listings = new HashSet<Listing>();

    @ManyToOne(optional = false)
    //@Column(unique = true)
    public Asset getBase() {
        return base;
    }

    @PostPersist
    @Override
    public void postPersist() {
        //  PersistUtil.clear();
        //  PersistUtil.refresh(this);
        //PersistUtil.merge(this);
        // PersistUtil.close();
        //PersistUtil.evict(this);

    }

    @ManyToOne(optional = false)
    //@Column(unique = true)
    public Asset getQuote() {
        return quote;
    }

    @ManyToOne(optional = true)
    public Prompt getPrompt() {
        return prompt;
    }

    /** will create the listing if it doesn't exist */
    public static Listing forPair(Asset base, Asset quote) {

        for (Listing listing : listings) {
            if (listing.getBase().equals(base) && listing.getQuote().equals(quote))
                return listing;
        }
        //this is very slow, we should cache it in a map!
        try {
            Listing listing = EM.namedQueryZeroOne(Listing.class, "Listing.findByQuoteBase", base, quote);
            if (listing == null) {
                listing = new Listing(base, quote);
                EM.find(base);
                EM.find(quote);
                EM.persist(listing);
            }
            listings.add(listing);
            return listing;
        } catch (NoResultException e) {
            final Listing listing = new Listing(base, quote);
            EM.persist(listing);
            listings.add(listing);
            return listing;
        }
    }

    public static Listing forPair(Asset base, Asset quote, Prompt prompt) {
        try {

            Listing listing = EM.namedQueryZeroOne(Listing.class, "Listing.findByQuoteBasePrompt", base, quote, prompt);
            if (listing == null) {
                listing = new Listing(base, quote, prompt);
                EM.persist(listing);
            }
            return listing;
        } catch (NoResultException e) {
            final Listing listing = new Listing(base, quote, prompt);
            EM.persist(listing);
            return listing;
        }
    }

    @Override
    public String toString() {
        return getSymbol();
    }

    @Override
    @Transient
    public EntityBase getParent() {

        return null;
    }

    @Transient
    public String getSymbol() {
        if (prompt != null)
            return base.getSymbol() + '.' + quote.getSymbol() + '.' + prompt.getSymbol();
        return base.getSymbol() + '.' + quote.getSymbol();
    }

    @Transient
    protected Amount getMultiplier(Market market, Amount entryPrice, Amount exitPrice) {
        if (prompt != null)
            return prompt.getMultiplier(market, entryPrice, exitPrice);

        return new DiscreteAmount((long) (getContractSize(market) * (1 / getTickSize())), getVolumeBasis());

    }

    @Transient
    protected double getTickValue() {
        if (prompt != null)
            return prompt.getTickValue();
        return 1;
    }

    @Transient
    protected double getContractSize(Market market) {
        if (prompt != null)
            return prompt.getContractSize(market);
        return 1;
    }

    @Transient
    protected double getTickSize() {
        if (prompt != null)
            return prompt.getTickSize();
        return getPriceBasis();
    }

    @Transient
    protected double getVolumeBasis() {
        double volumeBasis = 0;
        if (prompt != null)
            volumeBasis = prompt.getVolumeBasis();
        return volumeBasis == 0 ? getBase().getBasis() : volumeBasis;

    }

    @Transient
    public FeeMethod getMarginMethod() {
        FeeMethod marginMethod = null;
        if (prompt != null)
            marginMethod = prompt.getMarginMethod();
        return marginMethod == null ? null : marginMethod;

    }

    @Transient
    public FeeMethod getMarginFeeMethod() {
        FeeMethod marginFeeMethod = null;
        if (prompt != null)
            marginFeeMethod = prompt.getMarginFeeMethod();
        return marginFeeMethod == null ? null : marginFeeMethod;

    }

    @Transient
    protected double getPriceBasis() {
        double priceBasis = 0;
        if (prompt != null)
            priceBasis = prompt.getPriceBasis();
        return priceBasis == 0 ? getQuote().getBasis() : priceBasis;

    }

    @Transient
    protected Asset getTradedCurrency(Market market) {
        if (prompt != null && prompt.getTradedCurrency(market) != null)
            return prompt.getTradedCurrency(market);
        return null;
    }

    @Transient
    public FeeMethod getFeeMethod() {
        if (prompt != null && prompt.getFeeMethod() != null)
            return prompt.getFeeMethod();
        return null;
    }

    @Transient
    public double getFeeRate() {
        if (prompt != null && prompt.getFeeRate() != 0)
            return prompt.getFeeRate();
        return 0;
    }

    @Transient
    protected int getMargin() {
        if (prompt != null && prompt.getMargin() != 0)
            return prompt.getMargin();
        return 0;
    }

    public static List<String> allSymbols() {
        List<String> result = new ArrayList<>();
        List<Listing> listings = EM.queryList(Listing.class, "select x from Listing x");
        for (Listing listing : listings)
            result.add((listing.getSymbol()));
        return result;
    }

    // JPA
    protected Listing() {
    }

    protected void setBase(Asset base) {
        this.base = base;
    }

    protected void setQuote(Asset quote) {
        this.quote = quote;
    }

    protected void setPrompt(Prompt prompt) {
        this.prompt = prompt;
    }

    protected Asset base;
    protected Asset quote;
    private Prompt prompt;

    public Listing(Asset base, Asset quote) {
        this.base = base;
        this.quote = quote;
    }

    public Listing(Asset base, Asset quote, Prompt prompt) {
        this.base = base;
        this.quote = quote;
        this.prompt = prompt;
    }

    public static Listing forSymbol(String symbol) {
        symbol = symbol.toUpperCase();
        final int dot = symbol.indexOf('.');
        if (dot == -1)
            throw new IllegalArgumentException("Invalid Listing symbol: \"" + symbol + "\"");
        final String baseSymbol = symbol.substring(0, dot);
        Asset base = Asset.forSymbol(baseSymbol);
        if (base == null)
            throw new IllegalArgumentException("Invalid base symbol: \"" + baseSymbol + "\"");
        int len = symbol.substring(dot + 1, symbol.length()).indexOf('.');
        len = (len != -1) ? Math.min(symbol.length(), dot + 1 + symbol.substring(dot + 1, symbol.length()).indexOf('.')) : symbol.length();
        final String quoteSymbol = symbol.substring(dot + 1, len);
        final String promptSymbol = (symbol.length() > len) ? symbol.substring(len + 1, symbol.length()) : null;
        Asset quote = Asset.forSymbol(quoteSymbol);
        if (quote == null)
            throw new IllegalArgumentException("Invalid quote symbol: \"" + quoteSymbol + "\"");
        if (promptSymbol == null)
            return Listing.forPair(base, quote);
        Prompt prompt = Prompt.forSymbol(promptSymbol);
        return Listing.forPair(base, quote, prompt);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Listing) {
            Listing listing = (Listing) obj;

            if (!listing.getBase().equals(getBase())) {
                return false;
            }

            if (!listing.getQuote().equals(getQuote())) {
                return false;
            }
            if (listing.getPrompt() != null)
                if (this.getPrompt() != null) {
                    if (!listing.getPrompt().equals(getPrompt()))
                        return false;
                } else {
                    return false;
                }

            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return getPrompt() != null ? getQuote().hashCode() + getBase().hashCode() + getPrompt().hashCode() : getQuote().hashCode() + getBase().hashCode();

    }

    @Override
    public synchronized void persit() {

        this.setPeristanceAction(PersistanceAction.NEW);

        this.setRevision(this.getRevision() + 1);
        listingDao.persist(this);

    }

    @Override
    public EntityBase refresh() {
        return listingDao.refresh(this);
    }

    public <T> T find() {
        //   synchronized (persistanceLock) {
        try {
            return (T) listingDao.find(Listing.class, this.getId());
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {
            return null;
            // System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }

    }

    @Override
    public void detach() {
        listingDao.detach(this);
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized void merge() {

        this.setPeristanceAction(PersistanceAction.MERGE);

        this.setRevision(this.getRevision() + 1);
        listingDao.merge(this);
        // TODO Auto-generated method stub

    }

    @Override
    @Transient
    public Dao getDao() {
        return listingDao;
    }

    @Override
    @Transient
    public void setDao(Dao dao) {
        listingDao = (ListingDao) dao;
        // TODO Auto-generated method stub
        //  return null;
    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

    @Override
    public void prePersist() {
        // TODO Auto-generated method stub

    }

}
