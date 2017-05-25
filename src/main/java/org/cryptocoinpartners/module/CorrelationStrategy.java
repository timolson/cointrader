package org.cryptocoinpartners.module;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Tradeable;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;

/**
 * This simple Strategy first waits for Book data to arrive about the target Market, then it places a buy order
 * at demostrategy.spread below the current bestAsk.  Once it enters the trade, it places a sell order at
 * demostrategy.spread above the current bestBid.
 * This strategy ignores the available Positions in the Portfolio and always trades the amount set by demostrategy.volume on
 * the Market specified by demostrategy.market
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class CorrelationStrategy extends TestStrategy {

    static double percentEquityRisked = 0.01;
    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    static double atrStop = 2;
    static double atrTarget = 1000000;
    //* atrStop;
    static long slippage = 0;
    static double maxLossTarget = atrStop * percentEquityRisked;
    protected static Market leadingMarket;

    //double maxLossTarget = 0.25;

    @Inject
    public CorrelationStrategy(Context context, Configuration config) {
        super(context, config);
        String leadingMarketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        //String leadingMarketSymbol = ("BITSTAMP:BTC.USD");
        setRunsInterval(900);
        setPercentEquityRisked(percentEquityRisked);
        setAtrStop(atrStop);
        setAtrTarget(atrTarget);
        setSlippage(slippage);
        setMaxLossTarget(atrStop * percentEquityRisked);
        //notionalBaseBalance = new DiscreteAmount(Long.parseLong("10000000"), getMarket().getQuote().getBasis());
        // originalBaseNotionalBalance = new DiscreteAmount(Long.parseLong("00100000"), getMarket().getQuote().getBasis());
        leadingMarket = getLeadingMarket(leadingMarketSymbol);
    }

    //@Override
    //@When("@Priority(3) on LongHighRunIndicator as trigger select trigger.high from LongHighRunIndicator")
    @When("@Priority(3) on BuyCorrelationIndicator as trigger select trigger.high from BuyCorrelationIndicator")
    void handleRunsLongHighIndicator(Market market, double d) {
        super.handleLongHighIndicator(1.0, 1.0, d, ExecutionInstruction.MAKER, FillType.STOP_LOSS, market, null, false);
    }

    //@Override
    //@When("@Priority(3) on LongLowRunIndicator as trigger select trigger.low from LongLowRunIndicator")
    @When("@Priority(3) on SellCorrelationIndicator as trigger select trigger.low from SellCorrelationIndicator")
    void handleRunsLongLowIndicator(Market market, double d) {
        super.handleShortLowIndicator(1.0, 1.0, ExecutionInstruction.MAKER, market, null, false, false);
    }

    @Transient
    public static Market getLeadingMarket() {
        return leadingMarket;
    }

    private Market getLeadingMarket(String symbol) {

        //move to    Market,findOrCreate
        leadingMarket = (Market) context.getInjector().getInstance(Market.class).forSymbol(symbol);
        if (leadingMarket == null)
            throw new Error("Could not find Market for symbol " + symbol);
        return leadingMarket;
    }

    @Transient
    public static Double getRunsInterval() {
        return runsInterval;
    }

    @Transient
    public static Double getTrendInterval() {
        return trendInterval;
    }

    @Transient
    public double getBidATR(Market market) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_RUN_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @Transient
    public double getTradeATR(Market market) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_RUN_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @Transient
    public double getAskATR(Market market) {
        List<Object> events = null;
        double atr = 0;
        try {
            events = context.loadStatementByName("GET_TRADE_RUN_ATR");
            if (events.size() > 0) {
                HashMap value = ((HashMap) events.get(events.size() - 1));
                if (value.get("atr") != null) {
                    atr = (double) value.get("atr");
                }

            }
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (DeploymentException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return atr;

    }

    @Override
    public double getAskATR(Tradeable market, double interval) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getBidATR(Tradeable market, double interval) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTradeATR(Tradeable market, double interval) {
        // TODO Auto-generated method stub
        return 0;
    }
}
