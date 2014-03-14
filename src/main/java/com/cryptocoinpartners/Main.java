package com.cryptocoinpartners;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.cryptocoinpartners.schema.Esper;
import com.cryptocoinpartners.schema.Market;
import com.cryptocoinpartners.schema.Security;
import com.cryptocoinpartners.schema.Trade;
import com.cryptocoinpartners.service.MarketDataService;
import com.cryptocoinpartners.util.Config;
import com.cryptocoinpartners.util.PersistUtil;
import org.apache.commons.configuration.*;
import org.joda.time.Instant;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.File;
import java.math.BigDecimal;
import java.util.Iterator;


/**
 * This is the only entry point required into the Cryptocoin Partners executable world.  Depending on parameters passed,
 * this will act as a ticker plant, a strategy backtester, or an online trading system, either paper trading or live.
 */
public class Main
{
    static class MainParams {
        static final String DEFAULT_PROPERTIES_FILENAME = "trader.properties";

        @SuppressWarnings("UnusedDeclaration")
        @Parameter(names = {"h","help","-h","-H","-help","--help"}, help = true, description = "Show this usage help")
        boolean help;

        @Parameter(names = {"-f","--properties-file"})
        String propertiesFilename = DEFAULT_PROPERTIES_FILENAME;
    }

    @Parameters(commandNames = "ticker", commandDescription = "Launch a ticker plant")
    static class TickerParams {
    }

    @Parameters(commandNames = "backtest", commandDescription = "Backtest a strategy")
    static class BacktestParams {
    }

    @Parameters(commandNames = "paper", commandDescription = "Execute paper trades")
    static class PaperParams {
    }

    @Parameters(commandNames = "live", commandDescription = "Execute live trades")
    static class LiveParams {
    }

    public static void main( String[] args ) throws ConfigurationException {
        MainParams mainParams = new MainParams();
        JCommander jc = new JCommander(mainParams);
        jc.setProgramName(Main.class.getName());
        TickerParams tickerParams = new TickerParams();
        jc.addCommand(tickerParams);
        BacktestParams backtestParams = new BacktestParams();
        jc.addCommand(backtestParams);
        PaperParams paperParams = new PaperParams();
        jc.addCommand(paperParams);
        LiveParams liveParams = new LiveParams();
        jc.addCommand(liveParams);
        jc.parse(args);
        String command = jc.getParsedCommand();
        if( command == null )
            jc.usage();
        else {
            importConfig(mainParams.propertiesFilename);
            if( command.equals("ticker") )
                ticker(tickerParams);
            else if( command.equals("backtest") )
                backtest(backtestParams);
            else if( command.equals("paper") )
                paper(paperParams);
            else if( command.equals("live") )
                live(liveParams);
            else
                jc.usage();
        }
    }


    private static void importConfig(String propertiesFilename) throws ConfigurationException {
        Config.init(propertiesFilename);
    }


    private static void live(LiveParams liveParams) {
        System.err.println("live trading not implemented");
    }


    private static void paper(PaperParams paperParams) {
        System.err.println("paper trading not implemented");
    }


    private static void backtest(BacktestParams backtestParams) {
        System.err.println("backtesting not implemented");
    }


    private static void ticker(TickerParams tickerParams) {
        EntityManager em = PersistUtil.getEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        Security security = new Security(Market.BITFINEX, "BTC");
        em.persist(security);
        em.persist(new Trade(security, Instant.now(), BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(3)));
        transaction.commit();
        //Esper esper = new Esper();
        //MarketDataService.subscribeAll(esper);
    }

}
