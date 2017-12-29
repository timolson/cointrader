package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.Cacheable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NamedSubgraph;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.PortfolioDao;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.Remainder;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;

/**
 * Many Owners may have Stakes in the Portfolio, but there is only one PortfolioManager, who is not necessarily an Owner. The Portfolio has muFltiple
 * Positions.
 * 
 * @author Tim Olson
 */
@Entity
@Singleton
//@NamedQueries({ @NamedQuery(name = "Portfolio.findOpenPositions", query = "select po from Portfolio po where po.positions.fills.fill.openVolumeCount<>0", hints = { @QueryHint(name = "javax.persistence.fetchgraph", value = "graph.Portfolio.positions") }) })
//@NamedEntityGraph(name = "graph.Position.fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "fills"), subgraphs = @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode("order")))
//@NamedEntityGraph(name = "graph.Portfolio.positions", attributeNodes = @NamedAttributeNode(value = "positions", subgraph = "positions"), subgraphs = @NamedSubgraph(name = "positions", attributeNodes = @NamedAttributeNode("portfolio")))
//@NamedEntityGraph(name = "graph.Portfolio.positions", attributeNodes = @NamedAttributeNode("positions"))
//select po from portfolio po where p.fill.openVolumeCount<>0
@NamedQueries({ @NamedQuery(name = "Portfolio.findOpenPositions", query = "select p from Portfolio p where name=?1") })
//
@NamedEntityGraphs({

@NamedEntityGraph(name = "portfolioWithPositions", attributeNodes = { @NamedAttributeNode(value = "positions", subgraph = "positionsWithFills")
//,@NamedAttributeNode(value = "fillChildOrders", subgraph = "fillsWithChildOrders")
}, subgraphs = { @NamedSubgraph(name = "positionsWithFills", attributeNodes = { @NamedAttributeNode("fills") })
//,		@NamedSubgraph(name = "fillsWithChildOrders", attributeNodes = { @NamedAttributeNode("fillChildOrders") })
})
//   @NamedSubgraph(name = "fillsWithChildOrders", attributeNodes = { @NamedAttributeNode("fillChildOrders") }) 
//  })
// @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "order"))
//,@NamedSubgraph(name = "order", attributeNodes = @NamedAttributeNode("order")) 
})
// @NamedQueries({ @NamedQuery(name = "Portfolio.findOpenPositions", query = "select po from Portfolio po where po.position.fill.openVolumeCount<>0", hints = { @QueryHint(name = "javax.persistence.fetchgraph", value = "graph.Portfolio.positions") }) })
// @NamedEntityGraph(name = "graph.Position.fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "fills"), subgraphs = { @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode("order")) }),
//        @NamedEntityGraph(name = "graph.Portfolio.positions", attributeNodes = @NamedAttributeNode(value = "positions", subgraph = "positions"), subgraphs = { @NamedSubgraph(name = "positions" attributeNodes = @NamedAttributeNode(value = "positions", subgraph = "fills")
//            ),
//            @NamedSubgraph(
//                name = "Book.authors.name",
//                attributeNodes = @NamedAttributeNode("name")
//            )
//        
//        
//        
//        
//        
//        , attributeNodes = @NamedAttributeNode("fills")) }),
//        @NamedEntityGraph(name = "graph.Position.portfolio", attributeNodes = @NamedAttributeNode("portfolio"))
//
//
@Cacheable
public class Portfolio extends EntityBase {

	private transient static List<Tradeable> markets;

	/** returns all Positions, whether they are tied to an open Order or not. Use getTradeablePositions() */
	public @Transient
	List<Fill> getDetailedPositions() {
		List<Fill> allPositions = new CopyOnWriteArrayList<Fill>();
		synchronized (positionsMap) {
			for (Asset asset : positionsMap.keySet()) {
				// Asset asset = it.next();
				for (Exchange exchange : positionsMap.get(asset).keySet()) {
					for (Listing listing : positionsMap.get(asset).get(exchange).keySet()) {
						for (TransactionType transactionType : positionsMap.get(asset).get(exchange).get(listing).keySet()) {
							for (Iterator<Position> itp = positionsMap.get(asset).get(exchange).get(listing).get(transactionType).iterator(); itp.hasNext();) {
								Position pos = itp.next();
								synchronized (pos.getFills()) {
									for (Fill fill : pos.getFills()) {
										allPositions.add(fill);
									}
								}
							}
						}

					}
				}
			}
		}

		return allPositions;
	}

	@Transient
	public static List<Tradeable> getMarkets() {
		if (markets == null)
			markets = new ArrayList<Tradeable>();

		return markets;

	}

	@Transient
	public static Tradeable getMarket(Tradeable market) {
		synchronized (getMarkets()) {
			for (Tradeable portfolioMarket : getMarkets())
				if (market != null && market.equals(portfolioMarket))

					return portfolioMarket;
			return null;
		}
	}

	@Transient
	public boolean hasMarkets() {
		return (getMarkets() != null && !getMarkets().isEmpty());
	}

	protected synchronized void setMarkets(List<Tradeable> markets) {
		// reset();
		this.markets = markets;

	}

	public synchronized Tradeable addMarket(Tradeable market) {
		//   synchronized (lock) {
		if (!getMarkets().contains(market))
			getMarkets().add(market);

		return getMarket(market);
	}

	public synchronized void addMarket(Collection<Market> markets) {
		getMarkets().addAll(markets);

	}

	public synchronized void removeMarkets(Collection<Market> removedMarkets) {
		getMarkets().removeAll(removedMarkets);
	}

	public synchronized void removeAllMarkets() {

		getMarkets().clear();

	}

	public synchronized void removeMarket(Market market) {
		log.info("removing market: " + market + " from portfolio: " + this);
		if (getMarkets().remove(market))

			log.info("removed market: " + market + " from portfolio: " + this);

	}

	protected @Transient
	synchronized void persistPositions(Asset asset, Exchange exchange, Listing listing) {
		if (positionsMap != null && positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null
				&& positionsMap.get(asset).get(exchange).get(listing) != null)
			for (TransactionType transactionType : positionsMap.get(asset).get(exchange).get(listing).keySet()) {
				for (Position position : positionsMap.get(asset).get(exchange).get(listing).get(transactionType)) {
					position.persit();
				}
			}

	}

	//  fetch = FetchType.EAGER,

	@Nullable
	@OneToMany(mappedBy = "portfolio", fetch = FetchType.EAGER)
	@Fetch(value = FetchMode.SUBSELECT)
	//@OrderColumn(name = "id")
	// @Fetch(value = FetchMode.SUBSELECT)
	//(mappedBy = "portfolio")
	// @OrderColumn(name = "id")
	//, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
	public Set<Position> getPositions() {
		if (positions == null)
			positions = new HashSet<Position>();

		return positions;

	}

	protected synchronized void setPositions(Set<Position> positions) {

		this.positions = positions;

	}

	public void positionReset() {
		synchronized (positions) {
			positions.clear();
		}
		positionsMap.clear();
		restRealisedProfits();
		resetTransactions();

	}

	public void balanceReset() {

		restRealisedProfits();
		resetTransactions();

	}

	public synchronized void restRealisedProfits() {

		for (Asset asset : realisedProfits.keySet())
			for (Exchange exchange : realisedProfits.get(asset).keySet())
				for (Listing listing : realisedProfits.get(asset).get(exchange).keySet())
					realisedProfits.get(asset).get(exchange).put(listing, DecimalAmount.ZERO);

	}

	public synchronized void resetTransactions() {

		for (Asset asset : transactions.keySet())
			for (Exchange exchange : transactions.get(asset).keySet())
				for (TransactionType type : transactions.get(asset).get(exchange).keySet())
					transactions.get(asset).get(exchange).get(type).clear();

	}

	public @Transient
	Collection<Position> getNetPositions() {
		ConcurrentLinkedQueue<Position> allPositions = new ConcurrentLinkedQueue<Position>();
		synchronized (positionsMap) {
			for (Asset asset : positionsMap.keySet()) {
				for (Exchange exchange : positionsMap.get(asset).keySet()) {
					for (Listing listing : positionsMap.get(asset).get(exchange).keySet()) {
						for (TransactionType transactionType : positionsMap.get(asset).get(exchange).get(listing).keySet()) {
							for (Position position : positionsMap.get(asset).get(exchange).get(listing).get(transactionType)) {
								allPositions.add(position);
								//                            for (Fill pos : position.getFills()) {
								//
								//                                if (pos.isLong()) {
								//                                    longAvgPrice = ((longAvgPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getPrice(),
								//                                            Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
								//                                    if (pos.getStopPrice() != null)
								//                                        longAvgStopPrice = ((longAvgStopPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(
								//                                                pos.getStopPrice(), Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getOpenVolume()),
								//                                                Remainder.ROUND_EVEN);
								//
								//                                    longVolume = longVolume.plus(pos.getOpenVolume());
								//                                } else if (pos.isShort()) {
								//                                    shortAvgPrice = ((shortAvgPrice.times(shortVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getPrice(),
								//                                            Remainder.ROUND_EVEN))).dividedBy(shortVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
								//                                    if (pos.getStopPrice() != null)
								//                                        shortAvgStopPrice = ((shortAvgStopPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(
								//                                                pos.getStopPrice(), Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getOpenVolume()),
								//                                                Remainder.ROUND_EVEN);
								//
								//                                    shortVolume = shortVolume.plus(pos.getOpenVolume());
								//                                }
								//                            }
							}
							// need to change this to just return one position that is the total, not one long and one short.
							//                        if (!shortVolume.isZero() || !longVolume.isZero()) {
							//                            Market market = Market.findOrCreate(exchange, listing);
							//                            Fill pos = new Fill();
							//                            pos.setPortfolio(this);
							//                            pos.setMarket(market);
							//
							//                            pos.setPriceCount(longAvgPrice.toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN).getCount());
							//                            pos.setVolumeCount(longVolume.toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN).getCount());
							//                            Position position = new Position(pos);
							//                            allPositions.add(position);
							//                        }

						}
					}
				}
			}
		}
		return allPositions;
	}

