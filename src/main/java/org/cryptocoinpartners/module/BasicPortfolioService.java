package org.cryptocoinpartners.module;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Balance;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.ExchangeFactory;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.RemoteEvent;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.TradeFactory;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.PortfolioServiceException;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.FeesUtil;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class BasicPortfolioService implements PortfolioService {

  private final Map<Asset, Amount> allPnLs = new ConcurrentHashMap<Asset, Amount>();
  private static Object lock = new Object();

  public BasicPortfolioService(Portfolio portfolio) {
    this.addPortfolio(portfolio);
    // this.allPnLs = new ConcurrentHashMap<Asset, Amount>();

  }

  public BasicPortfolioService() {
    // this.portfolio = new Portfolio();

    //  this.portfolioService = new BasicPortfolioService(portfolio);
    //  this.allPnLs = new ConcurrentHashMap<Asset, Amount>();

  }

  @Override
  public void init() {
    log.info(this.getClass().getSimpleName() + " loading balances");
    loadBalances();
    log.info(this.getClass().getSimpleName() + " loading positions");
    findPositions();
    log.info(this.getClass().getSimpleName() + " loaded positions");
  }

  @Override
  public void loadBalances() {
    List<Exchange> loadedExchanges = (new ArrayList<Exchange>());
    log.info(this.getClass().getSimpleName() + " loading balances for portfolios : " + portfolios);
    for (Portfolio portfolio : portfolios) {
      log.info(
          this.getClass().getSimpleName()
              + " loading balances for markets: "
              + portfolio.getMarkets());

      for (Tradeable tradeable : portfolio.getMarkets()) {
        if (!tradeable.isSynthetic()) {
          Market market = (Market) tradeable;
          if (loadedExchanges.contains(market.getExchange())) {
            log.info(
                this.getClass().getSimpleName()
                    + " exchange already loaded : "
                    + market.getExchange());

            continue;
          }
          log.info(
              this.getClass().getSimpleName()
                  + " loading balance for exchange : "
                  + market.getExchange()
                  + " and portfolio "
                  + portfolio);

          market.getExchange().loadBalances(portfolio);
          log.info(
              this.getClass().getSimpleName()
                  + " loaded balances"
                  + market.getExchange().getBalances()
                  + " for exchange : "
                  + market.getExchange()
                  + " and portfolio "
                  + portfolio);
          for (Asset balanceAsset : market.getExchange().getBalances().keySet()) {
            if (market.getExchange().getBalances().get(balanceAsset).getAmount() == null
                || market.getExchange().getBalances().get(balanceAsset).getAmount().isZero())
              continue;
            // market.getExchange().getBalances().get(balanceAsset);
            DiscreteAmount price = new DiscreteAmount(0, balanceAsset.getBasis());
            TransactionType transactionType =
                (market.getExchange().getBalances().get(balanceAsset).getAmount().isNegative())
                    ? TransactionType.DEBIT
                    : TransactionType.CREDIT;
            Transaction initialCredit =
                transactionFactory.create(
                    portfolio,
                    market.getExchange(),
                    balanceAsset,
                    transactionType,
                    market.getExchange().getBalances().get(balanceAsset).getAmount(),
                    price);
            // TODI when support multiple protfoiols,we might need to credit each port folio with
            // the same amount per exchange.Prbbaly better to not make it protfoio based and have
            // all portfolio access the same exchange balance.
            //
            log.info(
                this.getClass().getSimpleName()
                    + " creating transaction balance for exchange : "
                    + market.getExchange()
                    + " and portfolio "
                    + portfolio);
            context.setPublishTime(initialCredit);
            initialCredit.persit();

            loadedExchanges.add(market.getExchange());
          }
        }
        Amount cashBalance = getBaseCashBalance(portfolio.getBaseAsset());
        if (cashBalance != null && !cashBalance.isZero())
          portfolio.setStartingBaseCashBalanceCount(
              getBaseCashBalance(portfolio.getBaseAsset())
                  .toBasis(portfolio.getBaseAsset().getBasis(), Remainder.ROUND_EVEN)
                  .getCount());
      }
    }
  }

  private void findPositions() {
    // String queryPortfolioStr = "select pf from Portfolio pf";
    // List<Portfolio> portfolios = PersistUtil.queryList(Portfolio.class, queryPortfolioStr, null);
    log.debug("Loading Portfolios:{}", portfolios);

    List<Position> emptyPositions = Collections.synchronizedList(new ArrayList<Position>());

    for (Portfolio portfolio : portfolios) {

      //  String queryStr = "select p from Position p  join fetch p.fills f join fetch p.portfolio
      // po where f.openVolumeCount!=0 and po = ?1 group by p";

      //  String queryStr = "select p from Position p  where f.openVolumeCount!=0 and po = ?1 group
      // by p";
      // String queryStr = "select p from Position p  where p.portfolio = ?1";
      // EntityGraph graph = EM.getEnityManager().getEntityGraph("graph.Position.fills");

      // Map hints = new HashMap();
      // hints.put("javax.persistence.fetchgraph", "graph.Position.fills");
      // hints.put("javax.persistence.fetchgraph", "graph.Position.portfolio");

      //  List<Position> positions = EM.queryList(Position.class, queryStr, hints, portfolio);
      List<Fill> fills = new ArrayList<Fill>();

      log.debug("Loading Positions: {}", portfolio.getPositions());
      synchronized (portfolio) {
        for (Position position : portfolio.getPositions()) {
          if (position == null) continue;
          log.debug("Loading Fills from positions: {}", position.getFills());
          context.getInjector().injectMembers(position);
          position.setMarket((Market) portfolio.addMarket(position.getMarket()));

          if (!position.hasFills()) {
            emptyPositions.add(position);
            continue;
          }

          //  position.getDao(localPositionDao);
          // portfolio.addPosition(position);
          // position.setPortfolio(portfolio);
          //  log.debug("Loading Fills:" + position.getFills().toString());
          for (Fill fill : position.getFills()) {
            if (fill == null) continue;
            // Map fillHints = new HashMap();
            // UUID portfolioID = EM.queryOne(UUID.class, queryStr, portfolioName);
            // fillHints.put("javax.persistence.fetchgraph", "fillsWithChildOrders");

            // Fill detailedFill = EM.namedQueryZeroOne(Fill.class, "Fills.findFillsById",
            // fillHints, fill.getId());

            context.getInjector().injectMembers(fill);
            fill.setMarket((Market) portfolio.addMarket(fill.getMarket()));
            if (!fill.getOpenVolume().isZero()) {
              fills.add(fill);
            }
            // portfolio.merge(fill);

          }
        }
      }

      portfolio.removePositions(emptyPositions);
      for (Position position : emptyPositions) {
        // EntityBase update = position.refresh();
        context.getInjector().injectMembers(position);

        /*
         * synchronized (portfolio.getPositions()) { portfolio.getPositions().remove(position); } position.setPortfolio(null); position.delete();
         */
        //    portfolio.removePosition(position);

        //  position.delete();
      }
      Collections.sort(fills, timeReceivedComparator);
      for (Fill fill : fills) {

        //  Exchange exchange = exchangeFactory.create(fill.getMarket().getExchange().getSymbol());

        log.trace("{}: findPositions merging fill for {}", this.getClass().getSimpleName(), fill);
        try {
          portfolio.merge(fill);
        } catch (Exception | Error ex) {
          log.error(
              this.getClass().getSimpleName()
                  + ": findPositions unable to merge fill for "
                  + fill
                  + " full stack trace follows:",
              ex);
        }
      }
    }
    // publish position update as the merge will only merge fills.
    for (Portfolio portfolio : portfolios) {
      synchronized (portfolio) {
        for (Position position : portfolio.getPositions()) {

          log.info(
              "{}:findPositions - Pulishing position update for {}",
              this.getClass().getSimpleName(),
              position);

          portfolio.publishPositionUpdate(
              position,
              (position.isLong()) ? PositionType.LONG : PositionType.SHORT,
              position.getMarket(),
              0);
        }
      }
    }

    log.debug("completed loading of existing portfolio");
  }

  private static final Comparator<RemoteEvent> timeReceivedComparator =
      new Comparator<RemoteEvent>() {
        @Override
        public int compare(RemoteEvent event, RemoteEvent event2) {
          return event.getTimeReceived().compareTo(event2.getTimeReceived());
        }
      };

  @Override
  @Nullable
  public Collection<Position> getPositions() {
    Collection<Position> AllPositions = new ArrayList<Position>();
    for (Portfolio portfolio : getPortfolios()) {
      for (Position position : portfolio.getNetPositions()) AllPositions.add(position);
    }
    return AllPositions;
  }

  @Transient
  public Context getContext() {
    return context;
  }

  protected void setContext(Context context) {
    this.context = context;
  }

  @Override
  @Nullable
  public synchronized Map<Asset, Amount> getRealisedPnLs() {
    Map<Asset, Amount> AllRealisedPnLs = new ConcurrentHashMap<Asset, Amount>();
    for (Portfolio portfolio : getPortfolios()) {

      Iterator<Asset> itf = portfolio.getRealisedPnLs().keySet().iterator();
      while (itf.hasNext()) {
        //  for (Fill pos : getFills()) {
        Asset asset = itf.next();
        log.debug(
            this.getClass().getSimpleName()
                + ":getRealisedPnLs - Calculated realised PnL of "
                + portfolio.getRealisedPnLs().get(asset)
                + " for position "
                + asset);

        if (AllRealisedPnLs.get(asset) == null)
          AllRealisedPnLs.put(asset, portfolio.getRealisedPnLs().get(asset));
        else AllRealisedPnLs.get(asset).plus(portfolio.getRealisedPnLs().get(asset));
      }
    }
    return AllRealisedPnLs;
  }

  @Override
  @Nullable
  public synchronized Map<Asset, Amount> getComissionsAndFees() {
    Map<Asset, Amount> allComissionsAndFees = new ConcurrentHashMap<Asset, Amount>();
    for (Portfolio portfolio : getPortfolios()) {

      Iterator<Asset> itf = portfolio.getComissionsAndFees().keySet().iterator();
      while (itf.hasNext()) {
        //  for (Fill pos : getFills()) {
        Asset asset = itf.next();
        log.debug(
            this.getClass().getSimpleName()
                + ":getComissionsAndFees - Calculated comissions and fees of "
                + portfolio.getRealisedPnLs().get(asset)
                + " for  "
                + asset);

        if (allComissionsAndFees.get(asset) == null)
          allComissionsAndFees.put(asset, portfolio.getComissionsAndFees().get(asset));
        else allComissionsAndFees.get(asset).plus(portfolio.getComissionsAndFees().get(asset));
      }
    }
    return allComissionsAndFees;
  }

  @Override
  @Nullable
  public synchronized Map<Asset, Amount> getRealisedPnLs(Market market) {
    Map<Asset, Amount> AllRealisedPnLs = new ConcurrentHashMap<Asset, Amount>();
    for (Portfolio portfolio : getPortfolios()) {

      Iterator<Asset> itf = portfolio.getRealisedPnLs(market).keySet().iterator();
      while (itf.hasNext()) {
        //  for (Fill pos : getFills()) {
        Asset asset = itf.next();
        if (AllRealisedPnLs.get(asset) == null)
          AllRealisedPnLs.put(asset, portfolio.getRealisedPnLs().get(asset));
        else AllRealisedPnLs.get(asset).plus(portfolio.getRealisedPnLs().get(asset));
      }
    }
    return AllRealisedPnLs;
  }

  @Override
  @Nullable
  public synchronized Map<Asset, Amount> getComissionsAndFees(Market market) {
    Map<Asset, Amount> AllComissionsAndFees = new ConcurrentHashMap<Asset, Amount>();
    for (Portfolio portfolio : getPortfolios()) {

      Iterator<Asset> itf = portfolio.getComissionsAndFees(market).keySet().iterator();
      while (itf.hasNext()) {
        //  for (Fill pos : getFills()) {
        Asset asset = itf.next();
        if (AllComissionsAndFees.get(asset) == null)
          AllComissionsAndFees.put(asset, portfolio.getComissionsAndFees().get(asset));
        else AllComissionsAndFees.get(asset).plus(portfolio.getComissionsAndFees().get(asset));
      }
    }
    return AllComissionsAndFees;
  }

  @Override
  @Nullable
  public synchronized Map<Asset, Map<Exchange, Map<Listing, Amount>>> getRealisedPnLByMarket() {

    Map<Asset, Map<Exchange, Map<Listing, Amount>>> AllRealisedPnL =
        new ConcurrentHashMap<Asset, Map<Exchange, Map<Listing, Amount>>>();
    for (Portfolio portfolio : getPortfolios()) {
      Iterator<Asset> itf = portfolio.getRealisedPnLs().keySet().iterator();
      while (itf.hasNext()) {
        //  for (Fill pos : getFills()) {
        Asset asset = itf.next();
        if (AllRealisedPnL.get(asset) == null)
          AllRealisedPnL.put(asset, portfolio.getRealisedPnL().get(asset));
        //  else
        //    AllRealisedPnL.get(asset).plus(portfolio.getRealisedPnL().get(asset));
      }
    }
    return AllRealisedPnL;
  }

  public DiscreteAmount getLongPosition(Asset asset, Exchange exchange) {
    return null;
  }

  public DiscreteAmount getShortPosition(Asset asset, Exchange exchange) {
    return null;
  }

  @Override
  public DiscreteAmount getNetPosition(Asset asset, Exchange exchange) {
    return null;
  }

  @Override
  @Nullable
  public synchronized ArrayList<Position> getPositions(Exchange exchange) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  @Nullable
  public synchronized Collection<Position> getPositions(Asset asset, Exchange exchange) {
    // return portfolio.getPositions(asset, exchange);
    return null;
  }

  @Override
  @Transient
  @SuppressWarnings("ConstantConditions")
  public synchronized Trade getMarketPrice(Listing listing) {
    Trade price;
    if (listing == null) return null;
    else {

      if (quotes.getLastTrade(listing) != null) {
        price = quotes.getLastTrade(listing);
      } else {

        String marketSymbol = ("SELF:" + listing.getSymbol());
        Market selfMarket = (Market) Market.forSymbol(marketSymbol);
        if (selfMarket == null) {
          Exchange exchange = Exchange.forSymbol("SELF");
          selfMarket = Market.findOrCreate(exchange, listing);
        }

        price =
            tradeFactory.create(
                selfMarket, context.getTime(), "0", BigDecimal.ZERO, BigDecimal.ZERO);
        //  price = new DiscreteAmount(0, postion.getMarket().getVolumeBasis());
        log.debug(
            this.getClass().getSimpleName()
                + ":getMarketPrice - Uable to retrieve last trade price from quote service for market "
                + listing);
      }

      return price;
    }
  }

  @Override
  @Transient
  @SuppressWarnings("ConstantConditions")
  public synchronized Trade getMarketPrice(Market market) {
    Trade price;
    if (market == null) return null;
    else {

      if (quotes.getLastTrade(market) != null) {
        price = quotes.getLastTrade(market);
      } else {
        price =
            tradeFactory.create(market, context.getTime(), "0", BigDecimal.ZERO, BigDecimal.ZERO);
        //  price = new DiscreteAmount(0, postion.getMarket().getVolumeBasis());
        log.debug(
            this.getClass().getSimpleName()
                + ":getMarketPrice - Uable to retrieve last trade price from quote service for market "
                + market);
      }

      return price;
    }
  }

  @Override
  @Transient
  @SuppressWarnings("ConstantConditions")
  public synchronized Trade getMarketPrice(Position postion) {
    return getMarketPrice(postion.getMarket());
  }

  @Override
  @Transient
  public synchronized Amount getMarketValue(Position position) {
    Amount marketPrice = getMarketPrice(position).getPrice();
    Amount marketValue = null;
    //   position.getAvgPrice()

    if (position.isOpen() && marketPrice != null && !marketPrice.isZero()) {
      if (position.getMarket().getListing().getPrompt() == null) {
        marketValue = position.getVolume().times(marketPrice, Remainder.ROUND_EVEN);

        log.debug(
            "{}:getMarketValue - Cacluated market value of {} with market price {} for position{} ",
            this.getClass().getSimpleName(),
            marketValue,
            marketPrice,
            position);

      } else {
        marketValue =
            (position
                .getVolume()
                .times(
                    position.getMarket().getContractSize(position.getMarket()),
                    Remainder.ROUND_EVEN));

        log.debug(
            this.getClass().getSimpleName()
                + "{} :getMarketValue - Cacluated market value of {} with market price {} and contract size {} for position {}",
            marketValue,
            marketPrice,
            position.getMarket().getContractSize(position.getMarket()),
            position);
      }
      return marketValue;
      //   Amount multiplier = position.getMarket().getMultiplier(position.getMarket(), marketPrice,
      // DecimalAmount.ONE);

      //    if
      // (position.getMarket().getTradedCurrency(position.getMarket()).equals(position.getMarket().getBase()))
      //      marketPrice = marketPrice.invert();

      // return (position.isFlat()) ? new DiscreteAmount(0, position.getMarket().getVolumeBasis()) :
      // ((marketPrice).times(position.getVolume(),
      //   Remainder.ROUND_EVEN)).times(position.getMarket().getMultiplier(position.getMarket(),
      // position.getAvgPrice(), marketPrice),
      // Remainder.ROUND_EVEN).times(position.getMarket().getContractSize(position.getMarket()),
      // Remainder.ROUND_EVEN);

    } else {
      return new DiscreteAmount(0, position.getMarket().getVolumeBasis());
    }
  }

  @Override
  @Transient
  public synchronized Amount getUnrealisedPnL(Position position, Amount markToMarketPrice) {
    // have to invert her
    // calculate long avarege price
    Amount totalUnrealisedPnl = new DiscreteAmount(0, position.getMarket().getVolumeBasis());
    Trade lastTrade = getMarketPrice(position);

    if (!position.getLongVolume().isZero()) {

      Amount avgPrice = position.getLongAvgPrice();
      Amount marketPrice =
          markToMarketPrice == null || (markToMarketPrice != null && markToMarketPrice.isZero())
              ? (lastTrade.getPrice().isZero() ? avgPrice : lastTrade.getPrice())
              : markToMarketPrice;
      log.info(
          this.getClass().getSimpleName()
              + ":getUnrealisedPnL - Calculated long unrealised PnL  with opening price:"
              + avgPrice
              + " and closing price:"
              + marketPrice);
      Amount unrealisedPnl =
          ((position.isFlat())
              ? new DiscreteAmount(0, position.getMarket().getVolumeBasis())
              : ((marketPrice.minus(avgPrice))
                      .times(position.getLongVolume(), Remainder.ROUND_EVEN))
                  .times(
                      position
                          .getMarket()
                          .getMultiplier(position.getMarket(), avgPrice, marketPrice),
                      Remainder.ROUND_EVEN)
                  .times(
                      position.getMarket().getContractSize(position.getMarket()),
                      Remainder.ROUND_EVEN));
      totalUnrealisedPnl = totalUnrealisedPnl.plus(unrealisedPnl);

      log.debug(
          "{}:getUnrealisedPnL - Calculated long unrealised PnL of {} with opening price:{}  and closing price: {} from {}  for position {} with totalUnrealisedPnL {}",
          this.getClass().getSimpleName(),
          unrealisedPnl,
          avgPrice,
          marketPrice,
          lastTrade.getTime(),
          position,
          totalUnrealisedPnl);

      ;
    }

    if (!position.getShortVolume().isZero()) {

      Amount avgPrice = position.getShortAvgPrice();
      Amount marketPrice =
          markToMarketPrice == null || (markToMarketPrice != null && markToMarketPrice.isZero())
              ? (lastTrade.getPrice().isZero() ? avgPrice : lastTrade.getPrice())
              : markToMarketPrice;
      log.info(
          this.getClass().getSimpleName()
              + ":getUnrealisedPnL - Calculated short unrealised PnL  with opening price:"
              + avgPrice
              + " and closing price:"
              + marketPrice);
      Amount unrealisedPnl =
          ((position.isFlat())
              ? new DiscreteAmount(0, position.getMarket().getVolumeBasis())
              : ((marketPrice.minus(avgPrice))
                      .times(position.getShortVolume(), Remainder.ROUND_EVEN))
                  .times(
                      position
                          .getMarket()
                          .getMultiplier(position.getMarket(), avgPrice, marketPrice),
                      Remainder.ROUND_EVEN)
                  .times(
                      position.getMarket().getContractSize(position.getMarket()),
                      Remainder.ROUND_EVEN));
      totalUnrealisedPnl = totalUnrealisedPnl.plus(unrealisedPnl);

      log.debug(
          "{}:getUnrealisedPnL - Calculated short unrealised PnL of {} with opening price:{} and closing price:{} from {} for position {} with totalUnrealisedPnl {} ",
          this.getClass().getSimpleName(),
          unrealisedPnl,
          avgPrice,
          marketPrice,
          lastTrade.getTime(),
          position,
          totalUnrealisedPnl);
    }
    // avgPrice = position.getMarket().getMultiplier(position.getMarket(), avgPrice,
    // DecimalAmount.ONE);

    // BTC (base)/USD (auote), LTC (base)/USD (quote)
    // posiotn price =
    /*
     * if (!(position.getMarket().getTradedCurrency(position.getMarket()).equals(position.getMarket().getQuote()))) { avgPrice =
     * (position.getAvgPrice()).invert(); if (!getMarketPrice(position).isZero()) marketPrice = getMarketPrice(position).invert(); }
     */
    //    Amount amount = (position.isFlat()) ? new DiscreteAmount(0,
    // position.getMarket().getVolumeBasis()) : ((avgPrice.minus(marketPrice)).times(
    //          position.getVolume(),
    // Remainder.ROUND_EVEN)).times(position.getMarket().getMultiplier(position.getMarket(),
    // avgPrice, DecimalAmount.ONE),
    //
    // Remainder.ROUND_EVEN).times(position.getMarket().getContractSize(position.getMarket()),
    // Remainder.ROUND_EVEN);

    // cash should be exit price - entry price, so marketepric-average price
    return totalUnrealisedPnl;
  }

  @Override
  @Transient
  public synchronized Map<Asset, Amount> getMarketValues() {

    // Amount marketValue = new DiscreteAmount(0, 0.01);
    Map<Asset, Amount> marketValues = new ConcurrentHashMap<>();
    // portfolio.getPositions().keySet()
    for (Portfolio portfolio : getPortfolios()) {
      Iterator<Position> itf = portfolio.getNetPositions().iterator();
      while (itf.hasNext()) {
        Amount marketValue = DecimalAmount.ZERO;

        //  for (Fill pos : getFills()) {
        Position position = itf.next();

        if (position.isOpen()) {
          if (marketValues.get(position.getAsset()) != null) {
            marketValue = marketValues.get(position.getAsset());
          }
          marketValue = marketValue.plus(getMarketValue(position));
          Asset tradedCCY =
              (position.getMarket().getTradedCurrency(position.getMarket()) == null)
                  ? position.getMarket().getBase()
                  : position.getMarket().getTradedCurrency(position.getMarket());
          marketValues.put(tradedCCY, marketValue);
        }
      }
    }

    return marketValues;
  }

  @Override
  @Transient
  public synchronized Map<Asset, Amount> getUnrealisedPnLs(Market market) {

    // Amount marketValue = new DiscreteAmount(0, 0.01);
    Map<Asset, Amount> unrealisedPnLs = new ConcurrentHashMap<>();
    // portfolio.getPositions().keySet()
    for (Portfolio portfolio : getPortfolios()) {
      Iterator<Position> itf = portfolio.getNetPositions().iterator();
      while (itf.hasNext()) {
        Amount unrealisedPnL = DecimalAmount.ZERO;

        //  for (Fill pos : getFills()) {
        Position position = itf.next();
        if (position.getMarket().equals(market)) {

          log.debug(
              "{}:getUnrealisedPnLs - Calculating unrealised PnL for {} traded currency {}",
              this.getClass().getSimpleName(),
              position,
              position.getMarket().getTradedCurrency(position.getMarket()));

          // OKCOIN_NEXTWEEK		base=ETC/quote=BTC
          // base=etc/quote=USD/this_week->ETC
          Asset currency =
              (position.getMarket().getTradedCurrency(position.getMarket()) == null)
                  ? position.getMarket().getQuote()
                  : position.getMarket().getTradedCurrency(position.getMarket());
          if (position.isOpen()) {
            if (unrealisedPnLs.get(currency) != null) {
              unrealisedPnL = unrealisedPnLs.get(currency);
            }
            Amount poistionUnrealisedPnL = getUnrealisedPnL(position, null);

            log.debug(
                "{}:getUnrealisedPnLs - At {} Calculating unrealised PnL of {} for position {} with existing unrealisedPnL of {} for currency {}",
                this.getClass().getSimpleName(),
                context.getTime(),
                poistionUnrealisedPnL,
                position,
                unrealisedPnL,
                currency);

            unrealisedPnL = unrealisedPnL.plus(poistionUnrealisedPnL);

            unrealisedPnLs.put(currency, unrealisedPnL);
          }
        }
      }
    }

    return unrealisedPnLs;
  }

  @Override
  @Transient
  public synchronized Map<Asset, Amount> getUnrealisedPnLs() {

    // Amount marketValue = new DiscreteAmount(0, 0.01);
    Map<Asset, Amount> unrealisedPnLs = new ConcurrentHashMap<>();
    // portfolio.getPositions().keySet()
    for (Portfolio portfolio : getPortfolios()) {
      Iterator<Position> itf = portfolio.getNetPositions().iterator();
      while (itf.hasNext()) {
        //  for (Fill pos : getFills()) {
        Amount unrealisedPnL = DecimalAmount.ZERO;

        Position position = itf.next();
        if (position.getAsset().getSymbol().equals("ETC")) log.debug("errr");
        Asset currency =
            (position.getMarket().getTradedCurrency(position.getMarket()) == null)
                ? position.getMarket().getQuote()
                : position.getMarket().getTradedCurrency(position.getMarket());
        if (position.isOpen()) {
          if (unrealisedPnLs.get(currency) != null) {
            unrealisedPnL = unrealisedPnLs.get(currency);
          }
          unrealisedPnL = unrealisedPnL.plus(getUnrealisedPnL(position, null));

          unrealisedPnLs.put(currency, unrealisedPnL);
        }
      }
    }

    return unrealisedPnLs;
  }

  @Override
  @Transient
  public synchronized Map<Asset, Amount> getUnrealisedPnLs(Exchange exchange) {
    // Amount marketValue = new DiscreteAmount(0, 0.01);
    Map<Asset, Amount> unrealisedPnLs = new ConcurrentHashMap<>();
    // portfolio.getPositions().keySet()
    for (Portfolio portfolio : getPortfolios()) {
      Iterator<Position> itf = portfolio.getNetPositions().iterator();
      while (itf.hasNext()) {
        Amount unrealisedPnL = DecimalAmount.ZERO;

        //  for (Fill pos : getFills()) {
        Position position = itf.next();
        if (!position.getExchange().equals(exchange)) continue;
        Asset currency =
            (position.getMarket().getTradedCurrency(position.getMarket()) == null)
                ? position.getMarket().getQuote()
                : position.getMarket().getTradedCurrency(position.getMarket());
        if (position.isOpen()) {
          if (position.getMarket().getSymbol().equals("OKCOIN_THISWEEK:LTC.USD.THISWEEK"))
            log.error("incorrect pnl");
          if (unrealisedPnLs.get(currency) != null) {
            unrealisedPnL = unrealisedPnLs.get(currency);
          }
          unrealisedPnL = unrealisedPnL.plus(getUnrealisedPnL(position, null));

          unrealisedPnLs.put(currency, unrealisedPnL);
        }
      }
    }

    return unrealisedPnLs;
  }

  @Transient
  public synchronized Map<Asset, Amount> getMargins(Exchange exchange) {

    // Amount marketValue = new DiscreteAmount(0, 0.01);
    Map<Asset, Amount> margins = new ConcurrentHashMap<>();
    // portfolio.getPositions().keySet()
    for (Portfolio portfolio : getPortfolios()) {
      Iterator<Position> itf = portfolio.getNetPositions().iterator();
      while (itf.hasNext()) {
        Amount margin = DecimalAmount.ZERO;

        //  for (Fill pos : getFills()) {
        Position position = itf.next();
        if (!position.getExchange().equals(exchange)) continue;
        Asset currency =
            (position.getMarket().getTradedCurrency(position.getMarket()) == null)
                ? position.getMarket().getQuote()
                : position.getMarket().getTradedCurrency(position.getMarket());
        if (position.isOpen()) {

          if (margins.get(position.getAsset()) != null) {
            margin = margins.get(position.getAsset());
          }
          Amount marginAmount = FeesUtil.getMargin(position);

          log.debug(
              "{} :getMargins - Calcuated marginAmount={},position={}",
              this.getClass().getSimpleName(),
              marginAmount,
              position);

          margin = margin.plus(marginAmount);

          margins.put(currency, margin);
        }
      }
    }

    return margins;
  }

  @Override
  @Transient
  public synchronized Amount getBaseMarketValue(Asset quoteAsset) {
    // Amount marketValue;
    // ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
    // portfolio.get

    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    //	Amount baseMarketValue = new DiscreteAmount(0, 0.01);

    Amount baseMarketValue = DecimalAmount.ZERO;

    Map<Asset, Amount> marketValues = getMarketValues();
    for (Asset baseAsset : marketValues.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {

        baseMarketValue =
            baseMarketValue.plus(
                marketValues.get(baseAsset).times(rate.getPrice(), Remainder.ROUND_EVEN));
        log.debug(
            this.getClass().getSimpleName()
                + ":getBaseMarketValue - Calculated base market "
                + baseMarketValue
                + " value balance "
                + marketValues.get(baseAsset)
                + " with "
                + quoteAsset
                + "/"
                + baseAsset
                + " rate "
                + rate);
      }
    }

    return baseMarketValue;
  }

  @Override
  @Transient
  public synchronized Amount getMarketValue(Asset quoteAsset) {
    // Amount marketValue;
    // ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
    // portfolio.get

    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    //	Amount baseMarketValue = new DiscreteAmount(0, 0.01);

    Amount marketValue = DecimalAmount.ZERO;

    Map<Asset, Amount> marketValues = getMarketValues();
    for (Asset baseAsset : marketValues.keySet()) {

      if (baseAsset.equals(quoteAsset)) {
        marketValue = marketValue.plus(marketValues.get(baseAsset));
        log.debug(
            this.getClass().getSimpleName()
                + ":getMarketValue - Calculated  market value"
                + marketValue
                + " with balance "
                + marketValues.get(baseAsset)
                + " for asset "
                + quoteAsset
                + " with "
                + quoteAsset
                + "/"
                + baseAsset);
      }
    }

    return marketValue;
  }

  @Override
  @Transient
  public synchronized Amount getBaseUnrealisedPnL(Asset quoteAsset, Market market) {
    // Amount marketValue;
    // ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
    // portfolio.get
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    //  Amount baseMarketValue = new DiscreteAmount(0, 0.01);
    Amount baseUnrealisedPnL = DecimalAmount.ZERO;
    log.debug(
        this.getClass().getSimpleName()
            + ":getBaseUnrealisedPnL - Getting unrelaised PNL for market "
            + market);

    Map<Asset, Amount> unrealisedPnLs = getUnrealisedPnLs(market);
    for (Asset baseAsset : unrealisedPnLs.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      if (baseAsset.getSymbol().equals("LTC") && quoteAsset.getSymbol().equals("USD"))
        log.debug("error");
      Trade rate = getMarketPrice(listing);
      if (rate.getPriceAsBigDecimal().equals(BigDecimal.valueOf(239.5)))
        rate = getMarketPrice(listing);
      // TODO for eth/btc the PnL is calcualted incorrectly the quote asset =Btc, base =USD so rate
      // is 4000,
      if (rate != null && !rate.getPrice().isZero()) {
        log.debug(
            this.getClass().getSimpleName()
                + ":getBaseUnrealisedPnL - Calculating base unrealised PnL"
                + unrealisedPnLs.get(baseAsset)
                + " with "
                + quoteAsset
                + "/"
                + baseAsset
                + " with "
                + rate);

        baseUnrealisedPnL =
            baseUnrealisedPnL.plus(
                unrealisedPnLs.get(baseAsset).times(rate.getPrice(), Remainder.ROUND_EVEN));
      }
    }

    return baseUnrealisedPnL;
  }

  @Override
  @Transient
  public synchronized Amount getBaseUnrealisedPnL(Asset quoteAsset) {
    // Amount marketValue;
    // ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
    // portfolio.get
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    //	Amount baseMarketValue = new DiscreteAmount(0, 0.01);
    Amount baseUnrealisedPnL = DecimalAmount.ZERO;

    Map<Asset, Amount> unrealisedPnLs = getUnrealisedPnLs();
    for (Asset baseAsset : unrealisedPnLs.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        log.debug(
            this.getClass().getSimpleName()
                + ":getBaseUnrealisedPnL - Calculating base unrealised PnL"
                + unrealisedPnLs.get(baseAsset)
                + " with "
                + quoteAsset
                + "/"
                + baseAsset
                + " with "
                + rate.getPrice());

        baseUnrealisedPnL =
            baseUnrealisedPnL.plus(
                unrealisedPnLs.get(baseAsset).times(rate.getPrice(), Remainder.ROUND_EVEN));
      }
    }

    return baseUnrealisedPnL;
  }

  @Override
  public synchronized Amount getBaseUnrealisedPnL(Position position, Asset quoteAsset) {
    Amount baseUnrealisedPnL = DecimalAmount.ZERO;

    Amount unrealisedPnL = getUnrealisedPnL(position, null);
    Listing listing = Listing.forPair(position.getAsset(), quoteAsset);
    Offer rate = quotes.getImpliedBestAskForListing(listing);
    if (rate != null && !rate.getPrice().isZero()) {
      log.trace(
          this.getClass().getSimpleName()
              + ":getBaseUnrealisedPnL - Calculating base unrealised PnL"
              + unrealisedPnL
              + " with "
              + quoteAsset
              + "/"
              + position.getAsset()
              + " rate "
              + rate);

      baseUnrealisedPnL = unrealisedPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
    }

    return baseUnrealisedPnL;
  }

  @Override
  public synchronized Amount getBaseUnrealisedPnL(
      Position position, Asset quoteAsset, DiscreteAmount marketPrice) {
    Amount baseUnrealisedPnL = DecimalAmount.ZERO;
    if (position.getAsset().getSymbol().equals("ETC")) log.debug("Err");
    Amount unrealisedPnL = getUnrealisedPnL(position, marketPrice);
    Asset tradedCurrency =
        position.getMarket().getTradedCurrency(position.getMarket()) == null
            ? position.getMarket().getQuote()
            : position.getMarket().getTradedCurrency(position.getMarket());
    Listing listing = Listing.forPair(tradedCurrency, quoteAsset);

    Trade rate = getMarketPrice(listing);
    if (rate != null && !rate.getPrice().isZero()) {
      log.trace(
          this.getClass().getSimpleName()
              + ":getBaseUnrealisedPnL - Calculating base unrealised PnL"
              + unrealisedPnL
              + " with "
              + quoteAsset
              + "/"
              + position.getAsset()
              + " rate "
              + rate);

      baseUnrealisedPnL = unrealisedPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
    }

    return baseUnrealisedPnL;
  }

  @Override
  public synchronized Amount getMarketValue(Position position, Asset quoteAsset) {
    Amount baseMarketValue = DecimalAmount.ZERO;

    Amount marketValue = getMarketValue(position);
    Listing listing = Listing.forPair(position.getMarket().getQuote(), quoteAsset);
    Trade rate = getMarketPrice(listing);
    if (rate != null && !rate.getPrice().isZero()) {
      baseMarketValue = marketValue.times(rate.getPrice(), Remainder.ROUND_EVEN);
      log.debug(
          this.getClass().getSimpleName()
              + ":getMarketValue - Calculated based market value "
              + baseMarketValue
              + " marketValue "
              + marketValue
              + " with "
              + quoteAsset
              + "/"
              + position.getAsset()
              + " rate "
              + rate);
    }

    return baseMarketValue;
  }

  @Override
  @Transient
  public synchronized Amount getUnrealisedPnL(Asset quoteAsset) {
    // Amount marketValue;
    // ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
    // portfolio.get
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    //	Amount baseMarketValue = new DiscreteAmount(0, 0.01);
    Amount unrealisedPnL = DecimalAmount.ZERO;

    Map<Asset, Amount> unrealisedPnLs = getUnrealisedPnLs();

    for (Asset baseAsset : unrealisedPnLs.keySet()) {
      if (baseAsset.equals(quoteAsset)) {
        unrealisedPnL = unrealisedPnL.plus(unrealisedPnLs.get(baseAsset));
      }
    }

    return unrealisedPnL;
  }

  @Override
  @Transient
  public synchronized Amount getBaseComissionAndFee(Asset quoteAsset, Market market) {

    // Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    Amount baseRealisedPnL = DecimalAmount.ZERO;

    // Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

    Map<Asset, Amount> realisedPnLs = getRealisedPnLs(market);
    for (Asset baseAsset : realisedPnLs.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        Amount localPnL = realisedPnLs.get(baseAsset);
        log.trace(
            this.getClass().getSimpleName()
                + ":getBaseComissionAndFee - Calculating base comissions and fees for "
                + market
                + " "
                + localPnL
                + " with "
                + quoteAsset
                + "/"
                + baseAsset
                + " rate "
                + rate);

        Amount basePnL = localPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
        baseRealisedPnL = baseRealisedPnL.plus(basePnL);
      }
    }
    return baseRealisedPnL;
  }

  @Override
  @Transient
  public synchronized Amount getBaseComissionAndFee(Asset quoteAsset) {

    // Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    Amount baseComissionsAndFees = DecimalAmount.ZERO;

    // Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

    Map<Asset, Amount> getComissionsAndFees = getComissionsAndFees();
    for (Asset baseAsset : getComissionsAndFees.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        Amount localFees = getComissionsAndFees.get(baseAsset);
        log.debug(
            this.getClass().getSimpleName()
                + ":getBaseComissionAndFee - Calculating base comissions and fees "
                + localFees
                + " with "
                + quoteAsset
                + "/"
                + baseAsset
                + " rate "
                + rate);

        Amount baseFees = localFees.times(rate.getPrice(), Remainder.ROUND_EVEN);
        baseComissionsAndFees = baseComissionsAndFees.plus(baseFees);
      }
    }
    return baseComissionsAndFees;
  }

  @Override
  @Transient
  public synchronized Amount getBaseRealisedPnL(Asset quoteAsset) {

    // Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    Amount baseRealisedPnL = DecimalAmount.ZERO;

    // Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

    Map<Asset, Amount> realisedPnLs = getRealisedPnLs();
    for (Asset baseAsset : realisedPnLs.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        Amount localPnL = realisedPnLs.get(baseAsset);
        log.debug(
            this.getClass().getSimpleName()
                + ":getBaseRealisedPnL - Calculating base realised PnL "
                + localPnL
                + " with "
                + quoteAsset
                + "/"
                + baseAsset
                + " rate "
                + rate);

        Amount basePnL = localPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
        baseRealisedPnL = baseRealisedPnL.plus(basePnL);
      }
    }
    return baseRealisedPnL;
  }

  @Override
  @Transient
  public synchronized Amount getBaseRealisedPnL(Asset quoteAsset, Market market) {

    // Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    Amount baseRealisedPnL = DecimalAmount.ZERO;

    // Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

    Map<Asset, Amount> realisedPnLs = getRealisedPnLs(market);
    for (Asset baseAsset : realisedPnLs.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        Amount localPnL = realisedPnLs.get(baseAsset);
        log.trace(
            this.getClass().getSimpleName()
                + ":getBaseRealisedPnL - Calculating base unrealised PnL for "
                + market
                + " "
                + localPnL
                + " with "
                + quoteAsset
                + "/"
                + baseAsset
                + " rate "
                + rate);

        Amount basePnL = localPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
        baseRealisedPnL = baseRealisedPnL.plus(basePnL);
      }
    }
    return baseRealisedPnL;
  }

  @Override
  @Transient
  public synchronized Amount getRealisedPnL(Asset quoteAsset) {

    // Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
    // Asset quoteAsset = list.getBase();
    // Asset baseAsset=new Asset();
    Amount realisedPnL = DecimalAmount.ZERO;

    // Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

    Map<Asset, Amount> realisedPnLs = getRealisedPnLs();
    for (Asset baseAsset : realisedPnLs.keySet()) {
      if (baseAsset.equals(quoteAsset)) {

        Amount localPnL = realisedPnLs.get(baseAsset);
        realisedPnL = realisedPnL.plus(localPnL);
      }
    }
    return realisedPnL;
  }

  @Override
  @Transient
  public synchronized Amount getBaseCashBalance(Asset quoteAsset) {
    Amount cashBalance = DecimalAmount.ZERO;
    //  Map<Asset, Amount> cashBalances = getCashBalances();
    Set<Exchange> exchanges = new HashSet<Exchange>();

    if (Portfolio.getMarkets() == null) return cashBalance;
    for (Tradeable tradeable : Portfolio.getMarkets()) {
      if (!tradeable.isSynthetic()) {
        Market market = (Market) tradeable;
        exchanges.add(market.getExchange());
      }
    }
    for (Exchange exchange : exchanges) {
      Map<Asset, Balance> exchangeBalances = exchange.getBalances();
      for (Asset currency : exchangeBalances.keySet()) {
        Listing listing = Listing.forPair(currency, quoteAsset);
        // Trade lastTrade = quotes.getLastTrade(listing);
        try {
          Offer rate = quotes.getImpliedBestAskForListing(listing);

          // dsfasdfasdf
          // Offer rate = quotes.getImpliedBestAskForListing(listing);
          if ((!currency.equals(quoteAsset) && rate == null)
              || (rate != null && rate.getPrice().isZero())) {

            log.error(
                this.getClass().getSimpleName()
                    + ":getBaseCashBalance - unable to calcuated balances with rate "
                    + rate
                    + " for listing "
                    + listing);

            continue;
          }
          //  return DecimalAmount.ZERO;

          // we have no prices so let's pull one.

          cashBalance =
              cashBalance.plus(
                  exchangeBalances
                      .get(currency)
                      .getAmount()
                      .times(rate.getPrice(), Remainder.ROUND_EVEN));
          log.debug(
              this.getClass().getSimpleName()
                  + " getBaseCashBalance: Calculating cash balances with rate "
                  + rate
                  + " exchangeAsset "
                  + listing
                  + "for  exchange "
                  + exchange
                  + " in "
                  + currency
                  + ": "
                  + exchangeBalances.get(currency));
        } catch (IllegalArgumentException iae) {
          log.error("unable to get base rate for portfolio");
        }
      }

      //   for(exchange.getBalances())
      //     cashBalance = cashBalance.plus(exchange.getBalances());
    }
    return cashBalance;
  }

  @Override
  @Transient
  public synchronized Amount getAvailableBaseBalance(Asset quoteAsset) {
    Amount marginBalance = DecimalAmount.ZERO;
    Map<Asset, Amount> margins = getMargins(quoteAsset);
    for (Asset baseAsset : margins.keySet()) {
      Listing listing = Listing.forPair(baseAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        Amount localMargin = margins.get(baseAsset);
        Amount baseMargin = localMargin.times(rate.getPrice(), Remainder.ROUND_EVEN);
        marginBalance = marginBalance.plus(baseMargin);
      }
    }

    return getBaseCashBalance(quoteAsset).plus(marginBalance);
  }

  @Transient
  private Map<Asset, Amount> getMargins(Asset quoteAsset) {

    // Amount baseCashBalance = getCashBalance(quoteAsset);
    Map<Asset, Amount> margins = new ConcurrentHashMap<Asset, Amount>();
    for (Portfolio portfolio : getPortfolios()) {

      Iterator<Position> itf = portfolio.getNetPositions().iterator();
      while (itf.hasNext()) {
        Amount totalMargin = DecimalAmount.ZERO;

        //  for (Fill pos : getFills()) {
        Position position = itf.next();
        Asset baseAsset =
            (position.getMarket().getTradedCurrency(position.getMarket()) == null)
                ? position.getMarket().getBase()
                : position.getMarket().getTradedCurrency(position.getMarket());
        // Asset baseAsset = position.getMarket().getTradedCurrency(position.getMarket());
        if (position.isOpen() && baseAsset.equals(quoteAsset)) {
          // calucate total margin

          if (margins.get(baseAsset) != null) totalMargin = margins.get(baseAsset);
          totalMargin = totalMargin.plus(FeesUtil.getMargin(position));
        }
        margins.put(baseAsset, totalMargin);
      }
    }

    return margins;
  }

  //    public void CreateTransaction(Portfolio portfolio, Exchange exchange, Asset asset,
  // TransactionType type, Amount amount, Amount price) {
  //        Transaction transaction = new Transaction(portfolio, exchange, asset, type, amount,
  // price);
  //
  //        context.route(transaction);
  //        transaction.persit();
  //    }

  @Override
  public synchronized void resetBalances() {
    //

  }

  @Override
  public synchronized void reset() {
    if (balances != null) balances.clear();
    if (allPnLs != null) allPnLs.clear();
    resetBalances();

    // remove all transactions
    // remove all positions

  }

  @Override
  public synchronized void exitPosition(Position position) throws Exception {

    reducePosition(position, (position.getVolume().abs()));
  }

  @Override
  public synchronized void reducePosition(final Position position, final Amount amount) {
    try {
      this.handleReducePosition(position, amount);
    } catch (Throwable th) {
      throw new PortfolioServiceException(
          "Error performing 'PositionService.reducePosition(int positionId, long quantity)' --> "
              + th,
          th);
    }
  }

  @Override
  public void handleReducePosition(Position position, Amount amount) throws Exception {

    // TODO remove subsrcption

  }

  @Override
  public void handleSetMargin(Position position) throws Exception {
    // TODO manage setting and mainuplating margin

  }

  @Override
  public void handleSetMargins() throws Exception {
    // TODO manage setting and mainuplating margin

  }

  @Inject protected transient ExchangeFactory exchangeFactory;
  @Inject protected transient TransactionFactory transactionFactory;

  @Inject protected transient Context context;
  @Inject protected transient QuoteService quotes;
  @Inject protected transient TradeFactory tradeFactory;

  protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.portfolioService");

  private static int transactionsHashCode;
  private static int tradesHashCode;
  private static int marginsHashCode;

  private static Map<Asset, Amount> balances = new ConcurrentHashMap<Asset, Amount>();;
  private Collection<Portfolio> portfolios;

  @Override
  public Collection<Portfolio> getPortfolios() {
    if (portfolios == null) portfolios = new HashSet<Portfolio>();

    // synchronized (lock) {
    return portfolios;
    // }

  }

  @Override
  public synchronized void setPortfolios(Collection<Portfolio> portfolios) {

    this.portfolios = portfolios;
  }

  @Override
  public synchronized void addPortfolio(Portfolio portfolio) {
    // synchronized (lock) {
    getPortfolios().add(portfolio);
    // }

  }

  @Override
  public Amount getAvailableBalance(Asset quoteAsset, Exchange exchange) {
    // TODO this needs to conside any margin requirements
    if (exchange == null
        || exchange.getBalances() == null
        || exchange.getBalances().get(quoteAsset) == null) return DecimalAmount.ZERO;
    return exchange.getBalances().get(quoteAsset).getAmount();
  }

  @Override
  public Amount getAvailableBaseBalance(Asset quoteAsset, Exchange exchange) {
    Amount baseExchangeBalance = DecimalAmount.ZERO;
    Amount baseMargin = DecimalAmount.ZERO;

    Amount baseUnrealisedPnL = DecimalAmount.ZERO;

    if (exchange == null || exchange.getBalances() == null) return baseExchangeBalance;
    // Balance exhcangeBalance;
    for (Asset exchangeAsset : exchange.getBalances().keySet()) {
      Listing listing = Listing.forPair(exchangeAsset, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        Amount existingBalance =
            (exchange.getBalances() == null || exchange.getBalances().get(exchangeAsset) == null)
                ? DecimalAmount.ZERO
                : exchange.getBalances().get(exchangeAsset).getAmount();
        Map<Asset, Balance> bals;
        //	log.debug(this.getClass().getSimpleName() + " getAvailableBaseBalance: Calculating
        // Available balance with rate " + rate + " exchangeAsset "
        //		+ exchangeAsset + " existingBalances  " + existingBalance);
        if (existingBalance == null) bals = exchange.getBalances();
        if (rate != null)
          baseExchangeBalance =
              baseExchangeBalance.plus(
                  (existingBalance).times(rate.getPrice(), Remainder.ROUND_EVEN));
      }
    }
    Map<Asset, Amount> margins = getMargins(exchange);
    for (Asset marginCurrency : margins.keySet()) {

      Listing listing = Listing.forPair(marginCurrency, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        baseMargin =
            baseMargin.plus(
                margins.get(marginCurrency).times(rate.getPrice(), Remainder.ROUND_EVEN));
        //	log.debug(this.getClass().getSimpleName() + " getAvailableBaseBalance: Calculating
        // margins with rate " + rate + " exchangeAsset "
        //		+ marginCurrency + " margin " + margins.get(marginCurrency));
      }
    }

    DecimalAmount marginRatio =
        baseExchangeBalance.isZero()
            ? DecimalAmount.ZERO
            : baseMargin.abs().divide(baseExchangeBalance, Remainder.ROUND_CEILING);
    //   if (marginRatio.compareTo(DecimalAmount.of("0.8")) > 0)
    log.debug(
        this.getClass().getSimpleName()
            + " getAvailableBaseBalance: Ratio of margin to "
            + exchange
            + " "
            + quoteAsset
            + " balance is "
            + marginRatio
            + ", "
            + exchange
            + "  "
            + quoteAsset
            + " balance: "
            + baseExchangeBalance
            + ", utlised "
            + quoteAsset
            + " margin "
            + baseMargin);
    Map<Asset, Amount> unrealisedPnLs = getUnrealisedPnLs(exchange);
    for (Asset currency : unrealisedPnLs.keySet()) {
      Listing listing = Listing.forPair(currency, quoteAsset);
      Trade rate = getMarketPrice(listing);
      if (rate != null && !rate.getPrice().isZero()) {
        baseUnrealisedPnL =
            baseUnrealisedPnL.plus(
                unrealisedPnLs.get(currency).times(rate.getPrice(), Remainder.ROUND_EVEN));
        //		log.debug(this.getClass().getSimpleName() + " getAvailableBaseBalance: Calculating
        // unrealisedPnLs with rate " + rate + " exchangeAsset "
        //			+ listing + " unrealisedPnL " + unrealisedPnLs.get(currency));
      }
    }

    // TODO Auto-generated method stub
    return baseExchangeBalance.plus(baseMargin).plus(baseUnrealisedPnL);
  }
}
