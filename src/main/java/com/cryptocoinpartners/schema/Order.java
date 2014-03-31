package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.*;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@Entity
@Table(name="\"Order\"")
// todo order is not a quote.  quote is market data
public class Order extends Quote {

    enum OrderType { MARKET, LIMIT }
    
    enum OrderStatus { NEW, PLACED, PARTFILLED, FILLED, CANCELLED }


    public Order(Strategy strategy, OrderType orderType, Side side,
                 Listing listing, Instant time, BigDecimal price, BigDecimal amount) {
        super(side, listing, time, null, price, amount);
        this.strategy = strategy;
        this.orderType = orderType;
        this.orderStatus = OrderStatus.NEW;
    }


    @Enumerated(EnumType.STRING)
    public OrderType getOrderType() { return orderType; }

    @Enumerated(EnumType.STRING)
    public OrderStatus getOrderStatus() { return orderStatus; }

    @ManyToOne(optional = false)
    public Strategy getStrategy() { return strategy; }


    // JPA
    protected Order() {}
    protected void setOrderType(OrderType orderType) { this.orderType = orderType; }
    protected void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
    protected void setStrategy(Strategy strategy) { this.strategy = strategy; }


    private OrderType orderType;
    private OrderStatus orderStatus;
    private Strategy strategy;
}