	private class handleUpdateWorkingExitRunnable implements Runnable {
		private final Fill fill;

		// protected Logger log;

		public handleUpdateWorkingExitRunnable(Fill fill) {
			this.fill = fill;

		}

		@Override
		public void run() {
			// get all pending orders for this fill

			// cancel the specfic orders that are maker
			// we know this is a closing trade
			// need to update any stop orders with new quanity
			ArrayList<Order> allChildOrders = new ArrayList<Order>();

			allChildOrders = new ArrayList<Order>();

			fill.getAllOrdersByParentFill(allChildOrders);
			TransactionType oppositeSide = (fill.getVolumeCount() > 0) ? TransactionType.SELL : TransactionType.BUY;
			for (Order childOrder : allChildOrders) {
				if ((childOrder.getPositionEffect() == null && childOrder.getTransactionType().equals(oppositeSide))
						|| childOrder.getPositionEffect() == (PositionEffect.CLOSE)) {

					log.info("updating quanity to : " + fill.getOpenVolume().negate() + " for  order: " + childOrder);
					//  orderService.updateWorkingOrderQuantity(childOrder, fill.getOpenVolume().negate());
				}
			}

			/*
			 * ArrayList<Order> allChildOrders = new ArrayList<Order>(); orderService.getAllOrdersByParentFill(fill, allChildOrders); SpecificOrder
			 * specficOrder = null; for (Order childOrder : allChildOrders) if (childOrder instanceof SpecificOrder) { specficOrder = (SpecificOrder)
			 * childOrder; if (specficOrder.getParentFill() != null && specficOrder.getLimitPrice() != null) if (fill.getOpenVolumeCount() == 0)
			 * orderService.cancelOrder(specficOrder); else orderService.updateOrderQuantity(specficOrder, fill.getOpenVolume()); }
			 */

			// cancelStopOrders(fill);
			// fill.persit();
			// portfolio.modifyPosition(fill, new Authorization("Fill for " + fill.toString()));

		}
	}

	private class handleCancelRunnable implements Runnable {
		private final Fill fill;

		// protected Logger log;

		public handleCancelRunnable(Fill fill) {
			this.fill = fill;

		}

		@Override
		public void run() {
			//
			orderService.handleCancelSpecificOrderByParentFill(fill);

			// cancelStopOrders(fill);
			// fill.persit();
			// portfolio.modifyPosition(fill, new Authorization("Fill for " + fill.toString()));

		}
	}

