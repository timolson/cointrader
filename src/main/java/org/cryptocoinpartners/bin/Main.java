package org.cryptocoinpartners.bin;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.apache.commons.configuration.ConfigurationException;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.Injector;
import org.cryptocoinpartners.util.PersistUtil;
import org.cryptocoinpartners.util.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * This is the only entry point required into the Cryptocoin Partners executable world.  Depending on parameters passed,
 * this will act as a ticker plant, a strategy backtester, or an online trading system, either paper trading or live.
 */
public class Main {

    static final String DEFAULT_PROPERTIES_FILENAME = "cointrader.properties";
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    final static ExecutorService service = Executors.newSingleThreadExecutor();

    static class MainParams {
        @SuppressWarnings("UnusedDeclaration")
        @Parameter(names = { "h", "help", "-h", "-H", "-help", "--help" }, help = true, description = "Show this usage help")
        boolean help;

        @Parameter(names = { "-f", "-properties-file" }, description = "location of the cointrader.properties config file")
        String propertiesFilename = DEFAULT_PROPERTIES_FILENAME;

        @DynamicParameter(names = { "-D" }, description = "use the -D flag to set configuration properties \"-Ddb.username=dbuser\"")
        Map<String, String> definitions = new HashMap<>();
    }

    static class MainParamsOnly extends MainParams {
        @Parameter
        List<String> everythingElseIgnored;
    }

    public static void main(String[] args) throws ConfigurationException, IllegalAccessException, InstantiationException {
        // first, parse only the MainParams to get the properties file location and initialize ConfigUtil and rootInjector
        MainParamsOnly mainParamsOnly = new MainParamsOnly();
        Semaphore semaphore = new Semaphore(0);
        JCommander parameterParser = new JCommander(mainParamsOnly);
        parameterParser.setProgramName(Main.class.getName());
        try {
            parameterParser.parse();
        } catch (Throwable t) {
            log.error("Threw a Execption, full stack trace follows:", t);
            t.printStackTrace();
            System.exit(1);
        }
        ConfigUtil.init(mainParamsOnly.propertiesFilename, mainParamsOnly.definitions);
        Injector rootInjector = Injector.root();

        // now parse the full command line
        MainParams mainParams = new MainParams();
        parameterParser = new JCommander(mainParams);

        // find the commands, register with the parameter parser, and put them into the commandLookup map
        Map<String, RunMode> runModesByName = new HashMap<>();
        Set<Class<? extends RunMode>> runModeClasses = ReflectionUtil.getSubtypesOf(RunMode.class);
        for (Class<? extends RunMode> runModeClass : runModeClasses) {
            if (Modifier.isAbstract(runModeClass.getModifiers()))
                continue;
            Parameters annotation = runModeClass.getAnnotation(Parameters.class);
            if (annotation == null) {
                System.err.println("The RunMode subclass " + runModeClass + " must have the com.beust.jcommander.Parameters annotation.");
                System.exit(1);
            }
            RunMode runMode = rootInjector.getInstance(runModeClass);
            for (String commandName : annotation.commandNames())
                runModesByName.put(commandName, runMode);
            parameterParser.addCommand(runMode);
        }

        // now parse the commandline
        try {
            parameterParser.parse(args);
        } catch (MissingCommandException e) {
            System.err.println(e.getMessage());
            parameterParser.usage();
            System.exit(7002);
        }
        String commandName = parameterParser.getParsedCommand();
        // find the runmode, if any
        RunMode runMode = runModesByName.get(commandName);
        if (runMode == null || mainParams.help) {
            parameterParser.usage();
            System.exit(7001);
        }
        PersistUtil.init();
        try {
            runMode.run(semaphore);
            semaphore.acquire(1);
        } catch (Throwable t) {
            log.error("Uncaught error while running " + runMode.getClass().getSimpleName(), t);
        } finally {
                 PersistUtil.shutdown();
        }
    }

}
