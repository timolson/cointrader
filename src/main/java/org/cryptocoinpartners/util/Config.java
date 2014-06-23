package org.cryptocoinpartners.util;

import org.apache.commons.configuration.*;
import org.apache.commons.configuration.tree.OverrideCombiner;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class Config {

    public static CombinedConfiguration combined() { return combined; }

    public static PropertiesConfiguration defaults() { return defaultConfig; }
    public static PropertiesConfiguration user() { return userConfig; }
    public static PropertiesConfiguration buildtime() { return buildtimeConfig; }
    public static SystemConfiguration system() { return sysConfig; }
    public static MapConfiguration commandLine() { return clConfig; }


    public static void init(String filename, Map<String,String> commandLine ) throws ConfigurationException {
        boolean loadUserPropertiesFile = new File(filename).exists();
        if( !loadUserPropertiesFile  )
            log.warn("Could not find configuration file \"" + filename + "\"");
        clConfig = new MapConfiguration(commandLine);
        sysConfig = new SystemConfiguration();
        if( loadUserPropertiesFile )
            userConfig = new PropertiesConfiguration(filename);
        else
            userConfig = new PropertiesConfiguration();
        URL defaultProps = Config.class.getResource("/cointrader-default.properties");
        if( defaultProps == null )
            throw new ConfigurationException("Could not load cointrader-default.properties");
        defaultConfig = new PropertiesConfiguration(defaultProps);
        URL buildtimeProps = Config.class.getResource("/org/cryptocoinpartners/buildtime.properties");
        if( buildtimeProps == null )
            throw new ConfigurationException("Could not load buildtime.properties");
        buildtimeConfig = new PropertiesConfiguration(buildtimeProps);
        combined = buildConfig(Collections.<AbstractConfiguration>emptyList());
        if( log.isDebugEnabled() )
            log.debug("Combined Configuration:\n"+ configAsString(combined));
    }


    public static CombinedConfiguration module( Object... keyValuePairs ) {
        if( keyValuePairs.length % 2 != 0 )
            throw new Error("Configuration parameters must be key-value pairs.  Found an odd number.");
        HashMap<String,Object> map = new HashMap<>();
        for( int i = 0; i < keyValuePairs.length; i++ )
            map.put(keyValuePairs[i++].toString(),keyValuePairs[i]);
        return Config.module(Collections.singletonList(new MapConfiguration(map)));
    }


    public static CombinedConfiguration module( Collection<? extends AbstractConfiguration> moduleConfigs ) {
        CombinedConfiguration result = buildConfig(moduleConfigs);
        if( log.isDebugEnabled() )
            log.debug("Module Configuration:\n"+configAsString(result));
        return result;
    }


    public static List<String> getPathProperty(String pathProperty) {
        CombinedConfiguration config = combined();
        return getPathProperty(config, pathProperty);
    }


    public static List<String> getPathProperty(CombinedConfiguration config, String pathProperty) {
        String modulePath = config.getString(pathProperty, "");
        List<String> paths = new ArrayList<>(Arrays.asList(modulePath.split(":")));
        paths.add("org.cryptocoinpartners.module");
        paths.remove("");
        return paths;
    }


    private static CombinedConfiguration buildConfig(Collection<? extends AbstractConfiguration> intermediateConfigs) {
        final CombinedConfiguration result = new CombinedConfiguration(new OverrideCombiner());
        result.addConfiguration(buildtimeConfig); // buildtime config cannot be overridden
        result.addConfiguration(clConfig);
        result.addConfiguration(sysConfig);
        for( AbstractConfiguration moduleConfig : intermediateConfigs )
            result.addConfiguration(moduleConfig);
        if( !userConfig.isEmpty() )
            result.addConfiguration(userConfig);
        result.addConfiguration(defaultConfig);
        return result;
    }


    private static String configAsString(CombinedConfiguration configuration) {
        StringWriter out = new StringWriter();
        PrintWriter pout = new PrintWriter(out);
        ConfigurationUtils.dump(configuration, pout);
        try {
            pout.close();
            out.close();
        }
        catch( IOException e ) {
            throw new Error(e);
        }
        ArrayList<String> outLines = new ArrayList<>();
        for( String line : out.toString().split("\n") ) {
            if( !line.contains("password") && !line.contains("secret") )
                outLines.add(line);
        }
        return StringUtils.join(outLines,"\n");
    }


    private static PropertiesConfiguration defaultConfig;
    private static PropertiesConfiguration buildtimeConfig;
    private static PropertiesConfiguration userConfig;
    private static MapConfiguration clConfig;
    private static SystemConfiguration sysConfig;
    private static CombinedConfiguration combined;
    private static Logger log = LoggerFactory.getLogger(Config.class);
}
