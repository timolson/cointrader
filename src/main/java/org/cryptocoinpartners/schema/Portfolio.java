package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.Cacheable;
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
import javax.persistence.OrderColumn;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.schema.dao.Dao;
import org.cryptocoinpartners.schema.dao.PortfolioDao;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Many Owners may have Stakes in the Portfolio, but there is only one PortfolioManager, who is not necessarily an Owner. The Portfolio has muFltiple Positions.
 * 
 * @author Tim Olson
 */
@Entity
@Cacheable
//@NamedQueries({ @NamedQuery(name = "Portfolio.findOpenPositions", query = "select po from Portfolio po where po.positions.fills.fill.openVolumeCount<>0", hints = { @QueryHint(name = "javax.persistence.fetchgraph", value = "graph.Portfolio.positions") }) })
//@NamedEntityGraph(name = "graph.Position.fills", attributeNodes = @NamedAttributeNode(value = "fills", subgraph = "fills"), subgraphs = @NamedSubgraph(name = "fills", attributeNodes = @NamedAttributeNode("order")))
//@NamedEntityGraph(name = "graph.Portfolio.positions", attributeNodes = @NamedAttributeNode(value = "positions", subgraph = "positions"), subgraphs = @NamedSubgraph(name = "positions", attributeNodes = @NamedAttributeNode("portfolio")))
//@NamedEntityGraph(name = "graph.Portfolio.positions", attributeNodes = @NamedAttributeNode("positions"))
//select po from portfolio po where p.fill.openVolumeCount<>0
@NamedQueries({ @NamedQuery(name = "Portfolio.findOpenPositions", query = "select p from Portfolio p where name=?1") })
//
@NamedEntityGraphs({

@NamedEntityGraph(name = "portfolioWithPositions", attributeNodes = { @NamedAttributeNode(value = "positions", subgraph = "positionsWithFills") }, subgraphs = { @NamedSubgraph(name = "positionsWithFills", attributeNodes = { @NamedAttributeNode("fills") })
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
public class Portfolio extends EntityBase {

    private static Object lock = new Object();
    private static ExecutorService service = Executors.newFixedThreadPool(10);

    /** returns all Positions, whether they are tied to an open Order or not.  Use getTradeablePositions() */
    public @Transient
    List<Fill> getDetailedPositions() {
        List<Fill> allPositions = new CopyOnWriteArrayList<Fill>();

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

        return allPositions;
    }

    protected @Transient
    void persistPositions(Asset asset, Exchange exchange, Listing listing) {
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
    @OneToMany(mappedBy = "portfolio")
    @OrderColumn(name = "version")
    //, cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    public List<Position> getPositions() {
        if (positions == null)
            positions = new CopyOnWriteArrayList<Position>();
        return positions;

    }

    protected void setPositions(List<Position> positions) {

        this.positions = positions;

    }

    public @Transient
    Collection<Position> getNetPositions() {
        ConcurrentLinkedQueue<Position> allPositions = new ConcurrentLinkedQueue<Position>();
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
                    orderService.updateWorkingOrderQuantity(childOrder, fill.getOpenVolume().negate());
                }
            }

            /*     
                 ArrayList<Order> allChildOrders = new ArrayList<Order>();

                 orderService.getAllOrdersByParentFill(fill, allChildOrders);
                 SpecificOrder specficOrder = null;
                 for (Order childOrder : allChildOrders)
                     if (childOrder instanceof SpecificOrder) {
                         specficOrder = (SpecificOrder) childOrder;
                         if (specficOrder.getParentFill() != null && specficOrder.getLimitPrice() != null)
                             if (fill.getOpenVolumeCount() == 0)
                                 orderService.cancelOrder(specficOrder);
                             else
                                 orderService.updateOrderQuantity(specficOrder, fill.getOpenVolume());
                     }*/

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

                //                           Amount longVolume = DecimalAmount.ZERO;
                //                            Amount longAvgPrice = DecimalAmount.ZERO;
                //                            Amount longAvgStopPrice = DecimalAmount.ZERO;
                //                            Amount shortVolume = DecimalAmount.ZERO;
                //                            Amount shortAvgPrice = DecimalAmount.ZERO;
                //                            Amount shortAvgStopPrice = DecimalAmount.ZERO;
                for (Position detailedPosition : positionsMap.get(asset).get(market.getExchange()).get(market.getListing()).get(transactionType)) {

                    // fills.addAll(detailedPosition.getFills());
                    for (Fill pos : detailedPosition.getFills())
                        fills.add(pos);
                    //
                    //                        //                                            if (pos.isLong()) {
                    //                        //                                                longAvgPrice = ((longAvgPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getPrice(),
                    //                        //                                                        Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
                    //                        //                                                if (pos.getStopPrice() != null)
                    //                        //                                                    longAvgStopPrice = ((longAvgStopPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getStopPrice(),
                    //                        //                                                            Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
                    //                        //                        //
                    //                        //                                                longVolume = longVolume.plus(pos.getOpenVolume());
                    //                        //                                            } else if (pos.isShort()) {
                    //                        //                                                shortAvgPrice = ((shortAvgPrice.times(shortVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getPrice(),
                    //                        //                                                        Remainder.ROUND_EVEN))).dividedBy(shortVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
                    //                        //                                                if (pos.getStopPrice() != null)
                    //                        //                                                    shortAvgStopPrice = ((shortAvgStopPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getOpenVolume().times(pos.getStopPrice(),
                    //                        //                                                            Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getOpenVolume()), Remainder.ROUND_EVEN);
                    //                        //                        //
                    //                        //                                                shortVolume = shortVolume.plus(pos.getOpenVolume());
                    //                        //                    }
                    //                    }
                }
            }

        // need to change this to just return one position that is the total, not one long and one short.
        //Position netPosition=new Position();
        //
        //netPosition.setExchange(market.getExchange());
        //netPosition.setExchange(market.getExchange());

        //        
        //        if (!shortVolume.isZero() || !longVolume.isZero()) {
        //                Fill pos = new Fill();
        //                pos.setPortfolio(this);
        //                pos.setMarket(market);
        //
        //                pos.setPriceCount(longAvgPrice.toBasis(market.getPriceBasis(), Remainder.ROUND_EVEN).getCount());
        //                pos.setVolumeCount(longVolume.toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount());
        //                position = new Position(pos);
        //                //allPositions.add(position);
        //            }

        //  log.debug("creating net position with fills" + fills);
        return positionFactory.create(fills);
        //  return position;

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
        return positionFactory.create(fills);

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
        return positionFactory.create(fills);
    }

    public @Transient
    Collection<Position> getPositions(Asset asset, Exchange exchange) {
        Collection<Position> allPositions = new ConcurrentLinkedQueue<Position>();

        if (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) {
            //synchronized (lock) {
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
            // }
        }

        return allPositions;

    }

    public @Transient
    ConcurrentHashMap<Asset, Amount> getRealisedPnLs() {

        ConcurrentHashMap<Asset, Amount> allPnLs = new ConcurrentHashMap<Asset, Amount>();
        //  synchronized (lock) {
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
        // }

        return allPnLs;
    }

    public @Transient
    Amount getRealisedPnL(Asset asset) {

        Amount realisedPnL = DecimalAmount.ZERO;
        for (Iterator<Exchange> ite = getRealisedPnL().get(asset).keySet().iterator(); ite.hasNext();) {
            Exchange exchange = ite.next();
            for (Iterator<Listing> itl = getRealisedPnL().get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
                Listing listing = itl.next();
                realisedPnL = realisedPnL.plus(getRealisedPnL().get(asset).get(exchange).get(listing));

            }
        }

        return realisedPnL;
    }

    public @Transient
    ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> getRealisedPnL() {

        return realisedProfits;
    }

    /*  public @Transient
      DiscreteAmount getLongPositionAmount(Asset asset, Exchange exchange) {
          long longVolumeCount = 0;
          //   synchronized (lock) {
          if (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) {
              for (Iterator<Listing> itl = positionsMap.get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
                  Listing listing = itl.next();
                  for (Position itpos : positionsMap.get(asset).get(exchange).get(listing).get(TransactionType.BUY)) {

                      for (Iterator<Fill> itp = itpos.getFills().iterator(); itp.hasNext();) {
                          Fill pos = itp.next();
                          longVolumeCount += pos.getOpenVolumeCount();
                      }
                  }

              }
          }
          //  }
          return new DiscreteAmount(longVolumeCount, asset.getBasis());

      }
    */
    public @Transient
    DiscreteAmount getNetPosition(Asset asset, Exchange exchange) {
        long netVolumeCount = 0;
        Fill pos = null;
        //  synchronized (lock) {
        if (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) {
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
        // }
        return new DiscreteAmount(netVolumeCount, asset.getBasis());
    }

    /*   public @Transient
       DiscreteAmount getShortPositionAmount(Asset asset, Exchange exchange) {
           long shortVolumeCount = 0;
           //  synchronized (lock) {

           if (positionsMap.get(asset) != null && positionsMap.get(asset).get(exchange) != null) {
               for (Iterator<Listing> itl = positionsMap.get(asset).get(exchange).keySet().iterator(); itl.hasNext();) {
                   Listing listing = itl.next();

                   for (Position itpos : positionsMap.get(asset).get(exchange).get(listing).get(TransactionType.SELL)) {
                       for (Iterator<Fill> itp = itpos.getFills().iterator(); itp.hasNext();) {

                           Fill pos = itp.next();
                           shortVolumeCount += pos.getOpenVolumeCount();

                       }
                   }
               }
           }
           // }
           return new DiscreteAmount(shortVolumeCount, asset.getBasis());

       }*/

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
    public void removeTransaction(Transaction reservation) {

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
     * @param f
     * @return
     */
    @Transient
    public Collection<Position> getTradeableBalanceOf(Exchange exchange, Asset asset) {

        throw new NotImplementedException();
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
        throw new NotImplementedException();
    }

    @Transient
    public void release(SpecificOrder order) {
        throw new NotImplementedException();
    }

    @Transient
    public void addRealisedPnL(Transaction transaction) {
        Amount TotalRealisedPnL = transaction.getAmount().plus(
                getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange()).get(transaction.getFill().getMarket().getListing()));

        getRealisedPnL().get(transaction.getCurrency()).get(transaction.getExchange()).put(transaction.getFill().getMarket().getListing(), TotalRealisedPnL);

    }

    @Transient
    public boolean addTransaction(Transaction transaction) {

        ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>> assetTransactions = transactions.get(transaction
                .getCurrency());

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
            portfolioService.resetBalances();
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
                portfolioService.resetBalances();
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
                    portfolioService.resetBalances();
                    return true;
                } else {
                    transactionList.add(transaction);
                    exchangeTransactions.put(transaction.getType(), transactionList);
                    if (transaction.getType() == TransactionType.REALISED_PROFIT_LOSS)
                        addRealisedPnL(transaction);
                    portfolioService.resetBalances();
                    return true;
                }

            }

        }

    }

    /**
     * finds other Positions in this portfolio which have the same Exchange and Asset and merges this position's
     * amount into the found position's amount, thus maintaining only one Position for each Exchange/Asset pair.
     * this method does not remove the position from the positions list.
     * @return true iff another position was found and merged
     */

    protected void publishPositionUpdate(Position position, PositionType lastType, Market market) {

        PositionType mergedType = (position.isShort()) ? PositionType.SHORT : (position.isLong()) ? PositionType.LONG : PositionType.FLAT;

        context.publish(new PositionUpdate(position, market, lastType, mergedType));
    }

    public synchronized void addPosition(Position position) {
        // synchronized (lock) {
        getPositions().add(position);
        // }
    }

    public synchronized void removePositions(Collection<Position> removedPositions) {
        //   synchronized (lock) {
        if (this.positions.removeAll(removedPositions))
            for (Position removedPosition : removedPositions)
                removedPosition.setPortfolio(null);

    }

    public synchronized void removePosition(Position removePosition) {
        //   synchronized (lock) {
        //  System.out.println("removing fill: " + fill + " from position: " + this);
        if (positions.remove(removePosition))
            removePosition.setPortfolio(null);

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

    /*   public void cancelStopOrders(Fill fill) {
           service.submit(new handleCancelRunnable(fill));

           // orderService.handleCancelSpecificOrderByParentFill(fill);
           for (Order order : fill.getFillChildOrders()) {
               if (order instanceof GeneralOrder && order.getFillType() == FillType.STOP_LIMIT) {
                   orderService.handleCancelGeneralOrder((GeneralOrder) order);

               }
           }

           // synchronized (lock) {

           // }
       }
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

    @Transient
    public synchronized boolean merge(Fill fill) {
        boolean persit = true;

        log.debug(this.getClass().getSimpleName() + ":merge. Determing position for fill" + fill);
        //synchronized (lock) {
        // We need to have a queue of buys and a queue of sells ( two array lists), ensure the itterator is descendingIterator for LIFO,
        // when we get a new trade coem in we add it to the buy or sell queue
        // 1) caluate price difference
        // 2) times price diff by min(trade quantity or the position) and add to relasied PnL
        // 3) update the quaitity of the postion and remove from queue if zero
        // 4) move onto next postion until the qty =0

        // https://github.com/webpat/jquant-core/blob/173d5ca79b318385a3754c8e1357de79ece47be4/src/main/java/org/jquant/portfolio/Portfolio.java

        TransactionType transactionType = (fill.isLong()) ? TransactionType.BUY : TransactionType.SELL;
        TransactionType openingTransactionType = (transactionType == (TransactionType.BUY)) ? TransactionType.SELL : TransactionType.BUY;
        PositionEffect openingPositionEffect = null;

        ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>> assetPositions = positionsMap
                .get(fill.getMarket().getBase());
        ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>> listingPosition = new ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>();
        //ConcurrentHashMap<Listing, ArrayList<Position>> listingPosition = new ConcurrentHashMap<Listing, ArrayList<Position>>();

        ConcurrentHashMap<Listing, Amount> marketRealisedProfits;
        ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>> assetRealisedProfits = getRealisedPnL().get(fill.getMarket().getTradedCurrency());
        if (assetRealisedProfits != null) {
            marketRealisedProfits = assetRealisedProfits.get(fill.getMarket().getListing());
        }

        if (assetPositions == null) {
            log.debug(this.getClass().getSimpleName() + ":merge. creating new asset positions for fill" + fill);

            ConcurrentLinkedQueue<Position> detailPosition = new ConcurrentLinkedQueue<Position>();
            // need to wrapper position!!
            Position detPosition;
            if (fill.getPosition() == null) {
                detPosition = positionFactory.create(fill);
                detPosition.persit();

            } else {
                detPosition = fill.getPosition();
                detPosition.merge();
                //   persit = false;

            }
            log.info(fill + "added to new " + fill.getMarket().getBase() + " position");
            //  if (persit)

            //   new Position(fill);

            //  PersistUtil.persist(detPosition);
            detailPosition.add(detPosition);

            ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>> positionType = new ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>();
            positionType.put(transactionType, detailPosition);

            listingPosition.put(fill.getMarket().getListing(), positionType);
            assetPositions = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>();
            assetPositions.put(fill.getMarket().getExchange(), listingPosition);
            positionsMap.put(fill.getMarket().getBase(), assetPositions);

            Amount profits = DecimalAmount.ZERO;
            if (assetRealisedProfits == null) {
                assetRealisedProfits = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>();
                marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
                marketRealisedProfits.put(fill.getMarket().getListing(), profits);
                assetRealisedProfits.put(fill.getMarket().getExchange(), marketRealisedProfits);
                getRealisedPnL().put(fill.getMarket().getTradedCurrency(), assetRealisedProfits);
            }
            publishPositionUpdate(getNetPosition(fill.getMarket().getBase(), fill.getMarket()), PositionType.FLAT, fill.getMarket());

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
                    detPosition = positionFactory.create(fill);
                    detPosition.persit();

                } else {
                    detPosition = fill.getPosition();
                    detPosition.merge();
                }
                log.info(fill + "added to new " + fill.getMarket().getExchange() + " position");

                //   PersistUtil.persist(detPosition);

                detailPosition.add(detPosition);
                positionType.put(transactionType, detailPosition);

                listingPosition.put(fill.getMarket().getListing(), positionType);

                assetPositions.put(fill.getMarket().getExchange(), listingPosition);
                Amount profits = DecimalAmount.ZERO;
                if (getRealisedPnL().get(fill.getMarket().getTradedCurrency()).get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) == null) {
                    marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
                    marketRealisedProfits.put(fill.getMarket().getListing(), profits);
                    getRealisedPnL().get(fill.getMarket().getTradedCurrency()).put(fill.getMarket().getExchange(), marketRealisedProfits);
                }
                publishPositionUpdate(getNetPosition(fill.getMarket().getBase(), fill.getMarket()), PositionType.FLAT, fill.getMarket());
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
                        detPosition = positionFactory.create(fill);
                        detPosition.persit();

                    } else {
                        detPosition = fill.getPosition();
                        detPosition.merge();

                    }
                    log.info(fill + "added to new " + transactionType + " position");
                    // PersistUtil.persist(detPosition);

                    listingsDetailPosition.add(detPosition);
                    exchangePositions.get(fill.getMarket().getListing()).put(transactionType, listingsDetailPosition);
                    listingPositions = exchangePositions.get(fill.getMarket().getListing()).get(transactionType);
                    Amount listingProfits = DecimalAmount.ZERO;
                    if (getRealisedPnL().get(fill.getMarket().getTradedCurrency()) == null
                            || getRealisedPnL().get(fill.getMarket().getTradedCurrency()).get(fill.getMarket().getExchange()) == null
                            || getRealisedPnL().get(fill.getMarket().getTradedCurrency()).get(fill.getMarket().getExchange())
                                    .get(fill.getMarket().getListing()) == null) {
                        marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
                        marketRealisedProfits.put(fill.getMarket().getListing(), listingProfits);
                        getRealisedPnL().get(fill.getMarket().getTradedCurrency()).put(fill.getMarket().getExchange(), marketRealisedProfits);
                    }
                    // else
                    //   detPosition.merge();
                    // fill.merge();
                } else {

                    if (!listingPositions.isEmpty() || listingPositions.peek() != null) {
                        Position position = listingPositions.peek();
                        log.info(fill + " prepareing to add to  existing " + transactionType + " position:" + position);

                        fill.setPosition(position);

                        if (position.addFill(fill)) {

                            log.info(fill + " added to existing " + transactionType + " position:" + position);
                            //  position.merge();

                        } else
                            log.info(fill + " not added to existing " + transactionType + " position:" + position);
                        // position.merge();
                        //listingPositions.peek().persit();
                        //   listingPositions.peek().Merge();
                        // TODO need to persit the updated postitions
                        //PersistUtil.merge(listingPositions.peek());

                    } else {

                        Position detPosition;
                        if (fill.getPosition() == null) {
                            log.debug(this.getClass().getSimpleName() + ":merge. creating new position for fill" + fill);

                            detPosition = positionFactory.create(fill);
                            detPosition.persit();
                            // fill.merge();

                        } else {
                            log.debug(this.getClass().getSimpleName() + ":merge. adding to exising fill position  for fill" + fill);

                            detPosition = fill.getPosition();
                            detPosition.merge();
                            // fill.merge();
                        }
                        listingPositions.add(detPosition);

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
                    /* Iterator<Position> itPos = listingPositions.iterator();
                     while (itPos.hasNext()) {
                         // closing position
                         Position pos = itPos.next();

                         closingFills: for (Iterator<Fill> itP = pos.getFills().iterator(); itP.hasNext();) {

                             Fill p = itP.next();
                             if (p.getPositionEffect() == PositionEffect.OPEN)
                                 continue;

                             if (Math.abs(p.getOpenVolumeCount()) == 0) {
                                 itP.remove();
                                 continue closingFills;
                             }*/

                    Amount entryPrice = DecimalAmount.ZERO;
                    Amount exitPrice = DecimalAmount.ZERO;
                    List<Position> closedOutListingPositions = new ArrayList<Position>();

                    CLOSEPOSITIONSLOOP: for (Position closePos : listingPositions) {
                        if (!closePos.hasFills()) {
                            log.debug("removing position: " + closePos + " from: " + listingPositions);
                            closedOutListingPositions.add(closePos);
                            continue;
                        }
                        List<Fill> closedOutClosingPositionFills = new ArrayList<Fill>();

                        CLOSEDFILLSLOOP: for (Fill closePosition : closePos.getFills()) {
                            if (closePosition.getOpenVolumeCount() == 0) {
                                log.debug("removing fill: " + closePosition + " from position: " + closePos);

                                closedOutClosingPositionFills.add(closePosition);
                                // closePosition.merge();

                                continue;
                            }
                            List<Position> closedOutOpenListingPositions = new ArrayList<Position>();

                            if (closePosition.getPositionEffect() != null)
                                openingPositionEffect = (closePosition.getPositionEffect() == (PositionEffect.CLOSE)) ? PositionEffect.OPEN
                                        : PositionEffect.CLOSE;

                            OPENPOSITIONSLOOP: for (Position openPos : openingListingPositions) {
                                if (!openPos.hasFills()) {
                                    log.debug("removing position: " + openPos + " from: " + openingListingPositions);
                                    closedOutOpenListingPositions.add(openPos);
                                    continue;
                                }
                                //  Iterator<Position> itOlp = openingListingPositions.iterator();
                                //while (itOlp.hasNext()) {
                                // openg postion
                                List<Fill> closedOutOpenPositionFills = new ArrayList<Fill>();

                                OPENFILLSLOOP: for (Fill openPosition : openPos.getFills()) {
                                    if (openPosition.getOpenVolumeCount() == 0) {
                                        log.debug("removing fill: " + openPosition + " from position: " + openPos);
                                        //  openPosition.merge();
                                        closedOutOpenPositionFills.add(openPosition);

                                        continue;
                                    }

                                    //  Iterator<Fill> itOp = openPos.getFills().iterator();
                                    //while (itOp.hasNext()) {

                                    //open fill
                                    realisedPnL = DecimalAmount.ZERO;
                                    closingVolumeCount = 0;
                                    // we only loop if it is 
                                    if (openPosition.getPositionEffect() == null || openPosition.getPositionEffect() == openingPositionEffect
                                            && openPosition.getOpenVolumeCount() != 0) {
                                        //  || (openPosition.getPositionEffect() == PositionEffect.OPEN && p.getPositionEffect() == PositionEffect.CLOSE)) {
                                        // //  if (p.getPositionEffect() == PositionEffect.OPEN)
                                        // log.info("the last fille should be a closing fill. Trying to net open position: " + openPosition
                                        //       + " with closing postion: " + p);
                                        exitPrice = closePosition.getPrice();
                                        entryPrice = openPosition.getPrice();

                                        if (closePosition.getMarket().getTradedCurrency() == closePosition.getMarket().getBase()) {
                                            // need to invert and revrese the prices if the traded ccy is not the quote ccy
                                            entryPrice = openPosition.getPrice().invert();
                                            exitPrice = closePosition.getPrice().invert();
                                        } else if (closePosition.getMarket().getTradedCurrency() != closePosition.getMarket().getQuote()) {
                                            throw new NotImplementedException("Listings traded in neither base or quote currency are not supported");
                                        }

                                        closingVolumeCount = (openingTransactionType == (TransactionType.SELL)) ? (Math.min(
                                                Math.abs(openPosition.getOpenVolumeCount()), Math.abs(closePosition.getOpenVolumeCount())))
                                                * -1 : (Math.min(Math.abs(openPosition.getOpenVolumeCount()), Math.abs(closePosition.getOpenVolumeCount())));
                                        long updatedVolumeCount = 0;
                                        if ((Math.abs(closePosition.getOpenVolumeCount()) >= Math.abs(openPosition.getOpenVolumeCount()))
                                                && (closePosition.getOpenVolumeCount() != 0)) {
                                            updatedVolumeCount = closePosition.getOpenVolumeCount() + closingVolumeCount;
                                            logCloseOut(closingVolumeCount, openPosition, closePosition, false);
                                            log.debug(this.getClass().getSimpleName() + ":merge. setting open position " + openPosition
                                                    + " open volume count to 0");

                                            openPosition.setOpenVolumeCount(0);
                                            openPosition.setPosition(null);
                                            closedOutOpenPositionFills.add(openPosition);

                                            // openPosition.merge();
                                            log.debug(this.getClass().getSimpleName() + ":merge. setting close position " + closePosition
                                                    + " open volume count to " + (closePosition.getOpenVolumeCount() + closingVolumeCount));

                                            closePosition.setOpenVolumeCount(closePosition.getOpenVolumeCount() + closingVolumeCount);
                                            if (closePosition.getOpenVolumeCount() == 0) {
                                                log.debug(this.getClass().getSimpleName() + ":merge. setting close position " + closePosition
                                                        + " position to null");

                                                closePosition.setPosition(null);

                                                closedOutClosingPositionFills.add(closePosition);
                                            }
                                            //closePosition.merge();

                                            //  closePosition.merge();
                                            logCloseOut(closingVolumeCount, openPosition, closePosition, true);

                                            //updateWorkingExitOrders(closePosition);
                                            //updateWorkingExitOrders(openPosition);
                                            // if the fill is now fully closed out I need to cancel any closing specfic limit orders associated with it
                                            // or I need to update the qunaity to the 
                                            // closed all closing limit orders

                                        } else if (closePosition.getOpenVolumeCount() != 0) {
                                            updatedVolumeCount = openPosition.getOpenVolumeCount() - closingVolumeCount;
                                            logCloseOut(closingVolumeCount, openPosition, closePosition, false);
                                            log.debug(this.getClass().getSimpleName() + ":merge. setting close position " + openPosition
                                                    + " open volume count to 0");

                                            closePosition.setOpenVolumeCount(0);
                                            closePosition.setPosition(null);
                                            closedOutClosingPositionFills.add(closePosition);
                                            // closePosition.merge();

                                            //closePos.removeFill(fill)
                                            //closePosition.re
                                            //    closePosition.merge();
                                            log.debug(this.getClass().getSimpleName() + ":merge. setting open position " + closePosition
                                                    + " open volume count to " + updatedVolumeCount);

                                            // closed all closing limit orders
                                            openPosition.setOpenVolumeCount(openPosition.getOpenVolumeCount() - closingVolumeCount);
                                            if (closePosition.getOpenVolumeCount() == 0) {
                                                log.debug(this.getClass().getSimpleName() + ":merge. setting open position " + openPosition
                                                        + " position to null");

                                                openPosition.setPosition(null);
                                                closedOutOpenPositionFills.add(openPosition);

                                            }
                                            //  openPosition.merge();
                                            logCloseOut(closingVolumeCount, openPosition, closePosition, true);

                                            // updateWorkingExitOrders(closePosition);
                                            // updateWorkingExitOrders(openPosition);
                                            // */// if the fill is now fully closed out I need to cancel any closing specfic limit orders associated with it
                                            // or I need to update the qunaity to the 
                                            // closed all closing limit orders

                                        }
                                        //  openPosition.merge();

                                        /*                                      if (openPosition.getOpenVolumeCount() == 0) {
                                                                                  log.debug("removing fill: " + openPosition + " from oprning position: " + openPos);

                                                                                  closedOutOpenPositionFills.add(openPosition);
                                                                                  // openPos.merge();
                                                                                  // openPosition.setPosition(null);
                                                                                  //openPosition.merge();
                                                                                  // cancel all specifc maker orders related to the closeing fill fill
                                                                                   if (openPosition.getPositionEffect() != null && openPosition.getPositionEffect() == PositionEffect.OPEN)
                                                                                       cancelWorkingExitOrders(closePosition);
                                                                                   else
                                                                                       cancelWorkingExitOrders(openPosition);

                                                                                  //    cancelStopOrders(openPosition);
                                                                                  if (!openPos.hasFills()) {
                                                                                      log.debug("removing opening position: " + openPos + " from: " + openingListingPositions);

                                                                                      closedOutOpenListingPositions.add(openPos);
                                                                                  }

                                                                                  //itOlp.remove();
                                                                              }
                                                                              if (closePosition.getOpenVolumeCount() == 0) {
                                                                                  log.debug("removing fill: " + closePosition + " from closing position: " + closePos);

                                                                                  closedOutClosingPositionFills.add(closePosition);

                                                                                  // cancelStopOrders(closePosition);
                                                                                  if (!closePos.hasFills()) {

                                                                                      log.debug("removing closing position: " + closePos + " from: " + listingPositions);

                                                                                      closedOutListingPositions.add(closePos);
                                                                                  }
                                        openPosition.merge                                     }*/
                                        openPosition.merge();
                                        DiscreteAmount volDiscrete = new DiscreteAmount(closingVolumeCount, closePosition.getMarket().getListing()
                                                .getVolumeBasis());

                                        realisedPnL = realisedPnL.plus(((entryPrice.minus(exitPrice)).times(volDiscrete, Remainder.ROUND_EVEN)).times(
                                                closePosition.getMarket().getContractSize(), Remainder.ROUND_EVEN));

                                        // need to confonvert to deiscreete amount

                                        Amount RealisedPnL = realisedPnL
                                                .toBasis(closePosition.getMarket().getTradedCurrency().getBasis(), Remainder.ROUND_EVEN);
                                        if (RealisedPnL.isNegative() && closePosition.getPositionEffect() == PositionEffect.OPEN)
                                            log.info("realsiedPnL is a loss. netted open position: " + openPosition + " with closing postion: " + closePosition);

                                        if (!RealisedPnL.isZero()) {

                                            Amount TotalRealisedPnL = RealisedPnL.plus(getRealisedPnL().get(closePosition.getMarket().getTradedCurrency())
                                                    .get(closePosition.getMarket().getExchange()).get(closePosition.getMarket().getListing()));

                                            Transaction trans = transactionFactory.create(closePosition, this, closePosition.getMarket().getExchange(),
                                                    closePosition.getMarket().getTradedCurrency(), TransactionType.REALISED_PROFIT_LOSS, RealisedPnL,
                                                    new DiscreteAmount(0, closePosition.getMarket().getTradedCurrency().getBasis()));
                                            log.info("Realised PnL:" + trans);
                                            context.setPublishTime(trans);
                                            trans.persit();

                                            context.route(trans);

                                        }

                                        if (closePosition.getOpenVolumeCount() == 0)
                                            break;
                                        else
                                            log.info(closePosition + " not closed fully out with " + openPosition);
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

                            }
                            if (!closedOutOpenListingPositions.isEmpty()) {
                                log.debug("removing all opening positions: " + openingListingPositions + "from: " + openingListingPositions);

                                for (Position closedPos : closedOutOpenListingPositions) {
                                    /*Boolean removePosition = true;
                                    for (Fill fillToRemove : closedPos.getFills()) {
                                        if (fillToRemove.getOpenVolumeCount() != 0) //{

                                            // fillToRemove.setPosition(null);
                                            //   fillToRemove.merge();
                                            // } else

                                            removePosition = false;

                                    }*/
                                    if (!closedPos.hasFills())
                                        closedPos.delete();
                                    else
                                        log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closedPos);
                                }
                                openingListingPositions.removeAll(closedOutOpenListingPositions);

                            }
                            closePosition.merge();

                        }
                        if (!closedOutClosingPositionFills.isEmpty()) {
                            log.debug("removing all fills: " + closedOutClosingPositionFills + "from: " + closePos);
                            Boolean removeFills = true;
                            for (Fill closedFill : closedOutClosingPositionFills) {
                                if (closedFill.getOpenVolumeCount() != 0)
                                    removeFills = false;

                            }
                            // closePos.merge();
                            if (removeFills)
                                closePos.removeFills(closedOutClosingPositionFills);
                            else
                                log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closePos);
                            if (!closePos.hasFills())
                                closedOutListingPositions.add(closePos);

                        }

                    }
                    if (!closedOutListingPositions.isEmpty()) {
                        log.debug("removing all closing positions: " + closedOutListingPositions + "from: " + listingPositions);

                        for (Position closedPos : closedOutListingPositions) {
                            // Boolean removePosition = true;

                            if (!closedPos.hasFills())
                                closedPos.delete();
                            else
                                log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closedPos);
                            /* for (Fill fillToRemove : closedPos.getFills()) {

                                 if (fillToRemove.getOpenVolumeCount() != 0)

                                     //   fillToRemove.setPosition(null);
                                     // fillToRemove.merge();
                                     //} else

                                     removePosition = false;

                                 // }
                                 if (removePosition && !closedPos.hasFills())
                                     closedPos.delete();
                                 else
                                     log.debug("unable to remove fills as dome fills have zero open qunaity in position " + closedPos);
                            */
                        }
                        listingPositions.removeAll(closedOutListingPositions);

                    }

                    //}
                    //}
                    if (getNetPosition(fill.getMarket().getBase(), fill.getMarket()) == null) {
                        Position detPosition;
                        if (fill.getPosition() == null) {
                            detPosition = positionFactory.create(fill);
                            detPosition.persit();

                        } else {
                            detPosition = fill.getPosition();
                            detPosition.merge();
                        }

                        publishPositionUpdate(detPosition, PositionType.FLAT, fill.getMarket());
                        //  if (persit)
                        //    detPosition.persit();
                        //  else
                        //    detPosition.merge();

                    } else {

                        PositionType lastType = (openingTransactionType == TransactionType.BUY) ? PositionType.LONG : PositionType.SHORT;
                        publishPositionUpdate(getNetPosition(fill.getMarket().getBase(), fill.getMarket()), lastType, fill.getMarket());
                    }
                    // fill.merge();
                    return true;
                }
                PositionType lastType = (fill.isLong()) ? PositionType.LONG : PositionType.SHORT;

                publishPositionUpdate(getNetPosition(fill.getMarket().getBase(), fill.getMarket()), lastType, fill.getMarket());
                // fill.merge();
                return true;

            }
        }
    }

    public Portfolio(String name, PortfolioManager manager) {
        this.name = name;
        this.manager = manager;

    }

    private String name;

    public String getName() {
        return name;
    }

    @Transient
    public Context getContext() {
        return context;
    }

    @Transient
    public PortfolioService getPortfolioService() {
        return portfolioService;
    }

    @Transient
    public OrderService getOrderService() {
        return orderService;
    }

    @Transient
    public Collection<Order> getAllOrders() {
        Collection<Order> orders = new ArrayList<Order>();
        for (Position position : getPositions())
            for (Fill fill : position.getFills()) {
                orders.add(fill.getOrder());
                fill.getAllOrdersByParentFill(orders);
            }
        return orders;
    }

    @Transient
    public Collection<Fill> getAllFills() {
        Collection<Fill> fills = new ArrayList<Fill>();
        for (Position position : getPositions())
            for (Fill fill : position.getFills()) {
                fills.add(fill);
                // fill.getAllOrdersByParentFill(orders);
            }
        return fills;
    }

    @OneToMany(fetch = FetchType.LAZY)
    @OrderBy
    public List<Stake> getStakes() {
        return stakes;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    public Asset getBaseAsset() {
        return baseAsset;
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
     * Adds the given position to this Portfolio.  Must be authorized.
     * @param position
     * @param authorization
     */
    @Transient
    public void modifyPosition(Fill fill, Authorization authorization) {
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

    protected void setPositions(
            ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>> positions) {
        this.positionsMap = positions;
    }

    protected void setBalances(List<Balance> balances) {
        this.balances = balances;
    }

    public void setBaseAsset(Asset baseAsset) {
        this.baseAsset = baseAsset;
    }

    protected void setTransactions(
            ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>>> transactions) {
        this.transactions = transactions;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected void setContext(Context context) {
        this.context = context;
    }

    protected void setPortfolioService(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    protected void setOrderService(OrderService orderService) {
        this.orderService = orderService;
    }

    protected void setStakes(List<Stake> stakes) {
        this.stakes = stakes;
    }

    @SuppressWarnings("unchecked")
    public static Portfolio findOrCreate(String portfolioName, Context context) {
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

            Map withChildrenHints = new HashMap();
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
                for (Fill fill : position.getFills()) {

                    // Fill filltest;
                    //  if (!portfolioFills.containsKey(fill)) {
                    context.getInjector().injectMembers(fill);

                    fill.loadAllChildOrdersByFill(fill, portfolioOrders, portfolioFills);
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

                /* UUID orderId;
                 Fill fillWithChildren = EM.namedQueryZeroOne(Fill.class, "Fill.findFill", withChildOrderHints, fill.getId());
                 if (fillWithChildren != null)
                     fill.setFillChildOrders(fillWithChildren.getFillChildOrders());
                 Order orderWithFills;
                 Order orderWithChildren;
                 Order orderWithTransactions;
                 // so for each fill in the open position we need to load the whole order tree
                 // getorder, then get all childe orders, then for each child, load child orders, so on and so forth.

                 // load all child orders, and theri child ordres
                 // load all parent orders and thier parent orders
                 // need to laod all parent fills, their child orders, and their children

                 // get a list of all orders in the tree then load 

                 orderId = fill.getOrder().getId();
                 try {
                     orderWithFills = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withFillsHints, orderId);
                     orderWithChildren = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withChildrenHints, orderId);
                     orderWithTransactions = EM.namedQueryZeroOne(Order.class, "Order.findOrder", withTransHints, orderId);
                 } catch (Error | Exception ex) {
                     log.error("Portfolio:findOrCreate unable to get order for orderID: " + orderId);
                     continue;

                 }
                 if ((orderWithFills != null && orderWithFills instanceof SpecificOrder && orderWithFills.getId().equals(orderId))
                         && (orderWithTransactions != null && orderWithTransactions instanceof SpecificOrder && orderWithTransactions.getId()
                                 .equals(orderId))
                         && (orderWithChildren != null && orderWithChildren instanceof SpecificOrder && orderWithChildren.getId().equals(orderId))) {
                     SpecificOrder order = (SpecificOrder) orderWithFills;
                     order.setTransactions(orderWithTransactions.getTransactions());
                     order.setOrderChildren(orderWithChildren.getOrderChildren());

                     fill.setOrder(order);

                     log.error("Portfolio:findOrCreate found order for orderID: " + orderId);

                 }
                }*/
            }
            for (Order order : myPort.getAllOrders()) {
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

    protected void setManager(PortfolioManager manager) {
        this.manager = manager;
    }

    @Override
    // @Transactional
    public synchronized void persit() {
        try {

            portfolioDao.persist(this);
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {

            System.out.println("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }
    }

    // @Transactional
    @Override
    public EntityBase refresh() {
        return portfolioDao.refresh(this);
    }

    @Override
    public synchronized void merge() {
        try {

            portfolioDao.merge(this);
            //if (duplicate == null || duplicate.isEmpty())
        } catch (Exception | Error ex) {

            System.out.println("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:" + ex);
            // ex.printStackTrace();

        }
    }

    @Override
    public boolean equals(Object object) {
        boolean result = false;
        if (object == null || object.getClass() != getClass()) {
            result = false;
        } else {
            Portfolio portfolio = (Portfolio) object;
            if (this.getName() == portfolio.getName()) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    private PortfolioManager manager;

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.portfolio");
    @Inject
    public transient Context context;
    @Inject
    protected transient PortfolioService portfolioService;
    @Inject
    protected transient OrderService orderService;

    @Inject
    protected transient PositionFactory positionFactory;

    @Inject
    protected transient TransactionFactory transactionFactory;

    @Inject
    protected transient PortfolioDao portfolioDao;

    private Asset baseAsset;
    private ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Position>>>>> positionsMap;
    private ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> realisedProfits;
    private List<Balance> balances = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ConcurrentLinkedQueue<Transaction>>>> transactions;
    private List<Stake> stakes = new CopyOnWriteArrayList<>();
    private List<Position> positions = new CopyOnWriteArrayList<>();
    //private ConcurrentHashMap<Market, ConcurrentSkipListMap<Long,ArrayList<TaxLot>>> longTaxLots;
    //private ConcurrentHashMap<Market, ConcurrentSkipListMap<Long,ArrayList<TaxLot>>> shortTaxLots;
    private final Collection<Balance> trades = new CopyOnWriteArrayList<>();

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
    public void detach() {
        // TODO Auto-generated method stub

    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

}
