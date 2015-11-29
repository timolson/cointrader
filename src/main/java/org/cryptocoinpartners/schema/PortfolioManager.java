package org.cryptocoinpartners.schema;

// import java.util.logging.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.enumeration.TransactionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.Remainder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PortfolioManagers are allowed to control the Positions within a Portfolio
 *
 * @author Tim Olson
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Singleton
@Cacheable
public class PortfolioManager extends EntityBase implements Context.AttachListener {

    // private BasicPortfolioService portfolioService;

    // todo we need to get the tradeable portfolio separately from the "reserved" portfolio (assets needed for open orders)
    @OneToOne
    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    @Transient
    public Context getContext() {
        return context;
    }

    @Transient
    public PortfolioService getPortfolioService() {
        return portfolioService;
    }

    @When("@Priority(6) select * from OrderUpdate where state.open=true`")
    private void updateReservation(OrderUpdate update) {

        //removes the reservation from the transactions
        Transaction reservation = update.getOrder().getReservation();
        if (reservation != null && update.getState() != OrderState.NEW) {
            if (reservation.getType().equals(TransactionType.BUY_RESERVATION) || reservation.getType().equals(TransactionType.SELL_RESERVATION)) {
                Amount price = (update.getOrder().getLimitPrice() == null) ? ((update.getOrder().getVolume().isNegative()) ? quotes.getLastBidForMarket(
                        update.getOrder().getMarket()).getPrice() : quotes.getLastAskForMarket(update.getOrder().getMarket()).getPrice()) : update.getOrder()
                        .getLimitPrice();
                Amount updateAmount = reservation.getType().equals(TransactionType.BUY_RESERVATION) ? (update.getOrder().getUnfilledVolume().times(price,
                        Remainder.ROUND_EVEN)).negate() : update.getOrder().getVolume();
                reservation.setAmount(updateAmount);
            }
        }
    }

    @When("@Priority(5) select * from OrderUpdate where state.open=false")
    private void removeReservation(OrderUpdate update) {
        //removes the reservation from the transactions
        Order order = update.getOrder();
        Transaction reservation = order.getReservation();
        switch (update.getState()) {
            case NEW:
                break;
            case TRIGGER:
                break;
            case ROUTED:
                break;
            case PLACED:
                break;
            case PARTFILLED:
                break;
            case FILLED:
                if (order instanceof SpecificOrder)
                    portfolio.removeTransaction(reservation);
                break;
            case CANCELLING:
                break;
            case CANCELLED:
                if (order instanceof SpecificOrder)
                    portfolio.removeTransaction(reservation);
                break;
            case REJECTED:
                if (order instanceof SpecificOrder)
                    portfolio.removeTransaction(reservation);
                break;
            case EXPIRED:
                if (order instanceof SpecificOrder)
                    portfolio.removeTransaction(reservation);
                break;
            default:
                log.warn("Unknown order state: " + update.getState());
                break;
        }

    }

    private class handleTransactionRunnable implements Runnable {
        private final Transaction transaction;

        // protected Logger log;

        public handleTransactionRunnable(Transaction transaction) {
            this.transaction = transaction;

        }

        @Override
        public void run() {
            updatePortfolio(transaction);

        }
    }

    private class handleFillRunnable implements Runnable {
        private final Fill fill;

        // protected Logger log;

        public handleFillRunnable(Fill fill) {
            this.fill = fill;

        }

        @Override
        public void run() {
            // fill.persit();
            portfolio.modifyPosition(fill, new Authorization("Fill for " + fill.toString()));

        }
    }

    //  @When("@Priority(9) select * from Fill")
    // public void handleFill(Fill fill) {
    //    service.submit(new handleFillRunnable(fill));

    // }

    //  @When("@Priority(8) select * from Transaction where NOT (Transaction.type=TransactionType.BUY and Transaction.type=TransactionType.SELL)")
    @When("@Priority(8) select * from Transaction")
    public void handleTransaction(Transaction transaction) {
        //
        // updatePortfolio(transaction);
        service.submit(new handleTransactionRunnable(transaction));

    }

