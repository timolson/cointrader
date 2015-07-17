package org.cryptocoinpartners.schema;

import java.util.List;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.util.PersistUtil;

/**
 * When Orders change OrderState, this Event is published
 *
 * @author Tim Olson
 */

@SuppressWarnings("UnusedDeclaration")
@Entity
@Table(indexes = { @Index(columnList = "state") })
//@IdClass(OrderUpdateID.class)
//@Table(indexes = { @Index(columnList = "seq") })
public class OrderUpdate extends Event {

    //  @GeneratedValue(strategy = GenerationType.TABLE, generator = "tab")

    //   @GeneratedValue(strategy = GenerationType.IDENTITY)
    //  @Column(name = "id", unique = true, nullable = false, insertable = false, updatable = false)

    public class OrderUpdateID extends Event {
        UUID id;
        Long sequence;

        public OrderUpdateID() {
        }

        public OrderUpdateID(UUID id, Long sequence) {
            this.id = id;
            this.sequence = sequence;
        }

        @Override
        public UUID getId() {

            return id;
        }

        @Override
        public void setId(UUID id) {

            this.id = id;
        }

        public Long getSequence() {

            return sequence;
        }

        public void setSequence(Long sequence) {

            this.sequence = sequence;
        }

    }

    // @Column(columnDefinition = "integer auto_increment")
    //@GeneratedValue(strategy = IDENTITY)
    //@Column(name = "columnName", unique = true, nullable = false, insertable = false, updatable = false)
    // columnDefinition = "integer auto_increment", 
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    // @GeneratedValue(strategy = GenerationType.SEQUENCE)
    // @Id
    // @Column(columnDefinition = "integer auto_increment", name = "seq", unique = true, nullable = false, insertable = false, updatable = false)
    @Id
    @Column(columnDefinition = "integer auto_increment")
    public Long getSequence() {
        return sequence;
    }

    @ManyToOne
    @JoinColumn(name = "`order`")
    public Order getOrder() {
        return order;
    }

    public OrderState getLastState() {
        return lastState;
    }

    public OrderState getState() {
        return state;
    }

    public OrderUpdate(Order order, OrderState lastState, OrderState state) {
        this.order = order;
        this.lastState = lastState;
        this.state = state;
    }

    public void persit() {

        //  if (getOrder() != null)
        //    getOrder().persit();

        // final Trade duplicate = PersistUtil.queryZeroOne(Trade.class, "select t from Trade t where market=?1 and remoteKey=?2 and time=?3",
        //       trade.getMarket(), trade.getRemoteKey(), trade.getTime());
        List<OrderUpdate> duplicate = PersistUtil.queryList(OrderUpdate.class, "select ou from OrderUpdate ou where id=?1 and sequence=?2", this.getId(),
                this.getSequence());

        // OrderUpdate duplicate = PersistUtil.queryZeroOne(OrderUpdate.class, "select ou from OrderUpdate ou where ou=?1 and sequence=?2", this,
        //         this.getSequence());
        if (duplicate == null || duplicate.isEmpty())
            PersistUtil.insert(this);
        // else
        //   PersistUtil.merge(this);

    }

    // JPA
    protected OrderUpdate() {
    }

    protected void setOrder(Order order) {
        this.order = order;
    }

    protected void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    protected void setLastState(OrderState lastState) {
        this.lastState = lastState;
    }

    protected void setState(OrderState state) {
        this.state = state;
    }

    private Order order;
    private Long sequence;
    private OrderState lastState;
    private OrderState state;

}
