package org.cryptocoinpartners.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
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
	private final Map<Asset, Integer> _currencies;

	private final Map<Integer, Asset> _currenciesLookup;
	/**
	 * The matrix with all exchange rates. The entry [i][j] is such that 1.0 * Asset[i] = _fxrate * Asset[j].
	 * If _currencies.get(BTC) = 0 and _currencies.get(USD) = 1, the element _rate[0][1] is likely to be something like 500 and _rate[1][0] like 0.002.
	 * (the rate _rate[1][0] will be computed from _rate[0][1] when the object is constructed or updated).
	 * All the element of the matrix are meaningful and coherent (the matrix is always completed in a coherent way when a currency is added or a rate updated).
	 */
	private long[][] _rates;
	/**
	 * The number of currencies.
	 */
	private int _nbCurrencies;

	/**
	 * Constructor with no currency. The ListingsMatrix constructed has no currency and no rates.
	 */
	public ListingsMatrix() {
		_currencies = new ConcurrentHashMap<Asset, Integer>();
		_currenciesLookup = new ConcurrentHashMap<Integer, Asset>();
		_rates = new long[0][0];
		_nbCurrencies = 0;
	}

	/**
	 * Constructor with one currency. The ListingsMatrix has one currency with a 1.0 exchange rate to itself.
	 * @param ccy The currency.
	 */
	public ListingsMatrix(final Asset ccy) {
		ArgumentChecker.notNull(ccy, "Asset");
		_currencies = new ConcurrentHashMap<Asset, Integer>();
		_currenciesLookup = new ConcurrentHashMap<Integer, Asset>();
		_currencies.put(ccy, 0);
		_currenciesLookup.put(0, ccy);

		_rates = new long[1][1];
		_rates[0][0] = (long) (1 / ccy.getBasis());
		;
		_nbCurrencies = 1;
	}

	/**
	 * Constructor with an initial currency pair.
	 * @param ccy1 The first currency.
	 * @param ccy2 The second currency.
	 * @param rate TheListings rate between ccy1 and the ccy2. It is 1 ccy1 = rate * ccy2. The Listings matrix will be completed with the ccy2/ccy1 rate.
	 */
	public ListingsMatrix(final Asset ccy1, final Asset ccy2, final long rate) {
		_currencies = new ConcurrentHashMap<Asset, Integer>();
		_currenciesLookup = new ConcurrentHashMap<Integer, Asset>();
		_rates = new long[0][0];
		addAsset(ccy1, ccy2, rate);
	}

	/**
	 * Constructor from an existing ListingsMatrix. A new map and array are created.
	 * @param ListingsMatrix The ListingsMatrix.
	 */
	public ListingsMatrix(final ListingsMatrix ListingsMatrix) {
		ArgumentChecker.notNull(ListingsMatrix, "ListingsMatrix");
		_nbCurrencies = ListingsMatrix._nbCurrencies;
		_currencies = new ConcurrentHashMap<Asset, Integer>(ListingsMatrix._currencies);
		_currenciesLookup = new ConcurrentHashMap<Integer, Asset>(ListingsMatrix._currenciesLookup);
		_rates = new long[_nbCurrencies][];
		for (int loopc = 0; loopc < _nbCurrencies; loopc++) {
			_rates[loopc] = ListingsMatrix._rates[loopc].clone();
		}
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
		if (_nbCurrencies == 0 && rate != 0) { // Listings Matrix is empty.
			_currencies.put(ccyReference, 0);
			_currencies.put(ccyToAdd, 1);
			_currenciesLookup.put(0, ccyReference);
			_currenciesLookup.put(1, ccyToAdd);

			_rates = new long[2][2];
			_rates[0][0] = (long) (1 / _currenciesLookup.get(0).getBasis());
			_rates[1][1] = (long) (1 / _currenciesLookup.get(1).getBasis());
			_rates[1][0] = rate;
			BigDecimal inverseRateBD = ((BigDecimal.ONE.divide(BigDecimal.valueOf(rate), _currenciesLookup.get(1).getScale(), RoundingMode.HALF_EVEN))
					.divide(BigDecimal.valueOf(_currenciesLookup.get(0).getBasis() * _currenciesLookup.get(1).getBasis())));
			long inverseCrossRate = inverseRateBD.longValue();
			_rates[0][1] = inverseCrossRate;
			_nbCurrencies = 2;
		} else if (rate != 0) {
			ArgumentChecker.isTrue(_currencies.containsKey(ccyReference), "Reference currency {} not in the Listings matrix", ccyReference);
			ArgumentChecker.isTrue(!_currencies.containsKey(ccyToAdd), "New currency {} already in the Listings matrix", ccyToAdd);
			_currencies.put(ccyToAdd, _nbCurrencies);
			_currenciesLookup.put(_nbCurrencies, ccyToAdd);

			_nbCurrencies++;
			// cahnge some stuff here to use has maps.
			long[][] ratesNew = new long[_nbCurrencies][_nbCurrencies];
			// Copy the previous matrix
			for (int loopccy = 0; loopccy < _nbCurrencies - 1; loopccy++) {
				System.arraycopy(_rates[loopccy], 0, ratesNew[loopccy], 0, _nbCurrencies - 1);
			}
			ratesNew[_nbCurrencies - 1][_nbCurrencies - 1] = (long) (1 / _currenciesLookup.get(_nbCurrencies - 1).getBasis());

			//final int indexRef = _currencies.get(ccyReference);
			int indexRef = _currencies.get(ccyReference);
			Iterator<Integer> it = _currenciesLookup.keySet().iterator();
			while (it.hasNext()) {
				int loopccy = it.next();
				if (loopccy < _currenciesLookup.size() - 1) {
					long crossRate = Math.round((rate * _rates[indexRef][loopccy]) * (_currenciesLookup.get(_nbCurrencies - 1).getBasis()));
					BigDecimal crossRateBD = BigDecimal.valueOf(crossRate);
					BigDecimal inverseCrossRateBD = ((BigDecimal.valueOf(1 / (_currenciesLookup.get(loopccy).getBasis()))).divide(crossRateBD,
							_currenciesLookup.get(indexRef).getScale(), RoundingMode.HALF_EVEN));
					inverseCrossRateBD = inverseCrossRateBD.divide(BigDecimal.valueOf(_currenciesLookup.get(_nbCurrencies - 1).getBasis()));

					long inverseCrossRate = inverseCrossRateBD.longValue();
					ratesNew[_nbCurrencies - 1][loopccy] = crossRate;
					ratesNew[loopccy][_nbCurrencies - 1] = inverseCrossRate;
				}
			}

			_rates = ratesNew;
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
		final Integer index1 = _currencies.get(ccy1);
		final Integer index2 = _currencies.get(ccy2);
		ArgumentChecker.isTrue(index1 != null, "Asset {} not in the Listings Matrix", ccy1);
		ArgumentChecker.isTrue(index2 != null, "Asset {} not in the Listings Matrix", ccy2);
		return _rates[index1][index2];
	}

	/**
	 * @param ccy1 The first currency
	 * @param ccy2 The second currency
	 * @return True if the matrix contains both currencies
	 */
	public boolean containsPair(final Asset ccy1, final Asset ccy2) {
		return _currencies.containsKey(ccy1) && _currencies.containsKey(ccy2);
	}

	/**
	 * Reset the exchange rate of a given currency.
	 * @param ccyToUpdate The currency for which the exchange rats should be updated. Should be in the Listings matrix already.
	 * @param ccyReference The reference currency used to compute the cross rates with the new currency. Should already be in the matrix.
	 * @param rate TheListings rate between the new currency and the reference currency. It is 1.0 * ccyToAdd = rate * ccyReference. The Listings matrix will be changed for currency1
	 * using cross rate coherent with the data provided.
	 */
	public void updateRates(final Asset ccyToUpdate, final Asset ccyReference, final long rate) {
		ArgumentChecker.isTrue(_currencies.containsKey(ccyReference), "Reference Asset not in the Listings matrix");
		ArgumentChecker.isTrue(_currencies.containsKey(ccyToUpdate), "Asset to update not in the Listings matrix");
		int indexUpdate = _currencies.get(ccyToUpdate);
		int indexRef = _currencies.get(ccyReference);
		if (rate != 0) {
			Iterator<Integer> it = _currenciesLookup.keySet().iterator();
			while (it.hasNext()) {
				int loopccy = it.next();

				long crossRate = Math.round((rate * _rates[indexRef][loopccy]) * (_currenciesLookup.get(indexRef).getBasis()));
				BigDecimal crossRateBD = BigDecimal.valueOf(crossRate);
				BigDecimal inverseCrossRateBD = ((BigDecimal.valueOf(1 / _currenciesLookup.get(loopccy).getBasis())).divide(crossRateBD,
						_currenciesLookup.get(indexUpdate).getScale(), RoundingMode.HALF_EVEN));
				inverseCrossRateBD = inverseCrossRateBD.divide(BigDecimal.valueOf(_currenciesLookup.get(indexUpdate).getBasis()));

				long inverseCrossRate = inverseCrossRateBD.longValue();
				_rates[indexUpdate][loopccy] = crossRate;
				_rates[loopccy][indexUpdate] = inverseCrossRate;

			}

			_rates[indexUpdate][indexUpdate] = (long) (1 / _currenciesLookup.get(indexUpdate).getBasis());

		}
	}

	@Override
	public String toString() {
		return _currencies.keySet().toString() + " - " + Arrays.toString(_rates);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + _currencies.hashCode();
		result = prime * result + Arrays.deepHashCode(_rates);
		return result;
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
		if (!ObjectUtils.equals(_currencies, other._currencies)) {
			return false;
		}
		if (!Arrays.deepEquals(_rates, other._rates)) {
			return false;
		}
		return true;
	}

}
