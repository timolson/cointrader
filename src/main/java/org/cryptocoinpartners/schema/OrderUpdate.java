package org.cryptocoinpartners.schema;

import java.util.UUID;

import javax.persistence.Entity;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.schema.dao.OrderUpdateDao;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * When Orders change OrderState, this Event is published
 *
 * @author Tim Olson
 */

@SuppressWarnings("UnusedDeclaration")
@Entity
//@IdClass(OrderUpdateId.class)
//@Table(indexes = { @Index(columnList = "state") })
//@IdClass(OrderUpdateID.class)
//@Table(indexes = { @Index(columnList = "seq") })
public class OrderUpdate extends Event {

    //  @GeneratedValue(strategy = GenerationType.TABLE, generator = "tab")

    //   @GeneratedValue(strategy = GenerationType.IDENTITY)
    //  @Column(name = "id", unique = true, nullable = false, insertable = false, updatable = false)

    // @Column(columnDefinition = "integer auto_increment")
    //@GeneratedValue(strategy = IDENTITY)
    //@Column(name = "columnName", unique = true, nullable = false, insertable = false, updatable = false)
    // columnDefinition = "integer auto_increment", 
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    // @GeneratedValue(strategy = GenerationType.SEQUENCE)
    // @Id
    // @Column(columnDefinition = "integer auto_increment", name = "seq", unique = true, nullable = false, insertable = false, updatable = false)
    //@Id
    //@Column(columnDefinition = "integer auto_increment")
    @Transient
    public Long getSequence() {
        return sequence;
    }

    //@Id
    //@Override
    //@ManyToOne
    //  @Override
    // @Transient
    // public UUID getId() {
    //    return id;
    //}

    // @ManyToOne
    // @JoinColumn(name = "`order`")
    @Transient
    public Order getOrder() {
        return order;
    }

    public OrderState getLastState() {
        return lastState;
    }

    public OrderState getState() {
        return state;
    }

    @AssistedInject
    public OrderUpdate(@Assisted Order order, @Assisted("orderUpdateLastState") OrderState lastState, @Assisted("orderUpdateState") OrderState state) {
        this.order = order;
        this.lastState = lastState;
        this.state = state;
    }

    @Override
    public synchronized void persit() {
        //  try {
        orderUpdateDao.persist(this);
        //if (duplicate == null || duplicate.isEmpty())
        //  } catch (Exception | Error ex) {

        //     System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
        // ex.printStackTrace();

        // }
        //  if (getOrder() != null)
        //    getOrder().persit();

        // final Trade duplicate = PersistUtil.queryZeroOne(Trade.class, "select t from Trade t where market=?1 and remoteKey=?2 and time=?3",
        //       trade.getMarket(), trade.getRemoteKey(), trade.getTime());
        //  List<OrderUpdate> duplicate = orderUpdateDao.queryList(OrderUpdate.class, "select ou from OrderUpdate ou where id=?1 and sequence=?2", this.getId(),
        //        this.getSequence());

        // OrderUpdate duplicate = PersistUtil.queryZeroOne(OrderUpdate.class, "select ou from OrderUpdate ou where ou=?1 and sequence=?2", this,
        //         this.getSequence());
        //if (duplicate == null || duplicate.isEmpty())
        // orderUpdateDao.persist(this);
        //PersistUtil.insert(this);
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

    @Override
    protected void setId(UUID id) {
        this.id = id;
    }

    protected void setLastState(OrderState lastState) {
        this.lastState = lastState;
    }

    protected void setState(OrderState state) {
        this.state = state;
    }

    @Inject
    protected OrderUpdateDao orderUpdateDao;

    private Order order;
    private Long sequence;
    private OrderState lastState;
    private OrderState state;

    @Override
    public synchronized void detach() {
        orderUpdateDao.detach(this);
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized void merge() {
        orderUpdateDao.merge(this);
        // TODO Auto-generated method stub

    }

}
