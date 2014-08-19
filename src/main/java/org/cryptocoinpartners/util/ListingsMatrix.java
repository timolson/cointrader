package org.cryptocoinpartners.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.ObjectUtils;
import org.cryptocoinpartners.schema.Asset;

/**
 * Class describing a set of currencies and all the cross rates between them.
 */
public class ListingsMatrix {

	/**
	 * The map between the currencies and their order.
	 */
	private final ConcurrentHashMap<Asset, ConcurrentHashMap<Asset, Long>> listings;

	/**
	 * Constructor with no currency. The ListingsMatrix constructed has no currency and no rates.
	 */
	public ListingsMatrix() {
		listings = new ConcurrentHashMap<Asset, ConcurrentHashMap<Asset, Long>>();

	}

	/**
	 * Constructor with one currency. The ListingsMatrix has one currency with a 1.0 exchange rate to itself.
	 * @param ccy The currency.
	 */
	public ListingsMatrix(final Asset ccy) {
		ArgumentChecker.notNull(ccy, "Asset");
		listings = new ConcurrentHashMap<Asset, ConcurrentHashMap<Asset, Long>>();
		listings.put(ccy, new ConcurrentHashMap<Asset, Long>());
		listings.get(ccy).put(ccy, (long) (1 / ccy.getBasis()));

	}

	/**
	 * Constructor with an initial currency pair.
	 * @param ccy1 The first currency.
	 * @param ccy2 The second currency.
	 * @param rate TheListings rate between ccy1 and the ccy2. It is 1 ccy1 = rate * ccy2. The Listings matrix will be completed with the ccy2/ccy1 rate.
	 */
	public ListingsMatrix(final Asset ccy1, final Asset ccy2, final long rate) {
		listings = new ConcurrentHashMap<Asset, ConcurrentHashMap<Asset, Long>>();

		addAsset(ccy1, ccy2, rate);
	}

	/**
	 * Constructor from an existing ListingsMatrix. A new map and array are created.
	 * @param ListingsMatrix The ListingsMatrix.
	 */
	public ListingsMatrix(final ListingsMatrix ListingsMatrix) {
		ArgumentChecker.notNull(ListingsMatrix, "ListingsMatrix");

		listings = new ConcurrentHashMap<Asset, ConcurrentHashMap<Asset, Long>>(ListingsMatrix.listings);

	}

	/**
	 * Add a new currency to the Listings matrix.
	 * @param ccyToAdd The currency to add. Should not be in the Listings matrix already.
	 * @param ccyReference The reference currency used to compute the cross rates with the new currency. Should already be in the matrix, except if the matrix is empty.
	 * IF the Listings  matrix is empty, the reference currency will be used as currency 0.
	 * @param rate TheListings rate between the new currency and the reference currency. It is 1 ccyToAdd = rate ccyReference. The Listings matrix will be completed using cross rate
	 * coherent with the data provided.
	 */
	public void addAsset(Asset ccyToAdd, Asset ccyReference, long rate) {
		ArgumentChecker.notNull(ccyToAdd, "Asset to add to the Listings matrix should not be null");
		ArgumentChecker.notNull(ccyReference, "Reference currency should not be null");
		ArgumentChecker.isTrue(!ccyToAdd.equals(ccyReference), "Currencies should be different");
		if (listings.isEmpty() && rate != 0) { // Listings Matrix is empty.
			BigDecimal inverseRateBD = (((BigDecimal.valueOf(1 / (ccyReference.getBasis()))).divide(BigDecimal.valueOf(rate), ccyToAdd.getScale(),
					RoundingMode.HALF_EVEN)).divide(BigDecimal.valueOf(ccyToAdd.getBasis())));
			long inverseCrossRate = inverseRateBD.longValue();
			listings.put(ccyReference, new ConcurrentHashMap<Asset, Long>());
			listings.put(ccyToAdd, new ConcurrentHashMap<Asset, Long>());

			listings.get(ccyToAdd).put(ccyReference, rate);
			listings.get(ccyToAdd).put(ccyToAdd, (long) (1 / ccyToAdd.getBasis()));
			listings.get(ccyReference).put(ccyToAdd, inverseCrossRate);
			listings.get(ccyReference).put(ccyReference, (long) (1 / ccyReference.getBasis()));

		} else if (rate != 0) {
			ArgumentChecker.isTrue(listings.containsKey(ccyReference), "Reference currency {} not in the Listings matrix", ccyReference);
			ArgumentChecker.isTrue(!listings.containsKey(ccyToAdd), "New currency {} already in the Listings matrix", ccyToAdd);

			Iterator<Asset> lit = listings.keySet().iterator();
			if (ccyToAdd.getSymbol().equals("NXT") || ccyToAdd.getSymbol().equals("LTC") || ccyToAdd.getSymbol().equals("DOGE")
					|| ccyToAdd.getSymbol().equals("CNY")) {
				System.out.print("hello");
			}
			while (lit.hasNext()) {
				Asset ccy = lit.next();
				if (!ccyToAdd.equals(ccy)) {
					// new matrix create of rates that is _currenciesLookup (size) x _currenciesLookup.sise()
					// loop over each of the quote currencies and get the cross rate, converting to th the baiss of the new curency

					long crossRate = Math.round((rate * listings.get(ccyReference).get(ccy).longValue() * (ccyToAdd.getBasis())));
					// get the rate for the 
					BigDecimal crossRateBD = BigDecimal.valueOf(crossRate);
					// calculate the inverse by getting the basis of the currenct rate and setting the scale to ttha tof the quote currency.
					BigDecimal inverseCrossRateBD = ((BigDecimal.valueOf(1 / (ccy.getBasis())))
							.divide(crossRateBD, ccyToAdd.getScale(), RoundingMode.HALF_EVEN));
					// divine the rate by the basis fo the currency to be added
					inverseCrossRateBD = inverseCrossRateBD.divide(BigDecimal.valueOf(ccyToAdd.getBasis()));

					long inverseCrossRate = inverseCrossRateBD.longValue();
					// update the base currecny vs the quote
					if (listings.get(ccyToAdd) == null) {
						listings.put(ccyToAdd, new ConcurrentHashMap<Asset, Long>());

					}
					listings.get(ccyToAdd).put(ccy, crossRate);
					listings.get(ccy).put(ccyToAdd, inverseCrossRate);

				}

			}
			listings.get(ccyToAdd).put(ccyToAdd, (long) (1 / ccyToAdd.getBasis()));

		}
	}

