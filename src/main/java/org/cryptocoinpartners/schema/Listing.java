package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.PostPersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FeeMethod;
import org.cryptocoinpartners.util.PersistUtil;

/**
 * Represents the possibility to trade one Asset for another
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
@Cacheable
@NamedQueries({ @NamedQuery(name = "Listing.findByQuoteBase", query = "select a from Listing a where base=?1 and quote=?2 and prompt IS NULL"),
        @NamedQuery(name = "Listing.findByQuoteBasePrompt", query = "select a from Listing a where base=?1 and quote=?2 and prompt=?3") })
@Table(indexes = { @Index(columnList = "base"), @Index(columnList = "quote"), @Index(columnList = "prompt") })
//@Table(name = "listing", uniqueConstraints = { @UniqueConstraint(columnNames = { "base", "quote", "prompt" }),
//@UniqueConstraint(columnNames = { "base", "quote" }) })
public class Listing extends EntityBase {

    @ManyToOne(optional = false)
    //@Column(unique = true)
    public Asset getBase() {
        return base;
    }

    @PostPersist
    private void postPersist() {
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

        try {
            Listing listing = PersistUtil.namedQueryZeroOne(Listing.class, "Listing.findByQuoteBase", base, quote);
            if (listing == null) {
                listing = new Listing(base, quote);
                PersistUtil.find(base);
                PersistUtil.find(quote);
                PersistUtil.insert(listing);
            }
            return listing;
        } catch (NoResultException e) {
            final Listing listing = new Listing(base, quote);
            PersistUtil.insert(listing);
            return listing;
        }
    }

    public static Listing forPair(Asset base, Asset quote, Prompt prompt) {
        try {

            Listing listing = PersistUtil.namedQueryZeroOne(Listing.class, "Listing.findByQuoteBasePrompt", base, quote, prompt);
            if (listing == null) {
                listing = new Listing(base, quote, prompt);
                PersistUtil.insert(listing);
            }
            return listing;
        } catch (NoResultException e) {
            final Listing listing = new Listing(base, quote, prompt);
            PersistUtil.insert(listing);
            return listing;
        }
    }

    @Override
    public String toString() {
        return getSymbol();
    }

    @Transient
    public String getSymbol() {
        if (prompt != null)
            return base.getSymbol() + '.' + quote.getSymbol() + '.' + prompt.getSymbol();
        return base.getSymbol() + '.' + quote.getSymbol();
    }

    @Transient
    protected double getMultiplier() {
        if (prompt != null)
            return prompt.getMultiplier();
        return getContractSize() * getTickSize();
    }

    @Transient
    protected double getTickValue() {
        if (prompt != null)
            return prompt.getTickValue();
        return 1;
    }

    @Transient
    protected double getContractSize() {
        if (prompt != null)
            return prompt.getContractSize();
        return 1;
    }

    @Transient
    protected double getTickSize() {
        if (prompt != null)
            return prompt.getTickSize();
        return getPriceBasis();
    }

    @Transient
    protected Amount getMultiplierAsAmount() {

        return new DiscreteAmount((long) getMultiplier(), getVolumeBasis());
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
    protected Asset getTradedCurrency() {
        if (prompt != null && prompt.getTradedCurrency() != null)
            return prompt.getTradedCurrency();
        return getQuote();
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
        List<Listing> listings = PersistUtil.queryList(Listing.class, "select x from Listing x");
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

}
