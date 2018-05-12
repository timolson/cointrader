package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.PostPersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.ContingencyType;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.FillJpaDao;
import org.cryptocoinpartners.schema.dao.OrderDao;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.FeesUtil;
import org.cryptocoinpartners.util.Remainder;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * This is the base class for GeneralOrder and SpecificOrder. To create orders, see OrderBuilder or BaseStrategy.order
 * 
 * @author Mike Olson
 * @author Tim Olson
 */

@Entity
//@MappedSuperclass
@Cacheable
@Table(name = "\"Order\"", indexes = { @Index(columnList = "Order_Type"), @Index(columnList = "fillType"), @Index(columnList = "portfolio"),
		@Index(columnList = "parentFill"), @Index(columnList = "parentOrder"), @Index(columnList = "version"), @Index(columnList = "revision") })
//, @Index(columnList = "portfolio") })
// This is required because ORDER is a SQL keyword and must be escaped
@DiscriminatorColumn(name = "Order_Type")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@SuppressWarnings({ "JpaDataSourceORMInspection", "UnusedDeclaration" })
@NamedQueries({ @NamedQuery(name = "Order.findOrder", query = "select o from Order o where id =?1") })
//
@NamedEntityGraphs({

		// @NamedEntityGraph(name = "orderWithParentFill", attributeNodes = { @NamedAttributeNode(value = "parentFill", subgraph = "orderWithParentFillDetails") }, subgraphs = { @NamedSubgraph(name = "orderWithParentFillDetails", attributeNodes = { @NamedAttributeNode("children") }) }),
		@NamedEntityGraph(name = "orderWithFills", attributeNodes = { @NamedAttributeNode(value = "fills") }),
		@NamedEntityGraph(name = "orderWithTransactions", attributeNodes = { @NamedAttributeNode(value = "transactions") }),
		@NamedEntityGraph(name = "orderWithChildOrders", attributeNodes = { @NamedAttributeNode(value = "orderChildren") }),
		@NamedEntityGraph(name = "orderWithOrderUpdates", attributeNodes = { @NamedAttributeNode(value = "orderUpdates") })

		//@NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
		//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
})
public abstract class Order extends Event {
	@Inject
	protected transient OrderDao orderDao;
	@Inject
	protected transient FillJpaDao fillDao;
	protected Integer stopAdjustmentCount = 0;
	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.order");

	protected static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
	private transient static Object lock = new Object();
	// private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy kk:mm:ss");
	protected static final String SEPARATOR = ",";

	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(name = "parentOrder")
	//, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	//@Transient
	public Order getParentOrder() {
		return parentOrder;
	}

	//@ManyToOne(optional = true)

	// @Transient
	@Nullable
	@ManyToOne(optional = true)
	//, cascade = { CascadeType.MERGE })
	//cascade = { CascadeType.ALL })
	@JoinColumn(name = "parentFill")
	public Fill getParentFill() {
		return parentFill;
	}

	public double getOrderGroup() {
		return orderGroup;
	}

	@Transient
	public abstract boolean isBid();

	@Transient
	public boolean isAsk() {
		return !isBid();
	}

	@Transient
	public TransactionType getTransactionType() {
		if (isBid())
			return TransactionType.BUY;
		else
			return TransactionType.SELL;
	}

	@ManyToOne(optional = false)
	@JoinColumn(name = "portfolio")
	//@Transient
	public Portfolio getPortfolio() {
		return portfolio;
	}

	@Transient
	public Amount getForcastedCommission() {
		if (commission == null)
			setForcastedCommission(FeesUtil.getCommission(this));
		return commission;

	}

	@Transient
	public Amount getForcastedMargin() {
		if (margin == null)
			setForcastedMargin(FeesUtil.getMargin(this));
		return margin;

	}

	//	@Nullable
	//	@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getLimitPrice();

	//@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getMarketPrice();

	public abstract void addFill(Fill fill);

	public abstract Order withLimitPrice(String price);

	public abstract Order withMarketPrice(String price);

	public abstract Order withLimitPrice(BigDecimal price);

	public abstract Order withMarketPrice(BigDecimal price);

	public abstract Order withLimitPrice(DiscreteAmount price);

	public abstract Order withMarketPrice(DiscreteAmount price);

	@ManyToOne(optional = true)
	@JoinColumn(name = "market")
	public abstract Market getMarket();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getStopAmount();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract double getStopPercentage();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract double getTargetPercentage();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract double getTriggerInterval();

	@Nullable
	//	@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getTrailingStopAmount();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getTargetAmount();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getStopPrice();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getLastBestPrice();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getTargetPrice();

	@Nullable
	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getTrailingStopPrice();

	//@Column(insertable = false, updatable = false)
	@Transient
	public abstract Amount getVolume();

	@Transient
	public abstract Amount getUnfilledVolume();

	@Transient
	public abstract Amount getOpenVolume();

	public abstract void setStopAmount(DecimalAmount stopAmount);

	@Nullable
	public Integer getStopAdjustmentCount() {
		if (stopAdjustmentCount == null || stopAdjustmentCount == 0)
			stopAdjustmentCount = 1;
		return stopAdjustmentCount;
	}

	public void setStopAdjustmentCount(Integer adjustmentCount) {
		this.stopAdjustmentCount = adjustmentCount;

	}

	public abstract void setStopPercentage(double percentage);

	public abstract void setTargetPercentage(double percentage);

	public abstract void setTriggerInterval(double triggerInterval);

	public abstract void setTrailingStopAmount(DecimalAmount stopAmount);

