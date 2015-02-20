package org.cryptocoinpartners.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import org.apache.commons.lang.NotImplementedException;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;

/**
 * Many Owners may have Stakes in the Portfolio, but there is only one PortfolioManager, who is not necessarily an Owner.  The
 * Portfolio has multiple Positions.
 *
 * @author Tim Olson
 */
@Entity
public class Portfolio extends EntityBase {

    private static Object lock = new Object();

    /** returns all Positions, whether they are tied to an open Order or not.  Use getTradeablePositions() */
    public @Transient
    Collection<Fill> getDetailedPositions() {
        ArrayList<Fill> allPositions = new ArrayList<Fill>();
        Iterator<Asset> it = positions.keySet().iterator();
        while (it.hasNext()) {
            Asset asset = it.next();
            Iterator<Exchange> ite = positions.get(asset).keySet().iterator();
            while (ite.hasNext()) {
                Exchange exchange = ite.next();
                Iterator<Listing> itl = positions.get(asset).get(exchange).keySet().iterator();
                while (itl.hasNext()) {
                    Listing listing = itl.next();
                    Iterator<TransactionType> itt = positions.get(asset).get(exchange).get(listing).keySet().iterator();
                    while (itt.hasNext()) {

                        TransactionType transactionType = itt.next();
                        Iterator<Fill> itp = positions.get(asset).get(exchange).get(listing).get(transactionType).iterator();
                        while (itp.hasNext()) {
                            Fill pos = itp.next();
                            allPositions.add(pos);
                        }
                    }

                }
            }
        }

        return allPositions;
    }

