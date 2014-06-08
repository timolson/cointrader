package org.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.*;
import java.util.Collection;


/**
 * @author Tim Olson
 */
@Entity
@Table(name="\"Order\"") // This is required because ORDER is a SQL keyword and must be escaped
@SuppressWarnings("JpaDataSourceORMInspection")
public class Order extends Event {

    public enum OrderType { MARKET, LIMIT }


    public enum FillType {
        GOOD_TIL_CANCELLED, // Order stays open until explicitly cancelled or expired
        GTC_OR_MARGIN_CAP, // Order stays open until explicitly cancelled, expired, or the order is filled to the capacity of the currently available Positions
        CANCEL_REMAINDER, // This will cancel any remaining volume after a partial fill
    }


    public enum MarginType {
        USE_MARGIN, // trade up to the limit of credit in the quote fungible
        CASH_ONLY, // do not trade more than the available cash-on-hand (quote fungible)
    }

    // TODO: extract OrderStatus into a new class that inherits from Event and is tied to Order
    public enum OrderStatus { NEW, PLACED, PARTFILLED, FILLED, CANCELLING, CANCELLED, EXPIRED, REJECTED }


    @Enumerated(EnumType.STRING)
    public OrderType getOrderType() { return orderType; }

    @Enumerated(EnumType.STRING)
    public OrderStatus getOrderStatus() { return orderStatus; }

    @ManyToOne(optional = false)
    public FundManager getManager() { return manager; }

    // TODO: fill in getters for the remaining data members and set relationships

    @OneToMany public Collection<Fill> getFills() { return fills; }

    @ManyToOne(optional = false)
    public Listing getListing() {
        return listing;
    }

    @Transient boolean isOpen() {
        return orderStatus == OrderStatus.NEW ||
                orderStatus == OrderStatus.PLACED ||
                orderStatus == OrderStatus.PARTFILLED;
    }

    @Transient boolean hasFills() { return !getFills().isEmpty(); }

    // OrderBuilder
    public static class OrderBuilder {
        public OrderBuilder(FundManager manager, MarketListing listing) {
            this.order = new Order();
            this.order.setOrderType(OrderType.MARKET);
            this.order.setOrderStatus(OrderStatus.NEW);
            this.order.setManager(manager);
            this.order.setListing(listing.getListing());
            this.order.setMarket(listing.getMarket());
        }

        public OrderBuilder setAmount(long amount /* units in basis of market listing's base fungible */) {
            this.order.setAmount(amount);
            return this;
        }

        public OrderBuilder setAmount(double amount) {
            // TODO: think about how we want to round this; for now we drop remainders
            this.order.setAmount((long)(amount / this.order.getListing().getBase().getBasis()));
            return this;
        }

        public OrderBuilder setLimit(long price /* units in basis of market listing's quote fungible */) {
            this.order.setLimit(price);
            return this;
        }

        public OrderBuilder setLimit(double price) {
            // TODO: change rounding to "best price" based on buy/sell side; for now we drop remainders
            this.order.setLimit((long) (price / this.order.getListing().getQuote().getBasis()));
            return this;
        }

        public OrderBuilder setStop(long price /* units in basis of market listing's quote fungible */) {
            this.order.setStop(price);
            return this;
        }

        public OrderBuilder setStop(double price) {
            // TODO: change rounding to nearest price; for now we drop remainders
            this.order.setStop((long) (price / this.order.getListing().getQuote().getBasis()));
            return this;
        }

        public OrderBuilder setFillType(FillType fillType) {
            this.order.setFillType(fillType);
            return this;
        }

        public OrderBuilder setMarginType(MarginType marginType) {
            this.order.setMarginType(marginType);
            return this;
        }

        public OrderBuilder setExpiration(Instant expiration) {
            this.order.setExpiration(expiration);
            return this;
        }

        public OrderBuilder setForce(boolean force) {
            this.order.setForce(force);
            return this;
        }

        public OrderBuilder setEmulation(boolean emulation) {
            this.order.setEmulation(emulation);
            return this;
        }

        public Order build() {
            // todo: save to database?
            // todo: place order?
            // todo: add to esper?
            return this.order;
        }

        private Order order;
    }

    // JPA
    protected Order() {}
    protected void setOrderType(OrderType orderType) { this.orderType = orderType; }
    protected void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
    protected void setManager(FundManager manager) { this.manager = manager; }
    protected void setFills(Collection<Fill> fills) { this.fills = fills; }
    protected void setMarket(Market market) { this.market = market; }
    protected void setListing(Listing listing) { this.listing = listing; }
    protected void setAmount(long amount) { this.amount = amount; }
    protected void setLimit(long limit) { this.limit = limit; }
    protected void setStop(long stop) { this.stop = stop; }
    protected void setFillType(FillType fillType) { this.fillType = fillType; }
    protected void setMarginType(MarginType marginType) { this.marginType = marginType; }
    protected void setExpiration(Instant expiration) { this.expiration = expiration; }
    protected void setForce(boolean force) { this.force = force; }
    protected void setEmulation(boolean emulation) { this.emulation = emulation; }

    private OrderType orderType;
    private OrderStatus orderStatus;
    private FundManager manager;
    private Collection<Fill> fills;
    private Market market;
    private Listing listing;
    private long amount;
    private long limit;
    private long stop;
    private FillType fillType;
    private MarginType marginType;
    private Instant expiration;
    private boolean force;     // allow this order to override various types of panic
    private boolean emulation; // ("allow order type emulation" [default, true] or "only use exchange's native functionality")
}