	public @Transient
	Position getNetPosition(Asset asset, Market market, double orderGroup) {
		//ArrayList<Position> allPositions = new ArrayList<Position>();
		Position position = null;
		//TODO need to add these per portfoio, portoflio should not be null
		//  Position position = new Position(null, market.getExchange(), market, asset, DecimalAmount.ZERO, DecimalAmount.ZERO);
		// new ConcurrentLinkedQueue<Transaction>();
		Collection<Fill> fills = new ArrayList<Fill>();
		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(market.getExchange()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()) != null)
			for (TransactionType transactionType : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).keySet()) {
				for (Position detailedPosition : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(transactionType)) {
					synchronized (detailedPosition.getFills()) {
						for (Fill pos : detailedPosition.getFills())
							if (pos.getOrder().getOrderGroup() == orderGroup)
								fills.add(pos);
					}
				}
			}

		return positionFactory.create(fills, market);
	}

	public @Transient
	Position getNetPosition(Asset asset, Market market) {
		//ArrayList<Position> allPositions = new ArrayList<Position>();
		Position position = null;
		//TODO need to add these per portfoio, portoflio should not be null
		//  Position position = new Position(null, market.getExchange(), market, asset, DecimalAmount.ZERO, DecimalAmount.ZERO);
		// new ConcurrentLinkedQueue<Transaction>();
		Collection<Fill> fills = new ArrayList<Fill>();
		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(market.getExchange()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()) != null)
			for (TransactionType transactionType : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).keySet()) {
				for (Position detailedPosition : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(transactionType)) {
					synchronized (detailedPosition.getFills()) {
						for (Fill pos : detailedPosition.getFills())
							fills.add(pos);
					}
				}
			}

		return positionFactory.create(fills, market);
	}

	public @Transient
	Position getLongPosition(Asset asset, Market market) {
		List<Fill> fills = new CopyOnWriteArrayList<Fill>();
		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(market.getExchange()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.BUY) != null)
			for (Position detailedPosition : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.BUY)) {
				synchronized (detailedPosition.getFills()) {
					for (Fill pos : detailedPosition.getFills()) {
						if (pos.getPositionEffect() == null || (pos.getPositionEffect() != null && pos.getMarket().getContractSize(pos.getMarket()) == 1D)
								|| pos.getPositionEffect() == PositionEffect.OPEN)
							fills.add(pos);

					}
				}
			}

		return positionFactory.create(fills, market);

	}

	public @Transient
	Position getLongPosition(Asset asset, Market market, double orderGroup) {
		List<Fill> fills = new CopyOnWriteArrayList<Fill>();
		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(market.getExchange()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.BUY) != null)
			for (Position detailedPosition : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.BUY)) {
				log.debug("Postion: " + detailedPosition.getFills());
				synchronized (detailedPosition.getFills()) {
					for (Fill pos : detailedPosition.getFills()) {
						if (pos.getOrder().getOrderGroup() == orderGroup
								&& (pos.getPositionEffect() == null
										|| (pos.getPositionEffect() != null && pos.getMarket().getContractSize(pos.getMarket()) == 1D) || pos
										.getPositionEffect() == PositionEffect.OPEN))
							fills.add(pos);

					}
				}
			}
		return positionFactory.create(fills, market);

	}

	public @Transient
	Position getShortPosition(Asset asset, Market market) {
		List<Fill> fills = new CopyOnWriteArrayList<Fill>();
		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(market.getExchange()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.SELL) != null)

			for (Position detailedPosition : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.SELL)) {
				synchronized (detailedPosition.getFills()) {
					for (Fill pos : detailedPosition.getFills()) {
						if (pos.getPositionEffect() == null || (pos.getPositionEffect() != null && pos.getMarket().getContractSize(pos.getMarket()) == 1D)
								|| pos.getPositionEffect() == PositionEffect.OPEN)

							fills.add(pos);

					}
				}
			}

		return positionFactory.create(fills, market);
	}

	public @Transient
	Position getShortPosition(Asset asset, Market market, double orderGroup) {
		List<Fill> fills = new CopyOnWriteArrayList<Fill>();
		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(market.getExchange()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()) != null
				&& positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.SELL) != null)

			for (Position detailedPosition : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(TransactionType.SELL)) {
				synchronized (detailedPosition.getFills()) {
					for (Fill pos : detailedPosition.getFills()) {
						if (pos.getOrder().getOrderGroup() == orderGroup
								&& (pos.getPositionEffect() == null
										|| (pos.getPositionEffect() != null && pos.getMarket().getContractSize(pos.getMarket()) == 1D) || pos
										.getPositionEffect() == PositionEffect.OPEN))

							fills.add(pos);

					}
				}
			}

		return positionFactory.create(fills, market);
	}

	public @Transient
	Collection<Position> getPositions(Asset asset, Exchange exchange) {
		Collection<Position> allPositions = new ConcurrentLinkedQueue<Position>();

		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) {
			for (Iterator<Listing> itl = positionsMap.get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
				Listing listing = itl.next();
				for (Iterator<TransactionType> itt = positionsMap.get(asset).get(exchange).get(listing).keySet().iterator(); itt.hasNext();) {
					TransactionType transactionType = itt.next();

					for (Iterator<Position> itp = positionsMap.get(asset).get(exchange).get(listing).get(transactionType).iterator(); itp.hasNext();) {
						Position pos = itp.next();
						allPositions.add(pos);
					}
				}
			}

		}

		return allPositions;

	}

	public @Transient
	Map<Asset, Amount> getRealisedPnLs() {

		Map<Asset, Amount> allPnLs = new ConcurrentHashMap<Asset, Amount>();
		synchronized (getRealisedPnL()) {
			for (Iterator<Asset> it = getRealisedPnL().keySet().iterator(); it.hasNext();) {

				Asset asset = it.next();
				for (Iterator<Exchange> ite = getRealisedPnL().get(asset).keySet().iterator(); ite.hasNext();) {
					Exchange exchange = ite.next();
					for (Iterator<Listing> itl = getRealisedPnL().get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
						Listing listing = itl.next();
						Amount realisedPnL = getRealisedPnL().get(asset).get(exchange).get(listing);

						if (allPnLs.get(asset) == null) {
							allPnLs.put(asset, realisedPnL);
						} else {
							allPnLs.put(asset, allPnLs.get(asset).plus(realisedPnL));
						}
					}

				}

			}
		}
		// }

		return allPnLs;
	}

	public @Transient
	Map<Asset, Amount> getRealisedPnLs(Market market) {

		Map<Asset, Amount> allPnLs = new ConcurrentHashMap<Asset, Amount>();
		synchronized (getRealisedPnL()) {
			for (Iterator<Asset> it = getRealisedPnL().keySet().iterator(); it.hasNext();) {

				Asset asset = it.next();
				for (Iterator<Exchange> ite = getRealisedPnL().get(asset).keySet().iterator(); ite.hasNext();) {
					Exchange exchange = ite.next();
					if (exchange.equals(market.getExchange())) {
						for (Iterator<Listing> itl = getRealisedPnL().get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
							Listing listing = itl.next();
							if (listing.equals(market.getListing())) {
								Amount realisedPnL = getRealisedPnL().get(asset).get(exchange).get(listing);

								if (allPnLs.get(asset) == null) {
									allPnLs.put(asset, realisedPnL);
								} else {
									allPnLs.put(asset, allPnLs.get(asset).plus(realisedPnL));
								}
							}
						}

					}
				}

			}
		}
		// }

		return allPnLs;
	}

	public @Transient
	Amount getRealisedPnL(Asset asset) {

		Amount realisedPnL = DecimalAmount.ZERO;
		synchronized (getRealisedPnL().get(asset)) {
			for (Iterator<Exchange> ite = getRealisedPnL().get(asset).keySet().iterator(); ite.hasNext();) {
				Exchange exchange = ite.next();
				for (Iterator<Listing> itl = getRealisedPnL().get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
					Listing listing = itl.next();
					realisedPnL = realisedPnL.plus(getRealisedPnL().get(asset).get(exchange).get(listing));

				}
			}
		}

		return realisedPnL;
	}

	public @Transient
	Map<Asset, Map<Exchange, Map<Listing, Amount>>> getRealisedPnL() {

		return realisedProfits;
	}

	public @Transient
	DiscreteAmount getNetPosition(Asset asset, Exchange exchange) {
		long netVolumeCount = 0;
		Fill pos = null;
		//  synchronized (lock) {
		if (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) {
			synchronized (positionsMap.get(asset).get(exchange)) {
				for (Iterator<Listing> itl = positionsMap.get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
					Listing listing = itl.next();
					for (Iterator<TransactionType> itt = positionsMap.get(asset).get(exchange).get(listing).keySet().iterator(); itt.hasNext();) {
						TransactionType transactionType = itt.next();

						for (Position itpos : positionsMap.get(asset).get(exchange).get(listing).get(transactionType)) {
							synchronized (itpos.getFills()) {
								for (Iterator<Fill> itp = itpos.getFills().iterator(); itp.hasNext();) {

									pos = itp.next();
									netVolumeCount += pos.getOpenVolumeCount();
								}
							}
						}

					}
				}
			}
		}
		// }
		return new DiscreteAmount(netVolumeCount, asset.getBasis());
	}

	/**
	 * Returns all Positions in the Portfolio which are not reserved as payment for an open Order
	 */
	@Transient
	public Collection<Position> getTradeableBalance(Exchange exchange) {
		throw new NotImplementedException();
	}

	@Transient
	public Collection<Transaction> getTransactions() {
		ConcurrentLinkedQueue<Transaction> allTransactions = new ConcurrentLinkedQueue<Transaction>();
		synchronized (transactions) {
			for (Iterator<Asset> it = transactions.keySet().iterator(); it.hasNext();) {
				Asset asset = it.next();
				for (Iterator<Exchange> ite = transactions.get(asset).keySet().iterator(); ite.hasNext();) {
					Exchange exchange = ite.next();
					for (Iterator<TransactionType> itt = transactions.get(asset).get(exchange).keySet().iterator(); itt.hasNext();) {
						TransactionType type = itt.next();
						for (Iterator<Transaction> ittr = transactions.get(asset).get(exchange).get(type).iterator(); ittr.hasNext();) {
							Transaction tran = ittr.next();
							allTransactions.add(tran);
						}

					}
				}

			}
		}
		return allTransactions;

	}

	private void logCloseOut(long updatedVolumeCount, Fill openFill, Fill closingFill, Boolean closed) {
		//  if (log.isDebugEnabled())
		if (updatedVolumeCount == 0)
			log.info("why closoue is zero");
		if (closed)
			log.info("Closed out " + updatedVolumeCount + " of open fill: " + openFill + " with closing fill: " + closingFill);
		else
			log.info("Closing out " + updatedVolumeCount + " of open fill: " + openFill + " with closing fill: " + closingFill);
	}

	@Transient
	public synchronized void removeTransaction(Transaction reservation) {

		if (transactions == null || transactions.isEmpty())
			return;
		if (reservation == null || reservation.getCurrency() == null)
			return;

		if (transactions.get(reservation.getCurrency()) == null)
			return;
		if (transactions.get(reservation.getCurrency()).get(reservation.getExchange()) == null)
			return;
		if (transactions.get(reservation.getCurrency()).get(reservation.getExchange()).get(reservation.getType()) == null)
			return;
		// synchronized (lock) {
		transactions.get(reservation.getCurrency()).get(reservation.getExchange()).get(reservation.getType()).remove(reservation);

	}

	//            Iterator<Transaction> it = transactions.get(reservation.getCurrency()).get(reservation.getExchange()).get(reservation.getType()).iterator();
	//            while (it.hasNext()) {
	//                Transaction transaction = it.next();
	//                if (transaction != null && reservation != null && transaction.equals(reservation))
	//                    it.remove();
	// }
	//   }

	/**
	 * This is the main way for a Strategy to determine what assets it has available for trading
	 */
	@Transient
	public Collection<Position> getReservedBalances(Exchange exchange) {
		throw new NotImplementedException();
	}

	/**
	 * This is the main way for a Strategy to determine how much of a given asset it has available for trading
	 * 
	 * @param f
	 * @return
	 */
	@Transient
	public Collection<Position> getTradeableBalanceOf(Exchange exchange, Asset asset) {

		throw new NotImplementedException();
	}

	/**
	 * Finds a Position in the Portfolio which has the same Asset as p, then breaks it into the amount p requires plus an unreserved amount. The
	 * resevered Position is then associated with the given order, while the unreserved remainder of the Position has getOrder()==null. To un-reserve
	 * the Position, call release(order)
	 * 
	 * @param order the order which will be placed
	 * @param p the cost of the order. could be a different fungible than the order's quote fungible
	 * @throws IllegalArgumentException
	 */
	@Transient
	public void reserve(SpecificOrder order, Position p) throws IllegalArgumentException {
		throw new NotImplementedException();
	}

	@Transient
	public void release(SpecificOrder order) {
		throw new NotImplementedException();
	}

	@Transient
	public synchronized void addRealisedPnL(Transaction transaction) {
		//if the realised map does not exist then we need to create it.
		try {
			Amount profits = DecimalAmount.ZERO;

			Map<Listing, Amount> marketRealisedProfits;
			if (getRealisedPnL().get(transaction.getCurrency()) == null) {
				Map<Exchange, Map<Listing, Amount>> assetRealisedProfits = new ConcurrentHashMap<Exchange, Map<Listing, Amount>>();
				marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
				marketRealisedProfits.put(transaction.getFill().getMarket().getListing(), profits);
				assetRealisedProfits.put(transaction.getExchange(), marketRealisedProfits);
				getRealisedPnL().put(transaction.getCurrency(), assetRealisedProfits);
			}

			if (getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange()) == null) {
				marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
				marketRealisedProfits.put(transaction.getFill().getMarket().getListing(), profits);
				getRealisedPnL().get(transaction.getCurrency()).put(transaction.getExchange(), marketRealisedProfits);

			}

			if (getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange()).get(transaction.getFill().getMarket().getListing()) == null) {
				getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange()).put(transaction.getFill().getMarket().getListing(), profits);

			}

			log.debug("Portfolio - addRealisedPnL: adding transaction " + transaction + " to "
					+ getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange()).get(transaction.getFill().getMarket().getListing()));
			Amount TotalRealisedPnL = transaction.getAmount().plus(
					getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange()).get(transaction.getFill().getMarket().getListing()));

			getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange())
					.put(transaction.getFill().getMarket().getListing(), TotalRealisedPnL);
		} catch (Exception | Error ex) {
			log.debug("Portfolio - addRealisedPnL: Unable to set relasied PnL", ex);

		}
	}

	@Transient
	public synchronized boolean addTransaction(Transaction transaction) {

		Map<Exchange, Map<TransactionType, ConcurrentLinkedQueue<Transaction>>> assetTransactions = transactions.get(transaction.getCurrency());

		if (assetTransactions == null) {
			ConcurrentLinkedQueue<Transaction> transactionList = new ConcurrentLinkedQueue<Transaction>();
			assetTransactions = new ConcurrentHashMap<Exchange, Map<TransactionType, ConcurrentLinkedQueue<Transaction>>>();
			transactionList.add(transaction);
			Map<TransactionType, ConcurrentLinkedQueue<Transaction>> transactionGroup = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>();
			transactionGroup.put(transaction.getType(), transactionList);
			assetTransactions.put(transaction.getExchange(), transactionGroup);
			transactions.put(transaction.getCurrency(), assetTransactions);
			if (transaction.getType() == TransactionType.REALISED_PROFIT_LOSS)
				addRealisedPnL(transaction);
			//      portfolioService.resetBalances();
			return true;
		} else {
			//asset is present, so check the market
			Map<TransactionType, ConcurrentLinkedQueue<Transaction>> exchangeTransactions = assetTransactions.get(transaction.getExchange());

			if (exchangeTransactions == null) {
				ConcurrentLinkedQueue<Transaction> transactionList = new ConcurrentLinkedQueue<Transaction>();
				transactionList.add(transaction);
				Map<TransactionType, ConcurrentLinkedQueue<Transaction>> transactionGroup = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>();
				transactionGroup.put(transaction.getType(), transactionList);
				assetTransactions.put(transaction.getExchange(), transactionGroup);
				if (transaction.getType() == TransactionType.REALISED_PROFIT_LOSS)
					addRealisedPnL(transaction);
				//    portfolioService.resetBalances();
				return true;
			} else {
				ConcurrentLinkedQueue<Transaction> transactionList = exchangeTransactions.get(transaction.getType());

				if (transactionList == null) {
					transactionList = new ConcurrentLinkedQueue<Transaction>();
					transactionList.add(transaction);
					Amount TotalRealisedPnL = null;
					if (transaction.getType() == TransactionType.REALISED_PROFIT_LOSS)
						addRealisedPnL(transaction);
					exchangeTransactions.put(transaction.getType(), transactionList);
					//         portfolioService.resetBalances();
					return true;
				} else {
					if (transactionList.contains(transaction)) {
						log.debug(this.getClass().getSimpleName() + "addTransaction - transaction " + transaction + " already in transactionList"
								+ transactionList.toString());
						return false;
					}

					transactionList.add(transaction);
					exchangeTransactions.put(transaction.getType(), transactionList);
					if (transaction.getType() == TransactionType.REALISED_PROFIT_LOSS)
						addRealisedPnL(transaction);
					//  portfolioService.resetBalances();
					return true;
				}

			}

		}

	}

	/**
	 * finds other Positions in this portfolio which have the same Exchange and Asset and merges this position's amount into the found position's
	 * amount, thus maintaining only one Position for each Exchange/Asset pair. this method does not remove the position from the positions list.
	 * 
	 * @return true iff another position was found and merged
	 */

	public void routePositionUpdate(Position position, PositionType lastType, Market market, double interval) {
		//	if (position.getAsset().getSymbol().equals("XRP"))
		//	log.debug("test");

		PositionType mergedType = (position.isShort()) ? PositionType.SHORT : (position.isLong()) ? PositionType.LONG : PositionType.FLAT;
		PositionUpdate posUpdate = new PositionUpdate(position, market, interval, lastType, mergedType);
		log.debug(this.getClass().getSimpleName() + ":routePositionUpdate - Routing position update " + posUpdate);
		context.route(posUpdate);
	}

	public void publishPositionUpdate(Position position, PositionType lastType, Market market, double interval) {
		//	if (position.getAsset().getSymbol().equals("XRP"))
		//	log.debug("test");

		PositionType mergedType = (position.isShort()) ? PositionType.SHORT : (position.isLong()) ? PositionType.LONG : PositionType.FLAT;
		PositionUpdate posUpdate = new PositionUpdate(position, market, interval, lastType, mergedType);
		log.debug(this.getClass().getSimpleName() + ":publishPositionUpdate - Publishg position update " + posUpdate);

		context.publish(posUpdate);
	}

	public void addPosition(Position position) {
		// synchronized (lock) {
		getPositions().add(position);

		// }
	}

	public void removePositions(Collection<Position> removedPositions) {
		//   synchronized (lock) {

		for (Position removedPosition : removedPositions) {
			getPositions().remove(removedPosition);
			removedPosition.reset();
			removedPosition.setPortfolio(null);
		}

	}

	public boolean removePosition(Position removePosition) {

		//  System.out.println("removing fill: " + fill + " from position: " + this);
		getPositions().remove(removePosition);

		//   removePosition.setPortfolio(null);
		//}
		removePosition.reset();
		synchronized (removePosition.getFills()) {
			for (Fill fill : removePosition.getFills())
				fill.setPosition(null);
		}
		//  this.merge();
		// return true;

		//}
		return true;

		//TODO We should do a check to make sure the fill is the samme attributes as position
		//}
		//this.exchange = fill.getMarket().getExchange();
		//this.market = fill.getMarket();
		//this.asset = fill.getMarket().getListing().getBase();
		//this.portfolio = fill.getPortfolio();

		// reset();

	}

	public void updateWorkingExitOrders(Fill fill) {
		//  service.submit(new handleUpdateWorkingExitRunnable(fill));
	}

	/*
	 * public void cancelStopOrders(Fill fill) { service.submit(new handleCancelRunnable(fill)); //
	 * orderService.handleCancelSpecificOrderByParentFill(fill); for (Order order : fill.getFillChildOrders()) { if (order instanceof GeneralOrder &&
	 * order.getFillType() == FillType.STOP_LIMIT) { orderService.handleCancelGeneralOrder((GeneralOrder) order); } } // synchronized (lock) { // } }
	 */
	@Transient
	public void insert(Position position) {

		//baseCCY->Exchange->Listing->TransactionType
		TransactionType transactionType = (position.isLong()) ? TransactionType.BUY : TransactionType.SELL;
		Map<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>> assetPositions = positionsMap.get(position.getMarket().getBase());
		if (assetPositions == null) {
			assetPositions = new ConcurrentHashMap<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>>();
			Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>> listingPosition = new ConcurrentHashMap<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>();
			Map<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();

			ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
			detailPosition.add(position);
			positionType.put(transactionType, detailPosition);
			listingPosition.put(position.getMarket().getListing(), positionType);
			assetPositions.put(position.getMarket().getExchange(), listingPosition);
			positionsMap.put(position.getMarket().getBase(), assetPositions);
			return;

		} else {
			Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>> listingPosition = assetPositions.get(position.getMarket().getExchange());
			if (listingPosition == null) {
				listingPosition = new ConcurrentHashMap<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>();
				Map<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
				ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
				detailPosition.add(position);
				positionType.put(transactionType, detailPosition);
				listingPosition.put(position.getMarket().getListing(), positionType);
				assetPositions.put(position.getMarket().getExchange(), listingPosition);
				return;

			} else {
				Map<TransactionType, ConcurrentLinkedQueue<Position>> positionType = listingPosition.get(position.getMarket().getListing());
				if (positionType == null) {
					positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
					ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
					detailPosition.add(position);
					positionType.put(transactionType, detailPosition);
					listingPosition.put(position.getMarket().getListing(), positionType);
					return;

				} else {
					ConcurrentLinkedQueue<Position> positions = positionType.get(transactionType);
					if (positions == null) {
						ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
						detailPosition.add(position);
						positionType.put(transactionType, detailPosition);
						return;
					} else {
						positions.add(position);
						return;
					}

				}

			}

		}

	}

	//TODO hold the positions per order group in seperate map so that the orderupdate published with the positions just for that order group, currently the order groups are colmingled so each order group gets a postions update with the same position in.

	@Transient
	public synchronized boolean merge(Fill fill) {
		fill.persit();
		boolean persit = true;
		if (!fill.getMarket().getSymbol().equals("OKCOIN_QUARTER:BTC.USD.QUARTER"))
			log.debug("test");
		if (fill.getPositionEffect().equals(PositionEffect.CLOSE) && fill.isLong())
			log.debug("closing short");
		if (fill.getPositionEffect().equals(PositionEffect.CLOSE) && fill.isShort())
			log.debug("closing long");
		TransactionType transactionType = (fill.isLong()) ? TransactionType.BUY : TransactionType.SELL;
		TransactionType openingTransactionType = (transactionType == (TransactionType.BUY)) ? TransactionType.SELL : TransactionType.BUY;
		Asset currency = (fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? fill.getMarket().getQuote() : fill.getMarket().getTradedCurrency(
				fill.getMarket());
		PositionEffect openingPositionEffect = null;
		Map<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>> assetPositions = positionsMap.get(fill.getMarket().getBase());
		Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>> listingPosition = new ConcurrentHashMap<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>();
		Map<Listing, Amount> marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
		Map<Exchange, Map<Listing, Amount>> assetRealisedProfits = getRealisedPnL().get(this.getBaseAsset());
		if (assetRealisedProfits != null && assetRealisedProfits.get(fill.getMarket().getExchange()) != null
				&& assetRealisedProfits.get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) != null) {
			marketRealisedProfits = assetRealisedProfits.get(fill.getMarket().getListing());
		}
		if (assetPositions == null) {
			log.debug(this.getClass().getSimpleName() + ":merge. creating new asset positions for fill" + fill);
			ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
			Position detPosition;
			if (fill.getPosition() == null) {
				detPosition = positionFactory.create(fill, fill.getMarket());
				log.debug(this.getClass().getSimpleName() + ":merge. created new position " + detPosition);
				detPosition.persit();
				fill.merge();
			} else {
				detPosition = fill.getPosition();
			}
			log.info(fill + "added to new " + fill.getMarket().getBase() + " position");
			detailPosition.add(detPosition);
			Map<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
			positionType.put(transactionType, detailPosition);
			listingPosition.put(fill.getMarket().getListing(), positionType);
			assetPositions = new ConcurrentHashMap<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>>();
			assetPositions.put(fill.getMarket().getExchange(), listingPosition);
			positionsMap.put(fill.getMarket().getBase(), assetPositions);
			publishPositionUpdate(fill.getPosition(), (fill.isLong()) ? PositionType.LONG : PositionType.SHORT, fill.getMarket(), fill.getOrder()
					.getOrderGroup());
			Amount profits = DecimalAmount.ZERO;
			if (getRealisedPnL() == null || getRealisedPnL().get(this.getBaseAsset()) == null) {
				assetRealisedProfits = new ConcurrentHashMap<Exchange, Map<Listing, Amount>>();
				marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
				marketRealisedProfits.put(fill.getMarket().getListing(), profits);
				assetRealisedProfits.put(fill.getMarket().getExchange(), marketRealisedProfits);
				getRealisedPnL().put(this.getBaseAsset(), assetRealisedProfits);
			}
			if (getRealisedPnL() == null || getRealisedPnL().get(this.getBaseAsset()) == null
					|| getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()) == null) {
				marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
				marketRealisedProfits.put(fill.getMarket().getListing(), profits);
				getRealisedPnL().get(this.getBaseAsset()).put(fill.getMarket().getExchange(), marketRealisedProfits);
			}
			if (getRealisedPnL() == null || getRealisedPnL().get(this.getBaseAsset()) == null
					|| getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()) == null
					|| getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) == null) {
				getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()).put(fill.getMarket().getListing(), profits);
			}

			return true;
		} else {
			Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>> exchangePositions = assetPositions.get(fill.getMarket().getExchange());
			if (exchangePositions == null) {
				log.debug(this.getClass().getSimpleName() + ":merge. creating new exchangePositions for fill" + fill);
				ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
				Map<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
				Position detPosition;
				if (fill.getPosition() == null) {
					detPosition = positionFactory.create(fill, fill.getMarket());
					log.debug(this.getClass().getSimpleName() + ":merge. created new position " + detPosition);
					detPosition.persit();
					fill.merge();
				} else {
					detPosition = fill.getPosition();
				}
				log.info(fill + "added to new " + fill.getMarket().getExchange() + " position");
				detailPosition.add(detPosition);
				positionType.put(transactionType, detailPosition);
				listingPosition.put(fill.getMarket().getListing(), positionType);
				assetPositions.put(fill.getMarket().getExchange(), listingPosition);
				publishPositionUpdate(fill.getPosition(), (fill.isLong()) ? PositionType.LONG : PositionType.SHORT, fill.getMarket(), fill.getOrder()
						.getOrderGroup());
				Amount profits = DecimalAmount.ZERO;
				if (getRealisedPnL() == null || getRealisedPnL().get(this.getBaseAsset()) == null
						|| getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()) == null
						|| getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) == null) {
					marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
					marketRealisedProfits.put(fill.getMarket().getListing(), profits);
					getRealisedPnL().get(this.getBaseAsset()).put(fill.getMarket().getExchange(), marketRealisedProfits);
				}

				return true;
			} else {
				ConcurrentLinkedQueue<Position> listingPositions = exchangePositions.get(fill.getMarket().getListing()).get(transactionType);
				ConcurrentLinkedQueue<Position> openingListingPositions = exchangePositions.get(fill.getMarket().getListing()).get(openingTransactionType);
				if (listingPositions == null) {
					log.debug(this.getClass().getSimpleName() + ":merge. creating new lisiting for fill" + fill);
					ConcurrentLinkedQueue<Position> listingsDetailPosition = new ConcurrentLinkedQueue<Position>();
					Position detPosition;
					if (fill.getPosition() == null) {
						detPosition = positionFactory.create(fill, fill.getMarket());
						log.debug(this.getClass().getSimpleName() + ":merge. created new position " + detPosition);
						// detPosition.getPortfolio().merge();
						detPosition.persit();
						fill.merge();
					} else {
						detPosition = fill.getPosition();
					}
					log.info(fill + "added to new " + transactionType + " position");
					listingsDetailPosition.add(detPosition);
					exchangePositions.get(fill.getMarket().getListing()).put(transactionType, listingsDetailPosition);
					listingPositions = exchangePositions.get(fill.getMarket().getListing()).get(transactionType);
					Amount listingProfits = DecimalAmount.ZERO;
					if (getRealisedPnL() == null || getRealisedPnL().get(this.getBaseAsset()) == null
							|| getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()) == null
							|| getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) == null) {
						marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
						marketRealisedProfits.put(fill.getMarket().getListing(), listingProfits);
						getRealisedPnL().get(this.getBaseAsset()).put(fill.getMarket().getExchange(), marketRealisedProfits);
					}
				} else {
					if (!listingPositions.isEmpty() || listingPositions.peek() != null) {
						Position position = listingPositions.peek();
						log.info(fill + " prepareing to add to  existing " + transactionType + " position:" + position);
						fill.setPosition(position);
						fill.merge();
						if (position.addFill(fill)) {
							log.info(fill + " added to existing " + transactionType + " position:" + position);
							synchronized (position.getFills()) {
								Collections.sort(position.getFills(), timeComparator);
							}
							log.info("sorted exisitng position by time then largest volume:" + position);
						} else
							log.info(fill + " not added to existing " + transactionType + " position:" + position);
					} else {
						Position detPosition;
						if (fill.getPosition() == null) {
							//fill.getPortfolio().merge();
							detPosition = positionFactory.create(fill, fill.getMarket());
							log.debug(this.getClass().getSimpleName() + ":merge. created new position " + detPosition);
							//       detPosition.getPortfolio().merge();
							detPosition.persit();
							fill.merge();
						} else {
							log.debug(this.getClass().getSimpleName() + ":merge. adding to exising fill position  for fill" + fill);
							detPosition = fill.getPosition();
						}
						listingPositions.add(detPosition);
					}
				}
				log.debug(this.getClass().getSimpleName() + ":merge fills -  Determing closeouts fill " + fill + " with open positions "
						+ openingListingPositions);

				if (openingListingPositions != null && !(openingListingPositions.isEmpty())) {
					Amount realisedPnL = DecimalAmount.ZERO;
					long closingVolumeCount = 0;
					Amount entryPrice = DecimalAmount.ZERO;
					Amount exitPrice = DecimalAmount.ZERO;
					Set<Position> positionsToPublish = new HashSet<Position>();
					synchronized (listingPositions) {
						Iterator<Position> lpitr = listingPositions.iterator();

						CLOSEPOSITIONSLOOP: while (lpitr.hasNext()) {
							Position closePos = lpitr.next();
							synchronized (closePos) {
								if (!closePos.hasFills()) {
									log.debug(this.getClass().getSimpleName() + ":merge fills 1 - removing position: " + closePos.getId()
											+ " from listingPositions: " + listingPositions);
									lpitr.remove();
									continue;
								}

								synchronized (closePos.getFills()) {
									Iterator<Fill> cpitr = closePos.getFills().iterator();
									log.debug(this.getClass().getSimpleName() + ":merge fills closePos has " + closePos.getFills().size() + " fills "
											+ closePos.getFills());
									int closedFillCount = 0;
									CLOSEDFILLSLOOP: while (cpitr.hasNext()) {
										log.debug(this.getClass().getSimpleName() + ":merge fills - Starting close outs with closing position "
												+ closePos.getId() + " incrementing loop " + closedFillCount + " with iterator "
												+ System.identityHashCode(cpitr));

										closedFillCount++;
										Fill closePosition = cpitr.next();
										log.debug(this.getClass().getSimpleName() + ":merge fills  - Closing fill " + closePosition.getId() + " loop "
												+ closedFillCount + " with itterator " + System.identityHashCode(cpitr));
										boolean closedFillBreak = false;
										if (closePosition.getOpenVolumeCount() != 0) {
											if (closePosition.getPositionEffect() != null)
												openingPositionEffect = (closePosition.getPositionEffect() == (PositionEffect.CLOSE)) ? PositionEffect.OPEN
														: PositionEffect.CLOSE;

											synchronized (openingListingPositions) {

												Iterator<Position> olpitr = openingListingPositions.iterator();

												OPENPOSITIONSLOOP: while (olpitr.hasNext() && !closedFillBreak) {

													Position openPos = olpitr.next();
													log.debug(this.getClass().getSimpleName() + ":merge fills - Starting close outs with opeingin position "
															+ openPos.getId() + " loop " + closedFillCount + " with iterator " + System.identityHashCode(cpitr));
													if (!openPos.hasFills()) {
														log.debug(this.getClass().getSimpleName() + ":merge fills removing position -3: " + openPos.getId()
																+ " from openingListingPositions: " + openingListingPositions);
														log.debug(this.getClass().getSimpleName() + ":merge fills - Opening Position " + openPos.getId()
																+ " has no fills to close outs with closing fill " + closePosition.getId() + "   loop "
																+ closedFillCount + " with iterator " + System.identityHashCode(cpitr));
														olpitr.remove();
														continue;
													}

													synchronized (openPos.getFills()) {
														Iterator<Fill> opitr = openPos.getFills().iterator();
														OPENFILLSLOOP: while (opitr.hasNext() && !closedFillBreak) {
															Fill openPosition = opitr.next();
															log.debug(this.getClass().getSimpleName() + ":merge fills - Starting close outs with opeing fill  "
																	+ openPosition.getId() + " loop " + closedFillCount + " with iterator "
																	+ System.identityHashCode(cpitr));
															if (openPosition.getOpenVolumeCount() != 0) {
																realisedPnL = DecimalAmount.ZERO;
																closingVolumeCount = 0;
																if (openPosition.getMarket().getListing().getPrompt() == null
																		|| (openPosition.getMarket().getListing().getPrompt() != null && (openPosition
																				.getPositionEffect() == null || openPosition.getPositionEffect() == openingPositionEffect))) {
																	exitPrice = openPosition.getPrice();
																	entryPrice = closePosition.getPrice();
																	closingVolumeCount = (openingTransactionType == (TransactionType.SELL)) ? (Math.min(
																			Math.abs(openPosition.getOpenVolumeCount()),
																			Math.abs(closePosition.getOpenVolumeCount())))
																			* -1 : (Math.min(Math.abs(openPosition.getOpenVolumeCount()),
																			Math.abs(closePosition.getOpenVolumeCount())));
																	if (closingVolumeCount != 0) {
																		long updatedVolumeCount = 0;
																		if ((Math.abs(closePosition.getOpenVolumeCount()) >= Math.abs(openPosition
																				.getOpenVolumeCount()))) {
																			updatedVolumeCount = closePosition.getOpenVolumeCount() + closingVolumeCount;
																			logCloseOut(closingVolumeCount, openPosition, closePosition, false);
																			log.debug(this.getClass().getSimpleName() + ":merge. setting open position "
																					+ openPosition + " open volume count to 0");
																			openPosition.setOpenVolumeCount(0);
																			openPosition.setUpdateTime(context.getTime());
																			openPosition.setPosition(null);
																			openPosition.merge();
																			openPos.reset();
																			openPosition.setHoldingTime(closePosition.getTimestamp()
																					- openPosition.getTimestamp());
																			openPosition.setPosition(null);
																			log.debug(this.getClass().getSimpleName() + ":merge. setting open volume count to "
																					+ (closePosition.getOpenVolumeCount() + closingVolumeCount)
																					+ "closePositionOpenVolumeCount " + closePosition.getOpenVolumeCount()
																					+ "closingVolumeCount" + closingVolumeCount + " for close position  "
																					+ closePosition);
																			closePosition.setOpenVolumeCount(updatedVolumeCount);
																			closePosition.setUpdateTime(context.getTime());
																			closePosition.merge();
																			closePos.reset();
																			if (closePosition.getOpenVolumeCount() == 0) {
																				log.debug(this.getClass().getSimpleName() + ":merge. setting close position "
																						+ closePosition + " position to null");
																				closePosition.setPosition(null);
																				closePosition.setHoldingTime(closePosition.getTimestamp()
																						- openPosition.getTimestamp());
																			}
																			logCloseOut(closingVolumeCount, openPosition, closePosition, true);
																		} else if (closePosition.getOpenVolumeCount() != 0) {
																			updatedVolumeCount = openPosition.getOpenVolumeCount() - closingVolumeCount;
																			logCloseOut(closingVolumeCount, openPosition, closePosition, false);
																			log.debug(this.getClass().getSimpleName() + ":merge. setting close position "
																					+ closePosition + " open volume count to 0");
																			closePosition.setOpenVolumeCount(0);
																			closePosition.setUpdateTime(context.getTime());

																			closePosition.setPosition(null);
																			closePosition.merge();
																			closePos.reset();
																			closePosition.setPosition(null);
																			log.debug(this.getClass().getSimpleName() + ":merge. setting open volume count to "
																					+ (openPosition.getOpenVolumeCount() - closingVolumeCount)
																					+ "openPositionOpenVolumeCount " + openPosition.getOpenVolumeCount()
																					+ "closingVolumeCount" + closingVolumeCount + " for open position  "
																					+ openPosition);
																			openPosition.setOpenVolumeCount(updatedVolumeCount);
																			openPosition.setUpdateTime(context.getTime());

																			openPosition.merge();
																			openPos.reset();
																			openPosition.setHoldingTime(closePosition.getTimestamp()
																					- openPosition.getTimestamp());
																			if (openPosition.getOpenVolumeCount() == 0) {
																				log.debug(this.getClass().getSimpleName() + ":merge. setting open position "
																						+ openPosition + " position to null");
																				openPosition.setPosition(null);
																				openPosition.setHoldingTime(closePosition.getTimestamp()
																						- openPosition.getTimestamp());
																			}

																			logCloseOut(closingVolumeCount, openPosition, closePosition, true);
																		} else if (closePosition.getOpenVolumeCount() == 0) {
																			log.debug(this.getClass().getSimpleName()
																					+ ":merge. setting open position to null " + closePosition
																					+ " as open volume is :" + closePosition.getOpenVolumeCount());
																			closePosition.setPosition(null);

																		}
																		DiscreteAmount volDiscrete = new DiscreteAmount(closingVolumeCount, closePosition
																				.getMarket().getListing().getVolumeBasis());
																		realisedPnL = realisedPnL.plus(((entryPrice.minus(exitPrice)).times(volDiscrete,
																				Remainder.ROUND_EVEN)).times(
																				closePosition.getMarket().getMultiplier(closePosition.getMarket(), entryPrice,
																						exitPrice), Remainder.ROUND_EVEN).times(
																				closePosition.getMarket().getContractSize(closePosition.getMarket()),
																				Remainder.ROUND_EVEN));
																		Amount RealisedPnL = realisedPnL.toBasis(currency.getBasis(), Remainder.ROUND_EVEN);
																		if (RealisedPnL.isNegative()
																				&& closePosition.getPositionEffect() == PositionEffect.OPEN)
																			log.info("realsiedPnL is a loss. netted open position: " + openPosition
																					+ " with closing postion: " + closePosition);
																		if (!RealisedPnL.isZero()) {
																			this.getBaseAsset();
																			Listing listing = Listing.forPair(currency, this.getBaseAsset());
																			Offer rate = quoteService.getImpliedBestAskForListing(listing);
																			Amount baseRealisedPnL = RealisedPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
																			Transaction trans = transactionFactory.create(closePosition, this, closePosition
																					.getMarket().getExchange(), currency, TransactionType.REALISED_PROFIT_LOSS,
																					RealisedPnL, new DiscreteAmount(0, currency.getBasis()));
																			log.info("Realised PnL:" + trans);
																			context.setPublishTime(trans);
																			trans.persit();
																			context.route(trans);
																		}
																		if (closePosition.getOpenVolumeCount() == 0) {
																			log.debug(this.getClass().getSimpleName() + ":Merge - closePosition Position fill "
																					+ closePosition.getId() + " has zero quanityt with opening  fill "
																					+ openPosition.getId() + "  loop " + closedFillCount + " with iterator "
																					+ System.identityHashCode(cpitr));

																			log.info(closePosition + " closed fully out with " + openPosition);
																			closedFillBreak = true;
																		} else
																			log.info(closePosition + " not closed fully out with " + openPosition);

																	}
																}
															}
															if (openPosition.getOpenVolumeCount() == 0) {
																log.debug(this.getClass().getSimpleName() + ":Merge - Opening Position fill "
																		+ openPosition.getId() + " has zero quanityt with close outs with closing fill "
																		+ closePosition.getId() + "   loop " + closedFillCount + " with iterator "
																		+ System.identityHashCode(cpitr));
																openPosition.setPosition(null);
																opitr.remove();
															}
														}
													}
													if (!openPos.hasFills()) {
														log.debug(this.getClass().getSimpleName() + ":Merge - Opening Position  " + openPos.getId()
																+ " has not fills so removing  from openingListingPositions "
																+ System.identityHashCode(openingListingPositions) + "   loop " + closedFillCount
																+ " with iterator " + System.identityHashCode(cpitr));

														olpitr.remove();
														openPos.delete();
													}
													positionsToPublish.add(openPos);
													//	publishPositionUpdate(openPos, (openPos.isLong()) ? PositionType.LONG : PositionType.SHORT,
													//		openPos.getMarket(), 0);
												}
											}
										}
										log.debug(this.getClass().getSimpleName() + ":merge fills - Completed close outs with closing fill "
												+ closePosition.getId() + " loop " + closedFillCount + " with iterator " + System.identityHashCode(cpitr));

										if (closePosition.getOpenVolumeCount() == 0) {
											log.debug(this.getClass().getSimpleName() + ":merge fills - Removing closed outs fills with "
													+ closePosition.getId() + " loop " + closedFillCount + " with iterator " + System.identityHashCode(cpitr));
											closePosition.setPosition(null);

											cpitr.remove();
										}
										log.debug(this.getClass().getSimpleName() + ":merge fills - Completed close outs with closing fill "
												+ closePosition.getId() + "   loop " + closedFillCount + " with iterator " + System.identityHashCode(cpitr));
									}
								}
							}
							if (!closePos.hasFills()) {
								log.debug(this.getClass().getSimpleName() + ":merge fills: Removing position " + closePos.getId() + " from listingPositions "
										+ listingPositions);

								lpitr.remove();
								closePos.delete();
							}
							positionsToPublish.add(closePos);
							//	publishPositionUpdate(closePos, (closePos.isLong()) ? PositionType.LONG : PositionType.SHORT, closePos.getMarket(), 0);

						}
					}
					//looop over latest postions and publish out.
					for (Position position : listingPositions)
						publishPositionUpdate(position, (position.isLong()) ? PositionType.LONG : PositionType.SHORT, position.getMarket(), 0);
					for (Position position : openingListingPositions)
						publishPositionUpdate(position, (position.isLong()) ? PositionType.LONG : PositionType.SHORT, position.getMarket(), 0);
					return true;
				}
				publishPositionUpdate(fill.getPosition(), (fill.isLong()) ? PositionType.LONG : PositionType.SHORT, fill.getMarket(), fill.getOrder()
						.getOrderGroup());
				return true;
			}
		}
	}

	public Portfolio(String name, PortfolioManager manager) {
		this.name = name;
		this.manager = manager;

	}

	private String name;
	private long startingBaseCashBalanceCount;
	private DiscreteAmount startingBaseCashBalance;

	public String getName() {
		return name;
	}

	@Transient
	public Context getContext() {
		return context;
	}

	@Transient
	public QuoteService getQuoteService() {
		return quoteService;
	}

	@Transient
	public PortfolioService getPortfolioService() {
		return portfolioService;
	}

	@Transient
	public OrderService getOrderService() {
		return orderService;
	}

	private static final Comparator<Fill> timeComparator = new Comparator<Fill>() {
		// Order fills oldest first (lower time), then have the biggest quanity first to close out.
		@Override
		public int compare(Fill fill, Fill fill2) {

			int sComp = fill.getTime().compareTo(fill2.getTime());
			if (sComp != 0) {
				return sComp;
			} else {
				return (fill2.getVolume().abs().compareTo(fill.getVolume().abs()));

			}
		}

	};

	@Transient
	public Collection<Order> getAllOrders() {
		Set<Order> orders = new HashSet<Order>();
		for (Position position : getPositions())
			synchronized (position.getFills()) {
				for (Fill positionFill : position.getFills()) {
					orders.add(positionFill.getOrder());
					synchronized (positionFill.getOrder().getFills()) {
						for (Fill fill : positionFill.getOrder().getFills())
							fill.getAllOrdersByParentFill(orders);
					}
					//fills.add(fill);
					// fill.loadAllChildOrdersByFill(fill, portfolioOrders, portfolioFills);

					//fill.getOrder().loadAllChildOrdersByParentOrder(fill.getOrder(), portfolioOrders, portfolioFills);

				}
			}

		return orders;
	}

	@Transient
	public Collection<Fill> getAllFills() {
		Collection<Fill> fills = new ArrayList<Fill>();

		for (Position position : getPositions())
			synchronized (position.getFills()) {
				for (Fill fill : position.getFills()) {
					fills.add(fill);
					// fill.getAllOrdersByParentFill(orders);
				}
			}

		return fills;

	}

	@OneToMany(fetch = FetchType.LAZY)
	@OrderBy
	public List<Stake> getStakes() {
		if (stakes == null)
			return new CopyOnWriteArrayList<>();
		return stakes;
	}

	@Override
	@Transient
	public EntityBase getParent() {

		return null;
	}

	@ManyToOne(fetch = FetchType.EAGER)
	public Asset getBaseAsset() {
		return baseAsset;
	}

	@Embedded
	@Nullable
	@Transient
	public DiscreteAmount getBaseNotionalBalance() {

		if (baseNotionalBalance == null && getBaseAsset() != null && getBaseAsset().getBasis() != 0)

			baseNotionalBalance = DiscreteAmount.withBasis(getBaseAsset().getBasis()).fromCount(baseNotionalBalanceCount);

		return baseNotionalBalance;
	}

	@Embedded
	@Nullable
	public DiscreteAmount getStartingBaseNotionalBalance() {

		if (startingBaseNotionalBalance == null && getBaseAsset() != null && getBaseAsset().getBasis() != 0)

			startingBaseNotionalBalance = DiscreteAmount.withBasis(getBaseAsset().getBasis()).fromCount(startingBaseNotionalBalanceCount);

		return startingBaseNotionalBalance;
	}

	@Embedded
	@Nullable
	public DiscreteAmount getStartingBaseCashBalance() {

		if (startingBaseCashBalance == null && getBaseAsset() != null && getBaseAsset().getBasis() != 0)

			startingBaseCashBalance = DiscreteAmount.withBasis(getBaseAsset().getBasis()).fromCount(startingBaseCashBalanceCount);

		return startingBaseCashBalance;
	}

	protected synchronized void setBaseNotionalBalance(DiscreteAmount baseNotionalBalance) {
		this.baseNotionalBalance = baseNotionalBalance;
	}

	protected long getBaseNotionalBalanceCount() {
		return baseNotionalBalanceCount;
	}

	public synchronized void setBaseNotionalBalanceCount(long baseNotionalBalanceCount) {
		this.baseNotionalBalanceCount = baseNotionalBalanceCount;
		baseNotionalBalance = null;
	}

	protected synchronized void setBaseCashBalance(DiscreteAmount baseCashBalance) {
		this.baseCashBalance = baseCashBalance;
	}

	protected synchronized void setStartingBaseNotionalBalance(DiscreteAmount startingBaseNotionalBalance) {
		this.startingBaseNotionalBalance = startingBaseNotionalBalance;
	}

	protected long getStartingBaseNotionalBalanceCount() {
		return startingBaseNotionalBalanceCount;
	}

	public synchronized void setStartingBaseNotionalBalanceCount(long startingBaseNotionalBalanceCount) {
		this.startingBaseNotionalBalanceCount = startingBaseNotionalBalanceCount;
		startingBaseNotionalBalance = null;
	}

	protected synchronized void setStartingBaseCashBalance(DiscreteAmount startingBaseCashBalance) {
		this.startingBaseCashBalance = startingBaseCashBalance;
	}

	protected long getStartingBaseCashBalanceCount() {
		return startingBaseCashBalanceCount;
	}

	public synchronized void setStartingBaseCashBalanceCount(long startingBaseCashBalanceCount) {
		this.startingBaseCashBalanceCount = startingBaseCashBalanceCount;
		startingBaseCashBalance = null;
	}

	public synchronized void setBaseCashBalanceCount(long baseCashBalanceCount) {
		this.baseCashBalanceCount = baseCashBalanceCount;
		baseCashBalance = null;
	}

	protected long getBaseCashBalanceCount() {
		return baseCashBalanceCount;
	}

	@Embedded
	@Nullable
	public DiscreteAmount getBaseCashBalance() {
		//TODO need to ensure the base asset if loaded with any manytoonemappings, currently lazy loading.
		if (baseCashBalance == null && getBaseAsset() != null && getBaseAsset().getBasis() != 0)

			baseCashBalance = DiscreteAmount.withBasis(getBaseAsset().getBasis()).fromCount(baseCashBalanceCount);

		return baseCashBalance;
	}

	//   @Nullable
	// @OneToMany(mappedBy = "portfolio", fetch = FetchType.EAGER)
	//@OrderColumn(name = "version")

	protected synchronized void setBaseTradingBalance(DiscreteAmount baseTradingBalance) {
		this.baseCashBalance = baseTradingBalance;
	}

	@Transient
	public PortfolioManager getManager() {
		return manager;
	}

	@Transient
	public Logger getLogger() {
		return log;
	}

	/**
	 * Adds the given position to this Portfolio. Must be authorized.
	 * 
	 * @param mean
	 * @param authorization
	 */
	@Transient
	public synchronized void modifyPosition(Fill fill, Authorization authorization) {
		assert authorization != null;
		assert fill != null;
		merge(fill);
		// fill.persit();
		//persistPositions(fill.getMarket().getBase(), fill.getMarket().getExchange(), fill.getMarket().getListing());
		// fill.persit();
		// if 

		//		for (Position curPosition : positions) {
		//			if (curPosition.merge(position)) {
		//				modifiedExistingPosition = true;
		//				break;
		//			}
		//		}
		//		if (!modifiedExistingPosition)
		//			positions.add(position);
	}

	@Override
	public String toString() {

		return getName();
	}

	// JPA
	public Portfolio() {
		this.positionsMap = new ConcurrentHashMap<Asset, Map<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>>>();
		this.realisedProfits = new ConcurrentHashMap<Asset, Map<Exchange, Map<Listing, Amount>>>();
		//this.balances = new CopyOnWriteArrayList<>();
		this.transactions = new ConcurrentHashMap<Asset, Map<Exchange, Map<TransactionType, ConcurrentLinkedQueue<Transaction>>>>();

	}

	protected synchronized void setPositionMap(Map<Asset, Map<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>>> positions) {
		this.positionsMap = positions;
	}

	public synchronized void setBaseAsset(Asset baseAsset) {
		this.baseAsset = baseAsset;
	}

	protected synchronized void setTransactions(Map<Asset, Map<Exchange, Map<TransactionType, ConcurrentLinkedQueue<Transaction>>>> transactions) {
		this.transactions = transactions;
	}

	public synchronized void setName(String name) {
		this.name = name;
	}

	protected synchronized void setContext(Context context) {
		this.context = context;
	}

	protected synchronized void setPortfolioService(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	protected synchronized void setOrderService(OrderService orderService) {
		this.orderService = orderService;
	}

	protected synchronized void setStakes(List<Stake> stakes) {
		this.stakes = stakes;
	}

	@SuppressWarnings("unchecked")
	@Transient
	public synchronized static Portfolio findOrCreate(String portfolioName, Context context) {
		final String queryStr = "select p.id from Portfolio p where name=?1";
		Portfolio myPort = null;
		// final String queryStr = "select p from Portfolio p  JOIN FETCH p.positions where p.name=?1";
		//   final String queryPositoin = "select p from Portfolio p  JOIN FETCH p.fills where p.name=?1";

		try {
			// Map hints = new HashMap();
			//UUID portfolioID = EM.queryOne(UUID.class, queryStr, portfolioName);
			Map hints = new HashMap();
			Map withFillsHints = new HashMap();
			Map withTransHints = new HashMap();

			Map withChildOrderHints = new HashMap();

			// Map orderHints = new HashMap();

			// UUID portfolioID = EM.queryOne(UUID.class, queryStr, portfolioName);
			hints.put("javax.persistence.fetchgraph", "portfolioWithPositions");
			withFillsHints.put("javax.persistence.fetchgraph", "orderWithFills");
			withTransHints.put("javax.persistence.fetchgraph", "orderWithTransactions");
			withChildOrderHints.put("javax.persistence.fetchgraph", "fillWithChildOrders");

			myPort = EM.namedQueryZeroOne(Portfolio.class, "Portfolio.findOpenPositions", hints, portfolioName);

			if (myPort == null)
				return myPort;
			// lets srippp of any emmpty positions.

			myPort.getPositions().remove(Collections.singleton(null));

			// portfiolio
			Map<Order, Order> portfolioOrders = new HashMap<Order, Order>();
			Map<Fill, Fill> portfolioFills = new HashMap<Fill, Fill>();
			Map<Position, Position> portfolioPositions = new HashMap<Position, Position>();

			Iterator<Position> it = myPort.getPositions().iterator();

			while (it.hasNext()) {
				Position position = it.next();
				context.getInjector().injectMembers(position);

				//  for (Position position : myPort.getPositions()) {
				if (!position.hasFills()) {

					it.remove();
					position.delete();
					continue;

				}
				synchronized (position.getFills()) {
					position.getFills().removeAll(Collections.singleton(null));
				}
				portfolioPositions.put(position, position);
				// log.debug("fills" + position.getFills());
				List<Fill> fillsToBeAdded = new ArrayList<Fill>();
				List<Fill> fills = new ArrayList<Fill>();
				int index = 0;
				synchronized (position.getFills()) {
					for (Fill fill : position.getFills()) {
						portfolioFills.put(fill, fill);
					}
				}

				//TODO to figure out why this is doubleing/trebbeling meomory
				synchronized (position.getFills()) {
					for (Fill fill : position.getFills()) {

						// Fill filltest;
						//  if (!portfolioFills.containsKey(fill)) {
						context.getInjector().injectMembers(fill);
						log.debug("Portfolio: findOrCreate Loading all child order for fill " + fill.getId());
						fill.loadAllChildOrdersByFill(fill, portfolioOrders, portfolioFills);
						log.debug("Portfolio: findOrCreate Loading all child order for fill order " + fill.getOrder().getId());
						if (fill.getOrder().getPortfolio().equals(myPort))
							fill.getOrder().setPortfolio(myPort);
						fill.getOrder().loadAllChildOrdersByParentOrder(fill.getOrder(), portfolioOrders, portfolioFills);
						log.debug("Portfolio: findOrCreate loaded all child order for fill order " + fill.getOrder().getId());
						//  } else {
						//    filltest = portfolioFills.get(fill);
						//fill = portfolioFills.get(fill);
						//  position.getFills().set(index, portfolioFills.get(fill));
						// filltest = portfolioFills.get(fill);
						// fill = portfolioFills.get(fill);
						//}
						//index++;
					}
				}
				// when we are loading posiitons we need to link the fill to that position

				//loop over all fills in the position
				// then we load all order by child fill, for each order we load the fills, if the fill belongs to the posiont we get a differnt reference but same id.
				// so when we load any fills, we need to see if we have them loaded somwehere else in the tree,
				// we need to set the loaded fill to the parent fill

				/*
				 * UUID orderId; Fill fillWithChildren = EM.namedQueryZeroOne(Fill.class, "Fill.findFill", withChildOrderHints, fill.getId()); if
				 * (fillWithChildren != null) fill.setFillChildOrders(fillWithChildren.getFillChildOrders()); Order orderWithFills; Order orderWithChildren;
				 * Order orderWithTransactions; // so for each fill in the open position we need to load the whole order tree // getorder, then get all
				 * childe orders, then for each child, load child orders, so on and so forth. // load all child orders, and theri child ordres // load all
				 * parent orders and thier parent orders // need to laod all parent fills, their child orders, and their children // get a list of all
				 * orders in the tree then load orderId = fill.getOrder().getId(); try { orderWithFills = EM.namedQueryZeroOne(Order.class,
				 * "Order.findOrder", withFillsHints, orderId); orderWithChildren = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withChildrenHints,
				 * orderId); orderWithTransactions = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withTransHints, orderId); } catch (Error |
				 * Exception ex) { log.error("Portfolio:findOrCreate unable to get order for orderID: " + orderId); continue; } if ((orderWithFills != null
				 * && orderWithFills instanceof SpecificOrder && orderWithFills.getId().equals(orderId)) && (orderWithTransactions != null &&
				 * orderWithTransactions instanceof SpecificOrder && orderWithTransactions.getId() .equals(orderId)) && (orderWithChildren != null &&
				 * orderWithChildren instanceof SpecificOrder && orderWithChildren.getId().equals(orderId))) { SpecificOrder order = (SpecificOrder)
				 * orderWithFills; order.setTransactions(orderWithTransactions.getTransactions());
				 * order.setOrderChildren(orderWithChildren.getOrderChildren()); fill.setOrder(order);
				 * log.error("Portfolio:findOrCreate found order for orderID: " + orderId); } }
				 */
			}

			myPort.getPositions().remove(Collections.singleton(null));
			log.debug("positions" + myPort.getPositions());

			System.gc();
			for (Order order : myPort.getAllOrders()) {
				log.trace("loding members for order:" + order.getId());
				context.getInjector().injectMembers(order);
				for (Transaction transaction : order.getTransactions()) {
					context.getInjector().injectMembers(transaction);
				}
			}
			System.gc();
			return myPort;
		} catch (Error | Exception ex) {
			log.error("unabled to load porfolio" + ex);
			throw ex;

		}

		//     PersistUtil.queryOne(Portfolio.class, queryStr, portfolioName);

	}

	protected synchronized void setManager(PortfolioManager manager) {
		this.manager = manager;
	}

	@Override
	// @Transactional
	public synchronized void persit() {

		try {
			log.debug("Portfolio - Persist : Persit of Portfolio " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);

			this.setPeristanceAction(PersistanceAction.NEW);
			this.setRevision(this.getRevision() + 1);

			portfolioDao.persist(this);

			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}

	}

	// @Transactional
	@Override
	public synchronized EntityBase refresh() {
		return portfolioDao.refresh(this);
	}

	@Override
	public synchronized void merge() {

		this.setPeristanceAction(PersistanceAction.MERGE);

		this.setRevision(this.getRevision() + 1);
		log.debug("Portfolio - Portfolio : Merge of Portfolio " + this.getId() + " called from class " + Thread.currentThread().getStackTrace()[2]);

		try {

			portfolioDao.merge(this);

			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {

			System.out.println("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}
	}

	private transient PortfolioManager manager;

	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.portfolio");
	@Inject
	public transient Context context;
	@Inject
	protected transient PortfolioService portfolioService;
	@Inject
	protected transient OrderService orderService;

	@Inject
	protected transient QuoteService quoteService;

	@Inject
	protected transient PositionFactory positionFactory;

	@Inject
	protected transient TransactionFactory transactionFactory;

	@Inject
	protected transient PortfolioDao portfolioDao;

	private Asset baseAsset;
	private DiscreteAmount baseNotionalBalance;
	private long baseNotionalBalanceCount;
	private DiscreteAmount startingBaseNotionalBalance;
	private long startingBaseNotionalBalanceCount;
	private DiscreteAmount baseCashBalance;
	protected List<Holding> holdings;
	private long baseCashBalanceCount;
	private transient Map<Asset, Map<Exchange, Map<Listing, Map<TransactionType, ConcurrentLinkedQueue<Position>>>>> positionsMap;
	private transient Map<Asset, Map<Exchange, Map<Listing, Amount>>> realisedProfits;
	private transient Map<Asset, Map<Exchange, Map<TransactionType, ConcurrentLinkedQueue<Transaction>>>> transactions;
	private transient List<Stake> stakes;

	private transient Set<Position> positions;

	//new ConcurrentHashSet<>();

	//private ConcurrentHashMap<Market, ConcurrentSkipListMap<Long,ArrayList<TaxLot>>> longTaxLots;
	//private ConcurrentHashMap<Market, ConcurrentSkipListMap<Long,ArrayList<TaxLot>>> shortTaxLots;

	public <T> T find() {
		//   synchronized (persistanceLock) {
		try {
			return (T) portfolioDao.find(Portfolio.class, this.getId());
			//if (duplicate == null || duplicate.isEmpty())
		} catch (Exception | Error ex) {
			return null;
			// System.out.println("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:" + ex);
			// ex.printStackTrace();

		}

	}

	@Override
	@Transient
	public Dao getDao() {
		return portfolioDao;
	}

	@Override
	@Transient
	public void setDao(Dao dao) {
		this.portfolioDao = (PortfolioDao) dao;
	}

	@Override
	public synchronized void detach() {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void delete() {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void prePersist() {

		if (getDao() != null) {

			EntityBase dbBaseAsset = null;
			EntityBase dbPortfolio = null;
			if (getBaseAsset() != null) {

				getDao().merge(getBaseAsset());
				/*
				 * try { dbBaseAsset = getDao().find(getBaseAsset().getClass(), getBaseAsset().getId()); if (dbBaseAsset != null && dbBaseAsset.getVersion()
				 * != getBaseAsset().getVersion()) { getBaseAsset().setVersion(dbBaseAsset.getVersion()); if (getBaseAsset().getRevision() >
				 * dbBaseAsset.getRevision()) { // getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getBaseAsset()); } } else {
				 * getBaseAsset().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getBaseAsset()); } } catch (Exception | Error ex) { if
				 * (dbBaseAsset != null) if (getBaseAsset().getRevision() > dbBaseAsset.getRevision()) { //
				 * getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(getBaseAsset()); } else { //
				 * getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(getBaseAsset()); } }
				 */

			}

			/*
			 * try { dbPortfolio = getDao().find(this.getClass(), this.getId()); if (dbPortfolio != null && dbPortfolio.getVersion() != this.getVersion()) {
			 * this.setVersion(dbPortfolio.getVersion()); if (this.getRevision() > dbPortfolio.getRevision()) { //
			 * getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(this); } } else {
			 * this.setPeristanceAction(PersistanceAction.NEW); getDao().persist(this); } } catch (Exception | Error ex) { if (dbPortfolio != null) if
			 * (this.getRevision() > dbPortfolio.getRevision()) { // getPortfolio().setPeristanceAction(PersistanceAction.MERGE); getDao().merge(this); }
			 * else { // getPortfolio().setPeristanceAction(PersistanceAction.NEW); getDao().persist(this); } }
			 */

			// this.persit();
			//  synchronized (getPositions()) {
			//  for (Position position : getPositions())
			//  getDao().merge(position);
			// }
		}
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void postPersist() {
		// TODO Auto-generated method stub

	}

	@Override
	public void persitParents() {
		// TODO Auto-generated method stub

	}

}
