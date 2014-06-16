package org.cryptocoinpartners.bin;

import com.beust.jcommander.*;
import org.apache.commons.configuration.ConfigurationException;
import org.cryptocoinpartners.util.Config;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.*;


/**
 * This is the only entry point required into the Cryptocoin Partners executable world.  Depending on parameters passed,
 * this will act as a ticker plant, a strategy backtester, or an online trading system, either paper trading or live.
 */
public class Main
{
    static final String DEFAULT_PROPERTIES_FILENAME = "cointrader.properties";
    static final String FALLBACK_PROPERTIES_FILENAME = "cointrader-default.properties";
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static class MainParams {
        @SuppressWarnings("UnusedDeclaration")
        @Parameter(names = {"h","help","-h","-H","-help","--help"}, help = true, description = "Show this usage help")
        boolean help;

        @Parameter(names = {"-f","-properties-file"}, description = "location of the cointrader.properties config file")
        String propertiesFilename = DEFAULT_PROPERTIES_FILENAME;

        @DynamicParameter( names = {"-D"}, description = "use the -D flag to set configuration properties \"-Ddb.username=dbuser\"" )
        Map<String,String> definitions = new HashMap<>();
    }


    public static void main( String[] args ) throws ConfigurationException, IllegalAccessException, InstantiationException {
        MainParams mainParams = new MainParams();
        JCommander parameterParser = new JCommander(mainParams);
        parameterParser.setProgramName(Main.class.getName());

        // find the commands, register with the parameter parser, and put them into the commandLookup map
        Map<String,RunMode> commandLookup = new HashMap<>();
        Set<Class<? extends RunMode>> commands = ReflectionUtil.getSubtypesOf(RunMode.class);
        for( Class<? extends RunMode> commandType : commands ) {
            if( Modifier.isAbstract(commandType.getModifiers()))
                continue;
            RunMode runMode = commandType.newInstance();
            Parameters annotation = runMode.getClass().getAnnotation(Parameters.class);
            if( annotation == null ) {
                System.err.println("The RunMode subclass "+ runMode.getClass()+" must have the com.beust.jcommander.Parameters annotation.");
                System.exit(1);
            }
            for( String commandName : annotation.commandNames() ) {
                commandLookup.put(commandName, runMode);
            }
            parameterParser.addCommand(runMode);
        }

        // now parse the commandline
        try {
            parameterParser.parse(args);
        }
        catch( MissingCommandException e ) {
            System.err.println(e.getMessage());
            parameterParser.usage();
            System.exit(7002);
        }
        String commandName = parameterParser.getParsedCommand();
        // find the runmode, if any
        RunMode runMode = commandLookup.get(commandName);
        if( runMode == null || mainParams.help ) {
            parameterParser.usage();
            System.exit(7001);
        }
        try {
            Config.init(mainParams.propertiesFilename, mainParams.definitions);
        }
        catch( ConfigurationException e ) {
            System.err.println("Could not load properties");
            e.printStackTrace(System.err);
            System.exit(1);
        }
        try {
            runMode.run();
        }
        catch( Throwable t ) {
            log.error("Uncaught error while running "+ runMode.getClass().getSimpleName(),t);
        }
        finally {
            PersistUtil.shutdown();
        }
    }


}