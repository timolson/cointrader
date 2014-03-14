package com.cryptocoinpartners.util;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import java.io.File;


/**
 * @author Tim Olson
 */
public class Config {

    public static Configuration get() {
        return instance;
    }

    public static void init(String filename) throws ConfigurationException {
        if( ! new File(filename).exists() )
            throw new ConfigurationException("Could not find configuration file \""+filename+"\"");
        instance = new PropertiesConfiguration(filename);
    }

    private static Configuration instance;
}