    public @Transient
    Collection<Position> getPositions() {
        ArrayList<Position> allPositions = new ArrayList<Position>();
        Iterator<Asset> it = positions.keySet().iterator();
        while (it.hasNext()) {
            Asset asset = it.next();
            Iterator<Exchange> ite = positions.get(asset).keySet().iterator();
            while (ite.hasNext()) {
                Exchange exchange = ite.next();
                Iterator<Listing> itl = positions.get(asset).get(exchange).keySet().iterator();
                while (itl.hasNext()) {
                    Listing listing = itl.next();
                    Iterator<TransactionType> itt = positions.get(asset).get(exchange).get(listing).keySet().iterator();

                    while (itt.hasNext()) {
                        Amount longVolume = DecimalAmount.ZERO;
                        Amount longAvgPrice = DecimalAmount.ZERO;
                        Amount longAvgStopPrice = DecimalAmount.ZERO;
                        Amount shortVolume = DecimalAmount.ZERO;
                        Amount shortAvgPrice = DecimalAmount.ZERO;
                        Amount shortAvgStopPrice = DecimalAmount.ZERO;
                        TransactionType transactionType = itt.next();
                        Iterator<Fill> itlp = positions.get(asset).get(exchange).get(listing).get(transactionType).iterator();
                        while (itlp.hasNext()) {
                            Fill pos = itlp.next();
                            if (pos.isLong()) {
                                longAvgPrice = ((longAvgPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getPrice(),
                                        Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);
                                if(pos.getStopPrice()!=null)
                                longAvgStopPrice = ((longAvgStopPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getStopPrice(),
                                        Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);

                                longVolume = longVolume.plus(pos.getVolume());
                            } else if (pos.isShort()) {
                                shortAvgPrice = ((shortAvgPrice.times(shortVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getPrice(),
                                        Remainder.ROUND_EVEN))).dividedBy(shortVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);
                                if(pos.getStopPrice()!=null)
                                shortAvgStopPrice = ((shortAvgStopPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getStopPrice(),
                                        Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);

                                shortVolume = shortVolume.plus(pos.getVolume());
                            }
                        }
                        // need to change this to just return one position that is the total, not one long and one short.
                        if (!shortVolume.isZero() || !longVolume.isZero()) {
                            Market market = Market.findOrCreate(exchange, listing);
                            Position position = new Position(this, exchange, market, asset, longVolume, longAvgPrice);
                            position.setLongAvgPrice(longAvgPrice);
                            position.setShortAvgPrice(shortAvgPrice);
                            position.setShortAvgStopPrice(shortAvgStopPrice);
                            position.setLongAvgStopPrice(longAvgStopPrice);
                            position.setVolumeCount((longVolume.plus(shortVolume)).toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount());
                            allPositions.add(position);
                        }

                    }
                }
            }
        }

        return allPositions;
    }

    public @Transient
    Position getPosition(Asset asset, Market market) {
        ArrayList<Position> allPositions = new ArrayList<Position>();
        Exchange exchange = market.getExchange();
        Listing listing = market.getListing();
        Position position = null;
        Iterator<Asset> it = positions.keySet().iterator();

        Iterator<TransactionType> itt = positions.get(asset).get(exchange).get(listing).keySet().iterator();

        while (itt.hasNext()) {
            Amount longVolume = DecimalAmount.ZERO;
            Amount longAvgPrice = DecimalAmount.ZERO;
            Amount longAvgStopPrice = DecimalAmount.ZERO;
            Amount shortVolume = DecimalAmount.ZERO;
            Amount shortAvgPrice = DecimalAmount.ZERO;
            Amount shortAvgStopPrice = DecimalAmount.ZERO;
            TransactionType transactionType = itt.next();
            Iterator<Fill> itlp = positions.get(asset).get(exchange).get(listing).get(transactionType).iterator();
            while (itlp.hasNext()) {
                Fill pos = itlp.next();
                if (pos.isLong()) {
                    longAvgPrice = ((longAvgPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getPrice(), Remainder.ROUND_EVEN)))
                            .dividedBy(longVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);
                    if(pos.getStopPrice()!=null)
                    longAvgStopPrice = ((longAvgStopPrice.times(longVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getStopPrice(),
                            Remainder.ROUND_EVEN))).dividedBy(longVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);

                    longVolume = longVolume.plus(pos.getVolume());
                } else if (pos.isShort()) {
                    shortAvgPrice = ((shortAvgPrice.times(shortVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getPrice(), Remainder.ROUND_EVEN)))
                            .dividedBy(shortVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);
                    if(pos.getStopPrice()!=null)
                    shortAvgStopPrice = ((shortAvgStopPrice.times(shortVolume, Remainder.ROUND_EVEN)).plus(pos.getVolume().times(pos.getStopPrice(),
                            Remainder.ROUND_EVEN))).dividedBy(shortVolume.plus(pos.getVolume()), Remainder.ROUND_EVEN);

                    shortVolume = shortVolume.plus(pos.getVolume());
                }
            }
            // need to change this to just return one position that is the total, not one long and one short.
            if (!shortVolume.isZero() || !longVolume.isZero()) {
                market = Market.findOrCreate(exchange, listing);
                position = new Position(this, exchange, market, asset, longVolume, longAvgPrice);
                position.setLongAvgPrice(longAvgPrice);
                position.setShortAvgPrice(shortAvgPrice);
                position.setLongAvgStopPrice(longAvgStopPrice);
                position.setShortAvgStopPrice(shortAvgStopPrice);
                position.setVolumeCount((longVolume.plus(shortVolume)).toBasis(market.getVolumeBasis(), Remainder.ROUND_EVEN).getCount());
                return position;
            }

        }

        return position;

    }

    public @Transient
    Collection<Fill> getPositions(Asset asset, Exchange exchange) {
        ArrayList<Fill> allPositions = new ArrayList<Fill>();
        if (positions.get(asset) != null && positions.get(asset).get(exchange) != null) {
            Iterator<Listing> itl = positions.get(asset).get(exchange).keySet().iterator();
            while (itl.hasNext()) {
                Listing listing = itl.next();
                Iterator<TransactionType> itt = positions.get(asset).get(exchange).get(listing).keySet().iterator();
                while (itt.hasNext()) {
                    TransactionType transactionType = itt.next();

                    Iterator<Fill> itp = positions.get(asset).get(exchange).get(listing).get(transactionType).iterator();
                    while (itp.hasNext()) {
                        Fill pos = itp.next();
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
        Iterator<Asset> it = realisedProfits.keySet().iterator();
        while (it.hasNext()) {
            Asset asset = it.next();
            Iterator<Exchange> ite = realisedProfits.get(asset).keySet().iterator();
            while (ite.hasNext()) {
                Exchange exchange = ite.next();
                Iterator<Listing> itl = realisedProfits.get(asset).get(exchange).keySet().iterator();
                while (itl.hasNext()) {
                    Listing listing = itl.next();
                    Amount realisedPnL = realisedProfits.get(asset).get(exchange).get(listing);

                    if (allPnLs.get(asset) == null) {
                        allPnLs.put(asset, realisedPnL);
                    } else {
                        allPnLs.put(asset, allPnLs.get(asset).plus(realisedPnL));
                    }
                }

            }

        }

        return allPnLs;
    }

    public @Transient
    Amount getRealisedPnL(Asset asset) {

        Amount realisedPnL = DecimalAmount.ZERO;
        Iterator<Exchange> ite = realisedProfits.get(asset).keySet().iterator();
        while (ite.hasNext()) {
            Exchange exchange = ite.next();
            Iterator<Listing> itl = realisedProfits.get(asset).get(exchange).keySet().iterator();
            while (itl.hasNext()) {
                Listing listing = itl.next();
                realisedPnL = realisedPnL.plus(realisedProfits.get(asset).get(exchange).get(listing));

            }
        }

        return realisedPnL;
    }

    public @Transient
    ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> getRealisedPnL() {

        return realisedProfits;
    }

    public @Transient
    DiscreteAmount getLongPosition(Asset asset, Exchange exchange) {
        long longVolumeCount = 0;
        if (positions.get(asset) != null && positions.get(asset).get(exchange) != null) {
            Iterator<Listing> itl = positions.get(asset).get(exchange).keySet().iterator();

            while (itl.hasNext()) {
                Listing listing = itl.next();

                Iterator<Fill> itp = positions.get(asset).get(exchange).get(listing).get(TransactionType.BUY).iterator();
                while (itp.hasNext()) {
                    Fill pos = itp.next();
                    longVolumeCount += pos.getVolumeCount();
                }

            }
        }
        return new DiscreteAmount(longVolumeCount, asset.getBasis());

    }

    public @Transient
    DiscreteAmount getNetPosition(Asset asset, Exchange exchange) {
        long netVolumeCount = 0;
        Fill pos = null;
        if (positions.get(asset) != null && positions.get(asset).get(exchange) != null) {
            Iterator<Listing> itl = positions.get(asset).get(exchange).keySet().iterator();
            while (itl.hasNext()) {
                Listing listing = itl.next();
                Iterator<TransactionType> itt = positions.get(asset).get(exchange).get(listing).keySet().iterator();
                while (itt.hasNext()) {
                    TransactionType transactionType = itt.next();

                    Iterator<Fill> itp = positions.get(asset).get(exchange).get(listing).get(transactionType).iterator();

                    while (itp.hasNext()) {
                        pos = itp.next();
                        netVolumeCount += pos.getVolumeCount();
                    }

                }
            }
        }

        return new DiscreteAmount(netVolumeCount, asset.getBasis());
    }

    public @Transient
    DiscreteAmount getShortPosition(Asset asset, Exchange exchange) {
        long shortVolumeCount = 0;
        if (positions.get(asset) != null && positions.get(asset).get(exchange) != null) {
            Iterator<Listing> itl = positions.get(asset).get(exchange).keySet().iterator();

            while (itl.hasNext()) {
                Listing listing = itl.next();

                Iterator<Fill> itp = positions.get(asset).get(exchange).get(listing).get(TransactionType.SELL).iterator();

                while (itp.hasNext()) {
                    Fill pos = itp.next();
                    shortVolumeCount += pos.getVolumeCount();

                }
            }
        }
        return new DiscreteAmount(shortVolumeCount, asset.getBasis());

    }

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
        ArrayList<Transaction> allTransactions = new ArrayList<Transaction>();
        Iterator<Asset> it = transactions.keySet().iterator();
        while (it.hasNext()) {
            Asset asset = it.next();
            Iterator<Exchange> ite = transactions.get(asset).keySet().iterator();
            while (ite.hasNext()) {
                Exchange exchange = ite.next();
                Iterator<TransactionType> itt = transactions.get(asset).get(exchange).keySet().iterator();
                while (itt.hasNext()) {
                    TransactionType type = itt.next();
                    Iterator<Transaction> ittr = transactions.get(asset).get(exchange).get(type).iterator();
                    while (ittr.hasNext()) {
                        Transaction tran = ittr.next();
                        allTransactions.add(tran);
                    }

                }
            }
        }
        return allTransactions;

    }

    @Transient
    public void removeTransaction(Transaction reservation) {

        Iterator<Transaction> it = transactions.get(reservation.getCurrency()).get(reservation.getExchange()).get(reservation.getType()).iterator();

        while (it.hasNext()) {
            Transaction transaction = it.next();
            if (transaction.equals(reservation))
                it.remove();
        }

    }

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
    public boolean addTransaction(Transaction transaction) {
        ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ArrayList<Transaction>>> assetTransactions = transactions.get(transaction.getCurrency());

        if (assetTransactions == null) {
            ArrayList<Transaction> transactionList = new ArrayList<Transaction>();
            assetTransactions = new ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ArrayList<Transaction>>>();
            transactionList.add(transaction);
            ConcurrentHashMap<TransactionType, ArrayList<Transaction>> transactionGroup = new ConcurrentHashMap<TransactionType, ArrayList<Transaction>>();
            transactionGroup.put(transaction.getType(), transactionList);
            assetTransactions.put(transaction.getExchange(), transactionGroup);
            transactions.put(transaction.getCurrency(), assetTransactions);
            return true;
        } else {
            //asset is present, so check the market
            ConcurrentHashMap<TransactionType, ArrayList<Transaction>> exchangeTransactions = assetTransactions.get(transaction.getExchange());

            if (exchangeTransactions == null) {
                ArrayList<Transaction> transactionList = new ArrayList<Transaction>();
                transactionList.add(transaction);
                ConcurrentHashMap<TransactionType, ArrayList<Transaction>> transactionGroup = new ConcurrentHashMap<TransactionType, ArrayList<Transaction>>();
                transactionGroup.put(transaction.getType(), transactionList);
                assetTransactions.put(transaction.getExchange(), transactionGroup);

                return true;
            } else {
                ArrayList<Transaction> transactionList = exchangeTransactions.get(transaction.getType());

                if (transactionList == null) {
                    transactionList = new ArrayList<Transaction>();
                    transactionList.add(transaction);
                    exchangeTransactions.put(transaction.getType(), transactionList);
                    return true;
                } else {
                    transactionList.add(transaction);
                    exchangeTransactions.put(transaction.getType(), transactionList);
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

    protected void publishPositionUpdate(Position position) {

        PositionType mergedType = (position.isShort()) ? PositionType.SHORT : (position.isLong()) ? PositionType.LONG : PositionType.FLAT;

        context.route(new PositionUpdate(position, mergedType));
    }

    @Transient
    private boolean merge(Fill fill) {
        // We need to have a queue of buys and a queue of sells ( two array lists), ensure the itterator is descendingIterator for LIFO,
        // when we get a new trade coem in we add it to the buy or sell queue
        // 1) caluate price difference
        // 2) times price diff by min(trade quantity or the position) and add to relasied PnL
        // 3) update the quaitity of the postion and remove from queue if zero
        // 4) move onto next postion until the qty =0
        // https://github.com/webpat/jquant-core/blob/173d5ca79b318385a3754c8e1357de79ece47be4/src/main/java/org/jquant/portfolio/Portfolio.java
        TransactionType transactionType = (fill.isLong()) ? TransactionType.BUY : TransactionType.SELL;
        TransactionType openingTransactionType = (transactionType.equals(TransactionType.BUY)) ? TransactionType.SELL : TransactionType.BUY;

        ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>>> assetPositions = positions.get(fill
                .getMarket().getBase());
        ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>> listingPosition = new ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>>();
        //ConcurrentHashMap<Listing, ArrayList<Position>> listingPosition = new ConcurrentHashMap<Listing, ArrayList<Position>>();

        ConcurrentHashMap<Listing, Amount> marketRealisedProfits;
        ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>> assetRealisedProfits = realisedProfits.get(fill.getMarket().getTradedCurrency());
        if (assetRealisedProfits != null) {
            marketRealisedProfits = assetRealisedProfits.get(fill.getMarket().getListing());
        }

        if (assetPositions == null) {
            ArrayList<Fill> detailPosition = new ArrayList<Fill>();
            detailPosition.add(fill);
            ConcurrentHashMap<TransactionType, ArrayList<Fill>> positionType = new ConcurrentHashMap<TransactionType, ArrayList<Fill>>();
            positionType.put(transactionType, detailPosition);

            listingPosition.put(fill.getMarket().getListing(), positionType);
            assetPositions = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>>>();
            assetPositions.put(fill.getMarket().getExchange(), listingPosition);
            positions.put(fill.getMarket().getBase(), assetPositions);

            Amount profits = DecimalAmount.ZERO;
            if (assetRealisedProfits == null) {
                assetRealisedProfits = new ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>();
                marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
                marketRealisedProfits.put(fill.getMarket().getListing(), profits);
                assetRealisedProfits.put(fill.getMarket().getExchange(), marketRealisedProfits);
                realisedProfits.put(fill.getMarket().getTradedCurrency(), assetRealisedProfits);
            }
            publishPositionUpdate(getPosition(fill.getMarket().getBase(), fill.getMarket()));
            return true;
        } else {
            //asset is present, so check the market
            ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>> exchangePositions = assetPositions.get(fill.getMarket()
                    .getExchange());
            //	Amount exchangeRealisedProfits = realisedProfits.get(position.getMarket().getTradedCurrency()).get(position.getExchange())
            //	.get(position.getMarket().getListing());

            if (exchangePositions == null) {
                ArrayList<Fill> detailPosition = new ArrayList<Fill>();
                ConcurrentHashMap<TransactionType, ArrayList<Fill>> positionType = new ConcurrentHashMap<TransactionType, ArrayList<Fill>>();
                detailPosition.add(fill);
                positionType.put(transactionType, detailPosition);

                listingPosition.put(fill.getMarket().getListing(), positionType);

                assetPositions.put(fill.getMarket().getExchange(), listingPosition);
                Amount profits = DecimalAmount.ZERO;
                if (realisedProfits.get(fill.getMarket().getTradedCurrency()).get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) == null) {
                    marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
                    marketRealisedProfits.put(fill.getMarket().getListing(), profits);
                    realisedProfits.get(fill.getMarket().getTradedCurrency()).put(fill.getMarket().getExchange(), marketRealisedProfits);
                }
                publishPositionUpdate(getPosition(fill.getMarket().getBase(), fill.getMarket()));

                return true;
            } else {

                //ConcurrentHashMap<TransactionType, ArrayList<Position>> listingPositions = exchangePositions.get(position.getMarket().getListing());
                //asset is present, so check the market
                // need yo vhnage this to have tne cocnurrent hashmap on here
                //ConcurrentHashMap<TransactionType, ArrayList<Position>> listingPositions = exchangePositions.get(position.getMarket().getListing());
                ArrayList<Fill> listingPositions = exchangePositions.get(fill.getMarket().getListing()).get(transactionType);
                ArrayList<Fill> openingListingPositions = exchangePositions.get(fill.getMarket().getListing()).get(openingTransactionType);

                if (listingPositions == null) {
                    ArrayList<Fill> listingsDetailPosition = new ArrayList<Fill>();
                    listingsDetailPosition.add(fill);
                    exchangePositions.get(fill.getMarket().getListing()).put(transactionType, listingsDetailPosition);
                    listingPositions = exchangePositions.get(fill.getMarket().getListing()).get(transactionType);
                    Amount listingProfits = DecimalAmount.ZERO;
                    if (realisedProfits.get(fill.getMarket().getTradedCurrency()) == null
                            || realisedProfits.get(fill.getMarket().getTradedCurrency()).get(fill.getMarket().getExchange()) == null
                            || realisedProfits.get(fill.getMarket().getTradedCurrency()).get(fill.getMarket().getExchange()).get(fill.getMarket().getListing()) == null) {
                        marketRealisedProfits = new ConcurrentHashMap<Listing, Amount>();
                        marketRealisedProfits.put(fill.getMarket().getListing(), listingProfits);
                        realisedProfits.get(fill.getMarket().getTradedCurrency()).put(fill.getMarket().getExchange(), marketRealisedProfits);
                    }
                } else {
                    listingPositions.add(fill);
                }
                if (openingListingPositions != null && !(openingListingPositions.isEmpty())) {
                    //	ArrayList<Position> positions = listingPositions.get(transactionType);

                    //somethign is up with the poistions calcuation for partial closeouts
                    // example 454 lots, closed out 421 lots, then added another 411 lots, total of 444 lots, but the average prices are not correct.
                    // need to update this .					

                    Amount realisedPnL = DecimalAmount.ZERO;
                    long closingVolumeCount = 0;
                    //position.getVolumeCount() 
                    Iterator<Fill> itp = listingPositions.iterator();
                    while (itp.hasNext()) {
                        Fill p = itp.next();
                        //while (p.getVolumeCount() != 0 && itp.hasNext()) {

                        //if (p.getExchange().equals(position.getExchange()) && p.getAsset().equals(position.getAsset())) {

                        Amount entryPrice = DecimalAmount.ZERO;
                        Amount exitPrice = DecimalAmount.ZERO;

                        // now need to get opposit side
                        Iterator<Fill> itop = openingListingPositions.iterator();
                        while (Math.abs(p.getVolumeCount()) > 0 && itop.hasNext()) {
                            Fill openPosition = itop.next();
                            if ((Long.signum(openPosition.getVolumeCount()) + Long.signum(p.getVolumeCount())) != 0) {
                                if (Math.abs(p.getVolumeCount()) == 0)
                                    itp.remove();
                                if (Math.abs(openPosition.getVolumeCount()) == 0)
                                    itop.remove();
                                break;

                            }
                            //Math signum();

                            entryPrice = p.getPrice();
                            exitPrice = openPosition.getPrice();
                            if (p.getMarket().getTradedCurrency() == p.getMarket().getBase()) {
                                // need to invert and revrese the prices if the traded ccy is not the quote ccy
                                entryPrice = openPosition.getPrice().invert();
                                exitPrice = p.getPrice().invert();

                                //shortExitPrice = position.getShortAvgPrice().invert();
                                //longEntryPrice = p.getLongAvgPrice().invert();
                                //longExitPrice = position.getLongAvgPrice().invert();
                                //shortEntryPrice = p.getShortAvgPrice().invert();

                            } else if (p.getMarket().getTradedCurrency() != p.getMarket().getQuote()) {
                                throw new NotImplementedException("Listings traded in neither base or quote currency are not supported");
                            }

                            // need to calcuate teh volume here
                            // we have opposite postions, so if I am long, 
                            // tests
                            // long - postions =10, net =-5 -> neet ot take 5 max(), postion =10, net =-10 net to take 10 (max), psotis =10, net =-20 net to take  (Min)10
                            // short postion =-10, net =5 neet to take 5, Max) postions = -10, net =10 need to take 10, postion =-10, net =20 net to take  min 10

                            // need to srt out closing postions here
                            // as we use negative numbers not long ans short numbers

                            //	10,-5 () my volume is 5
                            //	5,-10 my voulme is 5
                            //	-10,5 my volume is -5
                            //	-5,10 my volume is -5
                            //	10,-10 my voulme is 10

                            //Math.abs(a)

                            closingVolumeCount = (openingTransactionType.equals(TransactionType.SELL)) ? (Math.min(Math.abs(openPosition.getVolumeCount()),
                                    Math.abs(p.getVolumeCount()))) * -1 : (Math.min(Math.abs(openPosition.getVolumeCount()), Math.abs(p.getVolumeCount())));
                            // need to think hwere as one if negative and one is postive, nwee to work out what is the quanity to update on currrnet and the passed position
                            //when p=43 and open postion =-42
                            if (Math.abs(p.getVolumeCount()) >= Math.abs(openPosition.getVolumeCount())) {
                                long updatedVolumeCount = p.getVolumeCount() + closingVolumeCount;
                                //updatedVolumeCount = (p.isShort()) ? updatedVolumeCount * -1 : updatedVolumeCount;
                                p.setVolumeCount(updatedVolumeCount);
                                if (Math.abs(updatedVolumeCount) == 0)
                                    itp.remove();
                                openPosition.setVolumeCount(0);
                                itop.remove();

                            } else {
                                long updatedVolumeCount = openPosition.getVolumeCount() + p.getVolumeCount();
                                openPosition.setVolumeCount(updatedVolumeCount);

                                if (updatedVolumeCount == 0) {
                                    itop.remove();

                                }
                                p.setVolumeCount(0);
                                itp.remove();

                            }
                            DiscreteAmount volDiscrete = new DiscreteAmount(closingVolumeCount, p.getMarket().getListing().getVolumeBasis());

                            realisedPnL = realisedPnL.plus(((entryPrice.minus(exitPrice)).times(volDiscrete, Remainder.ROUND_EVEN)).times(p.getMarket()
                                    .getContractSize(), Remainder.ROUND_EVEN));

                            // need to confonvert to deiscreete amount

                            //LongRealisedPnL = ((exitPrice.minus(entryPrice)).times(volDiscrete, Remainder.ROUND_EVEN)).times(position.getMarket()
                            //	.getContractSize(), Remainder.ROUND_EVEN);

                            //	ShortRealisedPnL = (position.getShortAvgPrice().minus(p.getLongAvgPrice())).times(position.getShortVolume().negate(),
                            //	Remainder.ROUND_EVEN);
                            //	LongRealisedPnL = (position.getLongAvgPrice().minus(p.getShortAvgPrice())).times(position.getLongVolume().negate(),
                            //		Remainder.ROUND_EVEN);

                        }

                        Amount RealisedPnL = realisedPnL.toBasis(p.getMarket().getTradedCurrency().getBasis(), Remainder.ROUND_EVEN);
                        Amount PreviousPnL = (realisedProfits.get(p.getMarket().getTradedCurrency()) == null
                                || realisedProfits.get(p.getMarket().getTradedCurrency()).get(p.getMarket().getExchange()) == null || realisedProfits
                                .get(p.getMarket().getTradedCurrency()).get(p.getMarket().getExchange()).get(p.getMarket().getListing()) == null) ? DecimalAmount.ZERO
                                : realisedProfits.get(p.getMarket().getTradedCurrency()).get(p.getMarket().getExchange()).get(p.getMarket().getListing());
                        if (!RealisedPnL.isZero()) {

                            Amount TotalRealisedPnL = RealisedPnL.plus(realisedProfits.get(p.getMarket().getTradedCurrency()).get(p.getMarket().getExchange())
                                    .get(p.getMarket().getListing()));

                            realisedProfits.get(p.getMarket().getTradedCurrency()).get(p.getMarket().getExchange())
                                    .put(p.getMarket().getListing(), TotalRealisedPnL);
                            Transaction trans = new Transaction(this, p.getMarket().getExchange(), p.getMarket().getTradedCurrency(),
                                    TransactionType.REALISED_PROFIT_LOSS, RealisedPnL, new DiscreteAmount(0, p.getMarket().getTradedCurrency().getBasis()));
                            context.route(trans);
                            //		manager.getPortfolioService().CreateTransaction(position.getExchange(), position.getMarket().getQuote(),
                            //			TransactionType.REALISED_PROFIT_LOSS, TotalRealisedPnL.minus(PreviousPnL), DecimalAmount.ZERO);

                        }

                        //							if (!totalQuantity.isZero()) {
                        //								//generate PnL
                        //								//Update postion Quanitty
                        //								//Recculate Avaerge Price
                        //								Amount avgPrice = ((p.getAvgPrice().times(p.getVolume(), Remainder.ROUND_EVEN)).plus(position.getLongVolume().times(
                        //										position.getAvgPrice(), Remainder.ROUND_EVEN))).dividedBy(p.getVolume().plus(position.getLongVolume()),
                        //										Remainder.ROUND_EVEN);
                        //								p.setAvgPrice(avgPrice);
                        //							}

                        //							if (!position.getLongVolume().isZero()) {
                        //								// i.e long position
                        //								Amount vol = (p.getLongAvgPrice().isZero()) ? position.getLongVolume() : p.getLongVolume().plus(position.getLongVolume());
                        //								if (!vol.isZero()) {
                        //									longExitPrice = ((p.getLongAvgPrice().times(p.getLongVolume(), Remainder.ROUND_EVEN)).plus(position.getLongVolume().times(
                        //											position.getLongAvgPrice(), Remainder.ROUND_EVEN))).dividedBy(vol, Remainder.ROUND_EVEN);
                        //									p.setLongAvgPrice(longExitPrice);
                        //								}
                        //							}

                        //							if (!position.getShortVolume().isZero()) {
                        //								// i.e short position
                        //								//this does not work when we net out the postion as we have a divid by zero error
                        //								Amount vol = (p.getShortAvgPrice().isZero()) ? position.getShortVolume() : p.getShortVolume().plus(position.getShortVolume());
                        //								if (vol.isZero()) {
                        //									shortExitPrice = ((p.getShortAvgPrice().times(p.getShortVolume(), Remainder.ROUND_EVEN)).plus(position.getShortVolume()
                        //											.times(position.getShortAvgPrice(), Remainder.ROUND_EVEN))).dividedBy(vol, Remainder.ROUND_EVEN);
                        //									p.setShortAvgPrice(shortExitPrice);
                        //								}
                        //							}
                        //p.setLongVolumeCount(p.getLongVolumeCount() + position.getLongVolumeCount());
                        //p.setShortVolumeCount(p.getShortVolumeCount() + position.getShortVolumeCount());

                        //	Long avgPriceCount = (long) avgPrice.divide(BigDecimal.valueOf(p.getMarket().getPriceBasis()), Remainder.ROUND_EVEN).asDouble();
                        //avgPrice = new DiscreteAmount(avgPriceCount, p.getMarket().getPriceBasis());
                        //DiscreteAmount avgDiscretePrice = new DiscreteAmount((long) avgPrice.times(p.getMarket().getPriceBasis(), Remainder.ROUND_EVEN)
                        //	.asDouble(), (long) (p.getMarket().getPriceBasis()));
                        // I need to net the amounts

                        // if the long and short volumes are zero we can remove the position
                        //if (p.getShortVolumeCount() * -1 == p.getLongVolumeCount()) {
                        //listingPositions.remove(p);
                        // publish realised PnL for the long and short posiotion
                        //TODO: we are merging postions based on the order they were creted (FIFO), might want to have a comparator to merge using LIFO, or some other algo

                        //}
                        //return true;

                        //}

                    }
                }
                //listingPositions.add(position);
                //return true;
                if (getPosition(fill.getMarket().getBase(), fill.getMarket()) == null) {
                    publishPositionUpdate(new Position(fill.getPortfolio(), fill.getMarket().getExchange(), fill.getMarket(), fill.getMarket().getBase(),
                            DecimalAmount.ZERO, DecimalAmount.ZERO));
                } else {
                    publishPositionUpdate(getPosition(fill.getMarket().getBase(), fill.getMarket()));
                }
                return true;

            }//else {
             //listingPositions.add(position);
             //return true;
             //}

            //return true;

        }

    }

    public Portfolio(String name, PortfolioManager manager) {
        this.name = name;
        this.manager = manager;
        this.positions = new ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>>>>();
        this.realisedProfits = new ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>>();
        this.balances = new ArrayList<>();
        this.transactions = new ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ArrayList<Transaction>>>>();

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
    protected void modifyPosition(Fill fill, Authorization authorization) {
        assert authorization != null;
        assert fill != null;
        boolean modifiedExistingPosition = false;
        merge(fill);

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
    protected Portfolio() {
    }

    protected void setPositions(
            ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>>>> positions) {
        this.positions = positions;
    }

    protected void setBalances(Collection<Balance> balances) {
        this.balances = balances;
    }

    public void setBaseAsset(Asset baseAsset) {
        this.baseAsset = baseAsset;
    }

    protected void setTransactions(
            ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ArrayList<Transaction>>>> transactions) {
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
    @Inject
    private Logger log;
    @Inject
    protected Context context;
    private Asset baseAsset;
    private ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, ConcurrentHashMap<TransactionType, ArrayList<Fill>>>>> positions;
    private ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> realisedProfits;
    private Collection<Balance> balances = Collections.emptyList();
    private ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<TransactionType, ArrayList<Transaction>>>> transactions;
    private Collection<Stake> stakes = Collections.emptyList();
    //private ConcurrentHashMap<Market, ConcurrentSkipListMap<Long,ArrayList<TaxLot>>> longTaxLots;
    //private ConcurrentHashMap<Market, ConcurrentSkipListMap<Long,ArrayList<TaxLot>>> shortTaxLots;
    private final Collection<Balance> trades = Collections.emptyList();

}
