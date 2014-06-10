package org.cryptocoinpartners.util;

import org.apache.commons.configuration.*;
import org.apache.commons.configuration.tree.OverrideCombiner;

import java.io.File;
import java.util.Collection;
import java.util.Map;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class Config {

    public static CombinedConfiguration combined() { return combined; }

    public static PropertiesConfiguration defaults() { return defaultConfig; }
    public static PropertiesConfiguration application() { return appConfig; }
    public static SystemConfiguration system() { return sysConfig; }
    public static MapConfiguration commandLine() { return clConfig; }


    public static void init(String filename, Map<String,String> commandLine ) throws ConfigurationException {
        if( ! new File(filename).exists() )
            throw new ConfigurationException("Could not find configuration file \""+filename+"\"");
        clConfig = new MapConfiguration(commandLine);
        sysConfig = new SystemConfiguration();
        appConfig = new PropertiesConfiguration(filename);
        defaultConfig = new PropertiesConfiguration(Config.class.getResource("/trader-default.properties"));
        combined = new CombinedConfiguration(new OverrideCombiner());
        combined.addConfiguration(clConfig);
        combined.addConfiguration(sysConfig);
        combined.addConfiguration(appConfig);
        combined.addConfiguration(defaultConfig);
    }


    public static AbstractConfiguration module( Collection<? extends AbstractConfiguration> moduleConfigs ) {
        final CombinedConfiguration result = new CombinedConfiguration(new OverrideCombiner());
        result.addConfiguration(clConfig);
        result.addConfiguration(sysConfig);
        for( AbstractConfiguration moduleConfig : moduleConfigs )
            result.addConfiguration(moduleConfig);
        result.addConfiguration(appConfig);
        return result;
    }


    private static PropertiesConfiguration defaultConfig;
    private static PropertiesConfiguration appConfig;
    private static MapConfiguration clConfig;
    private static SystemConfiguration sysConfig;
    private static CombinedConfiguration combined;
}
