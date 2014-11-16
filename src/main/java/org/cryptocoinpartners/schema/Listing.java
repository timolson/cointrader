package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NoResultException;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.PersistUtil;

/**
 * Represents the possibility to trade one Asset for another
 */
@SuppressWarnings("UnusedDeclaration")
@Entity
public class Listing extends EntityBase {
	@ManyToOne(optional = false, cascade = { CascadeType.MERGE, CascadeType.REMOVE })
	public Asset getBase() {
		return base;
	}

	@ManyToOne(optional = false, cascade = { CascadeType.MERGE, CascadeType.REMOVE })
	public Asset getQuote() {
		return quote;
	}

	@Nullable
	public String getPrompt() {
		return prompt;
	}

	/** will create the listing if it doesn't exist */
	public static Listing forPair(Asset base, Asset quote) {
		try {
			Listing listing = PersistUtil.queryZeroOne(Listing.class, "select a from Listing a where base=?1 and quote=?2 and prompt IS NULL", base, quote);
			if (listing == null) {
				listing = new Listing(base, quote);
				PersistUtil.insert(listing);
			}
			return listing;
		} catch (NoResultException e) {
			final Listing listing = new Listing(base, quote);
			PersistUtil.insert(listing);
			return listing;
		}
	}

	public static Listing forPair(Asset base, Asset quote, String prompt) {
		try {
			Listing listing = PersistUtil.queryZeroOne(Listing.class, "select a from Listing a where base=?1 and quote=?2 and prompt=?3", base, quote, prompt);
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
			return base.getSymbol() + '.' + quote.getSymbol() + '.' + prompt;
		return base.getSymbol() + '.' + quote.getSymbol();
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

	protected void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	protected Asset base;
	protected Asset quote;
	private String prompt;

	public Listing(Asset base, Asset quote) {
		this.base = base;
		this.quote = quote;
	}

	public Listing(Asset base, Asset quote, String prompt) {
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
		final String prompt = (symbol.length() > len) ? symbol.substring(len + 1, symbol.length()) : null;
		Asset quote = Asset.forSymbol(quoteSymbol);
		if (quote == null)
			throw new IllegalArgumentException("Invalid quote symbol: \"" + quoteSymbol + "\"");
		if (prompt == null)
			return Listing.forPair(base, quote);
		return Listing.forPair(base, quote, prompt);
	}
}
