package com.cryptocoinpartners.util;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;


/**
 * @author Tim Olson
 */
public class Config {

    public static Configuration get() {
        return instance;
    }

    public static void init(String filename) throws ConfigurationException {
        instance = new PropertiesConfiguration(filename);
    }

    private static Configuration instance;
}
