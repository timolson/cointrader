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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import com.google.common.collect.Sets;
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
@NamedQueries({@NamedQuery(name = "Portfolio.findOpenPositions", query = "select p from Portfolio p where name=?1")})
//
@NamedEntityGraphs({

@NamedEntityGraph(name = "portfolioWithPositions", attributeNodes = {@NamedAttributeNode(value = "positions", subgraph = "positionsWithFills")},
    subgraphs = {@NamedSubgraph(name = "positionsWithFills", attributeNodes = {@NamedAttributeNode("fills")})
    //     @NamedSubgraph(name = "fillsWithChildOrders", attributeNodes = { @NamedAttributeNode("fillChildOrders") }) 
    })
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

  private transient static Object lock = new Object();
  private transient static ExecutorService service = Executors.newFixedThreadPool(10);
  private transient static List<Tradeable> markets = new ArrayList<Tradeable>();

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
                for (Fill fill : pos.getFills()) {
                  allPositions.add(fill);
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
    positions.clear();
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
              Amount longVolume = DecimalAmount.ZERO;
              Amount longAvgPrice = DecimalAmount.ZERO;
              Amount longAvgStopPrice = DecimalAmount.ZERO;
              Amount shortVolume = DecimalAmount.ZERO;
              Amount shortAvgPrice = DecimalAmount.ZERO;
              Amount shortAvgStopPrice = DecimalAmount.ZERO;
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
          for (Fill pos : detailedPosition.getFills())
            if (pos.getOrder().getOrderGroup() == orderGroup)
              fills.add(pos);
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
          for (Fill pos : detailedPosition.getFills())
            fills.add(pos);
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

        for (Fill pos : detailedPosition.getFills()) {
          if (pos.getPositionEffect() == null || pos.getPositionEffect() == PositionEffect.OPEN)
            fills.add(pos);

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
        for (Fill pos : detailedPosition.getFills()) {
          if (pos.getOrder().getOrderGroup() == orderGroup && (pos.getPositionEffect() == null || pos.getPositionEffect() == PositionEffect.OPEN))
            fills.add(pos);

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

        for (Fill pos : detailedPosition.getFills()) {
          if (pos.getPositionEffect() == null || pos.getPositionEffect() == PositionEffect.OPEN)

            fills.add(pos);

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

        for (Fill pos : detailedPosition.getFills()) {
          if (pos.getOrder().getOrderGroup() == orderGroup && (pos.getPositionEffect() == null || pos.getPositionEffect() == PositionEffect.OPEN))

            fills.add(pos);

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
  ConcurrentHashMap<Asset, Amount> getRealisedPnLs() {

    ConcurrentHashMap<Asset, Amount> allPnLs = new ConcurrentHashMap<Asset, Amount>();
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
  ConcurrentHashMap<Asset, Amount> getRealisedPnLs(Market market) {

    ConcurrentHashMap<Asset, Amount> allPnLs = new ConcurrentHashMap<Asset, Amount>();
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
  ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> getRealisedPnL() {

    return realisedProfits;
  }

  /*
   * public @Transient DiscreteAmount getLongPositionAmount(Asset asset, Exchange exchange) { long longVolumeCount = 0; // synchronized (lock) { if
   * (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) { for (Iterator<Listing> itl =
   * positionsMap.get(asset).get(exchange).keySet().iterator(); itl.hasNext();) { Listing listing = itl.next(); for (Position itpos :
   * positionsMap.get(asset).get(exchange).get(listing).get(TransactionType.BUY)) { for (Iterator<Fill> itp = itpos.getFills().iterator();
   * itp.hasNext();) { Fill pos = itp.next(); longVolumeCount += pos.getOpenVolumeCount(); } } } } // } return new DiscreteAmount(longVolumeCount,
   * asset.getBasis()); }
   */
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
              for (Iterator<Fill> itp = itpos.getFills().iterator(); itp.hasNext();) {

                pos = itp.next();
                netVolumeCount += pos.getOpenVolumeCount();
              }
            }

          }
        }
      }
    }
    // }
    return new DiscreteAmount(netVolumeCount, asset.getBasis());
  }

  /*
   * public @Transient DiscreteAmount getShortPositionAmount(Asset asset, Exchange exchange) { long shortVolumeCount = 0; // synchronized (lock) { if
   * (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) { for (Iterator<Listing> itl =
   * positionsMap.get(asset).get(exchange).keySet().iterator(); itl.hasNext();) { Listing listing = itl.next(); for (Position itpos :
   * positionsMap.get(asset).get(exchange).get(listing).get(TransactionType.SELL)) { for (Iterator<Fill> itp = itpos.getFills().iterator();
   * itp.hasNext();) { Fill pos = itp.next(); shortVolumeCount += pos.getOpenVolumeCount(); } } } } // } return new DiscreteAmount(shortVolumeCount,
   * asset.getBasis()); }
   */

  // public @OneToMany ConcurrentHashMap<BalanceType, List<Wallet>> getBalances() { return balances; }

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

      ConcurrentHashMap<Listing, Amount> marketRealisedProfits;
      if (getRealisedPnL().get(transaction.getCurrency()) == null) {
        ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>> assetRealisedProfits = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>();
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

    ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>> assetTransactions = transactions
        .get(transaction.getCurrency());

    if (assetTransactions == null) {
      ConcurrentLinkedQueue<Transaction> transactionList = new ConcurrentLinkedQueue<Transaction>();
      assetTransactions = new ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>>();
      transactionList.add(transaction);
      ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>> transactionGroup = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>();
      transactionGroup.put(transaction.getType(), transactionList);
      assetTransactions.put(transaction.getExchange(), transactionGroup);
      transactions.put(transaction.getCurrency(), assetTransactions);
      if (transaction.getType() == TransactionType.REALISED_PROFIT_LOSS)
        addRealisedPnL(transaction);
      //      portfolioService.resetBalances();
      return true;
    } else {
      //asset is present, so check the market
      ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>> exchangeTransactions = assetTransactions.get(transaction.getExchange());

      if (exchangeTransactions == null) {
        ConcurrentLinkedQueue<Transaction> transactionList = new ConcurrentLinkedQueue<Transaction>();
        transactionList.add(transaction);
        ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>> transactionGroup = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>();
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

  protected void publishPositionUpdate(Position position, PositionType lastType, Market market, double interval) {

    PositionType mergedType = (position.isShort()) ? PositionType.SHORT : (position.isLong()) ? PositionType.LONG : PositionType.FLAT;

    context.publish(new PositionUpdate(position, market, interval, lastType, mergedType));
  }

  public synchronized void addPosition(Position position) {
    // synchronized (lock) {
    getPositions().add(position);
    // }
  }

  public synchronized void removePositions(Collection<Position> removedPositions) {
    //   synchronized (lock) {
    this.positions.removeAll(removedPositions);
    for (Position removedPosition : removedPositions) {
      removedPosition.reset();
      removedPosition.setPortfolio(null);
    }

  }

  public boolean removePosition(Position removePosition) {
    synchronized (positions) {
      //  System.out.println("removing fill: " + fill + " from position: " + this);
      positions.remove(removePosition);
      removePosition.reset();
    }
    //   removePosition.setPortfolio(null);
    //}

    for (Fill fill : removePosition.getFills())
      fill.setPosition(null);
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
    ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>> assetPositions = positionsMap
        .get(position.getMarket().getBase());
    if (assetPositions == null) {
      assetPositions = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>();
      ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>> listingPosition = new ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>();
      ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();

      ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
      detailPosition.add(position);
      positionType.put(transactionType, detailPosition);
      listingPosition.put(position.getMarket().getListing(), positionType);
      assetPositions.put(position.getMarket().getExchange(), listingPosition);
      positionsMap.put(position.getMarket().getBase(), assetPositions);
      return;

    } else {
      ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>> listingPosition = assetPositions.get(position
          .getMarket().getExchange());
      if (listingPosition == null) {
        listingPosition = new ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>();
        ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
        ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
        detailPosition.add(position);
        positionType.put(transactionType, detailPosition);
        listingPosition.put(position.getMarket().getListing(), positionType);
        assetPositions.put(position.getMarket().getExchange(), listingPosition);
        return;

      } else {
        ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>> positionType = listingPosition.get(position.getMarket().getListing());
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
  public boolean merge(Fill fill) {
    boolean persit = true;
    if (fill.getPositionEffect().equals(PositionEffect.CLOSE) && fill.isLong())
      log.debug("closing short");
    if (fill.getPositionEffect().equals(PositionEffect.CLOSE) && fill.isShort())
      log.debug("closing long");

    //TODO only ensure we are persiting when we need to curernlty persiting very fill 50 times!!!!!
    log.debug(this.getClass().getSimpleName() + ":merge. Determing position for fill" + fill + " with position map " + positionsMap);
    //  if (fill.getMarket().getSymbol().equals("OKCOIN_THISWEEK:LTC.USD.THISWEEK"))
    //    log.debug(this.getClass().getSimpleName() + ":merge. Determing position for fill" + fill);

    //synchronized (lock) {
    // We need to have a queue of buys and a queue of sells ( two array lists), ensure the itterator is descendingIterator for LIFO,
    // when we get a new trade coem in we add it to the buy or sell queue
    // 1) caluate price difference
    // 2) times price diff by min(trade quantity or the position) and add to relasied PnL
    // 3) update the quaitity of the postion and remove from queue if zero
    // 4) move onto next postion until the qty =0

    // https://github.com/webpat/jquant-core/blob/173d5ca79b318385a3754c8e1357de79ece47be4/src/main/java/org/jquant/portfolio/Portfolio.java
    //  synchronized (fill.getMarket()) {
    TransactionType transactionType = (fill.isLong()) ? TransactionType.BUY : TransactionType.SELL;
    TransactionType openingTransactionType = (transactionType == (TransactionType.BUY)) ? TransactionType.SELL : TransactionType.BUY;
    Asset currency = (fill.getMarket().getTradedCurrency(fill.getMarket()) == null) ? fill.getMarket().getQuote() : fill.getMarket()
        .getTradedCurrency(fill.getMarket());

    PositionEffect openingPositionEffect = null;

    ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>> assetPositions = positionsMap
        .get(fill.getMarket().getBase());
    ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>> listingPosition = new ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>();
    //ConcurrentHashMap<Listing, ArrayList<Position>> listingPosition = new ConcurrentHashMap<Listing, ArrayList<Position>>();
    ConcurrentHashMap<Listing, Amount> marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();

    ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>> assetRealisedProfits = getRealisedPnL().get(this.getBaseAsset());
    //looks like an issue here that the asset release profits is not created
    if (assetRealisedProfits != null && assetRealisedProfits.get(fill.getMarket().getExchange()) != null
        && assetRealisedProfits.get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) != null) {
      // this is not created
      marketRealisedProfits = assetRealisedProfits.get(fill.getMarket().getListing());
    }

    if (assetPositions == null) {
      log.debug(this.getClass().getSimpleName() + ":merge. creating new asset positions for fill" + fill);

      ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
      // need to wrapper position!!
      Position detPosition;
      if (fill.getPosition() == null) {
        // fill.getPortfolio().merge();
        detPosition = positionFactory.create(fill, fill.getMarket());
        log.debug(this.getClass().getSimpleName() + ":merge. created new position " + detPosition);
        //   detPosition.getPortfolio().merge();
        detPosition.persit();
        fill.merge();

        // fill.merge();

      } else {
        detPosition = fill.getPosition();

        //    detPosition.merge();
        //   persit = false;

      }
      log.info(fill + "added to new " + fill.getMarket().getBase() + " position");
      //  if (persit)

      //   new Position(fill);

      //  PersistUtil.persist(detPosition);
      //    Collections.sort(detPosition.getFills(), timeComparator);
      detailPosition.add(detPosition);

      ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
      positionType.put(transactionType, detailPosition);

      listingPosition.put(fill.getMarket().getListing(), positionType);
      assetPositions = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>();
      assetPositions.put(fill.getMarket().getExchange(), listingPosition);
      positionsMap.put(fill.getMarket().getBase(), assetPositions);
      publishPositionUpdate(fill.getPosition(), (fill.isLong()) ? PositionType.LONG : PositionType.SHORT, fill.getMarket(), fill.getOrder()
          .getOrderGroup());

      Amount profits = DecimalAmount.ZERO;
      if (getRealisedPnL() == null || getRealisedPnL().get(this.getBaseAsset()) == null) {
        assetRealisedProfits = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>();
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
      //     publishPositionUpdate(fill.getPosition(), PositionType.FLAT, fill.getMarket(), fill.getOrder().getOrderGroup());

      //  else
      //    detPosition.merge();
      //  fill.merge();
      return true;
    } else {

      //asset is present, so check the market
      ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>> exchangePositions = assetPositions.get(fill
          .getMarket().getExchange());
      //	Amount exchangeRealisedProfits = realisedProfits.get(position.getMarket().getTradedCurrency()).get(position.getExchange())
      //	.get(position.getMarket().getListing());

      if (exchangePositions == null) {
        log.debug(this.getClass().getSimpleName() + ":merge. creating new exchangePositions for fill" + fill);

        ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
        ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
        Position detPosition;
        if (fill.getPosition() == null) {
          //  fill.getPortfolio().merge();
          detPosition = positionFactory.create(fill, fill.getMarket());
          log.debug(this.getClass().getSimpleName() + ":merge. created new position " + detPosition);
          //  detPosition.getPortfolio().merge();
          detPosition.persit();
          fill.merge();

        } else {
          detPosition = fill.getPosition();
          //   detPosition.merge();
        }
        log.info(fill + "added to new " + fill.getMarket().getExchange() + " position");

        //   PersistUtil.persist(detPosition);
        //          Collections.sort(detPosition.getFills(), timeComparator);

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
        //    publishPositionUpdate(fill.getPosition(), PositionType.FLAT, fill.getMarket(), fill.getOrder().getOrderGroup());
        //else
        //  detPosition.merge();
        //fill.merge();

        return true;
      } else {

        //ConcurrentHashMap<TransactionType, ArrayList<Position>> listingPositions = exchangePositions.get(position.getMarket().getListing());
        //asset is present, so check the market
        // need yo vhnage this to have tne cocnurrent hashmap on here
        //ConcurrentHashMap<TransactionType, ArrayList<Position>> listingPositions = exchangePositions.get(position.getMarket().getListing());
        ConcurrentLinkedQueue<Position> listingPositions = exchangePositions.get(fill.getMarket().getListing()).get(transactionType);
        ConcurrentLinkedQueue<Position> openingListingPositions = exchangePositions.get(fill.getMarket().getListing()).get(openingTransactionType);

        if (listingPositions == null) {
          log.debug(this.getClass().getSimpleName() + ":merge. creating new lisiting for fill" + fill);

          ConcurrentLinkedQueue<Position> listingsDetailPosition = new ConcurrentLinkedQueue<Position>();
          Position detPosition;
          if (fill.getPosition() == null) {
            //  fill.getPortfolio().merge();
            detPosition = positionFactory.create(fill, fill.getMarket());
            log.debug(this.getClass().getSimpleName() + ":merge. created new position " + detPosition);
            // detPosition.getPortfolio().merge();
            detPosition.persit();
            fill.merge();

          } else {
            detPosition = fill.getPosition();
            //  detPosition.merge();

          }
          log.info(fill + "added to new " + transactionType + " position");
          // PersistUtil.persist(detPosition);
          //   Collections.sort(detPosition.getFills(), timeComparator);

          listingsDetailPosition.add(detPosition);
          exchangePositions.get(fill.getMarket().getListing()).put(transactionType, listingsDetailPosition);
          listingPositions = exchangePositions.get(fill.getMarket().getListing()).get(transactionType);
          publishPositionUpdate(fill.getPosition(), (fill.isLong()) ? PositionType.LONG : PositionType.SHORT, fill.getMarket(), fill.getOrder()
              .getOrderGroup());

          Amount listingProfits = DecimalAmount.ZERO;
          if (getRealisedPnL() == null || getRealisedPnL().get(this.getBaseAsset()) == null
              || getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()) == null
              || getRealisedPnL().get(this.getBaseAsset()).get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) == null) {
            marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
            marketRealisedProfits.put(fill.getMarket().getListing(), listingProfits);
            getRealisedPnL().get(this.getBaseAsset()).put(fill.getMarket().getExchange(), marketRealisedProfits);
          }
          // else
          //   detPosition.merge();
          // fill.merge();
        } else {

          if (!listingPositions.isEmpty() || listingPositions.peek() != null) {
            Position position = listingPositions.peek();
            log.info(fill + " prepareing to add to  existing " + transactionType + " position:" + position);

            fill.setPosition(position);
            fill.merge();
            if (position.addFill(fill)) {

              log.info(fill + " added to existing " + transactionType + " position:" + position);
              // TODO Use Sorted List as the positions bassing the comparitor
              Collections.sort(position.getFills(), timeComparator);
              log.info("sorted exisitng position by time then largest volume:" + position);
              //  position.merge();

            } else
              log.info(fill + " not added to existing " + transactionType + " position:" + position);
            // position.merge();
            //listingPositions.peek().persit();
            //   listingPositions.peek().Merge();
            // TODO need to persit the updated postitions
            //PersistUtil.merge(listingPositions.peek());
            publishPositionUpdate(fill.getPosition(), (fill.isLong()) ? PositionType.LONG : PositionType.SHORT, fill.getMarket(), fill.getOrder()
                .getOrderGroup());

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
              // detPosition.merge();
              // fill.merge();
            }
            //   Collections.sort(detPosition.getFills(), timeComparator);

            listingPositions.add(detPosition);
            publishPositionUpdate(fill.getPosition(), (fill.isLong()) ? PositionType.LONG : PositionType.SHORT, fill.getMarket(), fill.getOrder()
                .getOrderGroup());

            // if (persit)
            //   detPosition.persit();
            //  else
            //     detPosition.merge();
            //  PersistUtil.persist(detPosition);
            // //   detPosition.addFill(fill);

            //           PersistUtil.insert(detPosition);
          }
          // fill.merge();

        }
        log.debug(this.getClass().getSimpleName() + ":merge. Determing closeouts fill " + fill + " with open positions " + openingListingPositions);

        if (openingListingPositions != null && !(openingListingPositions.isEmpty())) {
          //	ArrayList<Position> positions = listingPositions.get(transactionType);

          //somethign is up with the poistions calcuation for partial closeouts
          // example 454 lots, closed out 421 lots, then added another 411 lots, total of 444 lots, but the average prices are not correct.
          // need to update this .	

          // loop over OpenPositions fill by fill, if fill postions effect is null or of opengpostions effect, we close out

          Amount realisedPnL = DecimalAmount.ZERO;
          long closingVolumeCount = 0;
          //position.getVolumeCount() 
          /*
           * Iterator<Position> itPos = listingPositions.iterator(); while (itPos.hasNext()) { // closing position Position pos = itPos.next();
           * closingFills: for (Iterator<Fill> itP = pos.getFills().iterator(); itP.hasNext();) { Fill p = itP.next(); if (p.getPositionEffect() ==
           * PositionEffect.OPEN) continue; if (Math.abs(p.getOpenVolumeCount()) == 0) { itP.remove(); continue closingFills; }
           */

          Amount entryPrice = DecimalAmount.ZERO;
          Amount exitPrice = DecimalAmount.ZERO;
          List<Position> closedOutListingPositions = new ArrayList<Position>();
          synchronized (listingPositions) {
            CLOSEPOSITIONSLOOP: for (Position closePos : listingPositions) {
              if (!closePos.hasFills()) {
                log.debug("removing position: " + closePos + " from: " + listingPositions);
                //  closedOutListingPositions.add(closePos);
                continue;
              }
              List<Fill> closedOutClosingPositionFills = new ArrayList<Fill>();
              // TODO we are looping over fills we have already closed out.

              // Iterator<Fill> cpit = closePos.getFills().iterator(); cpit.hasNext();
              synchronized (closePos.getFills()) {
                CLOSEDFILLSLOOP: for (Iterator<Fill> cpit = closePos.getFills().iterator(); cpit.hasNext();) {
                  Fill closePosition = cpit.next();
                  // we are looping over teh closing fill we ahve already closed out as qunanity is zero.
                  synchronized (closePosition) {
                    if (closePosition.getOpenVolumeCount() == 0) {
                      log.error(this.getClass().getSimpleName() + " - Open volume count is zero/" + closePosition.getOpenVolumeCount()
                          + " for closing fill: " + closePosition + " from closing position: " + closePos);

                      closePosition.setPosition(null);
                      closePosition.merge();
                      cpit.remove();
                      closePos.reset();
                      //continue;
                      //  closedOutClosingPositionFills.add(closePosition);
                      //  closePosition.merge();

                      continue;
                    }
                    List<Position> closedOutOpenListingPositions = new ArrayList<Position>();

                    if (closePosition.getPositionEffect() != null)
                      openingPositionEffect = (closePosition.getPositionEffect() == (PositionEffect.CLOSE)) ? PositionEffect.OPEN
                          : PositionEffect.CLOSE;
                    //  Iterator<Fill> cpit = closePos.getFills().iterator(); cpit.hasNext();
                    synchronized (openingListingPositions) {
                      OPENPOSITIONSLOOP: for (Position openPos : openingListingPositions) {
                        if (!openPos.hasFills()) {
                          log.debug("removing position: " + openPos + " from: " + openingListingPositions);
                          //                            closedOutOpenListingPositions.add(openPos);
                          continue;
                        }
                        //  Iterator<Position> itOlp = openingListingPositions.iterator();
                        //while (itOlp.hasNext()) {
                        // openg postion
                        List<Fill> closedOutOpenPositionFills = new ArrayList<Fill>();

                        synchronized (openPos.getFills()) {
                          OPENFILLSLOOP: for (Iterator<Fill> opit = openPos.getFills().iterator(); opit.hasNext();) {
                            Fill openPosition = opit.next();

                            // try {

                            if (openPosition.getOpenVolumeCount() == 0) {
                              log.error(this.getClass().getSimpleName() + " - Open volume count is zero/" + closePosition.getOpenVolumeCount()
                                  + " for openg  fill: " + openPosition + " from open position: " + openPosition);
                              openPosition.setPosition(null);
                              openPosition.merge();

                              opit.remove();
                              openPos.reset();

                              //  log.debug("removing open fill: " + openPosition + " from open positions: " + openPos);
                              // openPosition.merge();
                              //closedOutOpenPositionFills.add(openPosition);

                              continue;
                            }

                            //  Iterator<Fill> itOp = openPos.getFills().iterator();
                            //while (itOp.hasNext()) {

                            //open fill
                            realisedPnL = DecimalAmount.ZERO;
                            closingVolumeCount = 0;
                            // we only loop if it is 
                            if ((openPosition.getPositionEffect() == null || openPosition.getPositionEffect() == openingPositionEffect)
                            /*
                             * && (openPosition.getOrder().getOrderGroup() == 0.0 || closePosition.getOrder().getOrderGroup() == 0.0 ||
                             * openPosition.getOrder() .getOrderGroup() == closePosition.getOrder().getOrderGroup())
                             */
                            ) {
                              //  || (openPosition.getPositionEffect() == PositionEffect.OPEN && p.getPositionEffect() == PositionEffect.CLOSE)) {
                              // //  if (p.getPositionEffect() == PositionEffect.OPEN)/setting open position
                              // log.info("the last fille should be a closing fill. Trying to net open position: " + openPosition
                              //       + " with closing postion: " + p);

                              /*
                               * if (!(closePosition.getMarket().getTradedCurrency(closePosition.getMarket()).equals(closePosition.getMarket()
                               * .getQuote()))) { // we have BTC (BASE)/USD(QUOTE) (traded BTC)-> LTC(BASE)//USD(QUOTE) (traded BTC) // need to invert
                               * and revrese the prices if the traded ccy is not the quote ccy entryPrice = openPosition.getPrice().invert();
                               * exitPrice = closePosition.getPrice().invert(); } else {
                               */
                              exitPrice = openPosition.getPrice();
                              entryPrice = closePosition.getPrice();

                              //throw new NotImplementedException("Listings traded in neither base or quote currency are not supported");

                              //open position =3, closing postions =-1
                              // closing volume count =1
                              // clsoing position
                              //openPosition.getUpdateLock();
                              //   synchronized (openPosition) {
                              closingVolumeCount = (openingTransactionType == (TransactionType.SELL)) ? (Math.min(
                                  Math.abs(openPosition.getOpenVolumeCount()), Math.abs(closePosition.getOpenVolumeCount())))
                                  * -1 : (Math.min(Math.abs(openPosition.getOpenVolumeCount()), Math.abs(closePosition.getOpenVolumeCount())));
                              long updatedVolumeCount = 0;
                              if ((Math.abs(closePosition.getOpenVolumeCount()) >= Math.abs(openPosition.getOpenVolumeCount()))) {
                                updatedVolumeCount = closePosition.getOpenVolumeCount() + closingVolumeCount;
                                logCloseOut(closingVolumeCount, openPosition, closePosition, false);
                                log.debug(this.getClass().getSimpleName() + ":merge. setting open position " + openPosition
                                    + " open volume count to 0");

                                openPosition.setOpenVolumeCount(0);

                                openPosition.setHoldingTime(closePosition.getTimestamp() - openPosition.getTimestamp());
                                publishPositionUpdate(openPosition.getPosition(), (openPosition.isLong()) ? PositionType.LONG : PositionType.SHORT,
                                    openPosition.getMarket(), openPosition.getOrder().getOrderGroup());

                                openPosition.setPosition(null);
                                openPosition.merge();
                                opit.remove();
                                openPos.reset();
                                PositionType lastType = (openPosition.isLong()) ? PositionType.LONG : PositionType.SHORT;

                                closedOutOpenPositionFills.add(openPosition);

                                // openPosition.merge();
                                // so closing positions 
                                log.debug(this.getClass().getSimpleName() + ":merge. setting open volume count to "
                                    + (closePosition.getOpenVolumeCount() + closingVolumeCount) + "closePositionOpenVolumeCount "
                                    + closePosition.getOpenVolumeCount() + "closingVolumeCount" + closingVolumeCount + " for close position  "
                                    + closePosition);

                                closePosition.setOpenVolumeCount(closePosition.getOpenVolumeCount() + closingVolumeCount);

                                publishPositionUpdate(closePosition.getPosition(), (closePosition.isLong()) ? PositionType.LONG : PositionType.SHORT,
                                    closePosition.getMarket(), closePosition.getOrder().getOrderGroup());

                                if (closePosition.getOpenVolumeCount() == 0) {
                                  log.debug(this.getClass().getSimpleName() + ":merge. setting close position " + closePosition + " position to null");
                                  closePosition.setOpenVolumeCount(0);

                                  closePosition.setPosition(null);
                                  //   closePosition.merge();
                                  cpit.remove();
                                  closePos.reset();
                                  // closePosition.merge();
                                  closedOutClosingPositionFills.add(closePosition);
                                }
                                closePosition.merge();
                                //closePosition.merge();

                                //  closePosition.merge();

                                logCloseOut(closingVolumeCount, openPosition, closePosition, true);
                                log.debug(this.getClass().getSimpleName() + ":merge. portfolio positions " + this.getPositions());

                                //updateWorkingExitOrders(closePosition);
                                //updateWorkingExitOrders(openPosition);
                                // if the fill is now fully closed out I need to cancel any closing specfic limit orders associated with it
                                // or I need to update the qunaity to the 
                                // closed all closing limit orders

                              } else if (closePosition.getOpenVolumeCount() != 0) {
                                //open position =3, closing postions =-1
                                // closing volume count =1
                                // clsoing position

                                updatedVolumeCount = openPosition.getOpenVolumeCount() - closingVolumeCount;

                                // updatedVolumeCount = 3 -1 = 2
                                logCloseOut(closingVolumeCount, openPosition, closePosition, false);
                                log.debug(this.getClass().getSimpleName() + ":merge. setting close position " + closePosition
                                    + " open volume count to 0");

                                closePosition.setOpenVolumeCount(0);

                                publishPositionUpdate(closePosition.getPosition(), (closePosition.isLong()) ? PositionType.LONG : PositionType.SHORT,
                                    closePosition.getMarket(), closePosition.getOrder().getOrderGroup());

                                closePosition.setPosition(null);
                                closePosition.merge();

                                cpit.remove();
                                closePos.reset();
                                closedOutClosingPositionFills.add(closePosition);
                                // closePosition.merge();

                                //closePos.removeFill(fill)
                                //closePosition.re
                                //    closePosition.merge();

                                // closed all closing limit orders

                                log.debug(this.getClass().getSimpleName() + ":merge. setting open volume count to "
                                    + (openPosition.getOpenVolumeCount() - closingVolumeCount) + "openPositionOpenVolumeCount "
                                    + openPosition.getOpenVolumeCount() + "closingVolumeCount" + closingVolumeCount + " for open position  "
                                    + openPosition);

                                openPosition.setOpenVolumeCount(openPosition.getOpenVolumeCount() - closingVolumeCount);

                                //openPosition.merge();
                                openPosition.setHoldingTime(closePosition.getTimestamp() - openPosition.getTimestamp());
                                publishPositionUpdate(openPosition.getPosition(), (openPosition.isLong()) ? PositionType.LONG : PositionType.SHORT,
                                    openPosition.getMarket(), openPosition.getOrder().getOrderGroup());

                                //openPosition.releaseUpdateLock();
                                if (openPosition.getOpenVolumeCount() == 0) {
                                  log.debug(this.getClass().getSimpleName() + ":merge. setting open position " + openPosition + " position to null");
                                  opit.remove();
                                  openPos.reset();
                                  openPosition.setPosition(null);
                                  closedOutOpenPositionFills.add(openPosition);

                                }
                                openPosition.merge();

                                // openPosition.merge();
                                // openPos.merge();
                                logCloseOut(closingVolumeCount, openPosition, closePosition, true);

                                // updateWorkingExitOrders(closePosition);
                                // updateWorkingExitOrders(openPosition);
                                // */// if the fill is now fully closed out I need to cancel any closing specfic limit orders associated with it
                                // or I need to update the qunaity to the 
                                // closed all closing limit orders

                              } else if (closePosition.getOpenVolumeCount() == 0) {
                                //open position =3, closing postions =-1
                                // closing volume count =1
                                // clsoing position
                                log.debug(this.getClass().getSimpleName() + ":merge. setting open position to null " + closePosition
                                    + " as open volume is :" + closePosition.getOpenVolumeCount());
                                cpit.remove();
                                closePos.reset();
                                publishPositionUpdate(closePosition.getPosition(), (closePosition.isLong()) ? PositionType.LONG : PositionType.SHORT,
                                    closePosition.getMarket(), closePosition.getOrder().getOrderGroup());

                                closePosition.setPosition(null);
                                closePosition.merge();
                                closedOutClosingPositionFills.add(closePosition);
                                // closePosition.merge();

                              }
                              //      }
                              //  openPosition.merge();

                              /*
                               * if (openPosition.getOpenVolumeCount() == 0) { log.debug("removing fill: " + openPosition + " from oprning position: "
                               * + openPos); closedOutOpenPositionFills.add(openPosition); // openPos.merge(); // openPosition.setPosition(null);
                               * //openPosition.merge(); // cancel all specifc maker orders related to the closeing fill fill if
                               * (openPosition.getPositionEffect() != null && openPosition.getPositionEffect() == PositionEffect.OPEN)
                               * cancelWorkingExitOrders(closePosition); else cancelWorkingExitOrders(openPosition); //
                               * cancelStopOrders(openPosition); if (!openPos.hasFills()) { log.debug("removing opening position: " + openPos +
                               * " from: " + openingListingPositions); closedOutOpenListingPositions.add(openPos); } //itOlp.remove(); } if
                               * (closePosition.getOpenVolumeCount() == 0) { log.debug("removing fill: " + closePosition + " from closing position: "
                               * + closePos); closedOutClosingPositionFills.add(closePosition); // cancelStopOrders(closePosition); if
                               * (!closePos.hasFills()) { log.debug("removing closing position: " + closePos + " from: " + listingPositions);
                               * closedOutListingPositions.add(closePos); } openPosition.// }
                               */
                              //  openPosition.merge();
                              DiscreteAmount volDiscrete = new DiscreteAmount(closingVolumeCount, closePosition.getMarket().getListing()
                                  .getVolumeBasis());

                              realisedPnL = realisedPnL.plus(((entryPrice.minus(exitPrice)).times(volDiscrete, Remainder.ROUND_EVEN)).times(
                                  closePosition.getMarket().getMultiplier(closePosition.getMarket(), entryPrice, exitPrice), Remainder.ROUND_EVEN)
                                  .times(closePosition.getMarket().getContractSize(closePosition.getMarket()), Remainder.ROUND_EVEN));

                              // need to confonvert to deiscreete amount

                              Amount RealisedPnL = realisedPnL.toBasis(currency.getBasis(), Remainder.ROUND_EVEN);
                              if (RealisedPnL.isNegative() && closePosition.getPositionEffect() == PositionEffect.OPEN)
                                log.info("realsiedPnL is a loss. netted open position: " + openPosition + " with closing postion: " + closePosition);

                              if (!RealisedPnL.isZero()) {

                                // lets convert this to base.
                                this.getBaseAsset();

                                Listing listing = Listing.forPair(currency, this.getBaseAsset());

                                //for ETH.BTC, tradedListing = ETH/USD (traded rate)
                                // for BTC.USD tradedListing = BTC/USD
                                // futures tradedListing ->USD/USD
                                // for eth/BTC -> ETH/USD

                                Offer rate = quoteService.getImpliedBestAskForListing(listing);
                                Amount baseRealisedPnL = RealisedPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);

                                //   Amount TotalRealisedPnL = RealisedPnL.plus(getRealisedPnL().get(closePosition.getMarket().getTradedCurrency())
                                //         .get(closePosition.getMarket().getExchange()).get(closePosition.getMarket().getListing()));
                                Transaction trans = transactionFactory.create(closePosition, this, closePosition.getMarket().getExchange(), this
                                    .getBaseAsset(), TransactionType.REALISED_PROFIT_LOSS, baseRealisedPnL, new DiscreteAmount(0, this.getBaseAsset()
                                    .getBasis()));
                                log.info("Realised PnL:" + trans);
                                context.setPublishTime(trans);
                                trans.persit();

                                context.route(trans);

                              }

                              if (closedOutClosingPositionFills.contains(closePosition)) {

                                // we have closed out this closing postion, so let's move onto next clsooing postions
                                break;
                              } else {

                                log.info(closePosition + " not closed fully out with " + openPosition);
                              }
                            }

                            //} //catch (InterruptedException e) {
                            // log.error(this.getClass().getSimpleName() + " - merge: Unable to perform closed outs of "
                            //       + closePosition.getClass().getSimpleName() + " id " + closePosition.getId());
                            // continue;
                            // }
                            //      openPosition.merge();

                          }
                        }
                        if (!closedOutOpenPositionFills.isEmpty()) {
                          log.debug("removing all fills: " + closedOutOpenPositionFills + "from: " + openPos);
                          Boolean removeFills = true;

                          for (Fill closedFill : closedOutOpenPositionFills) {
                            if (closedFill.getOpenVolumeCount() != 0)
                              removeFills = false;

                            //  else
                            //    closedFill.merge();
                          }
                          // closePos.merge();
                          if (removeFills)
                            openPos.removeFills(closedOutOpenPositionFills);
                          else
                            log.debug("unable to remove fills as dome fills have zero open qunaity in position " + openPos);

                          if (!openPos.hasFills())
                            closedOutOpenListingPositions.add(openPos);
                        }
                        // openPos.merge();
                      }
                    }
                    if (closedOutOpenListingPositions != null && !closedOutOpenListingPositions.isEmpty() && openingListingPositions != null
                        && !openingListingPositions.isEmpty()) {
                      log.debug("removing all opening positions: " + openingListingPositions + "from: " + openingListingPositions);

                      for (Position closedPos : closedOutOpenListingPositions) {
                        /*
                         * Boolean removePosition = true; for (Fill fillToRemove : closedPos.getFills()) { if (fillToRemove.getOpenVolumeCount() != 0)
                         * //{ // fillToRemove.setPosition(null); // fillToRemove.merge(); // } else removePosition = false; }
                         */
                        if (!closedPos.hasFills()) {

                          this.removePosition(closedPos);
                          //  closedPos.setPortfolio(null);

                          closedPos.setPortfolio(null);

                          closedPos.delete();
                        } else
                          log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closedPos);
                      }
                      openingListingPositions.removeAll(closedOutOpenListingPositions);

                    }
                    // closePosition.merge();

                  }
                }
              }
              //TODO how does flow get in here and have the position removed when the open volume count!=0
              if (!closedOutClosingPositionFills.isEmpty()) {
                log.debug("removing all closed closing fills: " + closedOutClosingPositionFills + "from closing position: " + closePos);
                Boolean removeFills = true;
                for (Fill closedFill : closedOutClosingPositionFills) {
                  if (closedFill.getOpenVolumeCount() != 0)
                    removeFills = false;
                  //   else
                  //     closedFill.merge();

                }
                // closePos.merge();
                if (removeFills)
                  closePos.removeFills(closedOutClosingPositionFills);
                else
                  log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closePos);
                if (!closePos.hasFills())
                  closedOutListingPositions.add(closePos);

              }

              // closePos.merge();
            }
          }
          if (closedOutListingPositions != null && !closedOutListingPositions.isEmpty() && listingPositions != null && !listingPositions.isEmpty()) {
            log.debug("removing all closing positions: " + closedOutListingPositions + "from: " + listingPositions);

            for (Position closedPos : closedOutListingPositions) {
              // Boolean removePosition = true;

              if (!closedPos.hasFills()) {
                this.removePosition(closedPos);
                closedPos.setPortfolio(null);

                // 

                closedPos.delete();
              } else
                log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closedPos);
              /*
               * for (Fill fillToRemove : closedPos.getFills()) { if (fillToRemove.getOpenVolumeCount() != 0) // fillToRemove.setPosition(null); //
               * fillToRemove.merge(); //} else removePosition = false; // } if (removePosition && !closedPos.hasFills()) closedPos.delete(); else
               * log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closedPos);
               */
            }
            listingPositions.removeAll(closedOutListingPositions);

          }

          //}
          //}
          /*
           * if (getNetPosition(fill.getMarket().getBase(), fill.getMarket()) == null) { Position detPosition; if (fill.getPosition() == null) { //
           * fill.getPortfolio().merge(); detPosition = positionFactory.create(fill, fill.getMarket()); log.debug(this.getClass().getSimpleName() +
           * ":merge. created new position " + detPosition); // detPosition.getPortfolio().merge(); detPosition.persit(); fill.merge(); } else {
           * detPosition = fill.getPosition(); // detPosition.merge(); } // publishPositionUpdate(fill.getPosition(), PositionType.FLAT,
           * fill.getMarket(), fill.getOrder().getOrderGroup()); // if (persit) // detPosition.persit(); // else // detPosition.merge(); } else { if
           * (fill.getPosition() == null) log.debug("null postion rather than zero quanity postion: " + getNetPosition(fill.getMarket().getBase(),
           * fill.getMarket())); // publishPositionUpdate(fill.getPosition(), lastType, fill.getMarket(), fill.getOrder().getOrderGroup()); }
           */
          // fill.merge();
          return true;
        }
        //  PositionType lastType = (fill.isLong()) ? PositionType.LONG : PositionType.SHORT;

        //  publishPositionUpdate(fill.getPosition(), lastType, fill.getMarket(), fill.getOrder().getOrderGroup());
        // fill.merge();
        return true;

      }
    }
    // }
  }

  public Portfolio(String name, PortfolioManager manager) {
    this.name = name;
    this.manager = manager;
    if (getDao() != null)
      getDao().persist(this);

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
        return (fill2.getVolume().compareTo(fill.getVolume()));

      }
    }

  };

  @Transient
  public Collection<Order> getAllOrders() {
    Set<Order> orders = new HashSet<Order>();
    synchronized (getPositions()) {
      for (Position position : getPositions())
        for (Fill positionFill : position.getFills()) {
          orders.add(positionFill.getOrder());
          for (Fill fill : positionFill.getOrder().getFills())
            fill.getAllOrdersByParentFill(orders);
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
    synchronized (getPositions()) {

      for (Position position : getPositions())
        for (Fill fill : position.getFills()) {
          fills.add(fill);
          // fill.getAllOrdersByParentFill(orders);
        }
      return fills;
    }
  }

  @OneToMany(fetch = FetchType.LAZY)
  @OrderBy
  public List<Stake> getStakes() {
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
   * @param position
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
    this.positionsMap = new ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>>();
    this.realisedProfits = new ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>>();
    //this.balances = new CopyOnWriteArrayList<>();
    this.transactions = new ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>>>();

  }

  protected synchronized void setPositions(
      ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>> positions) {
    this.positionsMap = positions;
  }

  public synchronized void setBaseAsset(Asset baseAsset) {
    this.baseAsset = baseAsset;
  }

  protected synchronized void setTransactions(
      ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>>> transactions) {
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
      myPort.getPositions().removeAll(Collections.singleton(null));
      // portfiolio
      Map<Order, Order> portfolioOrders = new HashMap<Order, Order>();
      Map<Fill, Fill> portfolioFills = new HashMap<Fill, Fill>();
      for (Position position : myPort.getPositions()) {
        context.getInjector().injectMembers(position);
        position.getFills().removeAll(Collections.singleton(null));
        List<Fill> fillsToBeAdded = new ArrayList<Fill>();
        List<Fill> fills = new ArrayList<Fill>();
        int index = 0;

        for (Fill fill : position.getFills()) {
          portfolioFills.put(fill, fill);
        }

        //TODO to figure out why this is doubleing/trebbeling meomory
        for (Fill fill : position.getFills()) {

          // Fill filltest;
          //  if (!portfolioFills.containsKey(fill)) {
          context.getInjector().injectMembers(fill);
          log.debug("Portfolio: findOrCreate Loading all child order for fill " + fill.getId());
          fill.loadAllChildOrdersByFill(fill, portfolioOrders, portfolioFills);
          log.debug("Portfolio: findOrCreate Loading all child order for fill order " + fill.getOrder().getId());
          fill.getOrder().loadAllChildOrdersByParentOrder(fill.getOrder(), portfolioOrders, portfolioFills);
          //  } else {
          //    filltest = portfolioFills.get(fill);
          //fill = portfolioFills.get(fill);
          //  position.getFills().set(index, portfolioFills.get(fill));
          // filltest = portfolioFills.get(fill);
          // fill = portfolioFills.get(fill);
          //}
          //index++;
        }

        // when we are loading posiitons we need to link the fill to that position

        //loop over all fills in the position
        // then we load all order by child fill, for each order we load the fills, if the fill belongs to the posiont we get a differnt reference but same id.
        // so when we load any fills, we need to see if we have them loaded somwehere else in the tree,
        // we need to set the loaded fill to the parent fill

        /*
         * UUID orderId; Fill fillWithChildren = EM.namedQueryZeroOne(Fill.class, "Fill.findFill", withChildOrderHints, fill.getId()); if
         * (fillWithChildren != null) fill.setFillChildOrders(fillWithChildren.getFillChildOrders()); Order orderWithFills; Order orderWithChildren;
         * Order orderWithTransactions; // so for each fill in the open position we need to load the whole order tree // getorder, then get all childe
         * orders, then for each child, load child orders, so on and so forth. // load all child orders, and theri child ordres // load all parent
         * orders and thier parent orders // need to laod all parent fills, their child orders, and their children // get a list of all orders in the
         * tree then load orderId = fill.getOrder().getId(); try { orderWithFills = EM.namedQueryZeroOne(Order.class, "Order.findOrder",
         * withFillsHints, orderId); orderWithChildren = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withChildrenHints, orderId);
         * orderWithTransactions = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withTransHints, orderId); } catch (Error | Exception ex) {
         * log.error("Portfolio:findOrCreate unable to get order for orderID: " + orderId); continue; } if ((orderWithFills != null && orderWithFills
         * instanceof SpecificOrder && orderWithFills.getId().equals(orderId)) && (orderWithTransactions != null && orderWithTransactions instanceof
         * SpecificOrder && orderWithTransactions.getId() .equals(orderId)) && (orderWithChildren != null && orderWithChildren instanceof
         * SpecificOrder && orderWithChildren.getId().equals(orderId))) { SpecificOrder order = (SpecificOrder) orderWithFills;
         * order.setTransactions(orderWithTransactions.getTransactions()); order.setOrderChildren(orderWithChildren.getOrderChildren());
         * fill.setOrder(order); log.error("Portfolio:findOrCreate found order for orderID: " + orderId); } }
         */
      }
      for (Order order : myPort.getAllOrders()) {
        log.trace("loding members for order:" + order.getId());
        context.getInjector().injectMembers(order);
        for (Transaction transaction : order.getTransactions()) {
          context.getInjector().injectMembers(transaction);
        }
      }
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
      synchronized (this.getPositions()) {
        portfolioDao.merge(this);
      }
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
  private transient ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>> positionsMap;
  private transient ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> realisedProfits;
  private transient ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>>> transactions;
  private transient List<Stake> stakes = new CopyOnWriteArrayList<>();
  private transient Set<Position> positions = Sets.newConcurrentHashSet();

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

}
