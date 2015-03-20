package org.cryptocoinpartners.schema;

// import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
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
import org.cryptocoinpartners.util.PersistUtil;
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
public class PortfolioManager extends EntityBase implements Context.AttachListener {

    // private BasicPortfolioService portfolioService;

    // todo we need to get the tradeable portfolio separately from the "reserved" portfolio (assets needed for open orders)
    @OneToOne
    public Portfolio getPortfolio() {
        return portfolio;
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
                Amount updateAmount = reservation.getType().equals(TransactionType.BUY_RESERVATION) ? (update.getOrder().getUnfilledVolume().times(update
                        .getOrder().getLimitPrice(), Remainder.ROUND_EVEN)).negate() : update.getOrder().getVolume();
                reservation.setAmount(updateAmount);
            }
        }
    }

    @When("@Priority(5) select * from OrderUpdate where state.open=false")
    private void removeReservation(OrderUpdate update) {
        //removes the reservation from the transactions
        Transaction reservation = update.getOrder().getReservation();
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
                portfolio.removeTransaction(reservation);
                break;
            case CANCELLING:
                break;
            case CANCELLED:
                portfolio.removeTransaction(reservation);
                break;
            case REJECTED:
                //portfolio.removeTransaction(reservation);
                break;
            case EXPIRED:
                //portfolio.removeTransaction(reservation);
                break;
            default:
                log.warn("Unknown order state: " + update.getState());
                break;
        }

    }

    @When("@Priority(7) select * from Transaction")
    public void handleTransaction(Transaction transaction) {
        PersistUtil.insert(transaction);
        //	Transaction tans = new Transaction(this, position.getExchange(), position.getAsset(), TransactionType.CREDIT, position.getVolume(),
        //		position.getAvgPrice());
        //context.route(transaction);
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
            Position position;
            // update postion
            if (type == TransactionType.BUY || type == TransactionType.SELL) {

                //position = new Position(portfolio, exchange, market, baseAsset, amount, price);
                //transaction.getFill();
                portfolio.modifyPosition(transaction.getFill(), new Authorization("Fill for " + transaction.toString()));

            } else if (type == TransactionType.REALISED_PROFIT_LOSS) {
                //Transfer ammount to base currency
                // neeed to be able to implment this on the exchange via orders
                if (!transaction.getCurrency().equals(transaction.getPortfolio().getBaseAsset())) {
                    Listing tradedListing = Listing.forPair(transaction.getCurrency(), transaction.getPortfolio().getBaseAsset());
                    //we will be selling transaction currency and buying base currency i.e. sell BTC, buy USD
                    Offer tradedRate = quotes.getImpliedBestAskForListing(tradedListing);

                    Transaction initialDedit = new Transaction(transaction.getPortfolio(), transaction.getExchange(), transaction.getCurrency(),
                            TransactionType.DEBIT, transaction.getAmount().negate());
                    context.route(initialDedit);
                    Transaction initialCredit = new Transaction(transaction.getPortfolio(), transaction.getExchange(), transaction.getPortfolio()
                            .getBaseAsset(), TransactionType.CREDIT, transaction.getAmount().times(tradedRate.getPrice(), Remainder.ROUND_EVEN));
                    context.route(initialCredit);

                }
            }
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
        portfolioService.getPortfolio().setName(portfolioName);
        portfolioService.getPortfolio().setManager(this);
        //  this.portfolioService = new BasicPortfolioService(portfolio);
    }

    public static class DataSubscriber {

        private static Logger logger = LoggerFactory.getLogger(DataSubscriber.class.getName());
        private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        @SuppressWarnings("rawtypes")
        public void update(Long Timestamp, Portfolio portfolio) {
            // PortfolioService portfolioService = context.
            PortfolioService portfolioService = portfolio.context.getInjector().getInstance(PortfolioService.class);
            //      ..getManager().getPortfolioService();
            //portfolio.getPositions();
            logger.info("Date: " + (Timestamp != null ? (FORMAT.print(Timestamp)) : "") + " Portfolio: " + portfolio + " Total Value ("
                    + portfolio.getBaseAsset() + "):"
                    + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()).plus(portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()))
                    + " (Cash Balance:" + portfolioService.getBaseCashBalance(portfolio.getBaseAsset()) + " Realised PnL (M2M):"
                    + portfolioService.getBaseRealisedPnL(portfolio.getBaseAsset()) + " Open Trade Equity:"
                    + portfolioService.getBaseUnrealisedPnL(portfolio.getBaseAsset()) + " MarketValue:"
                    + portfolioService.getBaseMarketValue(portfolio.getBaseAsset()) + ")");
            logger.info(portfolio.getPositions().toString());
            logger.info(portfolio.getDetailedPositions().toString());
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

    @Inject
    private static Logger log;
    @Inject
    protected Context context;
    @Inject
    private QuoteService quotes;
    @Inject
    private PortfolioService portfolioService;
    @Inject
    private Portfolio portfolio;

}