	public abstract void setTargetAmount(DecimalAmount targetAmount);

	public abstract void setStopPrice(DecimalAmount stopPrice);

	public abstract void setLastBestPrice(DecimalAmount lastBestPrice);

	public abstract void setTargetPrice(DecimalAmount targetPrice);

	public abstract void setTrailingStopPrice(DecimalAmount trailingStopPrice);

	public abstract void setMarket(Market market);

	public synchronized void setPositionEffect(PositionEffect positionEffect) {
		this.positionEffect = positionEffect;
	}

	public synchronized void setExecutionInstruction(ExecutionInstruction executionInstruction) {
		this.executionInstruction = executionInstruction;
	}

	public synchronized void setParentOrder(Order order) {
		if (order != null)
			log.trace("Order:setParentOrder - setting parent order to " + order.getId() + " / " + System.identityHashCode(order) + " for order " + this.getId()
					+ " / " + System.identityHashCode(this) + ". Calling class " + Thread.currentThread().getStackTrace()[2]);

		if (order == null || (order != null && !order.equals(this))) {

			this.parentOrder = order;
		}
	}

	public synchronized void setParentFill(Fill fill) {
		if (fill != null) {
			log.trace("Order:setParentFill setting parent fill to " + fill.getId() + " / " + System.identityHashCode(fill) + " for order " + this.getId()
					+ " / " + System.identityHashCode(this) + ". Calling class " + Thread.currentThread().getStackTrace()[2]);
			//   for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			//     log.error(ste.toString());
			//}
		}

		this.parentFill = fill;
	}

	public FillType getFillType() {
		return fillType;
	}

	public ContingencyType getContingencyType() {
		return (contingencyType == null ? ContingencyType.DEFAULT : contingencyType);
	}

	public PositionEffect getPositionEffect() {

		return positionEffect;

	}

	public ExecutionInstruction getExecutionInstruction() {

		return executionInstruction;

	}

	public String getComment() {
		return comment;
	}

	public boolean getUsePosition() {
		return usePosition;
	}

	public enum MarginType {
		USE_MARGIN, // trade up to the limit of credit in the quote fungible
		CASH_ONLY, // do not trade more than the available cash-on-hand (quote fungible)
	}

	public MarginType getMarginType() {
		return marginType;
	}

	public Instant getExpiration() {
		return expiration;
	}

	public boolean getPanicForce() {
		return force;
	}

	public boolean setPanicForce() {
		return force;
	}

	public boolean isEmulation() {
		return emulation;
	}

	@Override
	public void persitParents() {

		if (getPortfolio() != null)
			getDao().persist(getPortfolio());

		if (getParentOrder() != null)
			getDao().persist(getParentOrder());

		if (getParentFill() != null)
			getDao().persist(getParentFill());

		for (Transaction transaction : getTransactions())
			getDao().persist(transaction);

		for (Order childOrder : getOrderChildren())
			getDao().persist(childOrder);
		for (OrderUpdate orderUpdate : getOrderUpdates())
			getDao().persist(orderUpdate);

	}

	// @Transient
	@Nullable
	@OneToMany
	//(cascade = CascadeType.PERSIST)
	//, mappedBy = "order")

