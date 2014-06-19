package org.cryptocoinpartners.util;

import com.google.inject.*;
import org.apache.commons.configuration.Configuration;


/**
 * Guice doesn't allow binding overrides so we have to store the Configuration separately and
 * add it in just before instance creation
 *
 * My first time using Guice... not a fan.  Should have tried Pico.
 *
 * @author Tim Olson
 */
public class Injector {

    public static Injector root() { return root; }

    public Injector createChildInjector(java.lang.Iterable<? extends com.google.inject.Module> iterable) {
        return new Injector(injector.createChildInjector(iterable),config);
    }


    public Injector createChildInjector(com.google.inject.Module... modules) {
        return new Injector(injector.createChildInjector(modules),config);
    }


    public <T> T getInstance( Class<T> cls ) {
        return ic().getInstance(cls);
    }


    public void injectMembers(Object o) {
        ic().injectMembers(o);
    }


    public Injector withConfig(Configuration config) {
        setConfig(config);
        return this;
    }


    private com.google.inject.Injector ic() {
        if( injectorWithConfig == null ) {
            injectorWithConfig = injector.createChildInjector(new Module() {
                public void configure(Binder binder) {
                    binder.bind(Injector.class).toProvider(new Provider<Injector>() {
                        public Injector get() {
                            return Injector.this;
                        }
                    });
                    binder.bind(Configuration.class).toProvider(new Provider<Configuration>() {
                        public Configuration get() { return config; }
                    });
                }
            });
        }
        return injectorWithConfig;
    }


    public Configuration getConfig() { return config; }
    public void setConfig(Configuration config) { this.config = config; injectorWithConfig = null; }


    private Injector(com.google.inject.Injector injector, Configuration config) {
        this.injector = injector;
        this.config = config;
    }


    private Configuration config;
    private com.google.inject.Injector injector;
    private com.google.inject.Injector injectorWithConfig;


    private static Injector root;


    static {
        root = new Injector(Guice.createInjector(new LogInjector()),Config.combined());
    }
}