    public void updatePortfolio(Transaction transaction) {
        //  PersistUtil.insert(transaction);
        //	Transaction tans = new Transaction(this, position.getExchange(), position.getAsset(), TransactionType.CREDIT, position.getVolume(),
        //		position.getAvgPrice());
        //context.route(transaction);
        log.info("transaction: " + transaction + " Recieved.");
        if (transaction.getPortfolio() == (portfolio)) {

            Portfolio portfolio = transaction.getPortfolio();

            Asset baseAsset = transaction.getCurrency();

            Market market = transaction.getMarket();
            Amount amount = transaction.getAmount();
            TransactionType type = transaction.getType();

            Amount price = transaction.getPrice();
            Exchange exchange = transaction.getExchange();
            // Add transaction to approraite portfolio
            portfolio.addTransaction(transaction);
            //  portfolioService.
            Position position;
            // update postion
            //if (type == TransactionType.BUY || type == TransactionType.SELL) {

            //position = new Position(portfolio, exchange, market, baseAsset, amount, price);
            //transaction.getFill();

            //portfolio.modifyPosition(transaction.getFill(), new Authorization("Fill for " + transaction.toString()));

            //} else if (type == TransactionType.REALISED_PROFIT_LOSS) {
            //Transfer ammount to base currency
            // neeed to be able to implment this on the exchange via orders
            //                if (!transaction.getCurrency().equals(transaction.getPortfolio().getBaseAsset())) {
            //                    Listing tradedListing = Listing.forPair(transaction.getCurrency(), transaction.getPortfolio().getBaseAsset());
            //                    //we will be selling transaction currency and buying base currency i.e. sell BTC, buy USD
            //                    Offer tradedRate = quotes.getImpliedBestAskForListing(tradedListing);
            //
            //                   TransactionType type=(transaction.getAmount().isPositive()) ? TransactionType.CREDIT:TransactionType.DEBIT;
            //                    
            //                    Transaction initialDedit = new Transaction(transaction.getPortfolio(), transaction.getExchange(), transaction.getCurrency(),
            //                            type, transaction.getAmount());
            //                    context.route(initialDedit);
            //                    initialDedit.persit();

            //}
            // }
            log.info("transaction: " + transaction + " Proccessed.");

        } else {
            return;
        }
    }

    @Override
    public void afterAttach(Context context) {
        context.attachInstance(getPortfolio());
        context.attachInstance(getPortfolioService());
    }

    /** for subclasses */
    protected PortfolioManager(String portfolioName) {
        // new PortfolioManager();
        //   portfolio.setName(portfolioName);
        // portfolio.setManager(this);
        // this.portfolio = new Portfolio(portfolioName, this);
        // portfolioService.getPortfolio().setName(portfolioName);
        // portfolioService.getPortfolio().setManager(this);
        //  this.portfolioService = new BasicPortfolioService(portfolio);
    }

    public static class DataSubscriber {

        private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        @SuppressWarnings("rawtypes")
        public void update(Long Timestamp, Portfolio portfolio) {
            // PortfolioService portfolioService = context.
            PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);
            //      ..getManager().getPortfolioService();
            //portfolio.getPositions();
            log.info("Date: " + (Timestamp != null ? (FORMAT.print(Timestamp)) : "") + " Portfolio: " + portfolio + " Total Value (" + portfolio.getBaseAsset()
                    + "):"
                    + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).plus(portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()))
                    + " (Cash Balance:" + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " Realised PnL (M2M):"
                    + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()) + " Open Trade Equity:"
                    + portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()) + " MarketValue:"
                    + portfolioService.getBaseMarketValue(portfolio.getBaseAsset()) + ")");
            log.info(portfolio.getNetPositions().toString());
            log.info(portfolio.getDetailedPositions().toString());
            //			Object itt = portfolio.getPositions().iterator();
            //			while (itt.hasNext()) {
            //				Position postion = itt.next();
            //				logger.info("Date: " + (Timestamp != null ? (FORMAT.print(Timestamp)) : "") + " Asset: " + postion.getAsset() + " Position: "
            //						+ postion.getVolume());
            //			}
        }

    }

    // JPA

    protected PortfolioManager() {
        // portfolio.setManager(this);
        // portfolioService.setManager(this);

    }

    // @Inject
    protected PortfolioManager(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    // @Inject
    protected void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    @Transient
    protected void setPortfolioService(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.portfolioManager");

    @Inject
    protected transient Context context;
    @Inject
    protected QuoteService quotes;
    @Inject
    protected PortfolioService portfolioService;

    protected Portfolio portfolio;
    private static ExecutorService service = Executors.newFixedThreadPool(1);

    @Override
    public void persit() {
        // TODO Auto-generated method stub

    }

    @Override
    public void detach() {
        // TODO Auto-generated method stub

    }

    @Override
    public void merge() {
        // TODO Auto-generated method stub

    }

}
