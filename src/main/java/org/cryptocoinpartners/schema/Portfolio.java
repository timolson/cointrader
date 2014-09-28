package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

/**
 * Many Owners may have Stakes in the Portfolio, but there is only one PortfolioManager, who is not necessarily an Owner.  The
 * Portfolio has multiple Positions.
 *
 * @author Tim Olson
 */
@Entity
public class Portfolio extends EntityBase {

	/** returns all Positions, whether they are tied to an open Order or not.  Use getTradeablePositions() */
	public @Transient
	Collection<Position> getPositions() {

		return positions;
	}

	// public @OneToMany ConcurrentHashMap<BalanceType, List<Wallet>> getBalances() { return balances; }

	/**
	 * Returns all Positions in the Portfolio which are not reserved as payment for an open Order
	 */
	@Transient
	public Collection<Position> getTradeablePositions() {
		ArrayList<Position> result = new ArrayList<>();
		for (Position position : positions) {
			if (!position.isReserved())
				result.add(position);
		}
		return result;
	}

	@Transient
	public boolean addTransactions(Transaction transaction) {
		return this.transactions.add(transaction);
	}

	@Transient
	public Collection<Transaction> getTransactions() {
		return this.transactions;
	}

	/**
	 * This is the main way for a Strategy to determine what assets it has available for trading
	 */
	@Transient
	public Collection<Position> getReservePositions() {
		ArrayList<Position> result = new ArrayList<>();
		for (Position position : positions) {
			if (position.isReserved())
				result.add(position);
		}
		return result;
	}

	/**
	 * This is the main way for a Strategy to determine how much of a given asset it has available for trading
	 * @param f
	 * @return
	 */
	@Transient
	public Collection<Position> getTradeablePositionsOf(Asset f) {
		ArrayList<Position> result = new ArrayList<>();
		for (Position position : positions) {
			if (position.getAsset().equals(f) && !position.isReserved())
				result.add(position);
		}
		return result;
	}

	/**
	 * Finds a Position in the Portfolio which has the same Asset as p, then breaks it into the amount p requires
	 * plus an unreserved amount.  The resevered Position is then associated with the given order, while
	 * the unreserved remainder of the Position has getOrder()==null.  To un-reserve the Position, call release(order)
	 *
	 * @param order the order which will be placed
	 * @param p the cost of the order.  could be a different fungible than the order's quote fungible
	 * @throws IllegalArgumentException
	 */
	@Transient
	public void reserve(SpecificOrder order, Position p) throws IllegalArgumentException {
		for (Position position : positions) {
			if (!position.isReserved() && position.getAsset().equals(order.getMarket().getQuote())
					&& position.getExchange().equals(order.getMarket().getExchange())) {
				if (position.getVolumeCount() < p.getVolumeCount())
					throw new IllegalArgumentException("Insufficient funds");
				// subtract the reserve from the existing Position
				position.setVolumeCount(position.getVolumeCount() - p.getVolumeCount());
				// add a new reserve Position
				Position reserve = new Position(p.getPortfolio(), p.getExchange(), p.getMarket(), p.getAsset(), p.getVolume(), p.getPrice());
				reserve.setOrder(order);
				positions.add(reserve);
			}
		}
	}

	@Transient
	public void release(SpecificOrder order) {
		Iterator<Position> iterator = positions.iterator();
		while (iterator.hasNext()) {
			Position position = iterator.next();
			SpecificOrder positionOrder = position.getOrder();
			if (positionOrder != null && positionOrder.equals(order)) {
				position.setOrder(null);
				if (merge(position))
					iterator.remove();
			}
		}
	}

	/**
	 * finds other Positions in this portfolio which have the same Exchange and Asset and merges this position's
	 * amount into the found position's amount, thus maintaining only one Position for each Exchange/Asset pair.
	 * this method does not remove the position from the positions list.
	 * @return true iff another position was found and merged
	 */
	@Transient
	private boolean merge(Position position) {
		for (Position p : positions) {
			if (p.getExchange().equals(position.getExchange()) && p.getAsset().equals(position.getAsset())) {
				p.setVolumeCount(p.getVolumeCount() + position.getVolumeCount());
				return true;
			}
		}
		return false;
	}

	public Portfolio(String name, PortfolioManager manager) {
		this.name = name;
		this.manager = manager;
		this.positions = new ArrayList<>();
		this.balances = new ArrayList<>();
		this.transactions = new ArrayList<>();
	}

	private String name;

	public String getName() {
		return name;
	}

	@OneToMany
	public Collection<Stake> getStakes() {
		return stakes;
	}

	@ManyToOne
	public Asset getBaseAsset() {
		return baseAsset;
	}

	@ManyToOne
	public PortfolioManager getManager() {
		return manager;
	}

	/**
	 * Adds the given position to this Portfolio.  Must be authorized.
	 * @param position
	 * @param authorization
	 */
	@Transient
	protected void modifyPosition(Position position, Authorization authorization) {
		assert authorization != null;
		assert position != null;
		boolean modifiedExistingPosition = false;
		for (Position curPosition : positions) {
			if (curPosition.merge(position)) {
				modifiedExistingPosition = true;
				break;
			}
		}
		if (!modifiedExistingPosition)
			positions.add(position);
	}

	@Override
	public String toString() {

		return getName();
	}

	// JPA
	protected Portfolio() {
	}

	protected void setPositions(Collection<Position> positions) {
		this.positions = positions;
	}

	protected void setBalances(Collection<Balance> balances) {
		this.balances = balances;
	}

	public void setBaseAsset(Asset baseAsset) {
		this.baseAsset = baseAsset;
	}

	protected void setTransactions(Collection<Transaction> transactions) {
		this.transactions = transactions;
	}

	protected void setName(String name) {
		this.name = name;
	}

	protected void setStakes(Collection<Stake> stakes) {
		this.stakes = stakes;
	}

	protected void setManager(PortfolioManager manager) {
		this.manager = manager;
	}

	public static final class Factory {
		/**
		 * Constructs a new instance of {@link Tick}.
		 * @return new TickImpl()
		 */
		public static Portfolio newInstance() {
			return new Portfolio();
		}

		public static Portfolio newInstance(String name, PortfolioManager manager) {
			final Portfolio entity = new Portfolio(name, manager);
			return entity;
		}

		// HibernateEntity.vsl merge-point
	}

	private PortfolioManager manager;

	private Asset baseAsset;
	private Collection<Position> positions = Collections.emptyList();
	private Collection<Balance> balances = Collections.emptyList();
	private Collection<Transaction> transactions = Collections.emptyList();
	private Collection<Stake> stakes = Collections.emptyList();
}