	(mappedBy = "order")
	//, fetch = FetchType.EAGER)
	//, cascade = CascadeType.MERGE)
	//, fetch = FetchType.LAZY)
	// @OrderBy
	@OrderBy
	// @OrderColumn(name = "time")
	// @OrderColumn(name = "id")
	//, fetch = FetchType.EAGER)
	//, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	public List<Fill> getFills() {
		// if (fills == null)
		//      System.out.println("test");
		return fills;
		// }
	}

	void getAllFillsByParentOrder(Order parentOrder, List allChildren) {
		synchronized (parentOrder.getOrderChildren()) {
			for (Order child : parentOrder.getOrderChildren()) {
				if (child.getFills() != null)
					allChildren.addAll(child.getFills());
				getAllFillsByParentOrder(child, allChildren);

			}
		}
	}

	void getAllOrdersByParentOrder(Order parentOrder, List allChildren) {

		synchronized (parentOrder.getOrderChildren()) {
			for (Order child : parentOrder.getOrderChildren()) {
				// allChildren.addAll(child.getFills());
				allChildren.add(child);
				getAllOrdersByParentOrder(child, allChildren);
			}

		}
	}

	@Transient
	public static Amount getOpenAvgPrice(Collection<SpecificOrder> orders) {

		Amount cumVolume = DecimalAmount.ZERO;
		Amount avgPrice = DecimalAmount.ZERO;

		for (Order order : orders) {
			//  for (Fill pos : getFills()) {

			avgPrice = (cumVolume.plus(order.getUnfilledVolume())).isZero() ? avgPrice
					: ((avgPrice.times(cumVolume, Remainder.ROUND_EVEN)).plus(order.getUnfilledVolume().times(order.getLimitPrice(), Remainder.ROUND_EVEN)))
							.divide(cumVolume.plus(order.getUnfilledVolume()), Remainder.ROUND_EVEN);
			cumVolume = cumVolume.plus(order.getUnfilledVolume());

		}

		return avgPrice;
	}

	@Transient
	public synchronized static Amount getWorkingVolume(Collection<SpecificOrder> orders, Market market) {
		//  if (longAvgStopPrice == null) {
		//    Amount longCumVolume = DecimalAmount.ZERO;
		//  Amount longAvgStopPrice = DecimalAmount.ZERO;
		Amount cumVolume = DecimalAmount.ZERO;
		if (market != null && market.getVolumeBasis() != 0)

			cumVolume = new DiscreteAmount(0, market.getVolumeBasis());

		//= null;
		//        new DiscreteAmount(0,getMarket().getVolumeBasis());

		for (Order order : orders) {
			cumVolume = cumVolume.plus(order.getUnfilledVolume());
			//market=order.getMarket();
		}
		return cumVolume;
	}

	@Transient
	public static Amount getOrderOpenVolume(Collection<Order> orders, Market market) {
		//  if (longAvgStopPrice == null) {
		//    Amount longCumVolume = DecimalAmount.ZERO;
		//  Amount longAvgStopPrice = DecimalAmount.ZERO;
		Amount cumVolume;
		if (market == null || market.getVolumeBasis() == 0)
			cumVolume = DecimalAmount.ZERO;
		else

			cumVolume = new DiscreteAmount(0, market.getVolumeBasis());

		//= null;
		//        new DiscreteAmount(0,getMarket().getVolumeBasis());

		for (Order order : orders) {
			cumVolume = cumVolume.plus(order.getUnfilledVolume());
			//market=order.getMarket();
		}
		return cumVolume;
	}

	@Transient
	public static Amount getOpenAvgStopPrice(Collection<SpecificOrder> orders) {
		//  if (longAvgStopPrice == null) {
		//    Amount longCumVolume = DecimalAmount.ZERO;
		//  Amount longAvgStopPrice = DecimalAmount.ZERO;

		Amount cumVolume = DecimalAmount.ZERO;
		Amount avgStopPrice = DecimalAmount.ZERO;

		for (Order order : orders) {
			Amount parentStopPrice = null;
			Amount stopPrice;
			if (order.getStopPrice() == null && order.getParentOrder() != null && order.getParentOrder().getStopAmount() != null)

				stopPrice = order.getLimitPrice().minus(order.getParentOrder().getStopAmount());
			//  Amount stopPrice = (pos.getStopPrice() == null) ? parentStopPrice : pos.getStopPrice();
			else
				stopPrice = order.getStopPrice();
			if (stopPrice == null)
				continue;
			if (stopPrice != null)
				avgStopPrice = (cumVolume.plus(order.getUnfilledVolume())).isZero() ? avgStopPrice
						: ((avgStopPrice.times(cumVolume, Remainder.ROUND_EVEN)).plus(order.getUnfilledVolume().times(stopPrice, Remainder.ROUND_EVEN)))
								.divide(cumVolume.plus(order.getUnfilledVolume()), Remainder.ROUND_EVEN);

			cumVolume = cumVolume.plus(order.getUnfilledVolume());
		}

		return avgStopPrice;
	}

	public void loadAllChildOrdersByParentOrder(Order parentOrder, Map<Order, Order> orders, Map<Fill, Fill> fills) {
		log.debug(this.getClass().getSimpleName() + " - loadAllChildOrdersByParentOrder for " + parentOrder.getId() + "/" + System.identityHashCode(parentOrder)
				+ " called from class " + Thread.currentThread().getStackTrace()[2]);

		Map withFillsHints = new HashMap();
		Map withTransHints = new HashMap();

		Map withChildrenHints = new HashMap();
		Map withUpdatesHints = new HashMap();
		// Map orderHints = new HashMap();

		// UUID portfolioID = EM.queryOne(UUID.class, queryStr, portfolioName);

		withFillsHints.put("javax.persistence.fetchgraph", "orderWithFills");
		withTransHints.put("javax.persistence.fetchgraph", "orderWithTransactions");
		withChildrenHints.put("javax.persistence.fetchgraph", "orderWithChildOrders");
		withUpdatesHints.put("javax.persistence.fetchgraph", "orderWithOrderUpdates");
		Order orderWithFills;
		Order orderWithChildren;
		Order orderWithTransactions;
		Order orderWithUpdates;
		// so for each fill in the open position we need to load the whole order tree
		// getorder, then get all childe orders, then for each child, load child orders, so on and so forth.

		// load all child orders, and theri child ordres
		// load all parent orders and thier parent orders
		// need to laod all parent fills, their child orders, and their children

		// get a list of all orders in the tree then load 

		//  orderId = fill.getOrder().getId();
		//  if (parentOrder.getParentFill() != null) {

		//    parentOrder.getParentFill().loadAllChildOrdersByFill(parentOrder.getParentFill(), orders, fills);

		// }

		// if (parentOrder.getParentOrder() != null)
		//   parentOrder.getParentOrder().loadAllChildOrdersByParentOrder(parentOrder.getParentOrder());
		// if (!orders.containsKey(parentOrder)){
		try {
			orderWithFills = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withFillsHints, parentOrder.getId());
			orderWithChildren = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withChildrenHints, parentOrder.getId());
			orderWithTransactions = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withTransHints, parentOrder.getId());
			orderWithUpdates = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withUpdatesHints, parentOrder.getId());
		} catch (Error | Exception ex) {
			log.error("Order:loadAllChildOrdersByParentOrdere unable to get order for orderID: " + parentOrder.getId() + ". Full stack trace ", ex);
			return;
		}

		if (orderWithFills != null && orderWithFills.getFills() != null && Hibernate.isInitialized(orderWithFills.getFills())
				&& orderWithFills.getId().equals(parentOrder.getId())) {
			int index = 0;
			for (Fill fill : orderWithFills.getFills()) {

				if (fill.getPortfolio().equals(parentOrder.getPortfolio()))
					fill.setPortfolio(parentOrder.getPortfolio());
				if (fill.getOrder().getPortfolio().equals(parentOrder.getPortfolio()))
					fill.getOrder().setPortfolio(parentOrder.getPortfolio());
				if (fill.getOrder().getParentOrder() != null && fill.getOrder().getParentOrder().getPortfolio().equals(parentOrder.getPortfolio()))
					fill.getOrder().getParentOrder().setPortfolio(parentOrder.getPortfolio());
				if (fill.getPosition() != null && parentOrder.getPortfolio().getPositions().contains(fill.getPosition())) {
					synchronized (parentOrder.getPortfolio()) {
						for (Position fillPosition : parentOrder.getPortfolio().getPositions()) {
							if (fillPosition.equals(fill.getPosition())) {
								fill.setPosition(fillPosition);
								break;
							}
						}
					}
				}
				if (!fills.containsKey(fill)) {
					fills.put(fill, fill);
					log.debug("Order loadAllChildOrdersByParentOrder loading all child order for fill" + fill.getId() + " for order " + orderWithFills.getId()
							+ ". Calling class " + Thread.currentThread().getStackTrace()[2]);
					fill.loadAllChildOrdersByFill(fill, orders, fills);

				} else {
					orderWithFills.getFills().set(index, fills.get(fill));

				}
				index++;
			}
			log.debug("Order:loadAllChildOrdersByParentOrder - Setting fills for order" + parentOrder.getId() + "/" + System.identityHashCode(parentOrder)
					+ " to fills from " + orderWithFills.getId() + "/" + System.identityHashCode(orderWithFills));
			parentOrder.setFills(orderWithFills.getFills());
		} else
			parentOrder.setFills(new CopyOnWriteArrayList<Fill>());

		if (orderWithUpdates != null && orderWithUpdates.getOrderUpdates() != null && Hibernate.isInitialized(orderWithUpdates.getOrderUpdates())
				&& orderWithUpdates.getId().equals(parentOrder.getId())) {

			for (OrderUpdate orderUpdate : orderWithUpdates.getOrderUpdates()) {

				if (!orders.containsKey(orderUpdate.getOrder())) {
					orders.put(orderUpdate.getOrder(), orderUpdate.getOrder());
					if (orderUpdate.getOrder().getPortfolio().equals(parentOrder.getPortfolio()))
						orderUpdate.getOrder().setPortfolio(parentOrder.getPortfolio());
					if (orderUpdate.getOrder().getPortfolio().equals(parentOrder.getPortfolio()))
						orderUpdate.getOrder().setPortfolio(parentOrder.getPortfolio());
					if (orderUpdate.getOrder().getParentOrder() != null
							&& orderUpdate.getOrder().getParentOrder().getPortfolio().equals(parentOrder.getPortfolio()))
						orderUpdate.getOrder().getParentOrder().setPortfolio(parentOrder.getPortfolio());

					orderUpdate.getOrder().loadAllChildOrdersByParentOrder(orderUpdate.getOrder(), orders, fills);
				} else
					orderUpdate.setOrder(orders.get(orderUpdate.getOrder()));

			}

			parentOrder.setOrderUpdates(orderWithUpdates.getOrderUpdates());
		} else
			parentOrder.setOrderUpdates(new CopyOnWriteArrayList<OrderUpdate>());

		if (orderWithTransactions != null && orderWithTransactions.getTransactions() != null && Hibernate.isInitialized(orderWithTransactions.getTransactions())
				&& orderWithTransactions.getId().equals(parentOrder.getId())) {

			for (Transaction transaction : orderWithTransactions.getTransactions()) {
				if (transaction.getPortfolio().equals(parentOrder.getPortfolio()))
					transaction.setPortfolio(parentOrder.getPortfolio());

				//  if (transaction.getOrder().equals(parentOrder))
				//child = parentOrder;
				//    continue;
				if (!orders.containsKey(transaction.getOrder())) {
					orders.put(transaction.getOrder(), transaction.getOrder());
					if (transaction.getOrder().getPortfolio().equals(parentOrder.getPortfolio()))
						transaction.getOrder().setPortfolio(parentOrder.getPortfolio());
					if (transaction.getOrder().getPortfolio().equals(parentOrder.getPortfolio()))
						transaction.getOrder().setPortfolio(parentOrder.getPortfolio());
					if (transaction.getOrder().getParentOrder() != null
							&& transaction.getOrder().getParentOrder().getPortfolio().equals(parentOrder.getPortfolio()))
						transaction.getOrder().getParentOrder().setPortfolio(parentOrder.getPortfolio());

					transaction.getOrder().loadAllChildOrdersByParentOrder(transaction.getOrder(), orders, fills);
				} else
					transaction.setOrder(orders.get(transaction.getOrder()));

			}

			parentOrder.setTransactions(orderWithTransactions.getTransactions());
		} else
			parentOrder.setTransactions(new CopyOnWriteArrayList<Transaction>());

		if (orderWithChildren != null && orderWithChildren.getOrderChildren() != null && Hibernate.isInitialized(orderWithChildren.getOrderChildren())
				&& orderWithChildren.getId().equals(parentOrder.getId())) {
			int index = 0;
			for (Order order : orderWithChildren.getOrderChildren()) {
				//if (order.getId().toString().equals("5678a30e-ff17-43b7-be84-f92734f18d86"))
				//	log.debug("test");
				if (order.getPortfolio().equals(parentOrder.getPortfolio())) {
					log.debug("Order:loadAllChildOrdersByParentOrder - Setting order " + order.getId() + "/" + System.identityHashCode(order)
							+ " portfolio to: " + parentOrder.getPortfolio() + "/" + System.identityHashCode(parentOrder.getPortfolio()));

					order.setPortfolio(parentOrder.getPortfolio());
				}
				if (order.getParentOrder() != null && order.getParentOrder().getPortfolio().equals(parentOrder.getPortfolio())) {
					log.debug("Order:loadAllChildOrdersByParentOrder - Setting parent order " + order.getParentOrder().getId() + "/"
							+ System.identityHashCode(order.getParentOrder()) + " portfolio to: " + parentOrder.getPortfolio() + "/"
							+ System.identityHashCode(parentOrder.getPortfolio()));

					order.getParentOrder().setPortfolio(parentOrder.getPortfolio());
				}
				if (order.getParentFill() != null && order.getParentFill().getPortfolio().equals(parentOrder.getPortfolio())) {
					log.debug("Order:loadAllChildOrdersByParentOrder - Setting parent fill " + order.getParentFill().getId() + "/"
							+ System.identityHashCode(order.getParentFill()) + " portfolio to: " + parentOrder.getPortfolio() + "/"
							+ System.identityHashCode(parentOrder.getPortfolio()));

					order.getParentFill().setPortfolio(parentOrder.getPortfolio());
				}
				if (order.equals(parentOrder))
					//child = parentOrder;
					continue;
				if (!orders.containsKey(order)) {
					orders.put(order, order);
					log.debug("Order:loadAllChildOrdersByParentOrder - Loading child orders for: " + order.getId());
					order.loadAllChildOrdersByParentOrder(order, orders, fills);
				} else
					orderWithChildren.getOrderChildren().set(index, orders.get(order));
				index++;
			}
			log.debug("Order:loadAllChildOrdersByParentOrder - setting orderChildren to " + System.identityHashCode(orderWithChildren.getOrderChildren())
					+ "for order " + parentOrder.getId() + " /" + System.identityHashCode(parentOrder));

			parentOrder.setOrderChildren(orderWithChildren.getOrderChildren());
		} else {
			log.debug("Order:loadAllChildOrdersByParentOrder - setting orderChildren to new array list for order " + parentOrder.getId() + " /"
					+ System.identityHashCode(parentOrder));

			parentOrder.setOrderChildren(new CopyOnWriteArrayList<Order>());
		}
		if (orders.containsKey(parentOrder.getParentOrder())) {
			log.debug("Order:loadAllChildOrdersByParentOrder -setting parent order for" + parentOrder.getId() + " / " + System.identityHashCode(parentOrder)
					+ " to parent order " + orders.get(parentOrder.getParentOrder()).getId() + " / "
					+ System.identityHashCode(orders.get(parentOrder.getParentOrder())));

			setParentOrder(orders.get(parentOrder.getParentOrder()));
		}
		if (fills.containsKey(parentOrder.getParentFill())) {
			log.debug("Order:loadAllChildOrdersByParentOrder - setting parent fill for " + parentOrder.getId() + " / " + System.identityHashCode(parentOrder)
					+ " to fill  " + fills.get(parentOrder.getParentFill()).getId() + " / " + System.identityHashCode(fills.get(parentOrder.getParentFill())));

			setParentFill(fills.get(parentOrder.getParentFill()));
		}

		// System.gc();
	}

	void getAllGeneralOrderByParentOrder(Order paretnOrder, Set<Order> allChildren) {

		synchronized (paretnOrder.getOrderChildren()) {
			for (Order child : paretnOrder.getOrderChildren()) {
				if (child instanceof GeneralOrder)
					if (!allChildren.contains(child))
						allChildren.add(child);
				getAllGeneralOrderByParentOrder(child, allChildren);
			}

		}
	}

	void getAllSpecificOrderByParentOrder(Order parentOrder, Set<Order> allChildren) {

		synchronized (parentOrder.getOrderChildren()) {
			for (Order child : parentOrder.getOrderChildren()) {
				if (child instanceof SpecificOrder)
					if (!allChildren.contains(child))
						allChildren.add(child);
				getAllSpecificOrderByParentOrder(child, allChildren);
			}

		}
	}

	//  @Nullable
	//@OneToMany(cascade = CascadeType.PERSIST, mappedBy = "order")
	//, orphanRemoval = true, cascade = CascadeType.REMOVE)
	//, fetch = FetchType.LAZY)
	//@OrderBy
	// @OrderColumn(name = "id")
	//, fetch = FetchType.EAGER)
	//, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	// @Transient
	@Nullable
	@OneToMany
	//(cascade = CascadeType.PERSIST)
	//, mappedBy = "order")
	(mappedBy = "order")
	//, cascade = CascadeType.MERGE)
	//, fetch = FetchType.LAZY)
	//  @OrderBy
	@OrderBy
	public List<OrderUpdate> getOrderUpdates() {
		return orderUpdates;
		// }
	}

	public Order withTargetPrice(String price) {
		if (getFillType().isTrigger()) {
			this.setTargetPrice(DecimalAmount.of(price));
			return this;
		}
		throw new NotImplementedException();
	}

	public Order withFillType(FillType fillType) {
		this.setFillType(fillType);
		return this;

	}

	public Order withContingencyType(ContingencyType contingencyType) {
		this.setContingencyType(contingencyType);
		return this;

	}

	public Order withParentFill(Fill fill) {
		if (fill != null) {
			fill.addChildOrder(this);
			this.setParentFill(fill);
		}

		return this;

	}

	public Order withTargetPrice(BigDecimal price) {
		if (getFillType().isTrigger()) {
			this.setTargetPrice(DecimalAmount.of(price));
			return this;
		}

		throw new NotImplementedException();
	}

	public Order withStopPrice(String price) {
		if (getFillType().isTrigger()) {
			this.setStopPrice(DecimalAmount.of(price));
			this.stopAdjustmentCount++;

			return this;
		}
		throw new NotImplementedException();
	}

	public Order withStopPrice(BigDecimal price) {
		if (getFillType().isTrigger()) {
			this.setStopPrice(DecimalAmount.of(price));
			this.stopAdjustmentCount++;

			return this;
		}

		throw new NotImplementedException();
	}

	public Order withTrailingStopAmount(BigDecimal price) {
		if (getFillType().isTrigger()) {
			this.setTrailingStopAmount(DecimalAmount.of(price));
			return this;
		}

		throw new NotImplementedException();
	}

	public Order withStopAmount(BigDecimal price) {

		if (getFillType().isTrigger()) {
			if (price.compareTo(BigDecimal.ZERO) == 0)
				return this;
			this.setStopAmount(DecimalAmount.of(price));
			//	this.setStopPercentage(0.0);
			return this;
		}

		throw new NotImplementedException();
	}

	public Order withLastBestPrice(BigDecimal bestLastPrice) {

		if (bestLastPrice.compareTo(BigDecimal.ZERO) == 0 || bestLastPrice.longValue() == Long.MAX_VALUE || bestLastPrice.longValue() == Long.MIN_VALUE)
			return this;
		this.setLastBestPrice(DecimalAmount.of(bestLastPrice));
		return this;

	}

	public Order withStopPercentage(double stopPercentage) {

		if (getFillType().isTrigger()) {
			if (stopPercentage == 0)
				return this;
			this.setStopPercentage(stopPercentage);
			this.setStopAmount(DecimalAmount.of(getLimitPrice().times(stopPercentage, Remainder.ROUND_EVEN)));
			return this;
		}

		throw new NotImplementedException();

	}

	public Order withTargetPercentage(double targetPercentage) {

		if (getFillType().isTrigger()) {
			if (targetPercentage == 0)
				return this;
			this.setTargetPercentage(targetPercentage);
			this.setTargetAmount(DecimalAmount.of(getLimitPrice().times(targetPercentage, Remainder.ROUND_EVEN)));
			return this;
		}

		throw new NotImplementedException();

	}

	public Order withTriggerInterval(double triggerInterval) {

		if (getFillType().isTrigger()) {
			if (triggerInterval == 0)
				return this;
			this.setTriggerInterval(triggerInterval);
			return this;
		}

		throw new NotImplementedException();

	}

	public Order withTargetAmount(BigDecimal price) {
		this.setTargetAmount(DecimalAmount.of(price));
		return this;

	}

	public Order withTimeToLive(long timeToLive) {
		this.setTimeToLive(timeToLive);
		return this;

	}

	public Order withTrailingStopPrice(BigDecimal price, BigDecimal trailingStopPrice) {
		if (getFillType().isTrigger()) {
			this.setTrailingStopAmount(DecimalAmount.of(price));
			this.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
			return this;
		}
		throw new NotImplementedException();
	}

	public Order withTrailingStopPrice(String price, String trailingStopPrice) {
		if (getFillType().isTrigger()) {
			this.setTrailingStopAmount(DecimalAmount.of(price));
			this.setTrailingStopPrice(DecimalAmount.of(trailingStopPrice));
			return this;
		}
		throw new NotImplementedException();
	}

	public Order withComment(String comment) {
		this.setComment(comment);
		return this;
	}

	public Order withUsePosition(boolean usePosition) {
		this.setUsePosition(usePosition);
		return this;
	}

	public Order withParentOrder(Order parentOrder) {
		parentOrder.addChildOrder(this);
		this.setParentOrder(parentOrder);

		return this;
	}

	public Order withOrderGroup(double orderGroup) {
		this.setOrderGroup(orderGroup);
		return this;
	}

	public Order withPositionEffect(PositionEffect positionEffect) {
		this.setPositionEffect(positionEffect);
		return this;
	}

	public Order withExecutionInstruction(ExecutionInstruction executionInstruction) {
		this.setExecutionInstruction(executionInstruction);
		return this;
	}

	@Nullable
	//@OneToMany(mappedBy = "order")
	//, orphanRemoval = true, cascade = CascadeType.REMOVE)
	//@OrderBy
	// @OrderColumn(name = "id")
	//, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	// @Transient
	@OneToMany
	//(cascade = CascadeType.PERSIST)
	//, mappedBy = "order")
	(mappedBy = "order")
	//, fetch = FetchType.EAGER)
	//, cascade = CascadeType.MERGE)
	//, fetch = FetchType.LAZY)
	@OrderBy
	public List<Transaction> getTransactions() {
		if (transactions == null)
			transactions = new ArrayList<Transaction>();
		//  synchronized (lock) {
		return transactions;
		// }
	}

	@Override
	public synchronized void merge() {
		//   synchronized (persistanceLock) {
		try {

			this.setPeristanceAction(PersistanceAction.MERGE);

			this.setRevision(this.getRevision() + 1);
			log.trace("Order - Merge : Merge of Order " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);

			orderDao.merge(this);
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":merge, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}
	}

	public <T> T find() {
		//   synchronized (persistanceLock) {
		try {
			return (T) orderDao.find(Order.class, this.getId());
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {
			return null;
			//System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}
		// return null;
	}

	@Override
	@PostPersist
	public synchronized void postPersist() {
		//   detach();

	}

	//  @PrePersist
	//  @Override
	// public void prePersist() {

	// }

	@Override
	public synchronized void prePersist() {
		if (getDao() != null) {

			EntityBase dbPortfolio = null;
			EntityBase dbParent = null;
			EntityBase dbParentFill = null;
			/*
			 * if (getPortfolio() != null) { try { dbPortfolio = getDao().find(getPortfolio().getClass(), getPortfolio().getId()); if (dbPortfolio != null)
			 * { getPortfolio().setVersion(dbPortfolio.getVersion()); if (getPortfolio().getRevision() > dbPortfolio.getRevision()) {
			 * getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getPortfolio()); } } else {
			 * getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getPortfolio()); } } catch (Exception | Error ex) { if
			 * (dbPortfolio != null) if (getPortfolio().getRevision() > dbPortfolio.getRevision()) {
			 * getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getPortfolio()); } else {
			 * getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getPortfolio()); } } }
			 */
			if (getParentFill() != null) {
				getDao().merge(getParentFill());
				/*
				 * try { dbParentFill = getDao().find(getParentFill().getClass(), getParentFill().getId()); if (dbParentFill != null) {
				 * getParentFill().setVersion(dbParentFill.getVersion()); if (getParentFill().getRevision() > dbParentFill.getRevision()) {
				 * getParentFill().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getParentFill()); } } else if (dbParentFill == null) {
				 * getParentFill().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getParentFill()); } } catch (Exception | Error ex) { if
				 * (dbParentFill != null) if (getParentFill().getRevision() > dbParentFill.getRevision()) {
				 * getParentFill().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getParentFill()); } else {
				 * getParentFill().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getParentFill()); } }
				 */
			}

			if (getParentOrder() != null) {
				getDao().merge(getParentOrder());
				/*
				 * try { dbParent = getDao().find(getParentOrder().getClass(), getParentOrder().getId()); if (dbParent != null) {
				 * getParentOrder().setVersion(dbParent.getVersion()); if (getParentOrder().getRevision() > dbParent.getRevision()) {
				 * getParentOrder().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getParentOrder()); } } else if (dbParent == null) {
				 * getParentOrder().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getParentOrder()); } } catch (Exception | Error ex) { if
				 * (dbParent != null) if (getParentOrder().getRevision() > dbParent.getRevision()) {
				 * getParentOrder().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getParentOrder()); } else {
				 * getParentOrder().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getParentOrder()); } }
				 */
			}

		}

	}

	@Override
	public synchronized void detach() {
		orderDao.detach(this);
	}

	@Override
	@Transient
	public Dao getDao() {
		return orderDao;
	}

	@Override
	@Transient
	public synchronized void setDao(Dao dao) {
		orderDao = (OrderDao) dao;
		// TODO Auto-generated method stub
		//  return null;
	}

	@Override
	public synchronized EntityBase refresh() {
		return orderDao.refresh(this);
	}

	@Override
	public synchronized void persit() {
		//   synchronized (persistanceLock) {
		//  if (this.hasFills()) {
		//    for (Fill fill : this.getFills())

		//PersistUtil.merge(fill);
		//}
		//if (this.hasFills()) {
		// for (Fill fill : getFills()) {

		// if (this.parentOrder != null)
		//   parentOrder.persit();

		// if (this.parentFill != null)
		//   parentFill.persit();
		//   PersistUtil.insert(this);

		//  PersistUtil.find(this);
		//  List<Order> duplicate = orderDao.queryList(Order.class, "select o from Order o where o=?1", this);
		//List<Order> duplicate = null;
		//  EntityBase entity = PersistUtil.find(this);
		try {
			log.debug("Order - Persist : Persit of Order " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);

			this.setPeristanceAction(PersistanceAction.NEW);
			this.setRevision(this.getRevision() + 1);

			orderDao.persist(this);

			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}
		//else
		//  orderDao.merge(this);

		//  PersistUtil.insert(this);
		// else
		//   PersistUtil.merge(this);
		//  }
		// Iterator<Fill> itf = getFills().iterator();
		//while (itf.hasNext()) {
		//                //  for (Fill pos : getFills()) {

		//  Fill fill = itf.next();

		//fillDao.persist(fill);
		//   fill.persit();

		// }

	}

	@Transient
	public Transaction getReservation() {
		synchronized (getTransactions()) {
			for (Transaction tran : getTransactions()) {

				if (tran.getType() == (TransactionType.BUY_RESERVATION) || tran.getType() == (TransactionType.SELL_RESERVATION)) {
					return tran;

				}
			}
		}
		return null;
	}

	@Transient
	public synchronized void removeTransaction(Transaction transaction) {
		synchronized (getTransactions()) {
			getTransactions().remove(transaction);
		}
		transaction.setOrder(null);

	}

	public synchronized void removeChildOrder(Order child) {
		synchronized (getOrderChildren()) {
			getOrderChildren().remove(child);
		}
		child.setParentOrder(null);

	}

	public synchronized void removeFill(Fill fill) {
		synchronized (getFills()) {
			getFills().remove(fill);
		}
		fill.setOrder(null);

	}

	public synchronized void removeOrderUpdate(OrderUpdate orderUpdate) {
		synchronized (getOrderUpdates()) {
			getOrderUpdates().remove(orderUpdate);
		}
		orderUpdate.setOrder(null);

	}

	public synchronized void removeChildOrders() {
		synchronized (getOrderChildren()) {
			getOrderChildren().clear();
		}

	}

	public synchronized void removeTransactions() {
		synchronized (getTransactions()) {
			getTransactions().clear();
		}

	}

	@Nullable
	@OneToMany(mappedBy = "parentOrder")
	//, fetch = CascadeType.EAGER)
	//(orphanRemoval = true, cascade = CascadeType.REMOVE)
	//
	@OrderBy
	//  @OrderColumn(name = "id")
	//  @Transient
	public List<Order> getOrderChildren() {
		//  synchronized (lock) {
		return children;
		//  }
	}

	@Override
	public Order clone() {
		Order clone = null;
		try {
			clone = (Order) super.clone();
			clone.transactions = new ArrayList(this.transactions);
			clone.children = new ArrayList(this.children);
			clone.fills = new ArrayList(this.getFills());
			clone.orderUpdates = new ArrayList(this.getOrderUpdates());

			//deep copying 
		} catch (CloneNotSupportedException cns) {
			log.error("Error while cloning fill", cns);
		}
		return clone;
	}

	public synchronized void addOrderUpdate(OrderUpdate orderUpdate) {
		synchronized (getOrderUpdates()) {
			getOrderUpdates().add(orderUpdate);
		}
	}

	public synchronized void addTransaction(Transaction transaction) {
		if (!getTransactions().contains(transaction))
			synchronized (getTransactions()) {
				getTransactions().add(transaction);
			}
	}

	public synchronized void addChildOrder(Order order) {
		log.debug(this.getClass().getSimpleName() + "addChildOrder - adding child order " + order.getId() + "/" + System.identityHashCode(order) + " to order "
				+ this.getId() + "/" + System.identityHashCode(order));
		if (!getOrderChildren().contains(order) && order != null && !order.equals(this))
			synchronized (getOrderChildren()) {
				getOrderChildren().add(order);
			}
	}

	@Transient
	public boolean hasFills() {
		return !getFills().isEmpty();
	}

	@Transient
	public boolean hasChildren() {
		return !getOrderChildren().isEmpty();
	}

	@Transient
	public abstract boolean isFilled();

	@Transient
	public DecimalAmount getAverageFillPrice() {
		if (!hasFills())
			return DecimalAmount.ZERO;
		BigDecimal sumProduct = BigDecimal.ZERO;
		BigDecimal volume = BigDecimal.ZERO;
		//List<Fill> fills = getFills();
		synchronized (getFills()) {
			for (Fill fill : getFills()) {
				BigDecimal priceBd = fill.getPrice().asBigDecimal();
				BigDecimal volumeBd = fill.getVolume().asBigDecimal();
				sumProduct = sumProduct.add(priceBd.multiply(volumeBd));
				volume = volume.add(volumeBd);
			}
		}
		return (sumProduct.equals(BigDecimal.ZERO)) ? DecimalAmount.ZERO : new DecimalAmount(sumProduct.divide(volume, Amount.mc));
	}

	protected Order() {

	}

	protected Order(Instant time) {
		super(time);

	}

	protected synchronized void setFills(List<Fill> fills) {
		this.fills = fills;
	}

	protected synchronized void setOrderUpdates(List<OrderUpdate> orderUpdates) {
		this.orderUpdates = orderUpdates;
	}

	protected synchronized void setTransactions(List<Transaction> transactions) {
		this.transactions = transactions;
	}

	public synchronized void setFillType(FillType fillType) {
		this.fillType = fillType;
	}

	public synchronized void setContingencyType(ContingencyType contingencyType) {
		this.contingencyType = contingencyType;
	}

	public synchronized void setOrderGroup(double orderGroup) {
		this.orderGroup = orderGroup;
	}

	public synchronized void setComment(String comment) {
		this.comment = comment;
	}

	public synchronized void setUsePosition(boolean usePosition) {
		this.usePosition = usePosition;
	}

	protected synchronized void setMarginType(MarginType marginType) {
		this.marginType = marginType;
	}

	protected synchronized void setForcastedCommission(Amount commission) {
		this.commission = commission;
	}

	protected synchronized void setForcastedMargin(Amount margin) {
		this.margin = margin;
	}

	protected synchronized void setExpiration(Instant expiration) {
		this.expiration = expiration;
	}

	protected synchronized void setPanicForce(boolean force) {
		this.force = force;
	}

	protected synchronized void setEmulation(boolean emulation) {
		this.emulation = emulation;
	}

	public synchronized void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	@Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInstantAsMillisLong")
	@Basic(optional = true)
	public Instant getEntryTime() {
		return entryTime;
	}

	public synchronized void setEntryTime(Instant time) {
		this.entryTime = time;

	}

	@Basic(optional = true)
	public long getTimeToLive() {
		return timeToLive;
	}

	@Transient
	public Instant getExpiryTime() {
		if (getTimeToLive() != 0)
			return getTime().plus(getTimeToLive());
		else
			return null;
	}

	public synchronized void setTimeToLive(long time) {
		this.timeToLive = time;

	}

	protected synchronized void setOrderChildren(List<Order> children) {
		this.children = children;
	}

	protected List<Order> children;

	protected Instant entryTime;
	protected long timeToLive;
	protected Portfolio portfolio;
	protected List<Fill> fills;
	protected List<OrderUpdate> orderUpdates;
	protected List<Transaction> transactions;
	protected FillType fillType;
	protected MarginType marginType;
	protected ContingencyType contingencyType = ContingencyType.DEFAULT;
	protected Amount commission;
	protected Amount margin;
	protected String comment;
	protected PositionEffect positionEffect;
	protected Instant expiration;
	protected ExecutionInstruction executionInstruction;
	protected boolean force; // allow this order to override various types of panic
	protected boolean emulation; // ("allow order type emulation" [default, true] or "only use exchange's native functionality")
	protected boolean usePosition = false;
	protected Order parentOrder;
	protected Fill parentFill;
	protected double orderGroup;

}