	/**
	 * Return the exchange rate between two currencies.
	 * @param ccy1 The first currency.
	 * @param ccy2 The second currency.
	 * @return The exchange rate: 1.0 * ccy1 = x * ccy2.
	 */
	public long getRate(final Asset ccy1, final Asset ccy2) {
		if (ccy1.equals(ccy2)) {
			return (long) (1 / ccy1.getBasis());
		}
		final ConcurrentHashMap<Asset, Long> index1 = listings.get(ccy1);
		final ConcurrentHashMap<Asset, Long> index2 = listings.get(ccy2);
		ArgumentChecker.isTrue(listings.get(ccy1) != null, "Asset {} is  not in the Listings Matrix", ccy1);
		ArgumentChecker.isTrue(listings.get(ccy1).get(ccy2) != null, "Asset {} and {}  not in the Listings Matrix", ccy1, ccy2);

		return listings.get(ccy1).get(ccy2).longValue();

	}

	/**
	 * @param ccy1 The first currency
	 * @param ccy2 The second currency
	 * @return True if the matrix contains both currencies
	 */
	public boolean containsPair(final Asset ccy1, final Asset ccy2) {
		return listings.containsKey(ccy1) && listings.containsKey(ccy2);
	}

	/**
	 * Reset the exchange rate of a given currency.
	 * @param ccyToUpdate The currency for which the exchange rats should be updated. Should be in the Listings matrix already.
	 * @param ccyReference The reference currency used to compute the cross rates with the new currency. Should already be in the matrix.
	 * @param rate TheListings rate between the new currency and the reference currency. It is 1.0 * ccyToAdd = rate * ccyReference. The Listings matrix will be changed for currency1
	 * using cross rate coherent with the data provided.
	 */
	public void updateRates(final Asset ccyToUpdate, final Asset ccyReference, final long rate) {

		ArgumentChecker.isTrue(listings.get(ccyReference) != null, "Reference Asset not in the Listings matrix");
		ArgumentChecker.isTrue(listings.get(ccyReference).get(ccyToUpdate) != null, "Asset to update not in the Listings matrix");

		if (rate != 0) {

			Iterator<Asset> lit = listings.keySet().iterator();

			while (lit.hasNext()) {

				Asset ccy = lit.next();
				if (!ccyToUpdate.equals(ccy)) {

					long crossRate = Math.round((rate * listings.get(ccyReference).get(ccy).longValue() * (ccyReference.getBasis())));
					// get the rate for the 
					BigDecimal crossRateBD = BigDecimal.valueOf(crossRate);
					// calculate the inverse by getting the basis of the currenct rate and setting the scale to ttha tof the quote currency.
					BigDecimal inverseCrossRateBD = ((BigDecimal.valueOf(1 / (ccy.getBasis()))).divide(crossRateBD, ccyToUpdate.getScale(),
							RoundingMode.HALF_EVEN));
					// divine the rate by the basis fo the currency to be added
					inverseCrossRateBD = inverseCrossRateBD.divide(BigDecimal.valueOf(ccyToUpdate.getBasis()));

					long inverseCrossRate = inverseCrossRateBD.longValue();
					listings.get(ccyToUpdate).put(ccy, crossRate);
					listings.get(ccy).put(ccyToUpdate, inverseCrossRate);
				}
			}
			listings.get(ccyToUpdate).put(ccyToUpdate, (long) (1 / ccyToUpdate.getBasis()));

		}
	}

	@Override
	public String toString() {
		return listings.keySet().toString() + " - ";
	}

	@Override
	public int hashCode() {

		return listings.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ListingsMatrix other = (ListingsMatrix) obj;
		if (!ObjectUtils.equals(listings.keySet(), other.listings.keySet())) {
			return false;
		}

		return true;
	}

}
