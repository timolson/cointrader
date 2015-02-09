package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.OrderBuilder;
import org.cryptocoinpartners.schema.OrderBuilder.SpecificOrderBuilder;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.Transaction;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.PortfolioServiceException;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.FeesUtil;
import org.cryptocoinpartners.util.Remainder;
import org.slf4j.Logger;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * This depends on a QuoteService being attached to the Context first.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public class BasicPortfolioService implements PortfolioService {

	private final Portfolio portfolio;
	private final ConcurrentHashMap<Asset, Amount> allPnLs;

	public BasicPortfolioService(Portfolio portfolio) {
		this.portfolio = portfolio;
		this.allPnLs = new ConcurrentHashMap<Asset, Amount>();

	}

	@Override
	@Nullable
	public ArrayList<Position> getPositions() {
		return (ArrayList<Position>) portfolio.getPositions();
	}

	@Transient
	public Context getContext() {
		return context;
	}

	@Override
	@Nullable
	public ConcurrentHashMap<Asset, Amount> getRealisedPnLs() {
		return portfolio.getRealisedPnLs();
	}

	@Override
	@Nullable
	public ConcurrentHashMap<Asset, ConcurrentHashMap<Exchange, ConcurrentHashMap<Listing, Amount>>> getRealisedPnLByMarket() {
		return portfolio.getRealisedPnL();
	}

	public DiscreteAmount getLongPosition(Asset asset, Exchange exchange) {
		return portfolio.getLongPosition(asset, exchange);
	}

	public DiscreteAmount getShortPosition(Asset asset, Exchange exchange) {
		return portfolio.getShortPosition(asset, exchange);
	}

	public DiscreteAmount getNetPosition(Asset asset, Exchange exchange) {
		return portfolio.getNetPosition(asset, exchange);
	}

	@Override
	@Nullable
	public ArrayList<Position> getPositions(Exchange exchange) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	@Nullable
	public Collection<Position> getPositions(Asset asset, Exchange exchange) {
		return portfolio.getPositions(asset, exchange);

	}

	@Override
	public DiscreteAmount getLastTrade() {

		List<Object> events = null;
		try {
			events = context.loadStatementByName("GET_LAST_TICK");
			if (events.size() > 0) {
				Trade trade = ((Trade) events.get(events.size() - 1));
				return (trade.getPrice());

			}
		} catch (ParseException | DeploymentException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	@Transient
	public ConcurrentHashMap<Asset, Amount> getAvailableBalances() {

		// so we need to get the cash balances
		// then we will add to it the avg price x quantity of the open position

		Amount availableBalance = DecimalAmount.ZERO;
		ConcurrentHashMap<Asset, Amount> availableBalances = new ConcurrentHashMap<>();

		Iterator<Transaction> itt = getTrades().iterator();
		while (itt.hasNext()) {
			Transaction transaction = itt.next();
			if (availableBalances.get(transaction.getAsset()) != null) {

				availableBalance = availableBalances.get(transaction.getAsset());

			}
			Amount tranCost = transaction.getCost();
			availableBalance = availableBalance.plus(tranCost);
			availableBalances.put(transaction.getAsset(), availableBalance);

		}
		return availableBalances;
	}

	@Override
	@Transient
	public ConcurrentHashMap<Asset, Amount> getCashBalances() {

		// sum of all transactions that belongs to this strategy
		ConcurrentHashMap<Asset, Amount> balances = new ConcurrentHashMap<>();
		Amount balance = DecimalAmount.ZERO;
		Iterator<Transaction> itt = getTrades().iterator();
		while (itt.hasNext()) {
			balance = DecimalAmount.ZERO;

			Transaction transaction = itt.next();
			if (balances.get(transaction.getAsset()) != null) {

				balance = balances.get(transaction.getAsset());

			}
			Amount tranCost = transaction.getCost();
			balance = balance.plus(tranCost);
			balances.put(transaction.getAsset(), balance);

		}

		// plus part of all cashFlows
		//Amount cashFlows = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

		Iterator<Transaction> itc = getCashFlows().iterator();
		while (itc.hasNext()) {
			Amount cashFlows = DecimalAmount.ZERO;
			Transaction cashFlowTransaction = itc.next();
			if (balances.get(cashFlowTransaction.getCurrency()) != null) {

				cashFlows = balances.get(cashFlowTransaction.getCurrency());
			}
			Amount tranCost = cashFlowTransaction.getCost();
			cashFlows = cashFlows.plus(tranCost);
			balances.put(cashFlowTransaction.getCurrency(), cashFlows);

		}
		//Amount amount = balance.plus(cashFlows);

		Iterator<Asset> it = getRealisedPnLByMarket().keySet().iterator();
		while (it.hasNext()) {
			Asset asset = it.next();
			Iterator<Exchange> ite = getRealisedPnLByMarket().get(asset).keySet().iterator();
			while (ite.hasNext()) {
				Exchange exchange = ite.next();
				Iterator<Listing> itm = getRealisedPnLByMarket().get(asset).get(exchange).keySet().iterator();
				while (itm.hasNext()) {
					Listing listing = itm.next();
					Market market = Market.findOrCreate(exchange, listing);

					Amount realisedPnL = getRealisedPnLByMarket().get(asset).get(exchange).get(listing);
					// need to change this to the market and check the margin.
					if (!realisedPnL.isZero()) {
						if (balances.get(asset) != null) {

							balance = balances.get(asset);

						}
						balance = balance.plus(realisedPnL);
						balances.put(asset, balance);

					}

				}
			}

		}

		Amount transferCredits = DecimalAmount.ZERO;
		Amount transferDebits = DecimalAmount.ZERO;
		Iterator<Transaction> itr = getTransfers().iterator();
		while (itr.hasNext()) {
			Transaction transactionTransaction = itr.next();
			if (balances.get(transactionTransaction.getCurrency()) != null) {

				transferDebits = balances.get(transactionTransaction.getCurrency());
			}
			if (balances.get(transactionTransaction.getAsset()) != null) {

				transferCredits = balances.get(transactionTransaction.getAsset());
			}

			Amount tranCost = transactionTransaction.getCost();
			transferCredits = transferCredits.plus(tranCost);
			transferDebits = transferDebits.plus(transactionTransaction.getAmount());
			balances.put(transactionTransaction.getCurrency(), transferDebits);
			balances.put(transactionTransaction.getAsset(), transferCredits);

		}

		return balances;
	}

	@Override
	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getCashFlows() {
		// return all CREDIT,DEBIT,INTREST,FEES and REALISED PnL

		ArrayList<Transaction> cashFlows = new ArrayList<>();
		Iterator<Transaction> it = portfolio.getTransactions().iterator();
		while (it.hasNext()) {
			Transaction transaction = it.next();
			if (transaction.getType() == TransactionType.CREDIT || transaction.getType() == TransactionType.DEBIT
					|| transaction.getType() == TransactionType.INTREST || transaction.getType() == TransactionType.FEES
					|| transaction.getType() == TransactionType.REALISED_PROFIT_LOSS) {
				cashFlows.add(transaction);
			}
		}

		return cashFlows;
	}

	public List<Transaction> getTransfers() {
		// return all CREDIT,DEBIT,INTREST,FEES and REALISED PnL

		ArrayList<Transaction> transfers = new ArrayList<>();
		Iterator<Transaction> it = portfolio.getTransactions().iterator();
		while (it.hasNext()) {
			Transaction transaction = it.next();
			if (transaction.getType() == TransactionType.REBALANCE) {
				transfers.add(transaction);
			}
		}

		return transfers;
	}

	@Override
	@Transient
	@SuppressWarnings("null")
	public List<Transaction> getTrades() {
		//return all BUY and SELL
		ArrayList<Transaction> trades = new ArrayList<>();
		Iterator<Transaction> it = portfolio.getTransactions().iterator();
		while (it.hasNext()) {
			Transaction transaction = it.next();

			if (transaction.getType() == TransactionType.BUY || transaction.getType() == TransactionType.SELL) {
				trades.add(transaction);
			}
		}
		return trades;
	}

	@Override
	@Transient
	public DiscreteAmount getMarketPrice(Position postion) {

		if (postion.isOpen()) {
			if (postion.isShort()) {
				@SuppressWarnings("ConstantConditions")
				DiscreteAmount price = quotes.getLastAskForMarket(postion.getMarket()).getPrice();
				return price;

			} else {
				@SuppressWarnings("ConstantConditions")
				DiscreteAmount price = quotes.getLastBidForMarket(postion.getMarket()).getPrice();
				return price;
			}
		} else {
			return new DiscreteAmount(0, postion.getMarket().getVolumeBasis());

		}
	}

	@Override
	@Transient
	public Amount getMarketValue(Position position) {

		if (position.isOpen()) {
			Amount marketPrice = getMarketPrice(position);

			if (position.getMarket().getTradedCurrency() == position.getMarket().getBase())
				marketPrice = marketPrice.invert();

			return (position.getVolume().times(marketPrice, Remainder.ROUND_EVEN)).times(position.getMarket().getContractSize(), Remainder.ROUND_EVEN);

		} else {
			return new DiscreteAmount(0, position.getMarket().getVolumeBasis());

		}
	}

	@Override
	@Transient
	public Amount getUnrealisedPnL(Position position) {
		//have to invert her
		Amount marketPrice = position.getAvgPrice();

		Amount avgPrice = getMarketPrice(position);
		if (position.getMarket().getTradedCurrency() == position.getMarket().getBase()) {
			avgPrice = (position.getAvgPrice()).invert();
			marketPrice = getMarketPrice(position).invert();
		}

		return (position.isFlat()) ? new DiscreteAmount(0, position.getMarket().getVolumeBasis()) : ((avgPrice.minus(marketPrice)).times(position.getVolume(),
				Remainder.ROUND_EVEN)).times(position.getMarket().getContractSize(), Remainder.ROUND_EVEN);

	}

	@Override
	@Transient
	public ConcurrentHashMap<Asset, Amount> getMarketValues() {
		Amount marketValue = DecimalAmount.ZERO;

		//Amount marketValue = new DiscreteAmount(0, 0.01);
		ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.getPositions().keySet()
		Iterator<Position> it = portfolio.getPositions().iterator();
		while (it.hasNext()) {
			Position position = it.next();

			if (position.isOpen()) {
				if (marketValues.get(position.getAsset()) != null) {
					marketValue = marketValues.get(position.getAsset());
				}
				marketValue = marketValue.plus(getMarketValue(position));

				marketValues.put(position.getMarket().getTradedCurrency(), marketValue);

			}
		}

		return marketValues;

	}

	@Override
	@Transient
	public ConcurrentHashMap<Asset, Amount> getUnrealisedPnLs() {
		Amount unrealisedPnL = DecimalAmount.ZERO;

		//Amount marketValue = new DiscreteAmount(0, 0.01);
		ConcurrentHashMap<Asset, Amount> unrealisedPnLs = new ConcurrentHashMap<>();
		//portfolio.getPositions().keySet()
		Iterator<Position> it = portfolio.getPositions().iterator();
		while (it.hasNext()) {
			Position position = it.next();

			if (position.isOpen()) {
				if (unrealisedPnLs.get(position.getAsset()) != null) {
					unrealisedPnL = unrealisedPnLs.get(position.getAsset());
				}
				unrealisedPnL = unrealisedPnL.plus(getUnrealisedPnL(position));

				unrealisedPnLs.put(position.getMarket().getTradedCurrency(), unrealisedPnL);

			}
		}

		return unrealisedPnLs;

	}

	@Override
	@Transient
	public Amount getBaseMarketValue(Asset quoteAsset) {
		//Amount marketValue;
		//ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.get

		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		//	Amount baseMarketValue = new DiscreteAmount(0, 0.01);

		Amount baseMarketValue = DecimalAmount.ZERO;

		ConcurrentHashMap<Asset, Amount> marketValues = getMarketValues();

		Iterator<Asset> it = marketValues.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			baseMarketValue = baseMarketValue.plus(marketValues.get(baseAsset).times(rate.getPrice(), Remainder.ROUND_EVEN));

		}

		return baseMarketValue;

	}

	@Override
	@Transient
	public Amount getMarketValue(Asset quoteAsset) {
		//Amount marketValue;
		//ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.get

		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		//	Amount baseMarketValue = new DiscreteAmount(0, 0.01);

		Amount marketValue = DecimalAmount.ZERO;

		ConcurrentHashMap<Asset, Amount> marketValues = getMarketValues();

		Iterator<Asset> it = marketValues.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			if (baseAsset.equals(quoteAsset)) {
				marketValue = marketValue.plus(marketValues.get(baseAsset));
			}

		}

		return marketValue;

	}

	@Override
	@Transient
	public Amount getBaseUnrealisedPnL(Asset quoteAsset) {
		//Amount marketValue;
		//ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.get
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		//	Amount baseMarketValue = new DiscreteAmount(0, 0.01);
		Amount baseUnrealisedPnL = DecimalAmount.ZERO;

		ConcurrentHashMap<Asset, Amount> unrealisedPnLs = getUnrealisedPnLs();

		Iterator<Asset> it = unrealisedPnLs.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			baseUnrealisedPnL = baseUnrealisedPnL.plus(unrealisedPnLs.get(baseAsset).times(rate.getPrice(), Remainder.ROUND_EVEN));

		}

		return baseUnrealisedPnL;

	}

	@Override
	@Transient
	public Amount getUnrealisedPnL(Asset quoteAsset) {
		//Amount marketValue;
		//ConcurrentHashMap<Asset, Amount> marketValues = new ConcurrentHashMap<>();
		//portfolio.get
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		//	Amount baseMarketValue = new DiscreteAmount(0, 0.01);
		Amount unrealisedPnL = DecimalAmount.ZERO;

		ConcurrentHashMap<Asset, Amount> unrealisedPnLs = getUnrealisedPnLs();

		Iterator<Asset> it = unrealisedPnLs.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			if (baseAsset.equals(quoteAsset)) {
				unrealisedPnL = unrealisedPnL.plus(unrealisedPnLs.get(baseAsset));
			}

		}

		return unrealisedPnL;

	}

	@Override
	@Transient
	public Amount getBaseRealisedPnL(Asset quoteAsset) {

		//Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		Amount baseRealisedPnL = DecimalAmount.ZERO;

		//Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

		ConcurrentHashMap<Asset, Amount> realisedPnLs = getRealisedPnLs();

		Iterator<Asset> itp = realisedPnLs.keySet().iterator();
		while (itp.hasNext()) {
			Asset baseAsset = itp.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			Amount localPnL = realisedPnLs.get(baseAsset);
			Amount basePnL = localPnL.times(rate.getPrice(), Remainder.ROUND_EVEN);
			baseRealisedPnL = baseRealisedPnL.plus(basePnL);

		}
		return baseRealisedPnL;
	}

	@Override
	@Transient
	public Amount getRealisedPnL(Asset quoteAsset) {

		//Listing list = Listing.forSymbol(config.getString("base.symbol", "USD"));
		//Asset quoteAsset = list.getBase();
		//Asset baseAsset=new Asset();
		Amount realisedPnL = DecimalAmount.ZERO;

		//Amount baseCashBalance = new DiscreteAmount(0, portfolio.getBaseAsset().getBasis());

		ConcurrentHashMap<Asset, Amount> realisedPnLs = getRealisedPnLs();

		Iterator<Asset> itp = realisedPnLs.keySet().iterator();
		while (itp.hasNext()) {
			Asset baseAsset = itp.next();
			if (baseAsset.equals(quoteAsset)) {

				Amount localPnL = realisedPnLs.get(baseAsset);
				realisedPnL = realisedPnL.plus(localPnL);
			}

		}
		return realisedPnL;
	}

	@Override
	@Transient
	public Amount getCashBalance(Asset quoteAsset) {
		Amount cashBalance = DecimalAmount.ZERO;
		ConcurrentHashMap<Asset, Amount> cashBalances = getCashBalances();

		Iterator<Asset> it = cashBalances.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			if (baseAsset.equals(quoteAsset)) {
				//	Listing listing = Listing.forPair(baseAsset, quoteAsset);
				//Offer rate = quotes.getImpliedBestAskForListing(listing);
				Amount localBalance = cashBalances.get(baseAsset);
				//Amount baseBalance = localBalance.times(rate.getPrice(), Remainder.ROUND_EVEN);
				cashBalance = cashBalance.plus(localBalance);
			}

		}

		return cashBalance;

	}

	@Override
	@Transient
	public Amount getBaseCashBalance(Asset quoteAsset) {
		Amount cashBalance = DecimalAmount.ZERO;
		ConcurrentHashMap<Asset, Amount> cashBalances = getCashBalances();

		Iterator<Asset> it = cashBalances.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();

			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			Amount localBalance = cashBalances.get(baseAsset);
			Amount baseBalance = localBalance.times(rate.getPrice(), Remainder.ROUND_EVEN);
			cashBalance = cashBalance.plus(baseBalance);

		}

		return cashBalance;

	}

	@Override
	@Transient
	public Amount getAvailableBalance(Asset quoteAsset) {
		Amount marginBalance = DecimalAmount.ZERO;
		ConcurrentHashMap<Asset, Amount> margins = getMargins(quoteAsset);

		Iterator<Asset> it = margins.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			//Offer rate = quotes.getImpliedBestAskForListing(listing);
			Amount localMargin = margins.get(baseAsset);
			//Amount baseMargin = localMargin.times(rate.getPrice(), Remainder.ROUND_EVEN);
			marginBalance = marginBalance.plus(localMargin);

		}

		return getCashBalance(quoteAsset).plus(marginBalance);

	}

	@Override
	@Transient
	public Amount getAvailableBaseBalance(Asset quoteAsset) {
		Amount marginBalance = DecimalAmount.ZERO;
		ConcurrentHashMap<Asset, Amount> margins = getMargins(quoteAsset);

		Iterator<Asset> it = margins.keySet().iterator();
		while (it.hasNext()) {
			Asset baseAsset = it.next();
			Listing listing = Listing.forPair(baseAsset, quoteAsset);
			Offer rate = quotes.getImpliedBestAskForListing(listing);
			Amount localMargin = margins.get(baseAsset);
			Amount baseMargin = localMargin.times(rate.getPrice(), Remainder.ROUND_EVEN);
			marginBalance = marginBalance.plus(baseMargin);

		}

		return getBaseCashBalance(quoteAsset).plus(marginBalance);

	}

	@Transient
	public ConcurrentHashMap<Asset, Amount> getMargins(Asset quoteAsset) {
		Amount baseAvailableBalance = DecimalAmount.ZERO;
		Amount totalMargin = DecimalAmount.ZERO;

		//Amount baseCashBalance = getCashBalance(quoteAsset);
		ConcurrentHashMap<Asset, Amount> margins = new ConcurrentHashMap<Asset, Amount>();
		Iterator<Position> it = portfolio.getPositions().iterator();
		while (it.hasNext()) {
			Position position = it.next();
			Asset baseAsset = position.getMarket().getTradedCurrency();
			if (position.isOpen() && baseAsset.equals(quoteAsset)) {
				// calucate total margin

				if (margins.get(baseAsset) != null)
					totalMargin = margins.get(baseAsset);
				totalMargin = totalMargin.plus(FeesUtil.getMargin(position));

			}
			margins.put(baseAsset, totalMargin);
		}

		return margins;

	}

	@Override
	public void CreateTransaction(Exchange exchange, Asset asset, TransactionType type, Amount amount, Amount price) {
		Transaction transaction = new Transaction(portfolio, exchange, asset, type, amount, price);
		context.publish(transaction);
	}

	@Override
	public void exitPosition(Position position) throws Exception {

		reducePosition(position, (position.getVolume().abs()));
	}

	@Override
	public void reducePosition(final Position position, final Amount amount) {
		try {
			this.handleReducePosition(position, amount);
		} catch (Throwable th) {
			throw new PortfolioServiceException("Error performing 'PositionService.reducePosition(int positionId, long quantity)' --> " + th, th);
		}
	}

	@Override
	public void handleReducePosition(Position position, Amount amount) throws Exception {

		Market market = position.getMarket();
		OrderBuilder orderBuilder = new OrderBuilder(position.getPortfolio(), orderService);
		if (orderBuilder != null) {
			SpecificOrderBuilder exitOrder = orderBuilder.create(context.getTime(), market, amount.negate(), "Exit Order");
			log.info("Entering trade with order " + exitOrder);
			orderService.placeOrder(exitOrder.getOrder());
		}

		if (!position.isOpen()) {
			//TODO remove subsrcption
		}
	}

	@Override
	public void handleSetMargin(Position position) throws Exception {
		//TODO manage setting and mainuplating margin

	}

	@Override
	public void handleSetMargins() throws Exception {
		//TODO manage setting and mainuplating margin

	}

	@Inject
	protected Context context;
	@Inject
	protected QuoteService quotes;

	@Inject
	protected OrderService orderService;
	@Inject
	private Logger log;

}
