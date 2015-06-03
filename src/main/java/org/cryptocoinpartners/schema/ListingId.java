package org.cryptocoinpartners.schema;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * Represents the possibility to trade one Asset for another
 */
@SuppressWarnings("UnusedDeclaration")
@Embeddable
public class ListingId {

	@Column
	private static Asset base;

	@Column
	private static Asset quote;

	public ListingId() {

	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ListingId) {
			ListingId listing = (ListingId) obj;

			if (!ListingId.getBase().equals(getBase())) {
				return false;
			}

			if (!ListingId.getQuote().equals(getQuote())) {
				return false;
			}

			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return getQuote().hashCode() + getBase().hashCode();
	}

	public static Asset getBase() {
		return base;
	}

	public void setBase(Asset base) {
		this.base = base;
	}

	public static Asset getQuote() {
		return quote;
	}

	public void setQuote(Asset quote) {
		this.quote = quote;
	}

}
