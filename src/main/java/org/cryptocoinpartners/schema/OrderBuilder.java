package org.cryptocoinpartners.schema;

import java.math.BigDecimal;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
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

    public GeneralOrderBuilder create(Instant time, Market market, BigDecimal volume, FillType type) {
        return new GeneralOrderBuilder(time, market, volume, type);
    }

    public GeneralOrderBuilder create(Instant time, Order parentOrder, Market market, BigDecimal volume, FillType type) {
        return new GeneralOrderBuilder(time, parentOrder, market, volume, type);
    }

    public GeneralOrderBuilder create(Instant time, Fill parentFill, Market market, BigDecimal volume, FillType type) {
        return new GeneralOrderBuilder(time, parentFill, market, volume, type);
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

    public SpecificOrderBuilder create(Instant time, Market market, long volumeCount, Order parentOrder, String comment) {
        return new SpecificOrderBuilder(time, market, volumeCount, parentOrder, comment);
    }

    /** @param volume to create a sell order, use a negative volume */
    public SpecificOrderBuilder create(Instant time, Market market, Amount volume, String comment) {
        return new SpecificOrderBuilder(time, market, volume, comment);
    }

    public SpecificOrderBuilder create(Instant time, Market market, Amount volume, Order parentOrder, String comment) {
        return new SpecificOrderBuilder(time, market, volume, parentOrder, comment);
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
            order = new GeneralOrder(time, portfolio, listing, volume);
        }

        public GeneralOrderBuilder(Instant time, Order parentOrder, Listing listing, BigDecimal volume) {
            order = new GeneralOrder(time, portfolio, parentOrder, listing, volume);
        }

        public GeneralOrderBuilder(Instant time, Market market, BigDecimal volume, FillType type) {
            order = new GeneralOrder(time, portfolio, market, volume, type);
        }

        public GeneralOrderBuilder(Instant time, Order parentOrder, Market market, BigDecimal volume, FillType type) {
            order = new GeneralOrder(time, portfolio, parentOrder, market, volume, type);
        }

        public GeneralOrderBuilder(Instant time, Fill parentFill, Market market, BigDecimal volume, FillType type) {
            order = new GeneralOrder(time, portfolio, parentFill, market, volume, type);
        }

        public GeneralOrderBuilder(Instant time, Listing listing, String volume) {
            order = new GeneralOrder(time, portfolio, listing, volume);
        }

        public GeneralOrderBuilder withLimitPrice(String price) {
            order.setLimitPrice(DecimalAmount.of(price));
            return this;
        }

        public GeneralOrderBuilder withLimitPrice(BigDecimal price) {
            order.setLimitPrice(DecimalAmount.of(price));
            return this;
        }

        public GeneralOrderBuilder withTargetPrice(String price) {
            if (order.fillType.equals(FillType.STOP_LIMIT) || order.fillType.equals(FillType.STOP_LOSS) || order.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
                order.setTargetPrice(DecimalAmount.of(price));
                return this;
            }
            throw new NotImplementedException();
        }

        public GeneralOrderBuilder withTargetPrice(BigDecimal price) {
            if (order.fillType.equals(FillType.STOP_LIMIT) || order.fillType.equals(FillType.STOP_LOSS) || order.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
                order.setTargetPrice(DecimalAmount.of(price));
                return this;
            }

            throw new NotImplementedException();
        }

        public GeneralOrderBuilder withStopPrice(String price) {
            if (order.fillType.equals(FillType.STOP_LIMIT) || order.fillType.equals(FillType.STOP_LOSS) || order.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
                order.setStopPrice(DecimalAmount.of(price));
                return this;
            }
            throw new NotImplementedException();
        }

        public GeneralOrderBuilder withStopPrice(BigDecimal price) {
            if (order.fillType.equals(FillType.STOP_LIMIT) || order.fillType.equals(FillType.STOP_LOSS) || order.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
                order.setStopPrice(DecimalAmount.of(price));
                return this;
            }

            throw new NotImplementedException();
        }

        public GeneralOrderBuilder withTrailingStopPrice(BigDecimal price, BigDecimal trailingStopPrice) {
            if (order.fillType.equals(FillType.STOP_LIMIT) || order.fillType.equals(FillType.STOP_LOSS) || order.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
                order.setStopPrice(DecimalAmount.of(price));
                order.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
                return this;
            }
            throw new NotImplementedException();
        }

        public GeneralOrderBuilder withTrailingStopPrice(String price, String trailingStopPrice) {
            if (order.fillType.equals(FillType.STOP_LIMIT) || order.fillType.equals(FillType.STOP_LOSS) || order.fillType.equals(FillType.TRAILING_STOP_LIMIT)) {
                order.setStopPrice(DecimalAmount.of(price));
                order.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
                return this;
            }
            throw new NotImplementedException();
        }

        public GeneralOrderBuilder withComment(String comment) {
            order.setComment(comment);
            return this;
        }

        public GeneralOrderBuilder withPositionEffect(PositionEffect positionEffect) {
            order.setPositionEffect(positionEffect);
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

        public SpecificOrderBuilder(Instant time, Market market, Amount volume, Order parentOrder, String comment) {
            order = new SpecificOrder(time, portfolio, market, volume, parentOrder, comment);
        }

        public SpecificOrderBuilder(Instant time, Market market, long volumeCount) {
            order = new SpecificOrder(time, portfolio, market, volumeCount);
        }

        public SpecificOrderBuilder(Instant time, Market market, long volumeCount, String comment) {
            order = new SpecificOrder(time, portfolio, market, volumeCount, comment);
        }

        public SpecificOrderBuilder(Instant time, Market market, long volumeCount, Order parentOrder, String comment) {
            order = new SpecificOrder(time, portfolio, market, volumeCount, parentOrder, comment);
        }

        public SpecificOrderBuilder withLimitPriceCount(long price /* units in basis of Market's quote fungible */) {
            order.setLimitPriceCount(price);
            order.setFillType(FillType.LIMIT);
            return this;
        }

        public SpecificOrderBuilder withLimitPrice(DiscreteAmount price) {
            price.assertBasis(order.getMarket().getPriceBasis());
            return withLimitPriceCount(price.getCount());
        }

        public SpecificOrderBuilder withPositionEffect(PositionEffect positionEffect) {
            order.setPositionEffect(positionEffect);
            return this;
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
