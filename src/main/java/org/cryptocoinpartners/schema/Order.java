package org.cryptocoinpartners.schema;

import javax.persistence.*;
import java.util.Collection;


/**
 * @author Tim Olson
 */
@Entity
@Table(name="\"Order\"") // This is required because ORDER is a SQL keyword and must be escaped
@SuppressWarnings("JpaDataSourceORMInspection")
public class Order extends EntityBase {

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


    public enum OrderStatus { NEW, PLACED, PARTFILLED, FILLED, CANCELLING, CANCELLED, EXPIRED, REJECTED }


    @Enumerated(EnumType.STRING)
    public OrderType getOrderType() { return orderType; }

    @Enumerated(EnumType.STRING)
    public OrderStatus getOrderStatus() { return orderStatus; }

    @ManyToOne(optional = false)
    public Strategy getStrategy() { return strategy; }

    @OneToMany public Collection<Fill> getFills() { return fills; }

    @Transient boolean isOpen() {
        return orderStatus == OrderStatus.NEW ||
                orderStatus == OrderStatus.PLACED ||
                orderStatus == OrderStatus.PARTFILLED;
    }

    @Transient boolean hasFills() { return !getFills().isEmpty(); }


    // JPA
    protected Order() {}
    protected void setOrderType(OrderType orderType) { this.orderType = orderType; }
    protected void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
    protected void setStrategy(Strategy strategy) { this.strategy = strategy; }
    protected void setFills(Collection<Fill> fills) { this.fills = fills; }


    private OrderType orderType;
    private OrderStatus orderStatus;
    private Strategy strategy;
    private Collection<Fill> fills;
}
