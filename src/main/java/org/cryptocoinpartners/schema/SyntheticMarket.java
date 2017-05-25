package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.OrderBy;
import javax.persistence.PostPersist;
import javax.persistence.PrePersist;
import javax.persistence.QueryHint;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.RemainderHandler;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * Represents the possibility to trade one Asset for another at a specific Exchange.
 * 
 * @author Tim Olson
 */
@Entity
@Cacheable
@NamedQuery(name = "SyntheticMarket.findBySymbol", query = "select s from SyntheticMarket s where symbol=?1", hints = { @QueryHint(name = "org.hibernate.cacheable", value = "true") })
@NamedEntityGraphs({ @NamedEntityGraph(name = "syntheticMarketWithMarkets", attributeNodes = { @NamedAttributeNode(value = "markets") }), })
@Table(indexes = { @Index(columnList = "symbol"), @Index(columnList = "active"), @Index(columnList = "version"), @Index(columnList = "revision") })
public class SyntheticMarket extends Tradeable {
    @Inject
    protected transient static SyntheticMarketFactory syntheticMarketFactory;
    @Inject
    protected transient static MarketFactory marketFactory;

    public static SyntheticMarket findOrCreate(String symbol, List<Market> markets) {
        // final String queryStr = "select m from Market m where exchange=?1 and listing=?2";
        Map withMarketHints = new HashMap();

        withMarketHints.put("javax.persistence.fetchgraph", "syntheticMarketWithMarkets");

        /*       List<Market> childMarkets = new ArrayList<Market>();
               for (Market market : markets) {
                   Market dbMarket = market.findOrCreate(market.getExchange(), market.getListing());
                   context.getInjector().injectMembers(dbMarket);
                   childMarkets.add(dbMarket);

               }*/
        try {
            //  EM.namedQueryZeroOne(Order.class, "Order.findOrder", withFillsHints, parentOrder.getId());

            List<SyntheticMarket> results = EM.namedQueryList(SyntheticMarket.class, "SyntheticMarket.findBySymbol", symbol);
            if (results != null && !results.isEmpty() && results.get(0) != null)
                return results.get(0);
            else {
                SyntheticMarket sm = syntheticMarketFactory.create(symbol, markets);
                //  Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);
                sm.persit();
                // marketDao.persist(ml);
                return sm;
            }

            // marketDao.persist(ml);
            //return ml;
        } catch (NoResultException e) {

            SyntheticMarket sm = syntheticMarketFactory.create(symbol, markets);
            //  Market ml = new Market(exchange, listing, quoteBasis, volumeBasis);
            sm.persit();
            // marketDao.persist(ml);
            return sm;
        }

    }

    protected List<Market> markets;
    private String symbol;

    @Override
    @PostPersist
    public void postPersist() {
        // TODO Auto-generated method stub

        for (Market market : this.getMarkets())
            market.persit();

    }

    public void addMarket(Market market) {
        boolean existing = false;
        List<Market> markets = getMarkets();
        for (int i = 0; i < markets.size(); i++) {
            if (markets.get(i).equals(market)) {
                markets.set(i, market);
                existing = true;
            }
        }
        if (!existing)
            markets.add(market);

    }

    public synchronized void removeMarket(Market market) {
        getMarkets().remove(market);
        market.getSyntheticMarkets().remove(this);

    }

    @Transient
    public List<Listing> getListings() {
        List<Listing> listings = new ArrayList<Listing>();
        for (Market market : markets)
            if (market != null && market.getListing() != null)
                listings.add(market.getListing());
        return listings;
    }

    @Override
    public String getSymbol() {
        return symbol;

    }

    private void setMarkets(List<Market> markets) {
        this.markets = markets;
    }

    @Nullable
    @ManyToMany(mappedBy = "syntheticMarkets", fetch = FetchType.EAGER)
    @OrderBy
    public List<Market> getMarkets() {
        return markets;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;

    }

    @Override
    public String toString() {
        return getSymbol();
    }

    public static class MarketAmountBuilder {

        public DiscreteAmount fromPriceCount(long count) {
            return priceBuilder.fromCount(count);
        }

        public DiscreteAmount fromVolumeCount(long count) {
            return volumeBuilder.fromCount(count);
        }

        public DiscreteAmount fromPrice(BigDecimal amount, RemainderHandler remainderHandler) {
            return priceBuilder.fromValue(amount, remainderHandler);
        }

        public DiscreteAmount fromVolume(BigDecimal amount, RemainderHandler remainderHandler) {
            return volumeBuilder.fromValue(amount, remainderHandler);
        }

        public MarketAmountBuilder(double priceBasis, double volumeBasis) {
            this.priceBuilder = DiscreteAmount.withBasis(priceBasis);
            this.volumeBuilder = DiscreteAmount.withBasis(volumeBasis);
        }

        private final transient DiscreteAmount.DiscreteAmountBuilder priceBuilder;
        private final transient DiscreteAmount.DiscreteAmountBuilder volumeBuilder;
    }

    // JPA
    protected SyntheticMarket() {
    }

    //When we create a synthticmarket we need to set the pricebasis and the volume baiss to the smallest amount (greatest precsion) for all of the markets
    @AssistedInject
    public SyntheticMarket(@Assisted String symbol, @Assisted List<Market> markets) {
        this.symbol = symbol;
        this.active = true;
        Double pBasis = null;
        Double vBasis = null;
        for (Market market : markets) {
            if (pBasis == null)
                pBasis = market.getPriceBasis();
            else if (((Double) market.getPriceBasis()).compareTo(pBasis) < 0)
                pBasis = market.getPriceBasis();
            if (vBasis == null)
                vBasis = market.getVolumeBasis();
            else if (((Double) market.getVolumeBasis()).compareTo(vBasis) < 0)
                vBasis = market.getVolumeBasis();
            market.getSyntheticMarkets().add(this);
        }
        this.markets = markets;
        this.priceBasis = pBasis;
        this.volumeBasis = vBasis;

    }

    @Override
    @Basic(optional = false)
    public double getPriceBasis() {

        return priceBasis;

    }

    @Override
    @Basic(optional = false)
    public double getVolumeBasis() {
        return volumeBasis;

    }

    @Override
    @Transient
    public Amount getBalance(Asset currency) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    @PrePersist
    public void prePersist() {
        // TODO Auto-generated method stub

    }

    @Override
    @Transient
    public boolean isSynthetic() {

        return true;
    }

}
