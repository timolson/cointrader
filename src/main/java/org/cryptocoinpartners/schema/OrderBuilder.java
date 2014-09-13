package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.service.OrderService;
import org.joda.time.Instant;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class OrderBuilder {

	public OrderBuilder(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	public OrderBuilder(Portfolio portfolio, OrderService orderService) {
		this.orderService = orderService;
		this.portfolio = portfolio;
	}

	/** @param volume to create a sell order, use a negative volume */
	public GeneralOrderBuilder create(Instant time, Listing listing, String volume) {
		return new GeneralOrderBuilder(time, listing, volume);
	}

	/** @param volume to create a sell order, use a negative volume */
	public GeneralOrderBuilder create(Instant time, Listing listing, BigDecimal volume) {
		return new GeneralOrderBuilder(time, listing, volume);
	}

	/** @param volume to create a sell order, use a negative volume */
	public SpecificOrderBuilder create(Instant time, Market market, BigDecimal volume, String comment) {
		return new SpecificOrderBuilder(time, market, volume, comment);
	}

	/** @param volumeCount to create a sell order, use a negative volumeCount */
	public SpecificOrderBuilder create(Instant time, Market market, long volumeCount) {
		return new SpecificOrderBuilder(time, market, volumeCount);
	}

	public SpecificOrderBuilder create(Instant time, Market market, long volumeCount, String comment) {
		return new SpecificOrderBuilder(time, market, volumeCount, comment);
	}

	/** @param volume to create a sell order, use a negative volume */
	public SpecificOrderBuilder create(Instant time, Market market, Amount volume, String comment) {
		return new SpecificOrderBuilder(time, market, volume, comment);
	}

	@SuppressWarnings("unchecked")
	public abstract class CommonOrderBuilder<T> {

		public T withFillType(FillType fillType) {
			getOrder().setFillType(fillType);
			return (T) this;
		}

		public T withMarginType(Order.MarginType marginType) {
			getOrder().setMarginType(marginType);
			return (T) this;
		}

		public T withExpiration(Instant expiration) {
			getOrder().setExpiration(expiration);
			return (T) this;
		}

		public T withPanicForce(boolean force) {
			getOrder().setPanicForce(force);
			return (T) this;
		}

		public T withEmulation(boolean emulation) {
			getOrder().setEmulation(emulation);
			return (T) this;
		}

		/** This finalizes the Order and places it with the OrderService the Builder was constructed with. */
		public Order place() {
			if (orderService == null)
				throw new IllegalStateException("You must construct Order.Builder with an OrderService to use the place() method.");
			Order order = getOrder();
			orderService.placeOrder(order);
			return order;
		}

		/** The Order will be constructed but not placed with any OrderService. */
		public Order build() {
			return getOrder();
		}

		public abstract Order getOrder();
	}

	public class GeneralOrderBuilder extends CommonOrderBuilder<GeneralOrderBuilder> {

		public GeneralOrderBuilder(Instant time, Listing listing, BigDecimal volume) {
			order = new GeneralOrder(time, listing, volume);
		}

		public GeneralOrderBuilder(Instant time, Listing listing, String volume) {
			order = new GeneralOrder(time, listing, volume);
		}

		public GeneralOrderBuilder withLimitPrice(String price) {
			order.setLimitPrice(DecimalAmount.of(price));
			return this;
		}

		public GeneralOrderBuilder withStopPrice(String price) {
			order.setStopPrice(DecimalAmount.of(price));
			return this;
		}

		public GeneralOrderBuilder withTrailingStopPrice(String price, String trailingStopPrice) {
			order.setStopPrice(DecimalAmount.of(price));
			order.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
			return this;
		}

		public GeneralOrderBuilder withLimitPrice(BigDecimal price) {
			order.setLimitPrice(DecimalAmount.of(price));
			return this;
		}

		public GeneralOrderBuilder withStopPrice(BigDecimal price) {
			order.setStopPrice(DecimalAmount.of(price));
			return this;
		}

		@Override
		public GeneralOrder getOrder() {
			return order;
		}

		private final GeneralOrder order;
	}

	public class SpecificOrderBuilder extends CommonOrderBuilder<SpecificOrderBuilder> {

		public SpecificOrderBuilder(Instant time, Market market, BigDecimal volume, String comment) {
			order = new SpecificOrder(time, portfolio, market, volume, comment);
		}

		public SpecificOrderBuilder(Instant time, Market market, Amount volume, String comment) {
			order = new SpecificOrder(time, portfolio, market, volume, comment);
		}

		public SpecificOrderBuilder(Instant time, Market market, long volumeCount) {
			order = new SpecificOrder(time, portfolio, market, volumeCount);
		}

		public SpecificOrderBuilder(Instant time, Market market, long volumeCount, String comment) {
			order = new SpecificOrder(time, portfolio, market, volumeCount, comment);
		}

		public SpecificOrderBuilder withLimitPriceCount(long price /* units in basis of Market's quote fungible */) {
			order.setLimitPriceCount(price);
			order.setFillType(FillType.LIMIT);
			return this;
		}

		public SpecificOrderBuilder withStopPriceCount(long price /* units in basis of Market's quote fungible */) {
			order.setStopPriceCount(price);
			order.setFillType(FillType.STOP_LIMIT);
			return this;
		}

		public SpecificOrderBuilder withTrailingStopPriceCount(long price, long trailingPrice) {
			order.setStopPriceCount(price);
			order.setTrailingStopPriceCount(trailingPrice);
			order.setFillType(FillType.TRAILING_STOP_LIMIT);
			return this;
		}

		public SpecificOrderBuilder withLimitPrice(DiscreteAmount price) {
			price.assertBasis(order.getMarket().getPriceBasis());
			return withLimitPriceCount(price.getCount());
		}

		public SpecificOrderBuilder withStopPrice(DiscreteAmount price) {
			price.assertBasis(order.getMarket().getPriceBasis());
			return withStopPriceCount(price.getCount());
		}

		public SpecificOrderBuilder withTrailingStopPrice(DiscreteAmount price, DiscreteAmount trailingPrice) {
			price.assertBasis(order.getMarket().getPriceBasis());
			trailingPrice.assertBasis(order.getMarket().getPriceBasis());

			return withTrailingStopPriceCount(price.getCount(), trailingPrice.getCount());
		}

		@Override
		public SpecificOrder getOrder() {
			return order;
		}

		private final SpecificOrder order;
	}

	private OrderService orderService;
	private final Portfolio portfolio;

}
