package org.cryptocoinpartners.schema;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NamedSubgraph;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.schema.dao.Dao;
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
@Table(indexes = { @Index(columnList = "sequence"), @Index(columnList = "state"), @Index(columnList = "`order`") })
//@NamedQueries({ @NamedQuery(name = "orderUpdate.findTriggerOrders", query = "select ou from OrderUpdate ou where  ou.sequence = (select max(ouu.sequence) from OrderUpdate ouu where ouu.order = ou.order) and state=?1") })
//
//@NamedEntityGraphs({
//@NamedEntityGraph(name = "orderUpdateWithChildOrders", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "order") })
////, subgraphs = { @NamedSubgraph(name = "ordersWithChildOrders", attributeNodes = { @NamedAttributeNode("parentFill") }) })
// @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
//})
@NamedQueries({ @NamedQuery(name = "orderUpdate.findOrders", query = "select ou from OrderUpdate ou where  ou.state = (select max(ouu.state) from OrderUpdate ouu where ouu.order = ou.order) and state in (?1)") })
//
@NamedEntityGraphs({
// @NamedEntityGraph(name = "orderUpdateWithTransactions", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "orderWithTransactions") }, subgraphs = { @NamedSubgraph(name = "orderWithTransactions", attributeNodes = { @NamedAttributeNode("transactions") }) }),
// @NamedEntityGraph(name = "orderUpdateWithFills", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "orderWithFills") }, subgraphs = { @NamedSubgraph(name = "orderWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })
//@NamedEntityGraph(name = "orderUpdateWithFills", attributeNodes = { @NamedAttributeNode("order.fills") })
@NamedEntityGraph(name = "orderUpdateWithFills", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "orderWithFills") }, subgraphs = { @NamedSubgraph(name = "orderWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })

//attributeNodes = @NamedAttributeNode(value = "items", subgraph = "items"), 
//subgraphs = @NamedSubgraph(name = "items", attributeNodes = @NamedAttributeNode("product")))
//  @NamedEntityGraph(name = "orderUpdateWithFills", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "orderWithFills") }, subgraphs = { @NamedSubgraph(name = "orderWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })

// @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
})
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
    @Id
    @Column(columnDefinition = "integer auto_increment")
    // @Transient
    public Long getSequence() {
        return sequence;
    }

    //  @PrePersist
    private void prePersist() {
        if (orderUpdateDao != null) {
            Order parentOrder = null;

            if (getOrder() != null) {
                // parentOrder = ;

                // orderId = (fillDao.queryZeroOne(UUID.class, "select o.id from Order o where o.id=?1", order.getId()));

                parentOrder = orderUpdateDao.find(getOrder().getClass(), getOrder().getId());
                if (parentOrder == null)
                    orderUpdateDao.persist(getOrder());
                //  order.merge();
            }
        }

        //detach();
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
    //  @Transient
    public @ManyToOne
    //(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinColumn(name = "`order`")
    Order getOrder() {
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
    public EntityBase refresh() {
        return orderUpdateDao.refresh(this);
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

    public void setOrder(Order order) {
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

    @Override
    @Transient
    public Dao getDao() {
        return orderUpdateDao;
    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

}
