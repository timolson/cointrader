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
import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.OrderUpdateDao;
import org.joda.time.Instant;

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
//@Table(name = "\"Order\"",
@Table(name = "order_update", indexes = { @Index(columnList = "id"), @Index(columnList = "sequence"), @Index(columnList = "state"),
		@Index(columnList = "`order`") })
//@NamedQueries({ @NamedQuery(name = "orderUpdate.findTriggerOrders", query = "select ou from OrderUpdate ou where  ou.sequence = (select max(ouu.sequence) from OrderUpdate ouu where ouu.order = ou.order) and state=?1") })
//
//@NamedEntityGraphs({
//@NamedEntityGraph(name = "orderUpdateWithChildOrders", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "order") })
////, subgraphs = { @NamedSubgraph(name = "ordersWithChildOrders", attributeNodes = { @NamedAttributeNode("parentFill") }) })
// @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
//})
@NamedQueries({
		@NamedQuery(name = "orderUpdate.findOrdersByState", query = "select ou from OrderUpdate ou where  ou.state = (select max(ouu.state) from OrderUpdate ouu where ouu.order = ou.order) and state in (?1) and  ou.order.portfolio =?2"),
		@NamedQuery(name = "orderUpdate.findStateByOrder", query = "select ou from OrderUpdate ou where  ou.state = (select max(ouu.state) from OrderUpdate ouu where ouu.order = ou.order) and order in (?1)") })
@NamedEntityGraphs({
		// @NamedEntityGraph(name = "orderUpdateWithTransactions", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "orderWithTransactions") }, subgraphs = { @NamedSubgraph(name = "orderWithTransactions", attributeNodes = { @NamedAttributeNode("transactions") }) }),
		// @NamedEntityGraph(name = "orderUpdateWithFills", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "orderWithFills") }, subgraphs = { @NamedSubgraph(name = "orderWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })
		//@NamedEntityGraph(name = "orderUpdateWithFills", attributeNodes = { @NamedAttributeNode("order.fills") })
		@NamedEntityGraph(name = "orderUpdateWithFills", attributeNodes = { @NamedAttributeNode(value = "order", subgraph = "orderWithFills") }, subgraphs = {
				@NamedSubgraph(name = "orderWithFills", attributeNodes = { @NamedAttributeNode("fills") }) })

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

	// @PrePersist
	@Override
	public synchronized void prePersist() {
		if (getDao() != null) {

			EntityBase dbOrder = null;
			try {
				dbOrder = getDao().find(getOrder().getClass(), getOrder().getId());
				if (dbOrder != null) {
					getOrder().setVersion(dbOrder.getVersion());
					if (getOrder().getRevision() > dbOrder.getRevision()) {
						getOrder().setPeristanceAction(PersistanceAction.MERGE);
						getDao().merge(getOrder());
					}
				} else {
					getOrder().setPeristanceAction(PersistanceAction.NEW);
					getDao().persist(getOrder());
				}
			} catch (Exception | Error ex) {
				if (dbOrder != null)
					if (getOrder().getRevision() > dbOrder.getRevision()) {
						getOrder().setPeristanceAction(PersistanceAction.MERGE);
						getDao().merge(getOrder());
					} else {
						getOrder().setPeristanceAction(PersistanceAction.NEW);
						getDao().persist(getOrder());
					}
			}

		}
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
	//Ordres added to state mape before placned on exchange.
	public @ManyToOne
	//(cascade = { CascadeType.MERGE })
	@JoinColumn(name = "`order`") Order getOrder() {
		return order;
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return getOrder();
	}

	public OrderState getLastState() {
		return lastState;
	}

	public OrderState getState() {
		return state;
	}

	@AssistedInject
	public OrderUpdate(@Assisted Instant time, @Assisted Order order, @Assisted("orderUpdateLastState") OrderState lastState,
			@Assisted("orderUpdateState") OrderState state) {
		super(time);
		this.order = order;
		this.lastState = lastState;
		this.state = state;

	}

	@Override
	public synchronized EntityBase refresh() {
		return orderUpdateDao.refresh(this);
	}

	@Override
	public synchronized void persit() {

		try {
			log.debug(this.getClass().getSimpleName() + " - Persist : Persit of Order Update " + this.getId() + " called from class "
					+ Thread.currentThread().getStackTrace()[2]);

			this.setPeristanceAction(PersistanceAction.NEW);
			this.setRevision(this.getRevision() + 1);

			orderUpdateDao.persist(this);

			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}

		//  try {

	}

	// JPA
	protected OrderUpdate() {
	}

	public synchronized void setOrder(Order order) {
		this.order = order;
	}

	protected synchronized void setSequence(Long sequence) {
		this.sequence = sequence;
	}

	@Override
	protected synchronized void setId(UUID id) {
		this.id = id;
	}

	protected synchronized void setLastState(OrderState lastState) {
		this.lastState = lastState;
	}

	protected synchronized void setState(OrderState state) {
		this.state = state;
	}

	@Inject
	protected transient OrderUpdateDao orderUpdateDao;

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

		try {
			log.debug(this.getClass().getSimpleName() + " - Merge : Merge of Order Update " + this.getId() + " called from class "
					+ Thread.currentThread().getStackTrace()[2]);

			this.setPeristanceAction(PersistanceAction.NEW);
			this.setRevision(this.getRevision() + 1);

			orderUpdateDao.merge(this);

			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}
	}

	@Override
	@Transient
	public Dao getDao() {
		return orderUpdateDao;
	}

	@Override
	@Transient
	public synchronized void setDao(Dao dao) {
		orderUpdateDao = (OrderUpdateDao) dao;
		// TODO Auto-generated method stub
		//  return null;
	}

	@Override
	public synchronized void delete() {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void persitParents() {
		if (getOrder() != null)
			getDao().merge(getOrder());

	}

}
