package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

/**
 * This event is posted when there are any problems retreiving market data
 *
 * @author Tim Olson
 */
@MappedSuperclass
public class MarketDataError extends Event {

	public MarketDataError(Market market) {
		this(market, null);
	}

	public MarketDataError(Market market, @Nullable Exception exception) {
		this.exception = exception;
		this.market = market;
	}

	@Nullable
	public Exception getException() {
		return exception;
	}

	@ManyToOne
	public Market getMarket() {
		return market;
	}

	protected MarketDataError() {
	}

	protected void setException(@Nullable Exception exception) {
		this.exception = exception;
	}

	protected void setMarket(Market market) {
		this.market = market;
	}

	private Exception exception;
	private Market market;
}
