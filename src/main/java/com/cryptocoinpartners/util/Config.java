package com.cryptocoinpartners.util;

import org.apache.commons.configuration.*;

import java.io.File;
import java.util.Collection;
import java.util.Map;


/**
 * @author Tim Olson
 */
public class Config {

    public static CombinedConfiguration combined() { return combined; }

    public static PropertiesConfiguration application() { return appConfig; }
    public static SystemConfiguration system() { return sysConfig; }
    public static MapConfiguration commandLine() { return clConfig; }


    public static void init(String filename, Map<String,String> commandLine ) throws ConfigurationException {
        if( ! new File(filename).exists() )
            throw new ConfigurationException("Could not find configuration file \""+filename+"\"");
        appConfig = new PropertiesConfiguration(filename);
        clConfig = new MapConfiguration(commandLine);
        sysConfig = new SystemConfiguration();
        combined = new CombinedConfiguration();
        combined.addConfiguration(appConfig);
        combined.addConfiguration(sysConfig);
        combined.addConfiguration(clConfig);
    }


    public static Configuration module( Collection<? extends AbstractConfiguration> moduleConfigs ) {
        final CombinedConfiguration result = new CombinedConfiguration();
        result.addConfiguration(appConfig);
        for( AbstractConfiguration moduleConfig : moduleConfigs )
            result.addConfiguration(moduleConfig);
        result.addConfiguration(sysConfig);
        result.addConfiguration(clConfig);
        return result;
    }


    private static PropertiesConfiguration appConfig;
    private static MapConfiguration clConfig;
    private static SystemConfiguration sysConfig;
    private static CombinedConfiguration combined;
}
