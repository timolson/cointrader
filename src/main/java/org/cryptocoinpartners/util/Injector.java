package org.cryptocoinpartners.util;

import java.util.Properties;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.module.PersistanceModule;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.persist.jpa.JpaPersistModule;

/**
 * Guice doesn't allow binding overrides so we have to store the Configuration separately and
 * add it in just before instance creation
 *
 * My first time using Guice... not a fan.  Should have tried Pico.
 *
 * @author Tim Olson
 */
public class Injector {

    public static Injector root() {
        return root;
    }

    public Injector createChildInjector(java.lang.Iterable<? extends com.google.inject.Module> iterable) {
        return new Injector(injector.createChildInjector(iterable), config);
    }

    public Injector createChildInjector(com.google.inject.Module... modules) {
        return new Injector(injector.createChildInjector(modules), config);
    }

    public <T> T getInstance(Class<T> cls) {
        T instance = ic().getInstance(cls);
        return instance;
    }

    public void injectMembers(Object o) {
        ic().injectMembers(o);
    }

    public Injector withConfig(Configuration config) {
        setConfig(config);
        return this;
    }

    private com.google.inject.Injector ic() {
        if (injectorWithConfig == null) {
            injectorWithConfig = injector.createChildInjector(new Module() {
                @Override
                public void configure(Binder binder) {
                    binder.bind(Injector.class).toProvider(new Provider<Injector>() {
                        @Override
                        public Injector get() {
                            return Injector.this;
                        }
                    });
                    binder.bind(Configuration.class).toProvider(new Provider<Configuration>() {
                        @Override
                        public Configuration get() {
                            return config;
                        }
                    });
                }
            });
        }
        return injectorWithConfig;
    }

    public Configuration getConfig() {
        return config;
    }

    public void setConfig(Configuration config) {
        this.config = config;
        injectorWithConfig = null;
    }

    private Injector(com.google.inject.Injector injector, Configuration config) {
        this.injector = injector;
        this.config = config;
    }

    private Configuration config;
    private final com.google.inject.Injector injector;
    private com.google.inject.Injector injectorWithConfig;

    private static Injector root;

    static {
        //  int retryCount = ConfigUtil.combined().getInt("db.persist.retry");
        Properties properties = new Properties();
        //Map<String, String> properties = new HashMap<String, String>();

        //daretryCount = ConfigUtil.combined().getInt("db.persist.retry");

        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.connection.driver_class", ConfigUtil.combined().getString("db.driver"));
        properties.put("hibernate.dialect", ConfigUtil.combined().getString("db.dialect"));
        properties.put("hibernate.connection.url", ConfigUtil.combined().getString("db.url"));
        properties.put("hibernate.connection.username", ConfigUtil.combined().getString("db.username"));
        properties.put("hibernate.connection.password", ConfigUtil.combined().getString("db.password"));
        properties.put("hibernate.ejb.naming_strategy", "org.hibernate.cfg.ImprovedNamingStrategy");

        properties.put("hibernate.connection.autocommit", "true");
        properties.put("org.hibernate.flushMode", "AUTO");
        properties.put("hibernate.connection.release_mode", "auto");

        properties.put("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider");
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory");
        //	"org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory");
        properties.put("hibernate.cache.use_second_level_cache", "true");

        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.cache.use_structured_entries ", "true");
        properties.put("net.sf.ehcache.configurationResourceName", "META-INF/ehcache.xml");

        properties.put("hibernate.c3p0.min_size", "10");
        properties.put("hibernate.c3p0.max_size", ConfigUtil.combined().getString("db.pool.size"));
        properties.put("hibernate.c3p0.acquire_increment", ConfigUtil.combined().getString("db.pool.growth"));

        properties.put("hibernate.showSql", "true");
        properties.put("hibernate.format_sql", "true");
        properties.put("hibernate.use_sql_comments", "true");

        // properties.put("hibernate.c3p0.debugUnreturnedConnectionStackTraces", "true");
        //properties.put("hibernate.c3p0.unreturnedConnectionTimeout", "4000");
        // properties.put("hibernate.c3p0.idle_test_period", "300");
        //  properties.put("hibernate.c3p0.max_statements", "0");
        // properties.put("hibernate.c3p0.maxIdleTimeExcessConnections", "2");
        // properties.put("hibernate.c3p0.timeout", "300");
        properties.put("hibernate.c3p0.maxIdleTime", "21600");

        //properties.put("hibernate.c3p0.checkoutTimeout", "500");
        properties.put("hibernate.c3p0.preferredTestQuery", "SELECT 1 from exchange");
        //  properties.put("hibernate.c3p0.maxConnectionAge", ConfigUtil.combined().getString("db.max.connection.age"));
        final String testConnection = ConfigUtil.combined().getString("db.test.connection", "false");
        properties.put("hibernate.c3p0.testConnectionOnCheckout", testConnection);
        // properties.put("hibernate.c3p0.testConnectionOnCheckin", testConnection);
        properties.put("hibernate.c3p0.acquireRetryDelay", ConfigUtil.combined().getString("db.acquire_retry_delay", "1000"));
        properties.put("hibernate.c3p0.acquireRetryAttempts", ConfigUtil.combined().getString("db.acquire_retry_attempts", "30"));
        properties.put("hibernate.c3p0.breakAfterAcquireFailure", ConfigUtil.combined().getString("db.break_after_acquire_failure", "false"));
        properties.put("hibernate.c3p0.checkoutTimeout", ConfigUtil.combined().getString("db.checkout_timeout", "5000"));
        properties.put("hibernate.c3p0.idleConnectionTestPeriod", ConfigUtil.combined().getString("db.idle_connection_test_period", "10800"));
        properties.put("hibernate.c3p0.numHelperThreads", ConfigUtil.combined().getString("db.num_helper_threads", "10"));

        //  properties.put("hibernate.c3p0.max_statements", "0");
        //  properties.put("hibernate.c3p0.maxStatementsPerConnection", "100");
        // properties.put("hibernate.c3p0.validate", "true");
        //   properties.put("hibernate.c3p0.idleConnectionTestPeriod", "100");

        properties.put("javax.persistence.sharedCache.mode", "ENABLE_SELECTIVE");
        // properties.put("javax.persistence.LockModeType", "OPTIMISTIC");

        // root = new Injector(Guice.createInjector(new LogInjector(), new PersistanceModule()), ConfigUtil.combined());
        //  new JpaPersistModule("org.cryptocoinpartners.schema")
        root = new Injector(Guice.createInjector(new LogInjector(), new JpaPersistModule("org.cryptocoinpartners.schema").properties(properties),
                new PersistanceModule()), ConfigUtil.combined());
    }
}
