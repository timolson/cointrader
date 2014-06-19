package org.cryptocoinpartners.util;

import com.google.inject.Binder;
import com.google.inject.Module;
import org.apache.commons.configuration.Configuration;

import javax.inject.Provider;


/**
* @author Tim Olson
*/
public class ConfigInjector implements Module {


    public void configure(Binder binder) {
        binder.bind(Configuration.class).toInstance(config);
    }


    public ConfigInjector(Configuration config) {
        this.config = config;
    }


    private final Configuration config;


}
