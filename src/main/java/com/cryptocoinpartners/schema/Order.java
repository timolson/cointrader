package com.cryptocoinpartners.schema;

import org.joda.time.Instant;

import javax.persistence.*;
import java.math.BigDecimal;


/**
 * @author Tim Olson
 */
@Entity
@Table(name="Orders")
public class Order extends Quote {

    enum OrderType { MARKET, LIMIT }


    public Order(Strategy strategy, OrderType orderType, Side side,
                 Security security, Instant time, BigDecimal price, BigDecimal size) {
        super(side, security, time, price, size);
        this.strategy = strategy;
        this.orderType = orderType;
    }


    @Enumerated(EnumType.STRING)
    public OrderType getOrderType() { return orderType; }

    public @ManyToOne Strategy getStrategy() { return strategy; }


    // JPA
    protected Order() { }
    protected void setOrderType(OrderType orderType) { this.orderType = orderType; }
    protected void setStrategy(Strategy strategy) { this.strategy = strategy; }


    private OrderType orderType;
    private Strategy strategy;
}
