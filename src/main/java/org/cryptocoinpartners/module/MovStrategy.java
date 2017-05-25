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
public class MovStrategy extends TestStrategy {

    static double percentEquityRisked = 0.01;
    //1 day 2 day with 1 atr and twice stop is 61% wins, 1.6 win loss, 50% exencacys
    static double atrStop = 2;
    static double atrTarget = 20;
    //* atrStop;
    static long slippage = 1;
    static double maxLossTarget = atrStop * percentEquityRisked;

    //double maxLossTarget = 0.25;

    @Inject
    public MovStrategy(Context context, Configuration config) {
        super(context, config);
        String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        addMarket(Market.forSymbol(marketSymbol), 1.0);

        setRunsInterval(1800);
        setPercentEquityRisked(percentEquityRisked);
        setAtrStop(atrStop);
        setAtrTarget(atrTarget);
        setSlippage(slippage);
        setMaxLossTarget(atrStop * percentEquityRisked);
    }

    //exit long
    //@When("@Priority(4) on ShortHighRunBarIndicator as trigger select trigger.high from ShortHighRunBarIndicator")
    //  @When("@Priority(4) on ShortLowMovIndicator as trigger select trigger.low from ShortLowMovIndicator")
    void handleRunsShortLowIndicator(Market market, double d) {
        super.handleShortLowIndicator(1.0, d, ExecutionInstruction.TAKER, market, null, false, false);
    }

    // exit short
    // @When("@Priority(4) on ShortHighMovIndicator as trigger select trigger.high from ShortHighMovIndicator")
    //counter @When("@Priority(4) on ShortLowRunBarIndicator as trigger select trigger.low from ShortLowRunBarIndicator")
    void handleRunsShortHighIndicator(Market market, double d) {
        super.handleShortHighIndicator(1.0, d, ExecutionInstruction.TAKER, market, null, false);
    }

    // enter long
    @When("@Priority(3) on LongHighMovIndicator as trigger select trigger.high from LongHighMovIndicator")
    //counter@When("@Priority(3) on LongLowRunBarIndicator as trigger select trigger.low from LongLowRunBarIndicator")
    void handleRunsLongHighIndicator(Market market, double d) {
        super.handleLongHighIndicator(1.0, 1.0, d, ExecutionInstruction.MAKER, FillType.STOP_LOSS, market, null, false);
    }

    //enter short
    //counter @When("@Priority(3) on LongHighRunBarIndicator as trigger select trigger.high from LongHighRunBarIndicator")
    @When("@Priority(3) on LongLowMovIndicator as trigger select trigger.low from LongLowMovIndicator")
    void handleRunsLongLowIndicator(Market market, double d) {
        super.handleLongLowIndicator(1.0, 1.0, d, ExecutionInstruction.MAKER, FillType.STOP_LOSS, market, null, false);
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
