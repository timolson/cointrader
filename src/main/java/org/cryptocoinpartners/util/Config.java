package org.cryptocoinpartners.util;

import org.apache.commons.configuration.*;
import org.apache.commons.configuration.tree.OverrideCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class Config {

    public static CombinedConfiguration combined() { return combined; }

    public static PropertiesConfiguration defaults() { return defaultConfig; }
    public static PropertiesConfiguration user() { return userConfig; }
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
        combined = buildConfig(Collections.<AbstractConfiguration>emptyList());
        if( log.isDebugEnabled() )
            log.debug("Combined Configuration:\n"+ configAsString(combined));
    }


    public static CombinedConfiguration module( Collection<? extends AbstractConfiguration> moduleConfigs ) {
        CombinedConfiguration result = buildConfig(moduleConfigs);
        if( log.isDebugEnabled() )
            log.debug("Module Configuration:\n"+configAsString(result));
        return result;
    }


    private static CombinedConfiguration buildConfig(Collection<? extends AbstractConfiguration> intermediateConfigs) {
        final CombinedConfiguration result = new CombinedConfiguration(new OverrideCombiner());
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
        return out.toString();
    }


    private static PropertiesConfiguration defaultConfig;
    private static PropertiesConfiguration userConfig;
    private static MapConfiguration clConfig;
    private static SystemConfiguration sysConfig;
    private static CombinedConfiguration combined;
    private static Logger log = LoggerFactory.getLogger(Config.class);
}
